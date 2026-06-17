package com.tminos.productscene.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.util.TextAiUrlHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TemuSkuSpecTranslateService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final TextAiChannelResolver textAiChannelResolver;
    private final Map<String, String> memoryCache = new ConcurrentHashMap<>();

    public TemuSkuSpecTranslateService(ObjectMapper objectMapper,
                                       @Qualifier("aiLongRestTemplate") RestTemplate restTemplate,
                                       TextAiChannelResolver textAiChannelResolver) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.textAiChannelResolver = textAiChannelResolver;
    }

    public String translateSpecKey(String specKey) {
        return translatePlainText(specKey);
    }

    public String translateSpecJsonValues(String specJson) {
        if (!StringUtils.hasText(specJson)) {
            return specJson;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(specJson, new TypeReference<LinkedHashMap<String, Object>>() {});
            Map<String, Object> translated = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String text) {
                    translated.put(entry.getKey(), sanitizeSpecValue(translatePlainText(text)));
                } else {
                    translated.put(entry.getKey(), value);
                }
            }
            return objectMapper.writeValueAsString(translated);
        } catch (Exception ignored) {
            return specJson;
        }
    }

    public String translatePlainText(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String source = sanitizeSpecValue(text);
        if (!containsCjk(source)) {
            return source;
        }

        String cacheKey = md5(source);
        String cached = memoryCache.get(cacheKey);
        if (StringUtils.hasText(cached)) {
            return cached;
        }

        String local = translateSkuTextFallback(source);
        if (StringUtils.hasText(local) && !containsCjk(local)) {
            memoryCache.put(cacheKey, local);
            return local;
        }

        try {
            String ai = callAiTranslate(source);
            String cleaned = sanitizeSpecValue(ai);
            if (StringUtils.hasText(cleaned) && !containsCjk(cleaned)) {
                memoryCache.put(cacheKey, cleaned);
                return cleaned;
            }
        } catch (Exception ignored) {
        }
        return source;
    }

    private String callAiTranslate(String text) throws Exception {
        TextAiChannelResolver.ResolvedChannel channel = textAiChannelResolver.resolve(
                TextAiBusinessCodes.TEMU_SKU_SPEC_TRANSLATE,
                null,
                null,
                "gpt-5.5"
        );
        if (channel == null || !StringUtils.hasText(channel.getApiKey())) {
            return null;
        }

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", StringUtils.hasText(channel.getModel()) ? channel.getModel() : "gpt-5.5");
        req.put("stream", false);
        req.put("max_tokens", 300);
        req.put("messages", List.of(
                Map.of("role", "system", "content", systemInstruction()),
                Map.of("role", "user", "content", userPrompt(text))
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(channel.getApiKey().trim());
        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(req), headers);
        String url = TextAiUrlHelper.chatCompletionsUrl(channel.getBaseUrl(), "https://chatbot.tminos.com");
        ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
        if (!resp.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(resp.getBody())) {
            return null;
        }
        String content = extractAssistantContent(resp.getBody());
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String stripped = stripCodeFence(content);
        try {
            JsonNode root = objectMapper.readTree(stripped);
            return root.path("translation").asText(null);
        } catch (Exception ignored) {
            return stripped;
        }
    }

    private String systemInstruction() {
        return "You translate 1688 Chinese SKU specification values into concise English for TEMU listings. "
                + "Preserve numbers, units, model codes, voltage, size, package count, and material terms. "
                + "Do not add brand, IP, trademark, marketing claims, or explanations. Return strict JSON only.";
    }

    private String userPrompt(String text) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", text);
        return "Translate this SKU specification value to English.\n"
                + "Rules:\n"
                + "1. Return strict JSON only: {\"translation\":\"...\"}.\n"
                + "2. Keep numbers and units exactly, such as 16mm, 110V, M8, 3pcs.\n"
                + "3. Keep meaning precise and short for a SKU option.\n"
                + "4. If it describes packaging, translate OPP袋包装 as OPP Bag Pack, 散装发货 as Bulk Pack.\n"
                + "5. If it contains unsafe brand/IP/company text, remove that part and keep the generic spec.\n"
                + objectMapper.writeValueAsString(payload);
    }

    private String extractAssistantContent(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode message = choices.get(0).path("message");
        String content = message.path("content").asText(null);
        if (StringUtils.hasText(content)) {
            return content;
        }
        JsonNode contentNode = message.get("content");
        if (contentNode != null && contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : contentNode) {
                String text = part.path("text").asText(null);
                if (StringUtils.hasText(text)) {
                    sb.append(text);
                }
            }
            return sb.isEmpty() ? null : sb.toString();
        }
        return null;
    }

    private String stripCodeFence(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String s = value.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "");
            s = s.replaceFirst("\\s*```$", "");
        }
        return s.trim();
    }

    private String sanitizeSpecValue(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String s = value
                .replace('[', ' ')
                .replace(']', ' ')
                .replace('【', ' ')
                .replace('】', ' ')
                .replace('（', ' ')
                .replace('）', ' ')
                .replace('(', ' ')
                .replace(')', ' ')
                .replace('.', ' ')
                .replace('．', ' ')
                .replace('。', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return StringUtils.hasText(s) ? s : value.trim();
    }

    private String translateSkuTextFallback(String text) {
        if (!StringUtils.hasText(text)) return null;
        String s = text.trim();
        Map<String, String> dict = new LinkedHashMap<>();
        dict.put("玫瑰金", "Rose Gold");
        dict.put("香槟金", "Champagne Gold");
        dict.put("浅绿色", "Light Green");
        dict.put("深绿色", "Dark Green");
        dict.put("浅粉色", "Light Pink");
        dict.put("深粉色", "Dark Pink");
        dict.put("浅蓝色", "Light Blue");
        dict.put("深蓝色", "Dark Blue");
        dict.put("粉红色", "Pink");
        dict.put("粉色", "Pink");
        dict.put("蓝色", "Blue");
        dict.put("红色", "Red");
        dict.put("绿色", "Green");
        dict.put("黄色", "Yellow");
        dict.put("黑色", "Black");
        dict.put("白色", "White");
        dict.put("灰色", "Gray");
        dict.put("紫色", "Purple");
        dict.put("橘色", "Orange");
        dict.put("橙色", "Orange");
        dict.put("棕色", "Brown");
        dict.put("金色", "Gold");
        dict.put("银色", "Silver");
        dict.put("透明", "Transparent");
        dict.put("均码", "One Size");
        dict.put("特大号", "XL");
        dict.put("大号", "Large");
        dict.put("中号", "Medium");
        dict.put("小号", "Small");
        dict.put("OPP袋包装", "OPP Bag Pack");
        dict.put("散装发货", "Bulk Pack");
        dict.put("适用", "For");
        dict.put("直径", "Diameter ");
        dict.put("管子", "Tube");
        dict.put("单头", "Single Head");
        dict.put("双头", "Double Head");
        dict.put("规格", "Spec");
        dict.put("款", "Style");
        dict.put("色", "Color");
        for (Map.Entry<String, String> e : dict.entrySet()) {
            s = s.replace(e.getKey(), e.getValue());
        }
        return containsCjk(s) ? null : sanitizeSpecValue(s);
    }

    private boolean containsCjk(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') {
                return true;
            }
        }
        return false;
    }

    private String md5(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ignored) {
            return value;
        }
    }
}
