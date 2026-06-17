package com.tminos.productscene.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.OSSException;
import com.tminos.productscene.config.OssConfig;
import com.tminos.productscene.minio.MinioStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OssService {

    private final OssConfig ossConfig;
    private final MinioStorageClient minioStorageClient;

    public boolean isEnabled() {
        return isMinioEnabled() || isAliyunEnabled();
    }

    public record OssPingResult(boolean ok, String message, String requestId, String errorCode) {}

    public record OssDeleteResult(boolean deleted, boolean skipped, String message, String requestId, String errorCode) {}

    public String uploadSkuImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (isMinioEnabled()) {
            return uploadSkuImageToMinio(file);
        }
        if (!isAliyunEnabled()) {
            throw new IllegalStateException("Storage is disabled (aliyun.oss.enabled=false and tminos.minio.enabled=false)");
        }
        return uploadSkuImageToAliyun(file);
    }

    private String uploadSkuImageToMinio(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String ext = guessExtension(originalName, file.getContentType());
        String key = buildObjectKey("sku", ext);
        try (InputStream inputStream = file.getInputStream()) {
            return minioStorageClient.upload(key, inputStream, file.getSize(), file.getContentType());
        } catch (Exception e) {
            log.error("MinIO upload failed: {}", e.getMessage(), e);
            throw new RuntimeException("MinIO upload failed: " + e.getMessage(), e);
        }
    }

    private String uploadSkuImageToAliyun(MultipartFile file) {
        String accessKeyId = ossConfig.getAccessKeyId();
        String accessKeySecret = ossConfig.getAccessKeySecret();
        if (accessKeyId != null) {
            accessKeyId = accessKeyId.trim();
        }
        if (accessKeySecret != null) {
            accessKeySecret = accessKeySecret.trim();
        }
        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)) {
            throw new IllegalStateException("Missing OSS credentials in config: aliyun.oss.access-key-id/access-key-secret");
        }
        if (!StringUtils.hasText(ossConfig.getEndpoint()) || !StringUtils.hasText(ossConfig.getBucket())) {
            throw new IllegalStateException("Missing OSS config: aliyun.oss.endpoint / aliyun.oss.bucket");
        }

        String originalName = file.getOriginalFilename();
        String ext = guessExtension(originalName, file.getContentType());

        String key = buildObjectKey("sku", ext);

        OSS client = null;
        try {
            String endpoint = ossConfig.getEndpoint().trim();
            client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(file.getSize());
            if (StringUtils.hasText(file.getContentType())) {
                meta.setContentType(file.getContentType());
            }
            if (StringUtils.hasText(originalName)) {
                String encoded = URLEncoder.encode(originalName, StandardCharsets.UTF_8);
                meta.setContentDisposition("inline; filename*=UTF-8''" + encoded);
            }

            try (InputStream is = file.getInputStream()) {
                client.putObject(ossConfig.getBucket(), key, is, meta);
            }

            return toAliyunPublicUrl(key);
        } catch (Exception e) {
            log.error("OSS upload failed: {}", e.getMessage(), e);
            throw new RuntimeException("OSS upload failed: " + e.getMessage(), e);
        } finally {
            if (client != null) {
                try {
                    client.shutdown();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public OssDeleteResult deleteByUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return new OssDeleteResult(false, true, "Empty url", null, null);
        }

        if (isMinioEnabled()) {
            MinioStorageClient.MinioDeleteResult minioDeleteResult = minioStorageClient.deleteByUrl(url);
            if (!minioDeleteResult.skipped()) {
                return new OssDeleteResult(minioDeleteResult.deleted(), false, minioDeleteResult.message(), null, null);
            }
            if (!isAliyunEnabled()) {
                return new OssDeleteResult(false, true, minioDeleteResult.message(), null, null);
            }
        }

        if (!isAliyunEnabled()) {
            return new OssDeleteResult(false, true, "Storage disabled", null, null);
        }

        String key = extractAliyunKeyIfOwned(url);
        if (!StringUtils.hasText(key)) {
            return new OssDeleteResult(false, true, "URL not owned by configured OSS", null, null);
        }

        OSS client = null;
        try {
            String endpoint = ossConfig.getEndpoint().trim();
            String ak = ossConfig.getAccessKeyId() != null ? ossConfig.getAccessKeyId().trim() : null;
            String sk = ossConfig.getAccessKeySecret() != null ? ossConfig.getAccessKeySecret().trim() : null;
            client = new OSSClientBuilder().build(endpoint, ak, sk);
            client.deleteObject(ossConfig.getBucket(), key);
            return new OssDeleteResult(true, false, "OK", null, null);
        } catch (OSSException e) {
            return new OssDeleteResult(false, false, e.getMessage(), e.getRequestId(), e.getErrorCode());
        } catch (ClientException e) {
            return new OssDeleteResult(false, false, e.getMessage(), null, null);
        } catch (Exception e) {
            return new OssDeleteResult(false, false, e.getMessage(), null, null);
        } finally {
            if (client != null) {
                try {
                    client.shutdown();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String extractAliyunKeyIfOwned(String url) {
        String raw = url.trim();
        int q = raw.indexOf('?');
        if (q >= 0) {
            raw = raw.substring(0, q);
        }

        String publicDomain = ossConfig.getPublicDomain();
        if (StringUtils.hasText(publicDomain)) {
            String base = publicDomain.trim();
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            if (raw.startsWith(base + "/")) {
                return raw.substring((base + "/").length());
            }
        }

        // Default OSS public domain
        String defaultBase = "https://" + ossConfig.getBucket() + "." + ossConfig.getEndpoint();
        if (raw.startsWith(defaultBase + "/")) {
            return raw.substring((defaultBase + "/").length());
        }
        String defaultBaseHttp = "http://" + ossConfig.getBucket() + "." + ossConfig.getEndpoint();
        if (raw.startsWith(defaultBaseHttp + "/")) {
            return raw.substring((defaultBaseHttp + "/").length());
        }

        return null;
    }

    public String uploadBytes(String keyPrefix, byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Empty bytes");
        }
        if (isMinioEnabled()) {
            return uploadBytesToMinio(keyPrefix, bytes, contentType);
        }
        if (!isAliyunEnabled()) {
            throw new IllegalStateException("Storage is disabled (aliyun.oss.enabled=false and tminos.minio.enabled=false)");
        }
        return uploadBytesToAliyun(keyPrefix, bytes, contentType);
    }

    private String uploadBytesToMinio(String keyPrefix, byte[] bytes, String contentType) {
        String key = buildObjectKey(keyPrefix, ".png");
        String finalContentType = StringUtils.hasText(contentType) ? contentType : "image/png";
        return minioStorageClient.uploadBytes(key, bytes, finalContentType);
    }

    private String uploadBytesToAliyun(String keyPrefix, byte[] bytes, String contentType) {
        String accessKeyId = ossConfig.getAccessKeyId();
        String accessKeySecret = ossConfig.getAccessKeySecret();
        if (accessKeyId != null) accessKeyId = accessKeyId.trim();
        if (accessKeySecret != null) accessKeySecret = accessKeySecret.trim();

        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)) {
            throw new IllegalStateException("Missing OSS credentials in config: aliyun.oss.access-key-id/access-key-secret");
        }
        if (!StringUtils.hasText(ossConfig.getEndpoint()) || !StringUtils.hasText(ossConfig.getBucket())) {
            throw new IllegalStateException("Missing OSS config: aliyun.oss.endpoint / aliyun.oss.bucket");
        }

        String key = buildObjectKey(keyPrefix, ".png");

        OSS client = null;
        try {
            String endpoint = ossConfig.getEndpoint().trim();
            client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(bytes.length);
            if (StringUtils.hasText(contentType)) {
                meta.setContentType(contentType);
            } else {
                meta.setContentType("image/png");
            }

            client.putObject(ossConfig.getBucket(), key, new java.io.ByteArrayInputStream(bytes), meta);
            return toAliyunPublicUrl(key);
        } catch (Exception e) {
            log.error("OSS upload bytes failed: {}", e.getMessage(), e);
            throw new RuntimeException("OSS upload failed: " + e.getMessage(), e);
        } finally {
            if (client != null) {
                try {
                    client.shutdown();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String normalizePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "generated";
        }
        String p = prefix.trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    public OssPingResult ping() {
        if (isMinioEnabled()) {
            MinioStorageClient.MinioPingResult pingResult = minioStorageClient.ping();
            return new OssPingResult(pingResult.ok(), pingResult.message(), null, null);
        }
        if (!isAliyunEnabled()) {
            return new OssPingResult(false, "Storage disabled", null, null);
        }
        if (!StringUtils.hasText(ossConfig.getEndpoint()) || !StringUtils.hasText(ossConfig.getBucket())) {
            return new OssPingResult(false, "Missing endpoint/bucket", null, null);
        }
        if (!StringUtils.hasText(ossConfig.getAccessKeyId()) || !StringUtils.hasText(ossConfig.getAccessKeySecret())) {
            return new OssPingResult(false, "Missing access key", null, null);
        }

        OSS client = null;
        try {
            String endpoint = ossConfig.getEndpoint().trim();
            String ak = ossConfig.getAccessKeyId() != null ? ossConfig.getAccessKeyId().trim() : null;
            String sk = ossConfig.getAccessKeySecret() != null ? ossConfig.getAccessKeySecret().trim() : null;
            client = new OSSClientBuilder().build(endpoint, ak, sk);

            // Use an authenticated call to validate signature & permission.
            // doesBucketExist may succeed for public buckets even with wrong credentials.
            client.getBucketInfo(ossConfig.getBucket());
            return new OssPingResult(true, "OK", null, null);
        } catch (OSSException e) {
            return new OssPingResult(false, e.getMessage(), e.getRequestId(), e.getErrorCode());
        } catch (ClientException e) {
            return new OssPingResult(false, e.getMessage(), null, null);
        } catch (Exception e) {
            return new OssPingResult(false, e.getMessage(), null, null);
        } finally {
            if (client != null) {
                try {
                    client.shutdown();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String toAliyunPublicUrl(String key) {
        String base;
        if (StringUtils.hasText(ossConfig.getPublicDomain())) {
            base = ossConfig.getPublicDomain().trim();
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
        } else {
            base = "https://" + ossConfig.getBucket() + "." + ossConfig.getEndpoint();
        }
        return base + (key.startsWith("/") ? key : "/" + key);
    }

    private boolean isAliyunEnabled() {
        return ossConfig != null && ossConfig.isEnabled();
    }

    private boolean isMinioEnabled() {
        return minioStorageClient != null && minioStorageClient.isEnabled();
    }

    private String buildObjectKey(String keyPrefix, String extension) {
        LocalDate today = LocalDate.now();
        return String.format(Locale.ROOT, "%s/%04d/%02d/%02d/%s%s",
                normalizePrefix(keyPrefix),
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                UUID.randomUUID(),
                extension);
    }

    private String guessExtension(String originalName, String contentType) {
        if (StringUtils.hasText(originalName) && originalName.contains(".")) {
            String ext = originalName.substring(originalName.lastIndexOf('.'));
            if (ext.length() <= 10) {
                return ext;
            }
        }
        if (contentType == null) {
            return ".jpg";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
