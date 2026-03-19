package com.tminos.productscene.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Aliyun MT SDK wrapper.
 *
 * This replaces the previous manual ROA signing implementation.
 */
public final class AliyunMtRoaClient {

    private static final String ENDPOINT = "mt.cn-hangzhou.aliyuncs.com";

    private final ObjectMapper objectMapper;

    public AliyunMtRoaClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public record TranslateImageResult(
            Integer statusCode,
            String finalImageUrl,
            String rawBody
    ) {
    }

    public TranslateImageResult translateImageByUrl(String imageUrl,
                                                    String sourceLanguage,
                                                    String targetLanguage,
                                                    String field,
                                                    String extJsonString,
                                                    String accessKeyId,
                                                    String accessKeySecret) throws Exception {
        if (!StringUtils.hasText(imageUrl)) throw new IllegalArgumentException("imageUrl is required");
        if (!StringUtils.hasText(sourceLanguage)) throw new IllegalArgumentException("sourceLanguage is required");
        if (!StringUtils.hasText(targetLanguage)) throw new IllegalArgumentException("targetLanguage is required");
        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)) {
            throw new IllegalArgumentException("accessKeyId/accessKeySecret is required");
        }

        Object client = createClient(accessKeyId.trim(), accessKeySecret.trim());
        Object req = createTranslateImageRequest(imageUrl, sourceLanguage, targetLanguage, field, extJsonString);

        Object resp = invoke(client, "translateImage", new Class<?>[]{req.getClass()}, new Object[]{req});

        Integer statusCode = null;
        Object body = null;
        if (resp != null) {
            Object sc = invoke(resp, "getStatusCode", new Class<?>[]{}, new Object[]{});
            if (sc instanceof Integer i) {
                statusCode = i;
            }
            body = invoke(resp, "getBody", new Class<?>[]{}, new Object[]{});
        }

        String rawBody;
        if (body == null) {
            rawBody = null;
        } else {
            // Tea models serialize well with Jackson valueToTree.
            JsonNode node = objectMapper.valueToTree(body);
            rawBody = node.isMissingNode() ? null : node.toString();
        }

        String finalUrl = null;
        if (StringUtils.hasText(rawBody)) {
            finalUrl = tryExtractFinalImageUrl(rawBody);
        }

        return new TranslateImageResult(statusCode, finalUrl, rawBody);
    }

    private Object createClient(String accessKeyId, String accessKeySecret) throws Exception {
        // com.aliyun.teaopenapi.models.Config
        Class<?> configClz = Class.forName("com.aliyun.teaopenapi.models.Config");
        Object config = configClz.getDeclaredConstructor().newInstance();
        invoke(config, "setAccessKeyId", new Class<?>[]{String.class}, new Object[]{accessKeyId});
        invoke(config, "setAccessKeySecret", new Class<?>[]{String.class}, new Object[]{accessKeySecret});

        // In samples: config.endpoint = "mt.cn-hangzhou.aliyuncs.com";
        // Use reflection to set the public field when available.
        try {
            Field ep = configClz.getField("endpoint");
            ep.set(config, ENDPOINT);
        } catch (Exception ignored) {
            // Try method-based setter if SDK changes.
            try {
                invoke(config, "setEndpoint", new Class<?>[]{String.class}, new Object[]{ENDPOINT});
            } catch (Exception ignored2) {
            }
        }

        // com.aliyun.alimt20181012.Client
        Class<?> clientClz = Class.forName("com.aliyun.alimt20181012.Client");
        Constructor<?> ctor = clientClz.getConstructor(configClz);
        return ctor.newInstance(config);
    }

    private Object createTranslateImageRequest(String imageUrl,
                                               String sourceLanguage,
                                               String targetLanguage,
                                               String field,
                                               String extJsonString) throws Exception {
        Class<?> reqClz = Class.forName("com.aliyun.alimt20181012.models.TranslateImageRequest");
        Object req = reqClz.getDeclaredConstructor().newInstance();

        invoke(req, "setImageUrl", new Class<?>[]{String.class}, new Object[]{imageUrl.trim()});
        invoke(req, "setSourceLanguage", new Class<?>[]{String.class}, new Object[]{sourceLanguage.trim()});
        invoke(req, "setTargetLanguage", new Class<?>[]{String.class}, new Object[]{targetLanguage.trim()});
        if (StringUtils.hasText(field)) {
            invoke(req, "setField", new Class<?>[]{String.class}, new Object[]{field.trim()});
        }
        if (StringUtils.hasText(extJsonString)) {
            invoke(req, "setExt", new Class<?>[]{String.class}, new Object[]{extJsonString.trim()});
        }
        return req;
    }

    private Object invoke(Object target, String method, Class<?>[] types, Object[] args) throws Exception {
        if (target == null) return null;
        Method m = target.getClass().getMethod(method, types);
        return m.invoke(target, args);
    }

    private String tryExtractFinalImageUrl(String rawBodyJson) {
        try {
            JsonNode root = objectMapper.readTree(rawBodyJson);
            // Common variants:
            // - {"Data":{"FinalImageUrl":"..."}}
            // - {"data":{"finalImageUrl":"..."}}
            String u = root.path("Data").path("FinalImageUrl").asText(null);
            if (StringUtils.hasText(u)) return u.trim();
            u = root.path("data").path("finalImageUrl").asText(null);
            if (StringUtils.hasText(u)) return u.trim();
            u = root.path("Data").path("ImageUrl").asText(null);
            if (StringUtils.hasText(u)) return u.trim();
            u = root.path("data").path("imageUrl").asText(null);
            if (StringUtils.hasText(u)) return u.trim();
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
