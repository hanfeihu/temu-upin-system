package com.tminos.productscene.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DimensionExtractor {

    private static final BigDecimal MAX_CREDIBLE_PHYSICAL_DIMENSION_CM = BigDecimal.valueOf(1000);

    private static final Pattern KEYED_DIMENSION_PATTERN = Pattern.compile(
            "(?i)(直径|半径|长度|长|宽度|宽|高度|高|尺寸|规格|diameter|length|width|height|size)\\s*[:：=\\-【\\[\\(（]?\\s*(\\d+(?:\\.\\d+)?)\\s*(mm|毫米|cm|厘米|公分|m|米)?"
    );
    private static final Pattern MULTI_DIMENSION_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])(\\d+(?:\\.\\d+)?)\\s*[x×*]\\s*(\\d+(?:\\.\\d+)?)(?:\\s*[x×*]\\s*(\\d+(?:\\.\\d+)?))?\\s*(mm|毫米|cm|厘米|公分|m|米)(?![A-Za-z0-9])"
    );
    private static final Pattern UNIT_DIMENSION_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])(\\d+(?:\\.\\d+)?)\\s*(mm|毫米|cm|厘米|公分|m|米)(?![A-Za-z0-9])"
    );

    private DimensionExtractor() {
    }

    public static DimensionResult max(DimensionResult left, DimensionResult right) {
        left = credible(left);
        right = credible(right);
        if (left == null || left.maxDimensionCm() == null) {
            return right;
        }
        if (right == null || right.maxDimensionCm() == null) {
            return left;
        }
        return right.maxDimensionCm().compareTo(left.maxDimensionCm()) > 0 ? right : left;
    }

    public static DimensionResult fromSku(String specText, String specJson, ObjectMapper objectMapper) {
        DimensionResult result = fromText(specText, "SKU文案");
        result = max(result, fromJson(specJson, objectMapper, "SKU属性"));
        return result;
    }

    public static DimensionResult fromDetailJson(String parsedJson, String extractedJson, ObjectMapper objectMapper) {
        DimensionResult result = max(
                fromKnownJsonFields(parsedJson, objectMapper, "商品包装属性"),
                fromKnownJsonFields(extractedJson, objectMapper, "商品属性")
        );
        result = max(result, fromSelectedDetailAttributeFields(parsedJson, objectMapper, "商品属性"));
        result = max(result, fromSelectedDetailAttributeFields(extractedJson, objectMapper, "商品属性"));
        return result;
    }

    public static DimensionResult fromJson(String json, ObjectMapper objectMapper, String source) {
        if (!StringUtils.hasText(json) || objectMapper == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            return fromJsonNode(root, source, 0);
        } catch (Exception ignored) {
            return fromText(json, source);
        }
    }

    public static DimensionResult fromText(String text, String source) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim();
        DimensionResult result = null;

        Matcher keyedMatcher = KEYED_DIMENSION_PATTERN.matcher(normalized);
        while (keyedMatcher.find()) {
            BigDecimal value = toCm(keyedMatcher.group(2), keyedMatcher.group(3));
            if (value == null) {
                continue;
            }
            if (isRadiusKeyword(keyedMatcher.group(1))) {
                value = value.multiply(BigDecimal.valueOf(2));
            }
            result = max(result, build(value, source, keyedMatcher.group()));
        }

        Matcher multiMatcher = MULTI_DIMENSION_PATTERN.matcher(normalized);
        while (multiMatcher.find()) {
            BigDecimal value = maxNumber(
                    toCm(multiMatcher.group(1), multiMatcher.group(4)),
                    toCm(multiMatcher.group(2), multiMatcher.group(4)),
                    toCm(multiMatcher.group(3), multiMatcher.group(4))
            );
            result = max(result, build(value, source, multiMatcher.group()));
        }

        if (looksDimensionRelated(normalized)) {
            Matcher unitMatcher = UNIT_DIMENSION_PATTERN.matcher(normalized);
            while (unitMatcher.find()) {
                BigDecimal value = toCm(unitMatcher.group(1), unitMatcher.group(2));
                result = max(result, build(value, source, unitMatcher.group()));
            }
        }

        return result;
    }

    private static DimensionResult fromKnownJsonFields(String json, ObjectMapper objectMapper, String source) {
        if (!StringUtils.hasText(json) || objectMapper == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            BigDecimal max = maxNumber(
                    deepFindCm(root, "packagingLength"),
                    deepFindCm(root, "packagingWidth"),
                    deepFindCm(root, "packagingHeight"),
                    deepFindCm(root, "length"),
                    deepFindCm(root, "width"),
                    deepFindCm(root, "height"),
                    deepFindCm(root, "diameter")
            );
            DimensionResult result = build(max, source, "结构化长宽高/直径字段");
            DimensionResult dimensionsText = fromDeepText(root, "packagingDimensions", source);
            return max(result, dimensionsText);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static DimensionResult fromSelectedDetailAttributeFields(String json, ObjectMapper objectMapper, String source) {
        if (!StringUtils.hasText(json) || objectMapper == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            DimensionResult result = null;
            result = max(result, fromDeepJsonText(root, objectMapper, source, "attributesDataJson"));
            result = max(result, fromDeepJsonText(root, objectMapper, source, "attributeDataJson"));
            result = max(result, fromDeepJsonText(root, objectMapper, source, "productAttributesJson"));
            result = max(result, fromDeepJsonText(root, objectMapper, source, "customMadeSpecsJson"));
            result = max(result, fromDeepNode(root, source, "attributesData"));
            result = max(result, fromDeepNode(root, source, "attributes"));
            result = max(result, fromDeepNode(root, source, "productAttributes"));
            return result;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static DimensionResult fromDeepJsonText(JsonNode node, ObjectMapper objectMapper, String source, String fieldName) {
        JsonNode value = findDeepField(node, fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText("");
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return fromJson(text, objectMapper, source);
    }

    private static DimensionResult fromDeepNode(JsonNode node, String source, String fieldName) {
        JsonNode value = findDeepField(node, fieldName);
        return value == null ? null : fromJsonNode(value, source, 0);
    }

    private static JsonNode findDeepField(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                if (fieldName.equals(entry.getKey())) {
                    return entry.getValue();
                }
                JsonNode nested = findDeepField(entry.getValue(), fieldName);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode nested = findDeepField(child, fieldName);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static DimensionResult fromJsonNode(JsonNode node, String source, int depth) {
        if (node == null || node.isNull() || depth > 5) {
            return null;
        }
        if (node.isTextual()) {
            return fromText(node.asText(), source);
        }
        if (node.isArray()) {
            DimensionResult result = null;
            int count = 0;
            for (JsonNode child : node) {
                result = max(result, fromJsonNode(child, source, depth + 1));
                count++;
                if (count >= 80) {
                    break;
                }
            }
            return result;
        }
        if (node.isObject()) {
            DimensionResult result = null;
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            int count = 0;
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (isDimensionKey(key)) {
                    result = max(result, fromText(key + ":" + value.asText(""), source));
                }
                result = max(result, fromJsonNode(value, source, depth + 1));
                count++;
                if (count >= 120) {
                    break;
                }
            }
            return result;
        }
        return null;
    }

    private static DimensionResult fromDeepText(JsonNode node, String fieldName, String source) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                if (fieldName.equals(entry.getKey())) {
                    DimensionResult result = fromText(entry.getValue().asText(""), source);
                    if (result != null) {
                        return result;
                    }
                }
                DimensionResult nested = fromDeepText(entry.getValue(), fieldName, source);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                DimensionResult nested = fromDeepText(child, fieldName, source);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static BigDecimal deepFindCm(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                if (fieldName.equals(entry.getKey())) {
                    BigDecimal value = decimalNode(entry.getValue());
                    if (value != null) {
                        return value;
                    }
                }
                BigDecimal nested = deepFindCm(entry.getValue(), fieldName);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                BigDecimal nested = deepFindCm(child, fieldName);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static BigDecimal decimalNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        Matcher matcher = UNIT_DIMENSION_PATTERN.matcher(node.asText(""));
        if (matcher.find()) {
            return toCm(matcher.group(1), matcher.group(2));
        }
        try {
            return new BigDecimal(node.asText("").replaceAll("[^0-9.\\-]", ""));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isDimensionKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("dimension")
                || lower.contains("diameter")
                || lower.contains("length")
                || lower.contains("width")
                || lower.contains("height")
                || key.contains("尺寸")
                || key.contains("直径")
                || key.contains("长度")
                || key.contains("宽度")
                || key.contains("高度");
    }

    private static boolean looksDimensionRelated(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("cm")
                || lower.contains("mm")
                || lower.contains("厘米")
                || lower.contains("公分")
                || lower.contains("毫米")
                || lower.contains("尺寸")
                || lower.contains("直径")
                || lower.contains("长")
                || lower.contains("宽")
                || lower.contains("高");
    }

    private static boolean isRadiusKeyword(String keyword) {
        return StringUtils.hasText(keyword) && keyword.toLowerCase(Locale.ROOT).contains("半径");
    }

    private static BigDecimal toCm(String valueText, String unitText) {
        if (!StringUtils.hasText(valueText)) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(valueText);
            String unit = unitText == null ? "cm" : unitText.trim().toLowerCase(Locale.ROOT);
            if ("mm".equals(unit) || "毫米".equals(unit)) {
                value = value.divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP);
            } else if ("m".equals(unit) || "米".equals(unit)) {
                value = value.multiply(BigDecimal.valueOf(100));
            }
            return value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BigDecimal maxNumber(BigDecimal... values) {
        BigDecimal max = null;
        if (values == null) {
            return null;
        }
        for (BigDecimal value : values) {
            if (value == null) {
                continue;
            }
            max = max == null || value.compareTo(max) > 0 ? value : max;
        }
        return max;
    }

    private static DimensionResult build(BigDecimal value, String source, String evidence) {
        if (value == null) {
            return null;
        }
        if (!isCredible(value)) {
            return null;
        }
        return new DimensionResult(
                value.setScale(2, RoundingMode.HALF_UP),
                StringUtils.hasText(source) ? source : "AUTO",
                StringUtils.hasText(evidence) ? evidence.trim() : null
        );
    }

    private static DimensionResult credible(DimensionResult result) {
        if (result == null || !isCredible(result.maxDimensionCm())) {
            return null;
        }
        return result;
    }

    private static boolean isCredible(BigDecimal value) {
        return value != null
                && value.compareTo(BigDecimal.ZERO) > 0
                && value.compareTo(MAX_CREDIBLE_PHYSICAL_DIMENSION_CM) <= 0;
    }

    public record DimensionResult(BigDecimal maxDimensionCm, String source, String evidence) {
    }
}
