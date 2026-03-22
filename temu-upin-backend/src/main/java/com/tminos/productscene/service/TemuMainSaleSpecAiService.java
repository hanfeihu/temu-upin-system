package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.config.AITemuAttrFillerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

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

    public AiChooseResult chooseParentSpecName(String skuSignature,
                                               String inferredDimKey,
                                               List<String> allowedParentSpecNames) {
        AiChooseResult out = new AiChooseResult();
        out.success = false;

        if (config.getEnabled() == null || !config.getEnabled()) {
            out.errorMsg = "AI disabled";
            return out;
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            out.errorMsg = "Missing TEMU_ATTR_AI_API_KEY";
            return out;
        }
        if (allowedParentSpecNames == null || allowedParentSpecNames.isEmpty()) {
            out.errorMsg = "allowedParentSpecNames empty";
            return out;
        }

        try {
            String prompt = buildPrompt(skuSignature, inferredDimKey, allowedParentSpecNames);

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("model", config.getModel() == null ? "gpt-5.2" : config.getModel());
            req.put("messages", List.of(
                    Map.of("role", "system", "content", systemInstruction()),
                    Map.of("role", "user", "content", prompt)
            ));
            req.put("max_tokens", 250);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());

            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(req, headers);
            String url = completionsUrl(config.getBaseUrl());
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, httpEntity, String.class);
            if (resp.getStatusCode() != HttpStatus.OK || !StringUtils.hasText(resp.getBody())) {
                out.errorMsg = "AI request failed: " + resp.getStatusCode();
                return out;
            }

            String content = extractAssistantContent(resp.getBody());
            if (!StringUtils.hasText(content)) {
                out.errorMsg = "Empty AI response";
                return out;
            }

            JsonNode root = objectMapper.readTree(content);
            String chosen = root.path("parentSpecName").asText(null);
            if (!StringUtils.hasText(chosen)) {
                out.errorMsg = "Missing parentSpecName";
                return out;
            }
            chosen = chosen.trim();
            if (!allowedParentSpecNames.contains(chosen)) {
                out.errorMsg = "AI returned disallowed parentSpecName: " + chosen;
                return out;
            }

            out.success = true;
            out.parentSpecName = chosen;
            out.reason = root.path("reason").asText(null);
            return out;
        } catch (Exception e) {
            out.errorMsg = e.getMessage();
            log.warn("AI chooseParentSpecName failed", e);
            return out;
        }
    }

    private String systemInstruction() {
        return "You are a strict classifier. Return ONLY valid JSON. No markdown.";
    }

    private String buildPrompt(String skuSignature, String inferredDimKey, List<String> allowed) {
        return "Choose the best parentSpecName (TEMU main sales attribute) for the SKU variants.\n" +
                "Constraints:\n" +
                "- Output JSON: {\"parentSpecName\":<one of allowed>,\"reason\":<short>}\n" +
                "- parentSpecName must be exactly one of allowed\n" +
                "- Prefer inferredDimKey when it matches allowed\n\n" +
                "allowed: " + allowed + "\n" +
                "inferredDimKey: " + (inferredDimKey == null ? "" : inferredDimKey) + "\n" +
                "skuSignature: " + (skuSignature == null ? "" : skuSignature);
    }

    private String completionsUrl(String base) {
        if (!StringUtils.hasText(base)) return "https://cliapi.tminos.com/v1/chat/completions";
        String b = base.trim();
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/v1")) {
            return b + "/chat/completions";
        }
        if (b.endsWith("/v1/chat/completions")) return b;
        if (b.contains("/chat/completions")) return b;
        return b + "/chat/completions";
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

    public static class AiChooseResult {
        private boolean success;
        private String parentSpecName;
        private String reason;
        private String errorMsg;

        public boolean isSuccess() { return success; }
        public String getParentSpecName() { return parentSpecName; }
        public String getReason() { return reason; }
        public String getErrorMsg() { return errorMsg; }
    }
}
