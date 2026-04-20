package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.temu.upin.sdk.v2.image.TemuImageV2Client;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

@Service
public class TemuImageNormalizeService {

    private static final int TARGET_W = 800;
    private static final int TARGET_H = 800;
    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;
    private static final int MAX_UPLOAD_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;
    private final TemuOpenApiCredentialService temuOpenApiCredentialService;

    public TemuImageNormalizeService(ObjectMapper objectMapper,
                                    TemuOpenApiCredentialService temuOpenApiCredentialService) {
        this.objectMapper = objectMapper;
        this.temuOpenApiCredentialService = temuOpenApiCredentialService;
    }

    public Result normalizeToTemu800(String url) throws Exception {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("image url is required");
        }
        String original = normalizeUrlString(url);

        // Rule: If domain is not TEMU kwcdn, upload-by-url first to get a legal, kwcdn-hosted URL.
        boolean uploadedByUrlFirst = false;
        String candidate = original;
        boolean isKwcdn = isTemuKwcdn(candidate);
        if (!isKwcdn) {
            TemuOpenApiCredentials creds = temuOpenApiCredentialService.getDefaultTemuOpenApiCredentialsOrThrow();
            String uploadedRaw = uploadByUrlWithRetry(creds, candidate);
            String uploadedUrl = parseUploadedImageUrlOrThrow(uploadedRaw);
            candidate = normalizeUrlString(uploadedUrl);
            uploadedByUrlFirst = true;
            isKwcdn = isTemuKwcdn(candidate);
        }

        ImageInfo info;
        try {
            info = probe(candidate);
        } catch (Exception decodeError) {
            // Some source images are in formats ImageIO can't decode (e.g. webp). In that case,
            // we still return a kwcdn URL (already uploaded-by-url) so publish won't fail domain validation.
            if (uploadedByUrlFirst && isKwcdn) {
                Result r = new Result();
                r.setOriginalUrl(original);
                r.setNormalizedUrl(candidate);
                r.setUploadedUrl(candidate);
                r.setWidth(null);
                r.setHeight(null);
                r.setChanged(true);
                r.setReason("uploaded_to_kwcdn_decode_failed");
                return r;
            }
            throw decodeError;
        }

        boolean already800 = info.width == TARGET_W && info.height == TARGET_H;
        if (already800 && isKwcdn) {
            Result r = new Result();
            r.setOriginalUrl(original);
            r.setNormalizedUrl(candidate);
            r.setUploadedUrl(candidate);
            r.setWidth(info.width);
            r.setHeight(info.height);
            r.setChanged(uploadedByUrlFirst);
            r.setReason(uploadedByUrlFirst ? "uploaded_to_kwcdn" : "already_800_kwcdn");
            return r;
        }

        // Needs resize/crop: download -> transform -> base64 upload
        byte[] bytes = info.bytes;
        if (bytes == null) {
            bytes = downloadBytes(candidate);
        }
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
        if (src == null) {
            // Same fallback as above: allow kwcdn URL pass-through.
            if (isKwcdn) {
                Result r = new Result();
                r.setOriginalUrl(original);
                r.setNormalizedUrl(candidate);
                r.setUploadedUrl(candidate);
                r.setWidth(info.width);
                r.setHeight(info.height);
                r.setChanged(uploadedByUrlFirst);
                r.setReason("kwcdn_decode_failed");
                return r;
            }
            throw new IllegalStateException("Failed to decode image");
        }
        // Do NOT crop: keep full content by stretching to 800x800.
        BufferedImage out = stretchResize(src, TARGET_W, TARGET_H);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "jpg", bos);
        String base64 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
        TemuOpenApiCredentials creds = temuOpenApiCredentialService.getDefaultTemuOpenApiCredentialsOrThrow();
        String uploadedRaw2 = uploadBase64WithRetry(creds, base64);
        String uploadedUrl2 = parseUploadedImageUrlOrThrow(uploadedRaw2);

        Result r = new Result();
        r.setOriginalUrl(original);
        r.setNormalizedUrl("<generated-800x800>");
        r.setUploadedUrl(uploadedUrl2);
        r.setWidth(TARGET_W);
        r.setHeight(TARGET_H);
        r.setChanged(true);
        r.setReason(uploadedByUrlFirst ? "uploaded_to_kwcdn_then_resized" : "resized_to_800_then_uploaded");
        return r;
    }

    public ImageSize probeImageSize(String url) throws Exception {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("image url is required");
        }
        ImageInfo info = probe(normalizeUrlString(url));
        return new ImageSize(info.width, info.height);
    }

    private String parseUploadedImageUrlOrThrow(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException("TEMU upload response empty");
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            boolean ok = root.path("success").asBoolean(false);
            if (!ok) {
                String msg = root.path("errorMsg").asText("");
                int code = root.path("errorCode").asInt(0);
                throw new IllegalStateException("TEMU upload failed: code=" + code + ", msg=" + msg);
            }
            String imageUrl = root.path("result").path("imageUrl").asText(null);
            if (!StringUtils.hasText(imageUrl)) {
                imageUrl = root.path("result").path("url").asText(null);
            }
            if (!StringUtils.hasText(imageUrl)) {
                throw new IllegalStateException("TEMU upload ok but result.imageUrl missing");
            }
            return imageUrl.trim();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("TEMU upload response parse failed: " + e.getMessage());
        }
    }

    private boolean isTemuKwcdn(String url) {
        try {
            URI u = URI.create(url);
            String host = u.getHost();
            return host != null && host.endsWith("kwcdn.com") && host.startsWith("img.");
        } catch (Exception ignored) {
            return false;
        }
    }

    private ImageInfo probe(String url) throws Exception {
        byte[] bytes = downloadBytes(url);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) {
            throw new IllegalStateException("Failed to decode image");
        }
        ImageInfo out = new ImageInfo();
        out.width = img.getWidth();
        out.height = img.getHeight();
        out.bytes = bytes;
        return out;
    }

    private byte[] downloadBytes(String url) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                return doDownloadBytes(url);
            } catch (Exception e) {
                lastError = e;
                if (attempt >= MAX_DOWNLOAD_ATTEMPTS) break;
            }
        }
        throw lastError == null ? new IllegalStateException("Download failed") : lastError;
    }

    private byte[] doDownloadBytes(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(toSafeUri(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Download failed: HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    private String uploadByUrlWithRetry(TemuOpenApiCredentials creds, String imageUrl) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_UPLOAD_ATTEMPTS; attempt++) {
            try {
                TemuImageV2Client client = new TemuImageV2Client(creds);
                return client.uploadGlobalImageByUrlRaw(imageUrl, null, null);
            } catch (Exception e) {
                lastError = e;
                if (attempt >= MAX_UPLOAD_ATTEMPTS) break;
            }
        }
        throw lastError == null ? new IllegalStateException("TEMU upload-by-url failed") : lastError;
    }

    private String uploadBase64WithRetry(TemuOpenApiCredentials creds, String base64) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_UPLOAD_ATTEMPTS; attempt++) {
            try {
                TemuImageV2Client client = new TemuImageV2Client(creds);
                return client.uploadGlobalImageBase64Raw(base64, null, null);
            } catch (Exception e) {
                lastError = e;
                if (attempt >= MAX_UPLOAD_ATTEMPTS) break;
            }
        }
        throw lastError == null ? new IllegalStateException("TEMU upload-base64 failed") : lastError;
    }

    private URI toSafeUri(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("url is required");
        }
        String s = raw.trim();
        try {
            return URI.create(s);
        } catch (Exception ignored) {
        }
        // Some upstream URLs may contain spaces or other characters that are not legal in a raw URI.
        // Try to parse with URL then rebuild as URI (will escape as needed).
        try {
            URL u = new URL(s);
            return new URI(
                    u.getProtocol(),
                    u.getUserInfo(),
                    u.getHost(),
                    u.getPort(),
                    u.getPath(),
                    u.getQuery(),
                    u.getRef()
            );
        } catch (Exception e) {
            // last resort: encode spaces
            try {
                return URI.create(s.replace(" ", "%20"));
            } catch (Exception e2) {
                throw new IllegalArgumentException("invalid url: " + raw);
            }
        }
    }

    private String normalizeUrlString(String raw) {
        URI u = toSafeUri(raw);
        // keep it ASCII to avoid hidden unicode characters
        return u.toASCIIString();
    }

    private BufferedImage stretchResize(BufferedImage src, int targetW, int targetH) {
        // Stretch to exact size (no crop), keeps full content.
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        return out;
    }

    private static class ImageInfo {
        private int width;
        private int height;
        private byte[] bytes;

        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public byte[] getBytes() { return bytes; }
    }

    public static class Result {
        private String originalUrl;
        private String normalizedUrl;
        private String uploadedUrl;
        private Integer width;
        private Integer height;
        private Boolean changed;
        private String reason;

        public String getOriginalUrl() { return originalUrl; }
        public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }
        public String getNormalizedUrl() { return normalizedUrl; }
        public void setNormalizedUrl(String normalizedUrl) { this.normalizedUrl = normalizedUrl; }
        public String getUploadedUrl() { return uploadedUrl; }
        public void setUploadedUrl(String uploadedUrl) { this.uploadedUrl = uploadedUrl; }
        public Integer getWidth() { return width; }
        public void setWidth(Integer width) { this.width = width; }
        public Integer getHeight() { return height; }
        public void setHeight(Integer height) { this.height = height; }
        public Boolean getChanged() { return changed; }
        public void setChanged(Boolean changed) { this.changed = changed; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public record ImageSize(int width, int height) {
    }

}
