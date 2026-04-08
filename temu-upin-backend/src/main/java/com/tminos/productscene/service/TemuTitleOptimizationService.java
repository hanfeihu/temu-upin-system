package com.tminos.productscene.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.config.AITemuTitleOptimizerConfig;
import com.tminos.productscene.dto.TemuCategoryDTO;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.util.TextAiUrlHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TemuTitleOptimizationService {

    private final AITemuTitleOptimizerConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final TemuCategoryService temuCategoryService;

    public TemuTitleOptimizationService(AITemuTitleOptimizerConfig config,
                                        ObjectMapper objectMapper,
                                        @Qualifier("aiLongRestTemplate") RestTemplate restTemplate,
                                        TemuCategoryService temuCategoryService) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.temuCategoryService = temuCategoryService;
    }

    public TitleOptimizationResult generateAndMatch(ProductCollection productCollection) {
        TitleOptimizationResult result = new TitleOptimizationResult();
        result.setSourceTitle(productCollection == null ? null : normalizePlainText(productCollection.getProductName(), 240));
        result.setFailedKeywords(new ArrayList<>());

        if (productCollection == null) {
            result.setErrorMsg("product is null");
            return result;
        }
        if (config.getEnabled() == null || !config.getEnabled()) {
            result.setErrorMsg("Temu title optimizer disabled");
            return result;
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            result.setErrorMsg("Missing TEMU_TITLE_AI_API_KEY");
            return result;
        }

        int maxAttempts = config.getMaxAttempts() == null ? 3 : Math.max(1, config.getMaxAttempts());
        Map<String, Object> productContext = buildProductContext(productCollection);
        GenerationPayload lastPayload = null;
        TemuCategoryDTO.MatchCategoryResponse lastMatch = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            result.setAttemptCount(attempt);
            try {
                lastPayload = generatePayload(productContext, result.getFailedKeywords(), attempt);
            } catch (Exception ex) {
                result.setErrorMsg(StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName());
                return result;
            }
            applyGeneratedPayload(result, lastPayload);

            String keyword = normalizeKeywordPhrase(lastPayload.categoryKeywords());
            if (!StringUtils.hasText(keyword)) {
                result.getFailedKeywords().add("<empty>");
                continue;
            }

            result.setMatchedKeyword(keyword);
            lastMatch = temuCategoryService.matchCategory(keyword);
            if (hasCategoryOptions(lastMatch)) {
                result.setCategoryMatched(true);
                TemuCategoryDTO.MatchOption first = lastMatch.getOptions().get(0);
                result.setMatchedTemuCatid(first == null ? null : first.getPathIds());
                result.setMatchedTemuCatname(first == null ? null : first.getPathNames());
                result.setMatchCategoryResponse(lastMatch);
                return result;
            }

            result.getFailedKeywords().add(keyword);
            result.setMatchCategoryResponse(lastMatch);
        }

        if (lastMatch != null && StringUtils.hasText(lastMatch.getErrorMsg())) {
            result.setErrorMsg(lastMatch.getErrorMsg());
        } else {
            result.setErrorMsg("AI keywords did not match any TEMU category");
        }
        return result;
    }

    private void applyGeneratedPayload(TitleOptimizationResult result, GenerationPayload payload) {
        if (result == null || payload == null) {
            return;
        }
        result.setOptimizedTitleEn(normalizePlainText(payload.optimizedTitleEn(), 180));
        result.setOptimizedTitleZh(normalizePlainText(payload.optimizedTitleZh(), 120));
        result.setCategoryKeywords(normalizeKeywordPhrase(payload.categoryKeywords()));
    }

    private GenerationPayload generatePayload(Map<String, Object> productContext,
                                              List<String> failedKeywords,
                                              int attempt) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", defaultModel());
        req.put("messages", List.of(
                Map.of("role", "system", "content", systemInstruction()),
                Map.of("role", "user", "content", buildPrompt(productContext, failedKeywords, attempt))
        ));
        req.put("max_tokens", config.getMaxTokens() == null ? 1600 : Math.max(400, config.getMaxTokens()));
        req.put("stream", true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON));
        headers.setBearerAuth(config.getApiKey().trim());

        String url = completionsUrl(config.getBaseUrl());
        String requestJson = objectMapper.writeValueAsString(req);

        StreamResult streamResult = restTemplate.execute(
                url,
                HttpMethod.POST,
                request -> {
                    request.getHeaders().putAll(headers);
                    request.getBody().write(requestJson.getBytes(StandardCharsets.UTF_8));
                },
                this::readStreamingResponse
        );

        if (streamResult == null) {
            throw new IllegalStateException("AI request failed: null response");
        }
        if (streamResult.statusCode != HttpStatus.OK || !StringUtils.hasText(streamResult.raw)) {
            throw new IllegalStateException("AI request failed: " + streamResult.statusCode);
        }

        String content = stripCodeFence(streamResult.content);
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("Empty AI response");
        }

        Map<String, Object> obj = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        String titleEn = normalizePlainText(asString(obj.get("optimizedTitleEn")), 180);
        String titleZh = normalizePlainText(asString(obj.get("optimizedTitleZh")), 120);
        String keywords = normalizeKeywordPhrase(asString(obj.get("categoryKeywords")));
        if (!StringUtils.hasText(keywords) && obj.get("keywords") instanceof List<?> list && !list.isEmpty()) {
            List<String> texts = new ArrayList<>();
            for (Object item : list) {
                String v = normalizePlainText(asString(item), 40);
                if (StringUtils.hasText(v)) {
                    texts.add(v);
                }
                if (texts.size() >= 6) {
                    break;
                }
            }
            keywords = normalizeKeywordPhrase(String.join(" ", texts));
        }
        if (!StringUtils.hasText(titleEn) && !StringUtils.hasText(titleZh) && !StringUtils.hasText(keywords)) {
            throw new IllegalStateException("AI response missing required fields");
        }
        return new GenerationPayload(titleEn, titleZh, keywords);
    }

    private Map<String, Object> buildProductContext(ProductCollection productCollection) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("sourceTitle", normalizePlainText(productCollection.getProductName(), 240));
        ctx.put("originalCategory", normalizePlainText(productCollection.getOriginalCategory(), 160));
        ctx.put("productCategory", normalizePlainText(productCollection.getProductCategory(), 160));
        ctx.put("companyName", normalizePlainText(productCollection.getCompanyName(), 120));
        ctx.put("attributes", reduceStructuredPayload(productCollection.getAttributesData(), 30));
        ctx.put("customMadeSpecs", reduceStructuredPayload(productCollection.getCustomMadeSpecs(), 20));
        ctx.put("skuSummary", summarizeSkuModel(productCollection.getSkuModel()));
        ctx.put("originalContent", reduceOriginalContent(productCollection.getOriginalContent()));
        return ctx;
    }

    private Object reduceStructuredPayload(String json, int maxItems) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (parsed instanceof Map<?, ?> rawMap) {
                Map<String, Object> out = new LinkedHashMap<>();
                int count = 0;
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    String key = normalizePlainText(asString(entry.getKey()), 80);
                    if (!StringUtils.hasText(key)) {
                        continue;
                    }
                    out.put(key, compactValue(entry.getValue()));
                    count++;
                    if (count >= maxItems) {
                        break;
                    }
                }
                return out;
            }
            if (parsed instanceof List<?> rawList) {
                List<Object> out = new ArrayList<>();
                for (Object item : rawList) {
                    out.add(compactValue(item));
                    if (out.size() >= maxItems) {
                        break;
                    }
                }
                return out;
            }
            return normalizePlainText(asString(parsed), 1200);
        } catch (Exception ignored) {
            return normalizePlainText(json, 1200);
        }
    }

    private Object reduceOriginalContent(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> out = new LinkedHashMap<>();
            copyIfPresent(parsed, out, "productName");
            copyIfPresent(parsed, out, "productCategory");
            copyIfPresent(parsed, out, "originalCategory");
            copyIfPresent(parsed, out, "companyName");
            copyIfPresent(parsed, out, "material");
            copyIfPresent(parsed, out, "usage");
            copyIfPresent(parsed, out, "scene");
            return out.isEmpty() ? normalizePlainText(json, 1200) : out;
        } catch (Exception ignored) {
            return normalizePlainText(json, 1200);
        }
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source == null || target == null || !source.containsKey(key)) {
            return;
        }
        Object value = compactValue(source.get(key));
        if (value != null) {
            target.put(key, value);
        }
    }

    private Object summarizeSkuModel(String skuModelJson) {
        if (!StringUtils.hasText(skuModelJson)) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(skuModelJson, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> out = new LinkedHashMap<>();
            Object props = parsed.get("props");
            if (props instanceof List<?> propList) {
                List<Map<String, Object>> propSummary = new ArrayList<>();
                for (Object item : propList) {
                    if (!(item instanceof Map<?, ?> map)) {
                        continue;
                    }
                    Map<String, Object> one = new LinkedHashMap<>();
                    one.put("name", normalizePlainText(asString(map.get("name")), 80));
                    List<String> values = new ArrayList<>();
                    Object valuesObj = map.get("values");
                    if (valuesObj instanceof List<?> valueList) {
                        for (Object valueItem : valueList) {
                            if (!(valueItem instanceof Map<?, ?> valueMap)) {
                                continue;
                            }
                            String valueName = normalizePlainText(asString(valueMap.get("name")), 60);
                            if (StringUtils.hasText(valueName)) {
                                values.add(valueName);
                            }
                            if (values.size() >= 10) {
                                break;
                            }
                        }
                    }
                    one.put("values", values);
                    propSummary.add(one);
                    if (propSummary.size() >= 5) {
                        break;
                    }
                }
                out.put("props", propSummary);
            }
            Object skuMapObj = parsed.get("skuMap");
            if (skuMapObj instanceof Map<?, ?> skuMap) {
                List<String> specKeys = new ArrayList<>();
                for (Object key : skuMap.keySet()) {
                    String specKey = normalizePlainText(asString(key), 160);
                    if (StringUtils.hasText(specKey)) {
                        specKeys.add(specKey);
                    }
                    if (specKeys.size() >= 12) {
                        break;
                    }
                }
                out.put("skuCount", skuMap.size());
                out.put("specKeys", specKeys);
            }
            return out.isEmpty() ? normalizePlainText(skuModelJson, 1200) : out;
        } catch (Exception ignored) {
            return normalizePlainText(skuModelJson, 1200);
        }
    }

    private Object compactValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = normalizePlainText(asString(entry.getKey()), 60);
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                out.put(key, normalizePlainText(asString(entry.getValue()), 120));
                count++;
                if (count >= 8) {
                    break;
                }
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) {
                out.add(normalizePlainText(asString(item), 120));
                if (out.size() >= 8) {
                    break;
                }
            }
            return out;
        }
        return normalizePlainText(asString(value), 200);
    }

    private boolean hasCategoryOptions(TemuCategoryDTO.MatchCategoryResponse response) {
        return response != null && response.isSuccess() && response.getOptions() != null && !response.getOptions().isEmpty();
    }

    private String buildPrompt(Map<String, Object> productContext, List<String> failedKeywords, int attempt) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attempt", attempt);
        payload.put("failedKeywords", failedKeywords == null ? Collections.emptyList() : failedKeywords);
        payload.put("product", productContext);

        return "请根据下面商品信息，输出适合 TEMU 的非侵权标题和类目匹配关键词。\n"
                + "硬性要求：\n"
                + "1. 返回严格 JSON，不要 markdown，不要解释。\n"
                + "2. 只返回三个字段：optimizedTitleEn、optimizedTitleZh、categoryKeywords。\n"
                + "3. optimizedTitleEn 必须是英文标题，optimizedTitleZh 必须是中文标题。\n"
                + "4. categoryKeywords 是一个用于 TEMU 类目匹配的短语，不要写品牌词，不要写商标，不要写动漫/IP/人物名称。\n"
                + "5. 标题和关键词都不能出现明显品牌或侵权词，例如 Sanrio、Marvel、Disney、Hello Kitty、Pokemon 等，也不要出现 1688 店铺名。\n"
                + "6. 如果 failedKeywords 里有失败词，新的 categoryKeywords 不能重复或只做轻微改写。\n"
                + "7. 语义要尽量准确，优先描述品类、材质、用途、场景、核心外观。\n"
                + "8. 字段值必须是字符串。\n"
                + "JSON 示例：{\"optimizedTitleEn\":\"...\",\"optimizedTitleZh\":\"...\",\"categoryKeywords\":\"...\"}\n"
                + "商品信息：\n"
                + objectMapper.writeValueAsString(payload);
    }

    private String systemInstruction() {
        return "You are a cross-border e-commerce catalog assistant. "
                + "You generate generic, non-infringing product titles and category matching keywords. "
                + "You must remove brand, trademark, anime, celebrity, and copyrighted IP references. "
                + "Return strict JSON only.";
    }

    private String completionsUrl(String baseUrl) {
        return TextAiUrlHelper.chatCompletionsUrl(baseUrl, "https://chatbot.tminos.com");
    }

    private String defaultModel() {
        return StringUtils.hasText(config.getModel()) ? config.getModel().trim() : "gpt-5.2";
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

    private StreamResult readStreamingResponse(ClientHttpResponse response) throws IOException {
        HttpStatusCode statusCode = response.getStatusCode();
        StringBuilder raw = new StringBuilder();
        StringBuilder content = new StringBuilder();

        try (InputStream body = response.getBody()) {
            if (body != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        raw.append(line).append('\n');
                        if (!line.startsWith("data:")) {
                            continue;
                        }
                        String payload = line.substring(5).trim();
                        if (!StringUtils.hasText(payload) || "[DONE]".equals(payload)) {
                            continue;
                        }
                        appendStreamingContent(payload, content);
                    }
                }
            }
        }

        String rawText = raw.toString();
        String contentText = content.toString();
        if (!StringUtils.hasText(contentText) && StringUtils.hasText(rawText) && !rawText.contains("data:")) {
            try {
                contentText = extractAssistantContent(rawText);
            } catch (Exception ignored) {
            }
        }
        return new StreamResult(statusCode, rawText, contentText);
    }

    private void appendStreamingContent(String payload, StringBuilder content) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return;
            }
            JsonNode delta = choices.get(0).path("delta");
            String text = delta.path("content").asText(null);
            if (StringUtils.hasText(text)) {
                content.append(text);
                return;
            }
            JsonNode contentNode = delta.get("content");
            if (contentNode != null && contentNode.isArray()) {
                for (JsonNode part : contentNode) {
                    String partText = part.path("text").asText(null);
                    if (StringUtils.hasText(partText)) {
                        content.append(partText);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String stripCodeFence(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineBreak = trimmed.indexOf('\n');
        if (firstLineBreak < 0) {
            return trimmed.replace("```", "").trim();
        }
        String body = trimmed.substring(firstLineBreak + 1);
        int endFence = body.lastIndexOf("```");
        if (endFence >= 0) {
            body = body.substring(0, endFence);
        }
        return body.trim();
    }

    private record StreamResult(HttpStatusCode statusCode, String raw, String content) {
    }

    private String normalizePlainText(String raw, int maxLength) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim()
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ");
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength).trim();
    }

    private String normalizeKeywordPhrase(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim()
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .replace('，', ' ')
                .replace(',', ' ')
                .replace('/', ' ')
                .replace('|', ' ')
                .replaceAll("\\s+", " ");
        while (!text.isEmpty() && isWrapperQuote(text.charAt(0))) {
            text = text.substring(1).trim();
        }
        while (!text.isEmpty() && isWrapperQuote(text.charAt(text.length() - 1))) {
            text = text.substring(0, text.length() - 1).trim();
        }
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.length() > 90) {
            text = text.substring(0, 90).trim();
        }
        return text;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isWrapperQuote(char ch) {
        return ch == '\"' || ch == '\'' || ch == '`';
    }

    private record GenerationPayload(String optimizedTitleEn, String optimizedTitleZh, String categoryKeywords) {
    }

    public static class TitleOptimizationResult {
        private String sourceTitle;
        private String optimizedTitleEn;
        private String optimizedTitleZh;
        private String categoryKeywords;
        private Integer attemptCount;
        private String matchedKeyword;
        private Boolean categoryMatched = false;
        private String matchedTemuCatid;
        private String matchedTemuCatname;
        private String errorMsg;
        private List<String> failedKeywords = new ArrayList<>();
        private TemuCategoryDTO.MatchCategoryResponse matchCategoryResponse;

        public String getSourceTitle() {
            return sourceTitle;
        }

        public void setSourceTitle(String sourceTitle) {
            this.sourceTitle = sourceTitle;
        }

        public String getOptimizedTitleEn() {
            return optimizedTitleEn;
        }

        public void setOptimizedTitleEn(String optimizedTitleEn) {
            this.optimizedTitleEn = optimizedTitleEn;
        }

        public String getOptimizedTitleZh() {
            return optimizedTitleZh;
        }

        public void setOptimizedTitleZh(String optimizedTitleZh) {
            this.optimizedTitleZh = optimizedTitleZh;
        }

        public String getCategoryKeywords() {
            return categoryKeywords;
        }

        public void setCategoryKeywords(String categoryKeywords) {
            this.categoryKeywords = categoryKeywords;
        }

        public Integer getAttemptCount() {
            return attemptCount;
        }

        public void setAttemptCount(Integer attemptCount) {
            this.attemptCount = attemptCount;
        }

        public String getMatchedKeyword() {
            return matchedKeyword;
        }

        public void setMatchedKeyword(String matchedKeyword) {
            this.matchedKeyword = matchedKeyword;
        }

        public Boolean getCategoryMatched() {
            return categoryMatched;
        }

        public void setCategoryMatched(Boolean categoryMatched) {
            this.categoryMatched = categoryMatched;
        }

        public String getMatchedTemuCatid() {
            return matchedTemuCatid;
        }

        public void setMatchedTemuCatid(String matchedTemuCatid) {
            this.matchedTemuCatid = matchedTemuCatid;
        }

        public String getMatchedTemuCatname() {
            return matchedTemuCatname;
        }

        public void setMatchedTemuCatname(String matchedTemuCatname) {
            this.matchedTemuCatname = matchedTemuCatname;
        }

        public String getErrorMsg() {
            return errorMsg;
        }

        public void setErrorMsg(String errorMsg) {
            this.errorMsg = errorMsg;
        }

        public List<String> getFailedKeywords() {
            return failedKeywords;
        }

        public void setFailedKeywords(List<String> failedKeywords) {
            this.failedKeywords = failedKeywords == null ? new ArrayList<>() : failedKeywords;
        }

        public TemuCategoryDTO.MatchCategoryResponse getMatchCategoryResponse() {
            return matchCategoryResponse;
        }

        public void setMatchCategoryResponse(TemuCategoryDTO.MatchCategoryResponse matchCategoryResponse) {
            this.matchCategoryResponse = matchCategoryResponse;
        }
    }
}
