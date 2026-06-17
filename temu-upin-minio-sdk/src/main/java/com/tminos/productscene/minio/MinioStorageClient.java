package com.tminos.productscene.minio;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Slf4j
public class MinioStorageClient {

    private final MinioProperties minioProperties;

    public MinioStorageClient(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    public boolean isEnabled() {
        return minioProperties != null && minioProperties.isEnabled();
    }

    public String upload(String key, InputStream inputStream, long size, String contentType) {
        if (size <= 0) {
            throw new IllegalArgumentException("Empty object content");
        }
        ensureEnabled();
        String objectKey = normalizeKey(key);
        try {
            ensureBucketExists();
            MinioClient minioClient = buildClient();
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(requiredBucket())
                    .object(objectKey)
                    .stream(inputStream, size, -1);
            if (StringUtils.hasText(contentType)) {
                builder.contentType(contentType.trim());
            }
            minioClient.putObject(builder.build());
            return toPublicUrl(objectKey);
        } catch (Exception e) {
            log.error("MinIO upload failed for key={}: {}", objectKey, e.getMessage(), e);
            throw new RuntimeException("MinIO upload failed: " + e.getMessage(), e);
        }
    }

    public String uploadBytes(String key, byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Empty bytes");
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            return upload(key, inputStream, bytes.length, contentType);
        } catch (Exception e) {
            throw new RuntimeException("MinIO upload failed: " + e.getMessage(), e);
        }
    }

    public MinioDeleteResult deleteByUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return new MinioDeleteResult(false, true, "Empty url");
        }
        if (!isEnabled()) {
            return new MinioDeleteResult(false, true, "MinIO disabled");
        }

        String key = extractKeyIfOwned(url);
        if (!StringUtils.hasText(key)) {
            return new MinioDeleteResult(false, true, "URL not owned by configured MinIO");
        }

        try {
            buildClient().removeObject(RemoveObjectArgs.builder()
                    .bucket(requiredBucket())
                    .object(key)
                    .build());
            return new MinioDeleteResult(true, false, "OK");
        } catch (Exception e) {
            return new MinioDeleteResult(false, false, e.getMessage());
        }
    }

    public MinioPingResult ping() {
        if (!isEnabled()) {
            return new MinioPingResult(false, "MinIO disabled");
        }
        try {
            ensureBucketExists();
            return new MinioPingResult(true, "MinIO OK");
        } catch (Exception e) {
            return new MinioPingResult(false, e.getMessage());
        }
    }

    public String extractKeyIfOwned(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String raw = url.trim();
        int queryIndex = raw.indexOf('?');
        if (queryIndex >= 0) {
            raw = raw.substring(0, queryIndex);
        }

        String publicBase = normalizeBase(minioProperties.getPublicEndpoint());
        if (StringUtils.hasText(publicBase) && raw.startsWith(publicBase + "/")) {
            return raw.substring((publicBase + "/").length());
        }

        String endpointBase = normalizeBase(minioProperties.getEndpoint());
        if (StringUtils.hasText(endpointBase)) {
            String directBase = endpointBase + "/" + requiredBucket();
            if (raw.startsWith(directBase + "/")) {
                return raw.substring((directBase + "/").length());
            }
        }
        return null;
    }

    public String toPublicUrl(String key) {
        String normalizedKey = normalizeKey(key);
        String base = normalizeBase(minioProperties.getPublicEndpoint());
        if (!StringUtils.hasText(base)) {
            String endpointBase = normalizeBase(requiredEndpoint());
            base = endpointBase + "/" + requiredBucket();
        }
        return base + "/" + normalizedKey;
    }

    private void ensureBucketExists() throws Exception {
        MinioClient minioClient = buildClient();
        String bucket = requiredBucket();
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private MinioClient buildClient() {
        ensureEnabled();
        return MinioClient.builder()
                .endpoint(requiredEndpoint())
                .credentials(requiredAccessKey(), requiredSecretKey())
                .build();
    }

    private void ensureEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("MinIO is disabled (tminos.minio.enabled=false)");
        }
    }

    private String requiredEndpoint() {
        return requireText(minioProperties.getEndpoint(), "tminos.minio.endpoint");
    }

    private String requiredAccessKey() {
        return requireText(minioProperties.getAccessKey(), "tminos.minio.access-key");
    }

    private String requiredSecretKey() {
        return requireText(minioProperties.getSecretKey(), "tminos.minio.secret-key");
    }

    private String requiredBucket() {
        return requireText(minioProperties.getBucket(), "tminos.minio.bucket");
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Missing MinIO config: " + fieldName);
        }
        return value.trim();
    }

    private String normalizeBase(String base) {
        if (!StringUtils.hasText(base)) {
            return null;
        }
        String value = base.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeKey(String key) {
        String value = requireText(key, "object key");
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    public record MinioPingResult(boolean ok, String message) {}

    public record MinioDeleteResult(boolean deleted, boolean skipped, String message) {}
}
