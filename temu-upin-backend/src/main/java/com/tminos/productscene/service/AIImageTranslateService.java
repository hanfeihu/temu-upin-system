package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.config.AIImageTranslateConfig;
import com.tminos.productscene.util.TextAiUrlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AIImageTranslateService {

    private static final Logger log = LoggerFactory.getLogger(AIImageTranslateService.class);

    private final AIImageTranslateConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final OssService ossService;
    private final TextAiChannelResolver textAiChannelResolver;

    public AIImageTranslateService(
            AIImageTranslateConfig config,
            ObjectMapper objectMapper,
            @Qualifier("aiLongRestTemplate") RestTemplate restTemplate,
            OssService ossService,
            TextAiChannelResolver textAiChannelResolver
    ) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.ossService = ossService;
        this.textAiChannelResolver = textAiChannelResolver;
    }

    public TranslateResult translate(
            MultipartFile image,
            String imageUrl,
            RequestOptions options
    ) throws Exception {
        if (config.getEnabled() == null || !config.getEnabled()) {
            throw new IllegalStateException("AI image translate is disabled");
        }

        TextAiChannelResolver.ResolvedChannel aiChannel = textAiChannelResolver.resolve(
                TextAiBusinessCodes.AI_IMAGE_TRANSLATE,
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModel()
        );
        String apiKey = trimToNull(aiChannel.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Missing AI_IMAGE_TRANSLATE_API_KEY");
        }

        ImageInput imageInput = resolveImageInput(image, imageUrl);
        String model = trimToNull(options.model());
        if (!StringUtils.hasText(model)) {
            model = trimToNull(aiChannel.getModel());
        }
        if (!StringUtils.hasText(model)) {
            model = trimToNull(config.getModel());
        }
        if (!StringUtils.hasText(model)) {
            model = "gpt-image-1.5";
        }

        String size = normalizeSize(firstNonBlank(options.size(), config.getSize(), "auto"));
        String quality = normalizeQuality(firstNonBlank(options.quality(), config.getQuality(), "medium"));
        String outputFormat = normalizeOutputFormat(firstNonBlank(options.outputFormat(), config.getOutputFormat(), "png"));
        boolean uploadToOss = options.uploadToOss() != null
                ? options.uploadToOss()
                : Boolean.TRUE.equals(config.getUploadToOssDefault());

        String prompt = buildPrompt(options);
        String endpoint = TextAiUrlHelper.imageEditsUrl(aiChannel.getBaseUrl(), config.getBaseUrl());
        String clientRequestId = UUID.randomUUID().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(apiKey);
        headers.set("X-Client-Request-Id", clientRequestId);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", model);
        body.add("prompt", prompt);
        body.add("size", size);
        body.add("quality", quality);
        body.add("output_format", outputFormat);
        body.add("image", new NamedByteArrayResource(imageInput.bytes(), imageInput.filename()));

        log.info("AI image translate request model={} endpoint={} inputBytes={} size={} quality={} outputFormat={} uploadToOss={} filename={}",
                model,
                endpoint,
                imageInput.bytes().length,
                size,
                quality,
                outputFormat,
                uploadToOss,
                imageInput.filename());

        ResponseEntity<String> response = exchangeWithRetry(endpoint, body, headers);

        if (!response.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(response.getBody())) {
            throw new IllegalStateException("AI image translate failed: HTTP " + response.getStatusCode().value());
        }

        ParsedImage parsedImage = parseResponseImage(response.getBody(), outputFormat);
        String ossUrl = null;
        if (uploadToOss && ossService != null && ossService.isEnabled()) {
            ossUrl = ossService.uploadBytes("generated/openai-image-translate", parsedImage.bytes(), parsedImage.contentType());
        }

        String requestId = response.getHeaders().getFirst("x-request-id");
        if (!StringUtils.hasText(requestId)) {
            requestId = response.getHeaders().getFirst("X-Request-Id");
        }

        return new TranslateResult(
                parsedImage.bytes(),
                parsedImage.contentType(),
                buildOutputFilename(imageInput.filename(), parsedImage.outputFormat()),
                model,
                prompt,
                requestId,
                ossUrl,
                parsedImage.outputFormat()
        );
    }

    private ResponseEntity<String> exchangeWithRetry(String endpoint, MultiValueMap<String, Object> body, HttpHeaders headers) {
        RestClientException last = null;
        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return restTemplate.exchange(
                        endpoint,
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        String.class
                );
            } catch (RestClientException e) {
                last = e;
                if (attempt >= maxAttempts || !isRetryable(e)) {
                    throw e;
                }
                log.warn("AI image translate transient failure, retrying attempt={}/{} error={}",
                        attempt,
                        maxAttempts,
                        e.getMessage());
                sleepQuietly(3_000L * attempt);
            }
        }
        throw last == null ? new IllegalStateException("AI image translate failed") : last;
    }

    private boolean isRetryable(RestClientException e) {
        if (e instanceof RestClientResponseException responseException) {
            int status = responseException.getRawStatusCode();
            return status == 408 || status == 429 || status >= 500;
        }
        return true;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ImageInput resolveImageInput(MultipartFile image, String imageUrl) throws Exception {
        if (image != null && !image.isEmpty()) {
            String filename = sanitizeFilename(image.getOriginalFilename(), inferExtension(image.getContentType(), "png"));
            byte[] bytes = image.getBytes();
            if (bytes.length == 0) {
                throw new IllegalArgumentException("Uploaded image is empty");
            }
            return new ImageInput(bytes, filename, trimToNull(image.getContentType()));
        }

        if (!StringUtils.hasText(imageUrl)) {
            throw new IllegalArgumentException("image or imageUrl is required");
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(toSafeUri(imageUrl.trim()))
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().length == 0) {
            throw new IllegalStateException("Download image failed: HTTP " + response.statusCode());
        }

        String contentType = response.headers().firstValue("content-type").orElse(null);
        String extension = inferExtension(contentType, "png");
        String filename = sanitizeFilename(extractFilenameFromUrl(imageUrl), extension);
        return new ImageInput(response.body(), filename, contentType);
    }

    private ParsedImage parseResponseImage(String body, String requestedOutputFormat) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode first = null;
        JsonNode dataNode = root.path("data");
        if (dataNode.isArray() && !dataNode.isEmpty()) {
            first = dataNode.get(0);
        }

        String base64 = first == null ? null : text(first, "b64_json");
        String url = first == null ? null : text(first, "url");
        String outputFormat = firstNonBlank(text(root, "output_format"), requestedOutputFormat, "png");
        outputFormat = normalizeOutputFormat(outputFormat);

        if (StringUtils.hasText(base64)) {
            return new ParsedImage(Base64.getDecoder().decode(base64), contentTypeFromFormat(outputFormat), outputFormat);
        }

        if (StringUtils.hasText(url)) {
            return downloadOutputImage(url.trim(), outputFormat);
        }

        throw new IllegalStateException("AI image translate returned no image: " + extractErrorMessage(body));
    }

    private ParsedImage downloadOutputImage(String url, String requestedOutputFormat) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(toSafeUri(url))
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().length == 0) {
            throw new IllegalStateException("Download output image failed: HTTP " + response.statusCode());
        }
        String contentType = response.headers().firstValue("content-type").orElse(contentTypeFromFormat(requestedOutputFormat));
        String outputFormat = inferExtension(contentType, requestedOutputFormat);
        return new ParsedImage(response.body(), contentType, outputFormat);
    }

    private String buildPrompt(RequestOptions options) {
        String sourceLanguage = normalizeLanguageLabel(firstNonBlank(options.sourceLanguage(), config.getSourceLanguage(), "Chinese"));
        String targetLanguage = normalizeLanguageLabel(firstNonBlank(options.targetLanguage(), config.getTargetLanguage(), "English"));
        String marketplace = firstNonBlank(options.marketplace(), config.getMarketplace(), "Temu");

        StringBuilder prompt = new StringBuilder();
        prompt.append("Edit the provided e-commerce product image by replacing all visible ")
                .append(sourceLanguage)
                .append(" text and any garbled or unreadable text with clean, polished ")
                .append(targetLanguage)
                .append(" copy suitable for ")
                .append(marketplace)
                .append(". ");
        prompt.append("Preserve the original composition, products, faces, packaging layout, lighting, shadows, perspective, colors, and commercial design style. ");
        prompt.append("Change only text areas. Keep every non-text element unchanged. ");
        prompt.append("If a text block is too small to read, replace it with concise natural marketing copy that matches the original meaning and placement. ");
        prompt.append("Do not add watermarks, extra branding, new props, or extra objects. ");
        prompt.append("Avoid any Chinese characters in the final image.");

        if (StringUtils.hasText(options.instructions())) {
            prompt.append(" Additional requirements: ").append(options.instructions().trim());
        }

        return prompt.toString();
    }

    private String extractErrorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String errorMessage = root.path("error").path("message").asText(null);
            if (StringUtils.hasText(errorMessage)) {
                return errorMessage.trim();
            }
            errorMessage = root.path("message").asText(null);
            if (StringUtils.hasText(errorMessage)) {
                return errorMessage.trim();
            }
        } catch (Exception ignored) {
        }
        return body == null ? "Unknown upstream error" : body;
    }

    private URI toSafeUri(String rawUrl) {
        try {
            return URI.create(rawUrl);
        } catch (Exception ignored) {
        }
        try {
            URL url = new URL(rawUrl);
            return new URI(
                    url.getProtocol(),
                    url.getUserInfo(),
                    url.getHost(),
                    url.getPort(),
                    url.getPath(),
                    url.getQuery(),
                    url.getRef()
            );
        } catch (Exception e) {
            return URI.create(rawUrl.replace(" ", "%20"));
        }
    }

    private String normalizeSize(String value) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            return "auto";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if ("auto".equals(normalized)
                || "1024x1024".equals(normalized)
                || "1024x1536".equals(normalized)
                || "1536x1024".equals(normalized)) {
            return normalized;
        }
        return "auto";
    }

    private String normalizeQuality(String value) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            return "medium";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if ("auto".equals(normalized)
                || "low".equals(normalized)
                || "medium".equals(normalized)
                || "high".equals(normalized)) {
            return normalized;
        }
        return "medium";
    }

    private String normalizeOutputFormat(String value) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            return "png";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if ("jpg".equals(normalized)) {
            normalized = "jpeg";
        }
        if ("png".equals(normalized) || "jpeg".equals(normalized) || "webp".equals(normalized)) {
            return normalized;
        }
        return "png";
    }

    private String normalizeLanguageLabel(String value) {
        if (!StringUtils.hasText(value)) {
            return "English";
        }
        String normalized = value.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "zh", "zh-cn", "chinese", "cn" -> "Chinese";
            case "en", "en-us", "english", "us" -> "English";
            default -> normalized;
        };
    }

    private String buildOutputFilename(String originalFilename, String outputFormat) {
        String base = originalFilename;
        if (!StringUtils.hasText(base)) {
            base = "translated-image";
        }
        int dotIndex = base.lastIndexOf('.');
        if (dotIndex > 0) {
            base = base.substring(0, dotIndex);
        }
        return base + "-translated." + outputFormat;
    }

    private String extractFilenameFromUrl(String imageUrl) {
        try {
            URI uri = toSafeUri(imageUrl);
            String path = uri.getPath();
            if (!StringUtils.hasText(path)) {
                return null;
            }
            int slashIndex = path.lastIndexOf('/');
            if (slashIndex >= 0 && slashIndex < path.length() - 1) {
                return path.substring(slashIndex + 1);
            }
            return path;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String sanitizeFilename(String originalFilename, String fallbackExtension) {
        String filename = trimToNull(originalFilename);
        if (!StringUtils.hasText(filename)) {
            return "image." + fallbackExtension;
        }
        filename = filename.replace("\\", "/");
        int slashIndex = filename.lastIndexOf('/');
        if (slashIndex >= 0) {
            filename = filename.substring(slashIndex + 1);
        }
        if (!filename.contains(".")) {
            filename = filename + "." + fallbackExtension;
        }
        return filename;
    }

    private String inferExtension(String contentType, String fallback) {
        String normalized = trimToNull(contentType);
        if (!StringUtils.hasText(normalized)) {
            return fallback;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (normalized.contains("png")) {
            return "png";
        }
        if (normalized.contains("jpeg") || normalized.contains("jpg")) {
            return "jpeg";
        }
        if (normalized.contains("webp")) {
            return "webp";
        }
        return fallback;
    }

    private String contentTypeFromFormat(String format) {
        return switch (normalizeOutputFormat(format)) {
            case "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            case "webp" -> "image/webp";
            default -> MediaType.IMAGE_PNG_VALUE;
        };
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(fieldName);
        return child == null || child.isNull() ? null : child.asText(null);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record RequestOptions(
            String sourceLanguage,
            String targetLanguage,
            String marketplace,
            String instructions,
            String size,
            String quality,
            String outputFormat,
            String model,
            Boolean uploadToOss
    ) {
    }

    public record TranslateResult(
            byte[] imageBytes,
            String contentType,
            String filename,
            String model,
            String prompt,
            String requestId,
            String ossUrl,
            String outputFormat
    ) {
    }

    private record ImageInput(
            byte[] bytes,
            String filename,
            String contentType
    ) {
    }

    private record ParsedImage(
            byte[] bytes,
            String contentType,
            String outputFormat
    ) {
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
