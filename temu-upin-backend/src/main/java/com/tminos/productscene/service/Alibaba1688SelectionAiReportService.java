package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.config.AIAlibaba1688SelectionReportConfig;
import com.tminos.productscene.entity.Alibaba1688DetailRecord;
import com.tminos.productscene.entity.Alibaba1688SelectionPool;
import com.tminos.productscene.entity.Alibaba1688SelectionPoolSku;
import com.tminos.productscene.util.DimensionExtractor;
import com.tminos.productscene.util.TextAiUrlHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class Alibaba1688SelectionAiReportService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern START_BATCH_QTY_PATTERN = Pattern.compile("(\\d+)\\s*(?:件|个|份|套|箱|包|瓶|支|片|双|张|卷|袋|盒|只|台|本|斤|公斤|千克|卡|筒|桶|把|条|对|小盒|PCS|pcs)?\\s*起(?:批|定)");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final BigDecimal MAX_SUITABLE_DIMENSION_CM = new BigDecimal("10");
    private static final BigDecimal MAX_CREDIBLE_PHYSICAL_DIMENSION_CM = new BigDecimal("1000");

    private final AIAlibaba1688SelectionReportConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final TextAiChannelResolver textAiChannelResolver;

    public Alibaba1688SelectionAiReportService(
            AIAlibaba1688SelectionReportConfig config,
            ObjectMapper objectMapper,
            @Qualifier("alibaba1688SelectionAiRestTemplate") RestTemplate restTemplate,
            TextAiChannelResolver textAiChannelResolver
    ) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.textAiChannelResolver = textAiChannelResolver;
    }

    public AnalysisResult analyze(
            Alibaba1688SelectionPool pool,
            Alibaba1688DetailRecord detailRecord,
            List<Alibaba1688SelectionPoolSku> skus
    ) {
        if (config.getEnabled() == null || !config.getEnabled()) {
            throw new IllegalStateException("1688 选品 AI 报告已禁用");
        }
        TextAiChannelResolver.ResolvedChannel aiChannel = resolveAiChannel();
        if (!StringUtils.hasText(aiChannel.getApiKey())) {
            throw new IllegalStateException("缺少 1688 选品 AI 的 API Key");
        }
        if (pool == null) {
            throw new IllegalArgumentException("选品池记录不能为空");
        }

        try {
            String prompt = buildPrompt(pool, detailRecord, skus);

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("model", StringUtils.hasText(aiChannel.getModel()) ? aiChannel.getModel() : defaultModel());
            req.put("messages", List.of(
                    Map.of("role", "system", "content", systemInstruction()),
                    Map.of("role", "user", "content", prompt)
            ));
            req.put("max_tokens", config.getMaxTokens() == null ? 3200 : Math.max(1200, config.getMaxTokens()));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setBearerAuth(aiChannel.getApiKey().trim());

            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(req, headers);
            String url = completionsUrl(aiChannel.getBaseUrl());
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, httpEntity, String.class);
            if (resp.getStatusCode() != HttpStatus.OK || !StringUtils.hasText(resp.getBody())) {
                throw new IllegalStateException("AI 请求失败: " + resp.getStatusCode());
            }

            String assistantContent = extractAssistantContent(resp.getBody());
            if (!StringUtils.hasText(assistantContent)) {
                throw new IllegalStateException("AI 未返回有效内容");
            }

            AnalysisResult result = parseAnalysis(stripJsonFence(assistantContent));
            result.setModelName(StringUtils.hasText(aiChannel.getModel()) ? aiChannel.getModel() : defaultModel());
            applyFallbacks(result, pool, detailRecord, skus);
            return result;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("生成 1688 AI 选品报告失败: " + ex.getMessage(), ex);
        }
    }

    public AnalysisResult parseStoredReportJson(String reportJson) {
        if (!StringUtils.hasText(reportJson)) {
            return null;
        }
        try {
            AnalysisResult result = parseAnalysis(stripJsonFence(reportJson));
            applyFallbacks(result, null, null, List.of());
            return result;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String systemInstruction() {
        return "你是 1688 到 TEMU 选品分析专家。"
                + "业务场景是从中国自发货到美国，优先通货、轻小件、非液体、非带电、非易碎、非美国知名品牌侵权风险商品。"
                + "你必须保守评估：如果液体/电池/易碎/侵权证据不足但存在明显疑点，请不要乐观放行。"
                + "你必须从不规则 MOQ/销量文案中提炼最可信的数值。"
                + "只返回合法 JSON，不要返回 markdown，不要返回 JSON 以外的任何解释。";
    }

    private String buildPrompt(
            Alibaba1688SelectionPool pool,
            Alibaba1688DetailRecord detailRecord,
            List<Alibaba1688SelectionPoolSku> skus
    ) throws Exception {
        Map<String, Object> context = buildContext(pool, detailRecord, skus);
        return "请基于以下 1688 商品数据做 TEMU 中国自发货选品分析。\n"
                + "\n"
                + "重点判断规则：\n"
                + "1. 是否适合做 TEMU 从中国自发货模式。\n"
                + "2. 是否属于通货，是否轻小件。\n"
                + "3. 是否包含液体、是否带电、是否易碎、是否存在美国知名品牌侵权风险。\n"
                + "4. 从不规则文案中提取最可信的 MOQ 和销量数值。\n"
                + "4.1 如果文案里明确出现“1份起批/2件起批/3个起批”这类起批量，normalizedMoq 必须优先返回这个显式起批量，不要被结构化 moq 字段覆盖。\n"
                + "5. 最大重量 maxWeightG 优先使用明确的包装重量/净重/SKU重量；如果没有明确值，可以估算，但要在 evidence.weight 说明依据。\n"
                + "6. 最大尺寸 maxDimensionCm 必须从 SKU 文案、SKU 属性、商品属性、包装长宽高里提取。若有长/宽/高/直径/尺寸数据，取所有 SKU 和商品属性里的最大厘米值；只有一个维度也要返回。若最大尺寸超过 10cm，即使重量很轻，也不能判为 SUITABLE，应判为 UNSUITABLE，并说明包装重量可能不可信或不适合小件自发货。\n"
                + "\n"
                + "decision 枚举只能是：SUITABLE、REVIEW、UNSUITABLE。\n"
                + "保守原则：\n"
                + "- 若无法确认是否液体/带电/易碎/侵权，但有明显疑点，请返回 true 或把 decision 至少设为 REVIEW。\n"
                + "- 如果明确不适合 TEMU 中国自发货，请给 UNSUITABLE。\n"
                + "\n"
                + "返回 JSON schema：\n"
                + "{\n"
                + "  \"score\": 0-100,\n"
                + "  \"decision\": \"SUITABLE|REVIEW|UNSUITABLE\",\n"
                + "  \"summary\": \"一句话总结\",\n"
                + "  \"containsLiquid\": true,\n"
                + "  \"containsBattery\": false,\n"
                + "  \"isFragile\": false,\n"
                + "  \"hasPotentialBrandInfringement\": false,\n"
                + "  \"maxWeightG\": 320,\n"
                + "  \"maxDimensionCm\": 12.5,\n"
                + "  \"dimensionSource\": \"SKU文案/商品属性/包装长宽高\",\n"
                + "  \"startBatchQty\": 1,\n"
                + "  \"normalizedMoq\": 1,\n"
                + "  \"normalizedSalesVolume\": 2800,\n"
                + "  \"normalizedSalesText\": \"已售2800+份\",\n"
                + "  \"analysisReport\": \"多行中文报告\",\n"
                + "  \"sellingPoints\": [\"卖点1\", \"卖点2\"],\n"
                + "  \"riskPoints\": [\"风险1\", \"风险2\"],\n"
                + "  \"recommendations\": [\"建议1\", \"建议2\"],\n"
                + "  \"evidence\": {\n"
                + "    \"liquid\": \"证据\",\n"
                + "    \"battery\": \"证据\",\n"
                + "    \"fragile\": \"证据\",\n"
                + "    \"brand\": \"证据\",\n"
                + "    \"weight\": \"证据\",\n"
                + "    \"dimension\": \"证据\",\n"
                + "    \"moq\": \"证据\",\n"
                + "    \"sales\": \"证据\"\n"
                + "  }\n"
                + "}\n"
                + "\n"
                + "输入数据 JSON：\n"
                + objectMapper.writeValueAsString(context);
    }

    private Map<String, Object> buildContext(
            Alibaba1688SelectionPool pool,
            Alibaba1688DetailRecord detailRecord,
            List<Alibaba1688SelectionPoolSku> skus
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("businessContext", "TEMU 中国自发货，美国市场，优先通货、轻小件、非液体、非带电、非易碎、非侵权。");

        Map<String, Object> product = new LinkedHashMap<>();
        product.put("offerId", trimToNull(pool.getOfferId()));
        product.put("title", trimToNull(pool.getProductTitleSnapshot()));
        product.put("companyName", trimToNull(pool.getCompanyNameSnapshot()));
        product.put("category", trimToNull(pool.getCategorySnapshot()));
        product.put("shippingLocation", trimToNull(pool.getShippingLocationSnapshot()));
        product.put("publishedAt1688", pool.getPublishedAt1688() == null ? null : pool.getPublishedAt1688().format(DATE_TIME_FORMATTER));
        product.put("baseFreight", pool.getBaseFreightSnapshot());
        product.put("currentMoqSnapshot", pool.getMoqSnapshot());
        product.put("startBatchQtySnapshot", pool.getStartBatchQtySnapshot());
        product.put("currentMoqText", trimToNull(pool.getMoqTextSnapshot()));
        product.put("currentSalesSnapshot", trimToNull(pool.getMonthlySalesSnapshot()));
        product.put("serviceScore", pool.getServiceScoreSnapshot());
        product.put("repeatCustomerRate", pool.getRepeatCustomerRateSnapshot());
        product.put("onTimeDeliveryRate", pool.getOnTimeDeliveryRateSnapshot());
        product.put("shopPositiveRate", pool.getShopPositiveRateSnapshot());
        product.put("powerSeller", pool.getPowerSellerSnapshot());
        product.put("settledYearsText", trimToNull(pool.getSettledYearsTextSnapshot()));
        product.put("mainBusiness", trimToNull(pool.getMainBusinessSnapshot()));
        product.put("assistantExtra", reduceStructuredPayload(pool.getAssistantExtraJson(), 20, 3));
        context.put("product", product);

        context.put("skuSummary", buildSkuSummary(skus));

        if (detailRecord != null) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("detailRecordId", detailRecord.getId());
            detail.put("detailUrl", trimToNull(detailRecord.getDetailUrl()));
            detail.put("canonicalUrl", trimToNull(detailRecord.getCanonicalUrl()));
            detail.put("productName", trimToNull(detailRecord.getProductName()));
            detail.put("companyName", trimToNull(detailRecord.getCompanyName()));
            detail.put("parsedJson", reduceStructuredPayload(detailRecord.getParsedJson(), 40, 4));
            detail.put("extractedJson", reduceStructuredPayload(detailRecord.getExtractedJson(), 35, 4));
            context.put("detailRecord", detail);
        }
        return context;
    }

    private List<Map<String, Object>> buildSkuSummary(List<Alibaba1688SelectionPoolSku> skus) {
        if (skus == null || skus.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Alibaba1688SelectionPoolSku sku : skus) {
            if (sku == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sourceSkuId", trimToNull(sku.getSourceSkuId()));
            row.put("specText", trimToNull(sku.getSkuSpecText()));
            row.put("price", sku.getSkuPriceSnapshot());
            row.put("pageStock", sku.getPageStockSnapshot());
            row.put("weightG", sku.getWeightValue());
            row.put("weightSource", trimToNull(sku.getWeightSource()));
            row.put("maxDimensionCm", sku.getDimensionValue());
            row.put("dimensionSource", trimToNull(sku.getDimensionSource()));
            row.put("dimensionEvidence", trimToNull(sku.getDimensionEvidence()));
            row.put("isPrimarySku", sku.getIsPrimarySku());
            result.add(row);
            if (result.size() >= 40) {
                break;
            }
        }
        return result;
    }

    private Object reduceStructuredPayload(String json, int maxItems, int maxDepth) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            return compactValue(parsed, maxItems, maxDepth);
        } catch (Exception ignored) {
            return normalizeString(json, 1600);
        }
    }

    private Object compactValue(Object value, int maxItems, int depth) {
        if (value == null) {
            return null;
        }
        if (depth <= 0) {
            return compactScalar(value);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = normalizeString(String.valueOf(entry.getKey()), 80);
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                out.put(key, compactValue(entry.getValue(), maxItems, depth - 1));
                count++;
                if (count >= maxItems) {
                    break;
                }
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            int count = 0;
            for (Object item : list) {
                out.add(compactValue(item, maxItems, depth - 1));
                count++;
                if (count >= maxItems) {
                    break;
                }
            }
            return out;
        }
        return compactScalar(value);
    }

    private Object compactScalar(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return normalizeString(String.valueOf(value), 260);
    }

    private AnalysisResult parseAnalysis(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        AnalysisResult result = new AnalysisResult();
        result.setScore(integerValue(root, "score"));
        result.setDecision(normalizeDecision(text(root, "decision")));
        result.setSummary(firstText(text(root, "summary"), text(root, "conclusion")));
        result.setContainsLiquid(booleanValue(root, "containsLiquid"));
        result.setContainsBattery(booleanValue(root, "containsBattery"));
        result.setFragile(booleanValue(root, "isFragile"));
        result.setPotentialBrandInfringement(booleanValue(root, "hasPotentialBrandInfringement"));
        result.setMaxWeightG(decimalValue(root, "maxWeightG"));
        result.setMaxDimensionCm(firstNonNull(decimalValue(root, "maxDimensionCm"), decimalValue(root, "maxSizeCm")));
        result.setDimensionSource(firstText(text(root, "dimensionSource"), text(root, "maxDimensionSource")));
        result.setStartBatchQty(integerValue(root, "startBatchQty"));
        result.setNormalizedMoq(integerValue(root, "normalizedMoq"));
        result.setNormalizedSalesVolume(integerValue(root, "normalizedSalesVolume"));
        result.setNormalizedSalesText(firstText(text(root, "normalizedSalesText"), text(root, "salesText")));
        result.setAnalysisReport(firstText(text(root, "analysisReport"), text(root, "report")));
        result.setSellingPoints(stringList(root.path("sellingPoints"), 8));
        result.setRiskPoints(stringList(root.path("riskPoints"), 8));
        result.setRecommendations(stringList(root.path("recommendations"), 8));
        result.setEvidence(stringMap(root.path("evidence"), 12));
        return result;
    }

    private void applyFallbacks(
            AnalysisResult result,
            Alibaba1688SelectionPool pool,
            Alibaba1688DetailRecord detailRecord,
            List<Alibaba1688SelectionPoolSku> skus
    ) {
        if (result == null) {
            return;
        }

        BigDecimal fallbackWeight = computeFallbackMaxWeightG(detailRecord, skus);
        if (result.getMaxWeightG() == null && fallbackWeight != null) {
            result.setMaxWeightG(fallbackWeight);
            result.getEvidence().putIfAbsent("weight", "后端根据包装重量/净重/SKU重量做了保底推断");
        }

        DimensionExtractor.DimensionResult fallbackDimension = computeFallbackMaxDimension(detailRecord, skus);
        if (fallbackDimension != null && fallbackDimension.maxDimensionCm() != null) {
            if (result.getMaxDimensionCm() == null || fallbackDimension.maxDimensionCm().compareTo(result.getMaxDimensionCm()) > 0) {
                result.setMaxDimensionCm(fallbackDimension.maxDimensionCm());
                result.setDimensionSource(fallbackDimension.source());
                result.getEvidence().put("dimension", "后端从" + fallbackDimension.source()
                        + "识别最大尺寸≈" + formatDimension(fallbackDimension.maxDimensionCm()) + "cm"
                        + (StringUtils.hasText(fallbackDimension.evidence()) ? "（" + fallbackDimension.evidence() + "）" : ""));
            }
        }

        Integer explicitStartBatchQty = resolveExplicitStartBatchQty(pool, detailRecord);
        if (result.getStartBatchQty() == null) {
            result.setStartBatchQty(explicitStartBatchQty);
        }
        if (explicitStartBatchQty != null) {
            if (!Objects.equals(result.getNormalizedMoq(), explicitStartBatchQty)) {
                result.setNormalizedMoq(explicitStartBatchQty);
            }
            result.getEvidence().putIfAbsent("moq", "页面文案中明确出现“" + explicitStartBatchQty + "起批”，后端按显式起批量优先修正 MOQ");
        }

        if (result.getNormalizedMoq() == null && pool != null) {
            result.setNormalizedMoq(pool.getMoqSnapshot());
            if (pool.getMoqSnapshot() != null) {
                result.getEvidence().putIfAbsent("moq", "后端回退到当前快照 MOQ");
            }
        }

        if (result.getNormalizedSalesVolume() == null && pool != null) {
            Integer fallbackSales = parseSalesNumber(pool.getMonthlySalesSnapshot());
            result.setNormalizedSalesVolume(fallbackSales);
            if (fallbackSales != null) {
                result.getEvidence().putIfAbsent("sales", "后端回退到当前销量快照");
            }
        }

        if (!StringUtils.hasText(result.getNormalizedSalesText()) && pool != null) {
            result.setNormalizedSalesText(firstText(pool.getMonthlySalesSnapshot(), formatSalesText(result.getNormalizedSalesVolume())));
        }

        if (result.getContainsLiquid() == null) {
            result.setContainsLiquid(Boolean.FALSE);
        }
        if (result.getContainsBattery() == null) {
            result.setContainsBattery(Boolean.FALSE);
        }
        if (result.getFragile() == null) {
            result.setFragile(Boolean.FALSE);
        }
        if (result.getPotentialBrandInfringement() == null) {
            result.setPotentialBrandInfringement(Boolean.FALSE);
        }

        if (!StringUtils.hasText(result.getDecision())) {
            result.setDecision(deriveDecision(result));
        }
        if (Boolean.TRUE.equals(result.getContainsLiquid())
                || Boolean.TRUE.equals(result.getContainsBattery())
                || Boolean.TRUE.equals(result.getFragile())
                || Boolean.TRUE.equals(result.getPotentialBrandInfringement())) {
            result.setDecision("UNSUITABLE");
        }
        if (result.getMaxDimensionCm() != null
                && result.getMaxDimensionCm().compareTo(MAX_CREDIBLE_PHYSICAL_DIMENSION_CM) > 0) {
            result.getEvidence().put("dimension",
                    "AI/页面返回的最大尺寸超过可信物理尺寸上限，已忽略该异常值：" + formatDimension(result.getMaxDimensionCm()) + "cm");
            result.setMaxDimensionCm(null);
            result.setDimensionSource(null);
        }
        if (result.getMaxDimensionCm() != null && result.getMaxDimensionCm().compareTo(MAX_SUITABLE_DIMENSION_CM) > 0) {
            result.setDecision("UNSUITABLE");
            if (result.getScore() == null || result.getScore() > 45) {
                result.setScore(45);
            }
            addUnique(result.getRiskPoints(), "最大尺寸超过 " + formatDimension(MAX_SUITABLE_DIMENSION_CM)
                    + "cm，重量信息可能偏低或不适合小件自发货");
            result.getEvidence().putIfAbsent("dimension", "最大尺寸超过 " + formatDimension(MAX_SUITABLE_DIMENSION_CM) + "cm，后端按尺寸风险修正为不适合");
        }

        if (result.getScore() == null) {
            result.setScore(defaultScore(result.getDecision()));
        }
        result.setScore(Math.max(0, Math.min(100, result.getScore())));

        if (!StringUtils.hasText(result.getSummary())) {
            result.setSummary(buildSummary(result));
        }
        if (!StringUtils.hasText(result.getAnalysisReport())) {
            result.setAnalysisReport(buildAnalysisReport(result));
        }
    }

    private BigDecimal computeFallbackMaxWeightG(Alibaba1688DetailRecord detailRecord, List<Alibaba1688SelectionPoolSku> skus) {
        BigDecimal max = null;
        if (skus != null) {
            for (Alibaba1688SelectionPoolSku sku : skus) {
                if (sku == null || sku.getWeightValue() == null) {
                    continue;
                }
                max = max == null || sku.getWeightValue().compareTo(max) > 0 ? sku.getWeightValue() : max;
            }
        }
        BigDecimal parsedWeight = extractWeightFromDetail(detailRecord);
        if (parsedWeight != null) {
            max = max == null || parsedWeight.compareTo(max) > 0 ? parsedWeight : max;
        }
        return max == null ? null : max.setScale(2, RoundingMode.HALF_UP);
    }

    private DimensionExtractor.DimensionResult computeFallbackMaxDimension(Alibaba1688DetailRecord detailRecord, List<Alibaba1688SelectionPoolSku> skus) {
        DimensionExtractor.DimensionResult result = null;
        if (skus != null) {
            for (Alibaba1688SelectionPoolSku sku : skus) {
                if (sku == null) {
                    continue;
                }
                DimensionExtractor.DimensionResult direct = sku.getDimensionValue() == null
                        ? null
                        : new DimensionExtractor.DimensionResult(sku.getDimensionValue(), firstText(sku.getDimensionSource(), "SKU"), sku.getDimensionEvidence());
                result = DimensionExtractor.max(result, direct);
                result = DimensionExtractor.max(result, DimensionExtractor.fromSku(sku.getSkuSpecText(), sku.getSkuSpecJson(), objectMapper));
            }
        }
        if (detailRecord != null) {
            result = DimensionExtractor.max(result, DimensionExtractor.fromDetailJson(detailRecord.getParsedJson(), detailRecord.getExtractedJson(), objectMapper));
        }
        return result;
    }

    private Integer resolveExplicitStartBatchQty(Alibaba1688SelectionPool pool, Alibaba1688DetailRecord detailRecord) {
        Integer poolValue = pool == null ? null : pool.getStartBatchQtySnapshot();
        if (poolValue != null) {
            return poolValue;
        }
        String moqText = pool == null ? null : pool.getMoqTextSnapshot();
        Integer textValue = extractStartBatchQty(moqText);
        if (textValue != null) {
            return textValue;
        }
        return detailRecord == null ? null : extractStartBatchQty(detailRecord.getRawHtml());
    }

    private BigDecimal extractWeightFromDetail(Alibaba1688DetailRecord detailRecord) {
        if (detailRecord == null) {
            return null;
        }
        BigDecimal weight = firstDecimalFromJson(detailRecord.getParsedJson(), "packagingWeight", "netWeight");
        if (weight != null) {
            return weight;
        }
        return firstDecimalFromJson(detailRecord.getExtractedJson(), "packagingWeight", "netWeight", "weightG");
    }

    private BigDecimal firstDecimalFromJson(String json, String... fieldNames) {
        if (!StringUtils.hasText(json) || fieldNames == null || fieldNames.length == 0) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            for (String fieldName : fieldNames) {
                BigDecimal direct = decimalValue(root, fieldName);
                if (direct != null) {
                    return direct;
                }
                BigDecimal deep = deepFindDecimal(root, fieldName);
                if (deep != null) {
                    return deep;
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal deepFindDecimal(JsonNode node, String fieldName) {
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
                BigDecimal nested = deepFindDecimal(entry.getValue(), fieldName);
                if (nested != null) {
                    return nested;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                BigDecimal nested = deepFindDecimal(child, fieldName);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private Integer parseSalesNumber(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim().replace(",", "");
        Matcher matcher = NUMBER_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        BigDecimal base = new BigDecimal(matcher.group(1));
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("万") || lower.contains("w")) {
            base = base.multiply(BigDecimal.valueOf(10_000L));
        } else if (lower.contains("千") || lower.contains("k")) {
            base = base.multiply(BigDecimal.valueOf(1_000L));
        }
        return base.setScale(0, RoundingMode.DOWN).intValue();
    }

    private Integer extractStartBatchQty(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = START_BATCH_QTY_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String deriveDecision(AnalysisResult result) {
        if (Boolean.TRUE.equals(result.getContainsLiquid())
                || Boolean.TRUE.equals(result.getContainsBattery())
                || Boolean.TRUE.equals(result.getFragile())
                || Boolean.TRUE.equals(result.getPotentialBrandInfringement())) {
            return "UNSUITABLE";
        }
        if (result.getMaxWeightG() == null) {
            return "REVIEW";
        }
        if (result.getMaxWeightG().compareTo(new BigDecimal("1200")) > 0) {
            return "REVIEW";
        }
        return "SUITABLE";
    }

    private int defaultScore(String decision) {
        return switch (normalizeDecision(decision)) {
            case "SUITABLE" -> 88;
            case "UNSUITABLE" -> 28;
            default -> 58;
        };
    }

    private String buildSummary(AnalysisResult result) {
        List<String> parts = new ArrayList<>();
        parts.add("结论：" + humanDecision(result.getDecision()));
        parts.add("液体：" + yesNo(result.getContainsLiquid()));
        parts.add("带电：" + yesNo(result.getContainsBattery()));
        parts.add("易碎：" + yesNo(result.getFragile()));
        parts.add("侵权风险：" + yesNo(result.getPotentialBrandInfringement()));
        if (result.getMaxWeightG() != null) {
            parts.add("最大重量≈" + formatWeight(result.getMaxWeightG()) + "g");
        }
        if (result.getMaxDimensionCm() != null) {
            parts.add("最大尺寸≈" + formatDimension(result.getMaxDimensionCm()) + "cm");
        }
        if (result.getStartBatchQty() != null) {
            parts.add("起批量=" + result.getStartBatchQty());
        }
        if (result.getNormalizedMoq() != null) {
            parts.add("AI MOQ=" + result.getNormalizedMoq());
        }
        if (result.getNormalizedSalesVolume() != null) {
            parts.add("AI 销量≈" + result.getNormalizedSalesVolume());
        }
        return String.join("；", parts);
    }

    private String buildAnalysisReport(AnalysisResult result) {
        List<String> lines = new ArrayList<>();
        lines.add("AI 选品结论：" + humanDecision(result.getDecision()) + "（" + result.getScore() + " 分）");
        lines.add("液体：" + yesNo(result.getContainsLiquid()) + "；带电：" + yesNo(result.getContainsBattery())
                + "；易碎：" + yesNo(result.getFragile()) + "；侵权风险：" + yesNo(result.getPotentialBrandInfringement()));
        lines.add("最大重量：" + (result.getMaxWeightG() == null ? "未识别" : formatWeight(result.getMaxWeightG()) + " g"));
        lines.add("最大尺寸：" + (result.getMaxDimensionCm() == null ? "未识别" : formatDimension(result.getMaxDimensionCm()) + " cm"
                + (StringUtils.hasText(result.getDimensionSource()) ? "（" + result.getDimensionSource() + "）" : "")));
        lines.add("起批量：" + (result.getStartBatchQty() == null ? "未识别" : result.getStartBatchQty()));
        lines.add("AI 识别 MOQ：" + (result.getNormalizedMoq() == null ? "未识别" : result.getNormalizedMoq()));
        lines.add("AI 识别销量：" + firstText(result.getNormalizedSalesText(), result.getNormalizedSalesVolume() == null ? null : String.valueOf(result.getNormalizedSalesVolume()), "未识别"));

        if (!result.getSellingPoints().isEmpty()) {
            lines.add("");
            lines.add("卖点：");
            for (String item : result.getSellingPoints()) {
                lines.add("- " + item);
            }
        }
        if (!result.getRiskPoints().isEmpty()) {
            lines.add("");
            lines.add("风险点：");
            for (String item : result.getRiskPoints()) {
                lines.add("- " + item);
            }
        }
        if (!result.getRecommendations().isEmpty()) {
            lines.add("");
            lines.add("建议：");
            for (String item : result.getRecommendations()) {
                lines.add("- " + item);
            }
        }
        if (!result.getEvidence().isEmpty()) {
            lines.add("");
            lines.add("关键信号：");
            for (Map.Entry<String, String> entry : result.getEvidence().entrySet()) {
                if (!StringUtils.hasText(entry.getValue())) {
                    continue;
                }
                lines.add("- " + entry.getKey() + "： " + entry.getValue());
            }
        }
        return String.join("\n", lines);
    }

    private String normalizeDecision(String decision) {
        String normalized = trimToNull(decision);
        if (!StringUtils.hasText(normalized)) {
            return "REVIEW";
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("SUITABLE".equals(upper) || "REVIEW".equals(upper) || "UNSUITABLE".equals(upper)) {
            return upper;
        }
        if (upper.contains("PASS") || upper.contains("GOOD") || upper.contains("适合")) {
            return "SUITABLE";
        }
        if (upper.contains("UNSUIT") || upper.contains("REJECT") || upper.contains("不适合")) {
            return "UNSUITABLE";
        }
        return "REVIEW";
    }

    private String humanDecision(String decision) {
        return switch (normalizeDecision(decision)) {
            case "SUITABLE" -> "适合";
            case "UNSUITABLE" -> "不适合";
            default -> "需人工复核";
        };
    }

    private String yesNo(Boolean value) {
        return Boolean.TRUE.equals(value) ? "是" : "否";
    }

    private String formatWeight(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String formatDimension(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private void addUnique(List<String> values, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (values.stream().noneMatch(item -> value.equals(item))) {
            values.add(value);
        }
    }

    private String formatSalesText(Integer value) {
        return value == null ? null : String.valueOf(value);
    }

    private String completionsUrl(String baseUrl) {
        return TextAiUrlHelper.chatCompletionsUrl(baseUrl, config.getBaseUrl());
    }

    private String defaultModel() {
        return StringUtils.hasText(config.getModel()) ? config.getModel().trim() : "gpt-5.5";
    }

    private TextAiChannelResolver.ResolvedChannel resolveAiChannel() {
        return textAiChannelResolver.resolve(
                TextAiBusinessCodes.ALIBABA_1688_SELECTION_REPORT,
                config.getBaseUrl(),
                config.getApiKey(),
                defaultModel()
        );
    }

    private String extractAssistantContent(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode message = choices.get(0).path("message");
        JsonNode contentNode = message.path("content");
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : contentNode) {
                if (item == null || item.isNull()) {
                    continue;
                }
                if (item.isTextual()) {
                    builder.append(item.asText());
                    continue;
                }
                String type = text(item, "type");
                if ("text".equalsIgnoreCase(type)) {
                    String text = item.path("text").isTextual() ? item.path("text").asText() : null;
                    if (StringUtils.hasText(text)) {
                        builder.append(text);
                    }
                }
            }
            return builder.length() == 0 ? null : builder.toString();
        }
        return null;
    }

    private String stripJsonFence(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        String value = content.trim();
        if (value.startsWith("```") && value.endsWith("```")) {
            value = value.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        return value.trim();
    }

    private String normalizeString(String value, int maxLength) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private List<String> stringList(JsonNode node, int limit) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            String text = trimToNull(item.asText());
            if (text == null) {
                continue;
            }
            result.add(text);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private Map<String, String> stringMap(JsonNode node, int limit) {
        if (node == null || !node.isObject()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            String key = trimToNull(entry.getKey());
            String value = entry.getValue() == null || entry.getValue().isNull() ? null : trimToNull(entry.getValue().asText());
            if (key != null && value != null) {
                result.put(key, value);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private Integer integerValue(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode child = node.path(fieldName);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        if (child.isInt() || child.isLong()) {
            return child.asInt();
        }
        String text = trimToNull(child.asText());
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(text.replace(",", ""));
        if (!matcher.find()) {
            return null;
        }
        BigDecimal value = new BigDecimal(matcher.group(1));
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("万") || lower.contains("w")) {
            value = value.multiply(BigDecimal.valueOf(10_000L));
        } else if (lower.contains("千") || lower.contains("k")) {
            value = value.multiply(BigDecimal.valueOf(1_000L));
        }
        return value.setScale(0, RoundingMode.DOWN).intValue();
    }

    private BigDecimal decimalValue(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        return decimalNode(node.path(fieldName));
    }

    private BigDecimal decimalNode(JsonNode child) {
        if (child == null || child.isMissingNode() || child.isNull()) {
            return null;
        }
        if (child.isNumber()) {
            return child.decimalValue();
        }
        String text = trimToNull(child.asText());
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(text.replace(",", ""));
        if (!matcher.find()) {
            return null;
        }
        return new BigDecimal(matcher.group(1));
    }

    private Boolean booleanValue(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode child = node.path(fieldName);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        if (child.isBoolean()) {
            return child.asBoolean();
        }
        String text = trimToNull(child.asText());
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if (Set.of("true", "yes", "y", "1", "是", "有").contains(normalized)) {
            return Boolean.TRUE;
        }
        if (Set.of("false", "no", "n", "0", "否", "无").contains(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode child = node.path(fieldName);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        return trimToNull(child.asText());
    }

    private String firstText(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public static class AnalysisResult {
        private Integer score;
        private String decision;
        private String summary;
        private Boolean containsLiquid;
        private Boolean containsBattery;
        private Boolean fragile;
        private Boolean potentialBrandInfringement;
        private BigDecimal maxWeightG;
        private BigDecimal maxDimensionCm;
        private String dimensionSource;
        private Integer startBatchQty;
        private Integer normalizedMoq;
        private Integer normalizedSalesVolume;
        private String normalizedSalesText;
        private String analysisReport;
        private List<String> sellingPoints = new ArrayList<>();
        private List<String> riskPoints = new ArrayList<>();
        private List<String> recommendations = new ArrayList<>();
        private Map<String, String> evidence = new LinkedHashMap<>();
        private String modelName;

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }

        public String getDecision() {
            return decision;
        }

        public void setDecision(String decision) {
            this.decision = decision;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public Boolean getContainsLiquid() {
            return containsLiquid;
        }

        public void setContainsLiquid(Boolean containsLiquid) {
            this.containsLiquid = containsLiquid;
        }

        public Boolean getContainsBattery() {
            return containsBattery;
        }

        public void setContainsBattery(Boolean containsBattery) {
            this.containsBattery = containsBattery;
        }

        public Boolean getFragile() {
            return fragile;
        }

        public void setFragile(Boolean fragile) {
            this.fragile = fragile;
        }

        public Boolean getPotentialBrandInfringement() {
            return potentialBrandInfringement;
        }

        public void setPotentialBrandInfringement(Boolean potentialBrandInfringement) {
            this.potentialBrandInfringement = potentialBrandInfringement;
        }

        public BigDecimal getMaxWeightG() {
            return maxWeightG;
        }

        public void setMaxWeightG(BigDecimal maxWeightG) {
            this.maxWeightG = maxWeightG;
        }

        public BigDecimal getMaxDimensionCm() {
            return maxDimensionCm;
        }

        public void setMaxDimensionCm(BigDecimal maxDimensionCm) {
            this.maxDimensionCm = maxDimensionCm;
        }

        public String getDimensionSource() {
            return dimensionSource;
        }

        public void setDimensionSource(String dimensionSource) {
            this.dimensionSource = dimensionSource;
        }

        public Integer getNormalizedMoq() {
            return normalizedMoq;
        }

        public void setNormalizedMoq(Integer normalizedMoq) {
            this.normalizedMoq = normalizedMoq;
        }

        public Integer getStartBatchQty() {
            return startBatchQty;
        }

        public void setStartBatchQty(Integer startBatchQty) {
            this.startBatchQty = startBatchQty;
        }

        public Integer getNormalizedSalesVolume() {
            return normalizedSalesVolume;
        }

        public void setNormalizedSalesVolume(Integer normalizedSalesVolume) {
            this.normalizedSalesVolume = normalizedSalesVolume;
        }

        public String getNormalizedSalesText() {
            return normalizedSalesText;
        }

        public void setNormalizedSalesText(String normalizedSalesText) {
            this.normalizedSalesText = normalizedSalesText;
        }

        public String getAnalysisReport() {
            return analysisReport;
        }

        public void setAnalysisReport(String analysisReport) {
            this.analysisReport = analysisReport;
        }

        public List<String> getSellingPoints() {
            return sellingPoints;
        }

        public void setSellingPoints(List<String> sellingPoints) {
            this.sellingPoints = sellingPoints == null ? new ArrayList<>() : new ArrayList<>(sellingPoints);
        }

        public List<String> getRiskPoints() {
            return riskPoints;
        }

        public void setRiskPoints(List<String> riskPoints) {
            this.riskPoints = riskPoints == null ? new ArrayList<>() : new ArrayList<>(riskPoints);
        }

        public List<String> getRecommendations() {
            return recommendations;
        }

        public void setRecommendations(List<String> recommendations) {
            this.recommendations = recommendations == null ? new ArrayList<>() : new ArrayList<>(recommendations);
        }

        public Map<String, String> getEvidence() {
            return evidence;
        }

        public void setEvidence(Map<String, String> evidence) {
            this.evidence = evidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(evidence);
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("score", score);
            map.put("decision", decision);
            map.put("summary", summary);
            map.put("containsLiquid", containsLiquid);
            map.put("containsBattery", containsBattery);
            map.put("isFragile", fragile);
            map.put("hasPotentialBrandInfringement", potentialBrandInfringement);
            map.put("maxWeightG", maxWeightG);
            map.put("maxDimensionCm", maxDimensionCm);
            map.put("dimensionSource", dimensionSource);
            map.put("startBatchQty", startBatchQty);
            map.put("normalizedMoq", normalizedMoq);
            map.put("normalizedSalesVolume", normalizedSalesVolume);
            map.put("normalizedSalesText", normalizedSalesText);
            map.put("analysisReport", analysisReport);
            map.put("sellingPoints", sellingPoints);
            map.put("riskPoints", riskPoints);
            map.put("recommendations", recommendations);
            map.put("evidence", evidence);
            map.put("modelName", modelName);
            return map;
        }
    }
}
