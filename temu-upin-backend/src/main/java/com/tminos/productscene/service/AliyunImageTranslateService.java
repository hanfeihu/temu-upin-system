package com.tminos.productscene.service;

import com.aliyun.alimt20181012.models.TranslateImageRequest;
import com.aliyun.alimt20181012.models.TranslateImageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.config.OssConfig;
import com.tminos.productscene.dto.TemuImageDTO;
import com.tminos.productscene.utils.AliyunMtRoaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class AliyunImageTranslateService {

    private static final Logger log = LoggerFactory.getLogger(AliyunImageTranslateService.class);

    private final OssConfig ossConfig;
    private final OssService ossService;
    private final ObjectMapper objectMapper;
    private final AliyunMtRoaClient aliyunClient;

    public AliyunImageTranslateService(OssConfig ossConfig,
                                      OssService ossService,
                                      ObjectMapper objectMapper) {
        this.ossConfig = ossConfig;
        this.ossService = ossService;
        this.objectMapper = objectMapper;
        this.aliyunClient = new AliyunMtRoaClient(objectMapper);
    }

    public Result translateImageByUrl(String imageUrl,
                                     String sourceLanguage,
                                     String targetLanguage,
                                     boolean withoutText,
                                     boolean uploadToOss) throws Exception {
        if (!StringUtils.hasText(imageUrl)) {
            throw new IllegalArgumentException("imageUrl is required");
        }
        String sl = StringUtils.hasText(sourceLanguage) ? sourceLanguage.trim() : "zh";
        String tl = StringUtils.hasText(targetLanguage) ? targetLanguage.trim() : "en";

        String ak = ossConfig == null ? null : ossConfig.getAccessKeyId();
        String sk = ossConfig == null ? null : ossConfig.getAccessKeySecret();
        if (ak != null) ak = ak.trim();
        if (sk != null) sk = sk.trim();
        if (!StringUtils.hasText(ak) || !StringUtils.hasText(sk)) {
            throw new IllegalStateException("Missing Aliyun credentials (reuse aliyun.oss.access-key-id/access-key-secret)");
        }


        String ext = withoutText
                ? "{\"needEditorData\":\"false\",\"ignoreEntityRecognize\":\"true\",\"without_text\":\"true\"}"
                : "{\"needEditorData\":\"false\",\"ignoreEntityRecognize\":\"true\"}";

        AliyunMtRoaClient.TranslateImageResult r = aliyunClient.translateImageByUrl(
                imageUrl.trim(),
                sl,
                tl,
                "e-commerce",
                ext,
                ak,
                sk
        );

        String respRaw = r == null ? null : r.rawBody();
        String finalImageUrl = r == null ? null : r.finalImageUrl();
        if (!StringUtils.hasText(finalImageUrl)) {
            String msg = StringUtils.hasText(respRaw) ? extractErrorMsg(respRaw) : null;
            log.warn("Aliyun imageTranslate failed. statusCode={}, msg={}, raw={}",
                    r == null ? null : r.statusCode(),
                    StringUtils.hasText(msg) ? msg : "(empty)",
                    respRaw == null ? "(null)" : (respRaw.length() > 2000 ? respRaw.substring(0, 2000) : respRaw));
            throw new IllegalStateException(StringUtils.hasText(msg) ? msg : "Aliyun imageTranslate failed");
        }

        String storedUrl = finalImageUrl;
        if (uploadToOss && ossService != null && ossService.isEnabled()) {
            try {
                storedUrl = downloadAndUpload(finalImageUrl);
            } catch (Exception ignored) {
                storedUrl = finalImageUrl;
            }
        }

        return new Result(imageUrl.trim(), finalImageUrl.trim(), storedUrl, respRaw);
    }

    private String extractFinalImageUrl(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            // Some Aliyun responses use {"Data":{"FinalImageUrl":"..."}}
            String u = root.path("Data").path("FinalImageUrl").asText(null);
            if (StringUtils.hasText(u)) return u.trim();
            // Some variants may use ImageUrl/ResultImageUrl keys
            u = root.path("Data").path("ImageUrl").asText(null);
            if (StringUtils.hasText(u)) return u.trim();
            u = root.path("Data").path("ResultImageUrl").asText(null);
            if (StringUtils.hasText(u)) return u.trim();
            // Some wrappers use lower-case keys
            u = root.path("data").path("finalImageUrl").asText(null);
            if (StringUtils.hasText(u)) return u.trim();
            u = root.path("data").path("imageUrl").asText(null);
            if (StringUtils.hasText(u)) return u.trim();
            // Some custom gateway style could be {result:{...}}
            u = root.path("result").path("finalImageUrl").asText(null);
            if (StringUtils.hasText(u)) return u.trim();
            u = root.path("result").path("imageUrl").asText(null);
            if (StringUtils.hasText(u)) return u.trim();
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractErrorMsg(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            String m = root.path("Message").asText(null);
            if (StringUtils.hasText(m)) return m.trim();
            m = root.path("message").asText(null);
            if (StringUtils.hasText(m)) return m.trim();
            String code = root.path("Code").asText(null);
            if (StringUtils.hasText(code)) return "Aliyun code=" + code;
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String downloadAndUpload(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Download translated image failed: HTTP " + resp.statusCode());
        }
        String contentType = resp.headers().firstValue("content-type").orElse("image/png");
        byte[] bytes = resp.body();
        return ossService.uploadBytes("aliyun/translated", bytes, contentType);
    }

    public record Result(String originalUrl,
                         String translatedUrl,
                         String storedUrl,
                         String raw) {
    }
}
