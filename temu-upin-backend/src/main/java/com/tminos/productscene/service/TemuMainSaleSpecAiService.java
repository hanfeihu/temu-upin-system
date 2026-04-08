package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.config.AITemuAttrFillerConfig;
import com.tminos.productscene.util.TextAiUrlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TemuMainSaleSpecAiService {

    private static final Logger log = LoggerFactory.getLogger(TemuMainSaleSpecAiService.class);

    private final AITemuAttrFillerConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public TemuMainSaleSpecAiService(AITemuAttrFillerConfig config,
                                    ObjectMapper objectMapper,
                                    @Qualifier("aiLongRestTemplate") RestTemplate restTemplate) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public AiPlanResult inferMainSaleSpecPlan(String promptPayloadJson,
                                              List<Map<String, Object>> allowedParentSpecs) {
        AiPlanResult out = new AiPlanResult();
        out.success = false;

        if (config.getEnabled() == null || !config.getEnabled()) {
            out.errorMsg = "AI disabled";
            return out;
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            out.errorMsg = "Missing TEMU_ATTR_AI_API_KEY";
            return out;
        }
        if (!StringUtils.hasText(promptPayloadJson)) {
            out.errorMsg = "promptPayloadJson empty";
            return out;
        }
        if (allowedParentSpecs == null || allowedParentSpecs.isEmpty()) {
            out.errorMsg = "allowedParentSpecs empty";
            return out;
        }

        try {
            String prompt = buildPrompt(promptPayloadJson, allowedParentSpecs);

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("model", config.getModel() == null ? "gpt-5.2" : config.getModel());
            req.put("messages", List.of(
                    Map.of("role", "system", "content", systemInstruction()),
                    Map.of("role", "user", "content", prompt)
            ));
            req.put("max_tokens", 1800);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());

            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(req, headers);
            String url = completionsUrl(config.getBaseUrl());
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, httpEntity, String.class);
            out.responseRaw = resp.getBody();
            if (resp.getStatusCode() != HttpStatus.OK || !StringUtils.hasText(resp.getBody())) {
                out.errorMsg = "AI request failed: " + resp.getStatusCode();
                return out;
            }

            String content = extractAssistantContent(resp.getBody());
            out.responseContent = content;
            if (!StringUtils.hasText(content)) {
                out.errorMsg = "Empty AI response";
                return out;
            }

            JsonNode root = objectMapper.readTree(stripJsonFence(content));
            List<String> allowedNames = extractAllowedNames(allowedParentSpecs);
            List<Map<String, Object>> dimensions = parseDimensions(root.path("dimensions"), allowedNames);
            if (dimensions.isEmpty()) {
                out.errorMsg = "Missing dimensions";
                return out;
            }

            List<Map<String, Object>> skuPlans = parseSkuPlans(root.path("skuPlans"), allowedNames);
            if (skuPlans.isEmpty()) {
                out.errorMsg = "Missing skuPlans";
                return out;
            }

            out.success = true;
            out.dimensions = dimensions;
            out.skuPlans = skuPlans;
            out.reason = root.path("reason").asText(null);
            return out;
        } catch (Exception e) {
            out.errorMsg = e.getMessage();
            log.warn("AI inferMainSaleSpecPlan failed", e);
            return out;
        }
    }

    private String systemInstruction() {
        return "You design TEMU main sale spec plans. Return ONLY valid JSON. No markdown, no explanation outside JSON.";
    }

    private String buildPrompt(String promptPayloadJson, List<Map<String, Object>> allowedParentSpecs) throws Exception {
        String allowedJson = objectMapper.writeValueAsString(allowedParentSpecs);
        return "根据商品标题、原始1688 SKU、当前 TEMU SKU 数据，设计 TEMU 主销售属性方案。\n" +
                "目标不是只选一个 parentSpecName，而是输出可直接映射到 AddGloGoodsRequest 的多维主销售属性草案。\n" +
                "你可以选择 1 到 3 个维度，例如 颜色、型号、风格。\n" +
                "\n" +
                "硬性约束:\n" +
                "1. parentSpecName 必须严格来自 allowedParentSpecs。\n" +
                "2. 每个 SKU 都必须输出 selectedSpecs。\n" +
                "3. selectedSpecs 的顺序必须和 dimensions 顺序一致。\n" +
                "4. specName 必须优先直接使用该 SKU 在 specJson 中对应 parentSpecName 的原始值，不要擅自缩写、总结、翻译或删除后缀词。只有在原始值缺失时，才允许给出保守的简洁值。\n" +
                "5. skuPlans 中尽量同时保留 skuIndex、originSkuId、temuSkuId，方便后端映射。\n" +
                "6. 如果某个商品只需要一个维度，就只输出一个维度；如果需要两个或三个维度，也可以输出。\n" +
                "7. 如果某个 SKU 的 specJson 已经有 颜色/风格/型号 等明确值，selectedSpecs.specName 应与该值完全一致。\n" +
                "8. 如果 inputPayload 中存在 preferredParentSpecName，则 dimensions 必须优先使用它；没有充分理由时不要改成别的维度。\n" +
                "9. 返回 JSON，结构必须满足下述 schema，不要返回额外字段。\n" +
                "\n" +
                "输出 schema:\n" +
                "{\n" +
                "  \"reason\": \"整体判断说明\",\n" +
                "  \"dimensions\": [\n" +
                "    {\"parentSpecName\": \"颜色\", \"reason\": \"为什么选这个维度\"}\n" +
                "  ],\n" +
                "  \"skuPlans\": [\n" +
                "    {\n" +
                "      \"skuIndex\": 0,\n" +
                "      \"originSkuId\": \"...\",\n" +
                "      \"temuSkuId\": \"...\",\n" +
                "      \"selectedSpecs\": [\n" +
                "        {\"parentSpecName\": \"颜色\", \"specName\": \"紫色\"}\n" +
                "      ],\n" +
                "      \"reason\": \"该 SKU 为什么映射成这些主销售属性\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "\n" +
                "allowedParentSpecs: " + allowedJson + "\n" +
                "inputPayload: " + promptPayloadJson;
    }

    private List<String> extractAllowedNames(List<Map<String, Object>> allowedParentSpecs) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> row : allowedParentSpecs) {
            if (row == null) continue;
            String name = row.get("parentSpecName") == null ? null : String.valueOf(row.get("parentSpecName")).trim();
            if (StringUtils.hasText(name) && !out.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }

    private List<Map<String, Object>> parseDimensions(JsonNode node, List<String> allowedNames) {
        if (node == null || !node.isArray()) return Collections.emptyList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) continue;
            String parentSpecName = item.path("parentSpecName").asText(null);
            if (!StringUtils.hasText(parentSpecName)) continue;
            parentSpecName = parentSpecName.trim();
            if (!allowedNames.contains(parentSpecName)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("parentSpecName", parentSpecName);
            row.put("reason", item.path("reason").asText(null));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> parseSkuPlans(JsonNode node, List<String> allowedNames) {
        if (node == null || !node.isArray()) return Collections.emptyList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            if (item.has("skuIndex") && !item.get("skuIndex").isNull()) {
                row.put("skuIndex", item.get("skuIndex").asInt());
            }
            row.put("originSkuId", textOrNull(item, "originSkuId"));
            row.put("temuSkuId", textOrNull(item, "temuSkuId"));
            row.put("reason", textOrNull(item, "reason"));

            JsonNode selectedSpecsNode = item.path("selectedSpecs");
            if (!selectedSpecsNode.isArray() || selectedSpecsNode.isEmpty()) continue;

            List<Map<String, Object>> selectedSpecs = new ArrayList<>();
            for (JsonNode specNode : selectedSpecsNode) {
                if (specNode == null || !specNode.isObject()) continue;
                String parentSpecName = textOrNull(specNode, "parentSpecName");
                String specName = textOrNull(specNode, "specName");
                if (!StringUtils.hasText(parentSpecName) || !StringUtils.hasText(specName)) continue;
                parentSpecName = parentSpecName.trim();
                specName = specName.trim();
                if (!allowedNames.contains(parentSpecName)) continue;

                Map<String, Object> spec = new LinkedHashMap<>();
                spec.put("parentSpecName", parentSpecName);
                spec.put("specName", specName);
                selectedSpecs.add(spec);
            }
            if (selectedSpecs.isEmpty()) continue;

            row.put("selectedSpecs", selectedSpecs);
            out.add(row);
        }
        return out;
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || field == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText(null);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String stripJsonFence(String content) {
        if (!StringUtils.hasText(content)) return content;
        String s = content.trim();
        if (s.startsWith("```") && s.endsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
            s = s.replaceFirst("\\s*```$", "");
        }
        return s.trim();
    }

    private String completionsUrl(String base) {
        return TextAiUrlHelper.chatCompletionsUrl(base, "https://chatbot.tminos.com");
    }

    private String extractAssistantContent(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode msg = choices.get(0).path("message");
                return msg.path("content").asText(null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static class AiPlanResult {
        private boolean success;
        private List<Map<String, Object>> dimensions;
        private List<Map<String, Object>> skuPlans;
        private String reason;
        private String errorMsg;
        private String responseRaw;
        private String responseContent;

        public boolean isSuccess() { return success; }
        public List<Map<String, Object>> getDimensions() { return dimensions; }
        public List<Map<String, Object>> getSkuPlans() { return skuPlans; }
        public String getReason() { return reason; }
        public String getErrorMsg() { return errorMsg; }
        public String getResponseRaw() { return responseRaw; }
        public String getResponseContent() { return responseContent; }
    }
}
