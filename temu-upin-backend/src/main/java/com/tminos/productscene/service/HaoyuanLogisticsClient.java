package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.LogisticsProviderConfig;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Component
public class HaoyuanLogisticsClient {

    private final ObjectMapper objectMapper;

    public HaoyuanLogisticsClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode sendRequest(LogisticsProviderConfig config, String serviceMethod, String paramsJson) {
        if (config == null) {
            throw new IllegalArgumentException("logistics config is required");
        }
        if (!StringUtils.hasText(config.getBaseUrl())) {
            throw new IllegalStateException("浩远国际 baseUrl 为空");
        }
        if (!StringUtils.hasText(config.getAppToken())) {
            throw new IllegalStateException("浩远国际 appToken 为空");
        }
        if (!StringUtils.hasText(config.getAppKey())) {
            throw new IllegalStateException("浩远国际 appKey 为空");
        }
        try {
            RestTemplate restTemplate = new RestTemplate(buildFactory(config));
            restTemplate.getMessageConverters().removeIf(StringHttpMessageConverter.class::isInstance);
            restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("appToken", config.getAppToken().trim());
            form.add("appKey", config.getAppKey().trim());
            form.add("serviceMethod", serviceMethod);
            form.add("paramsJson", paramsJson == null ? "" : paramsJson);

            ResponseEntity<String> response = restTemplate.postForEntity(config.getBaseUrl().trim(), form, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("HTTP " + response.getStatusCode().value());
            }
            String body = response.getBody();
            if (!StringUtils.hasText(body)) {
                throw new IllegalStateException("浩远国际响应为空");
            }
            JsonNode root = objectMapper.readTree(body);
            if (!isSuccess(root)) {
                throw new IllegalStateException(resolveMessage(root));
            }
            return root;
        } catch (Exception e) {
            throw new IllegalStateException("浩远国际接口调用失败(" + serviceMethod + "): " + e.getMessage(), e);
        }
    }

    private static SimpleClientHttpRequestFactory buildFactory(LogisticsProviderConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getConnectTimeoutMs() == null ? 10000 : config.getConnectTimeoutMs());
        factory.setReadTimeout(config.getReadTimeoutMs() == null ? 30000 : config.getReadTimeoutMs());
        return factory;
    }

    private static boolean isSuccess(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return false;
        }
        JsonNode successNode = root.path("success");
        if (successNode.isBoolean()) {
            return successNode.asBoolean(false);
        }
        if (successNode.isNumber()) {
            return successNode.asInt(0) == 1;
        }
        return false;
    }

    private static String resolveMessage(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return "未知错误";
        }
        String cnMessage = root.path("cnmessage").asText(null);
        if (StringUtils.hasText(cnMessage)) {
            return cnMessage.trim();
        }
        String enMessage = root.path("enmessage").asText(null);
        if (StringUtils.hasText(enMessage)) {
            return enMessage.trim();
        }
        return "未知错误";
    }
}
