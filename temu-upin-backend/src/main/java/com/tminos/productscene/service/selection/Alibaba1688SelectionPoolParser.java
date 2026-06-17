package com.tminos.productscene.service.selection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.Alibaba1688DetailRecord;
import com.tminos.productscene.service.pull.parser.Alibaba1688HtmlParser;
import com.tminos.productscene.util.DimensionExtractor;
import lombok.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class Alibaba1688SelectionPoolParser {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern ALIBABA_THUMB_SUFFIX_PATTERN = Pattern.compile("(?i)(\\.(?:jpg|jpeg|png|webp))_b\\.(?:jpg|jpeg|png|webp)$");
    private static final DateTimeFormatter[] SUPPORTED_DATE_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };
    private static final Pattern START_BATCH_QTY_PATTERN = Pattern.compile("(\\d+)\\s*(?:件|个|份|套|箱|包|瓶|支|片|双|张|卷|袋|盒|只|台|本|斤|公斤|千克|卡|筒|桶|把|条|对|小盒|PCS|pcs)?\\s*起(?:批|定)");

    private final ObjectMapper objectMapper;

    public Alibaba1688SelectionPoolParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedResult parse(Alibaba1688DetailRecord detailRecord) {
        if (detailRecord == null) {
            throw new IllegalArgumentException("1688 详情记录不能为空");
        }

        JsonNode parsedRoot = readJson(detailRecord.getParsedJson());
        JsonNode extractedRoot = readJson(detailRecord.getExtractedJson());
        JsonNode purchaseAssistantNode = firstNode(extractedRoot, "purchaseAssistant");
        RawHtmlMeta rawHtmlMeta = extractRawHtmlMeta(detailRecord.getRawHtml());
        RawHtmlSnapshot rawHtmlSnapshot = extractRawHtmlSnapshot(detailRecord.getRawHtml());
        List<String> carouselImageUrls = resolveCarouselImageUrls(
                readStringArrayFromNestedJson(parsedRoot, "carouselImagesJson"),
                readStringArray(extractedRoot, "carouselImages"),
                rawHtmlSnapshot.carouselImages(),
                rawHtmlMeta.carouselImages()
        );
        List<String> detailImageUrls = resolveDetailImageUrls(
                readStringArrayFromNestedJson(parsedRoot, "detailImagesJson"),
                readStringArray(extractedRoot, "detailImages"),
                rawHtmlSnapshot.detailImages()
        );

        BigDecimal inferredWeight = firstDecimal(parsedRoot, "netWeight", "packagingWeight");
        String weightSource = inferredWeight == null ? null : "AUTO";
        String moqTextSnapshot = text(parsedRoot, "moqText");
        Integer startBatchQtySnapshot = extractStartBatchQty(
                moqTextSnapshot,
                detailRecord.getRawHtml()
        );
        Integer resolvedMoqSnapshot = firstNonNull(startBatchQtySnapshot, firstInteger(parsedRoot, "moq"));

        ParsedMain main = ParsedMain.builder()
                .detailRecordId(detailRecord.getId())
                .offerId(firstText(detailRecord.getOfferId(), text(parsedRoot, "productId"), text(parsedRoot, "alibabaProductId")))
                .detailUrl(firstText(
                        detailRecord.getDetailUrl(),
                        detailRecord.getCanonicalUrl(),
                        text(parsedRoot, "productUrl"),
                        text(extractedRoot, "canonicalUrl"),
                        text(extractedRoot, "pageUrl")
                ))
                .sourcePlatform(firstText(detailRecord.getSourcePlatform(), "1688"))
                .productTitleSnapshot(firstText(
                        detailRecord.getProductName(),
                        text(parsedRoot, "productName"),
                        text(extractedRoot, "title"),
                        text(extractedRoot, "pageTitle"),
                        rawHtmlMeta.title()
                ))
                .mainImageSnapshot(firstText(
                        firstItem(carouselImageUrls),
                        upgradeAlibabaThumbUrl(normalizeImageUrl(detailRecord.getProductMainImage())),
                        upgradeAlibabaThumbUrl(normalizeImageUrl(text(parsedRoot, "productMainImage"))),
                        upgradeAlibabaThumbUrl(normalizeImageUrl(rawHtmlMeta.mainImageUrl()))
                ))
                .carouselImageUrls(carouselImageUrls)
                .detailImageUrls(detailImageUrls)
                .companyNameSnapshot(firstText(
                        detailRecord.getCompanyName(),
                        text(parsedRoot, "companyName"),
                        text(extractedRoot, "seller"),
                        rawHtmlMeta.companyName()
                ))
                .repeatCustomerRateSnapshot(firstNonNull(detailRecord.getRepeatCustomerRate(), firstDecimal(parsedRoot, "repeatCustomerRate")))
                .serviceScoreSnapshot(firstNonNull(
                        detailRecord.getServiceScore(),
                        firstDecimal(parsedRoot, "serviceScore"),
                        rawHtmlMeta.serviceScore()
                ))
                .onTimeDeliveryRateSnapshot(firstNonNull(detailRecord.getOnTimeDeliveryRate(), firstDecimal(parsedRoot, "onTimeDeliveryRate")))
                .shopPositiveRateSnapshot(firstNonNull(detailRecord.getShopPositiveRate(), firstDecimal(parsedRoot, "shopPositiveRate")))
                .powerSellerSnapshot(firstNonNull(detailRecord.getPowerSeller(), booleanValue(parsedRoot, "powerSeller")))
                .settledYearsTextSnapshot(firstText(detailRecord.getSettledYearsText(), text(parsedRoot, "settledYearsText")))
                .mainBusinessSnapshot(firstText(detailRecord.getMainBusiness(), text(parsedRoot, "mainBusiness")))
                .shippingLocationSnapshot(firstText(
                        text(parsedRoot, "shippingLocation"),
                        rawHtmlMeta.shippingLocation()
                ))
                .categorySnapshot(firstText(
                        text(parsedRoot, "productCategory"),
                        text(parsedRoot, "originalCategory"),
                        text(purchaseAssistantNode, "categoryText")
                ))
                .publishedAt1688(extractPublishedAt1688(detailRecord, parsedRoot, extractedRoot))
                .baseFreightSnapshot(firstDecimal(parsedRoot, "baseFreight"))
                .moqSnapshot(resolvedMoqSnapshot)
                .startBatchQtySnapshot(startBatchQtySnapshot)
                .moqTextSnapshot(moqTextSnapshot)
                .monthlySalesSnapshot(firstText(
                        text(parsedRoot, "monthlySales"),
                        text(parsedRoot, "annualSales"),
                        text(purchaseAssistantNode, "monthlySoldText"),
                        text(purchaseAssistantNode, "yearlySoldQuantityText")
                ))
                .salesTrendSnapshotJson(extractSalesTrendJson(parsedRoot, extractedRoot))
                .priceStepsSnapshotJson(text(parsedRoot, "priceStepsJson"))
                .assistantExtraJson(extractAssistantExtraJson(extractedRoot))
                .firstSeenAt(detailRecord.getCreatedAt())
                .detailLastCollectedAt(detailRecord.getLastCollectedAt())
                .suggestedScore(calculateScore(parsedRoot, resolvedMoqSnapshot))
                .scoreDetailJson(buildScoreDetailJson(parsedRoot, resolvedMoqSnapshot, startBatchQtySnapshot))
                .build();

        List<ParsedSku> skus = parseSkus(parsedRoot, main.getMainImageSnapshot(), inferredWeight, weightSource);
        return new ParsedResult(main, skus);
    }

    private LocalDateTime extractPublishedAt1688(Alibaba1688DetailRecord detailRecord, JsonNode parsedRoot, JsonNode extractedRoot) {
        LocalDateTime extractedDate = parseDateTimeText(firstText(
                text(extractedRoot, "listingDateText"),
                text(extractedRoot, "publishedAtText"),
                text(extractedRoot, "postDate"),
                text(firstNode(extractedRoot, "purchaseAssistant"), "listingDateText"),
                text(firstNode(extractedRoot, "purchaseAssistant"), "publishedAtText"),
                text(firstNode(extractedRoot, "purchaseAssistant"), "postDate")
        ));
        if (extractedDate != null) {
            return extractedDate;
        }

        LocalDateTime parsedDate = parseDateTimeText(firstText(
                text(parsedRoot, "publishedAt1688"),
                text(parsedRoot, "publishedAt"),
                text(parsedRoot, "postDate"),
                text(parsedRoot, "createDate")
        ));
        if (parsedDate != null) {
            return parsedDate;
        }

        Long htmlEpochMillis = extractEpochMillisFromRawHtml(detailRecord == null ? null : detailRecord.getRawHtml());
        if (htmlEpochMillis != null) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(htmlEpochMillis), CHINA_ZONE);
        }

        return null;
    }

    private List<ParsedSku> parseSkus(JsonNode parsedRoot, String fallbackImage, BigDecimal inferredWeight, String weightSource) {
        String skuDataJson = text(parsedRoot, "skuDataJson");
        if (!StringUtils.hasText(skuDataJson)) {
            return Collections.emptyList();
        }
        JsonNode skuData = readJson(skuDataJson);
        JsonNode items = skuData.path("skus");
        if (!items.isArray() || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<ParsedSku> result = new ArrayList<>();
        int totalItems = items.size();
        int itemIndex = 0;
        for (JsonNode item : items) {
            String specText = firstText(text(item, "name"), text(item, "spec"), text(item, "skuName"));
            String skuId = firstText(text(item, "skuId"), text(item, "id"));
            if (!hasAnySkuPayload(item, specText, skuId)) {
                continue;
            }
            if (!StringUtils.hasText(specText)) {
                specText = buildFallbackSkuSpecText(totalItems, itemIndex);
            }
            DimensionExtractor.DimensionResult dimension = DimensionExtractor.fromSku(specText, serialize(item), objectMapper);
            result.add(ParsedSku.builder()
                    .sourceSkuId(skuId)
                    .skuSpecText(specText)
                    .skuSpecJson(serialize(item))
                    .skuImage(firstText(text(item, "image"), fallbackImage))
                    .skuMainImage(firstText(text(item, "image"), fallbackImage))
                    .skuPriceSnapshot(decimal(item, "price"))
                    .pageStockSnapshot(integer(item, "stock"))
                    .weightValue(inferredWeight)
                    .weightSource(weightSource)
                    .dimensionValue(dimension == null ? null : dimension.maxDimensionCm())
                    .dimensionSource(dimension == null ? null : dimension.source())
                    .dimensionEvidence(dimension == null ? null : dimension.evidence())
                    .build());
            itemIndex += 1;
        }
        return result;
    }

    private boolean hasAnySkuPayload(JsonNode item, String specText, String skuId) {
        return StringUtils.hasText(specText)
                || StringUtils.hasText(skuId)
                || decimal(item, "price") != null
                || decimal(item, "discountPrice") != null
                || integer(item, "stock") != null
                || integer(item, "canBookCount") != null
                || StringUtils.hasText(firstText(text(item, "image"), text(item, "skuImage"), text(item, "img")));
    }

    private String buildFallbackSkuSpecText(int totalItems, int itemIndex) {
        if (totalItems <= 1) {
            return "默认规格";
        }
        return "默认规格-" + (itemIndex + 1);
    }

    private Integer calculateScore(JsonNode parsedRoot, Integer resolvedMoqSnapshot) {
        int score = 45;
        Integer sales = parseFirstInt(text(parsedRoot, "monthlySales"));
        if (sales != null) {
            if (sales >= 5000) {
                score += 20;
            } else if (sales >= 1000) {
                score += 15;
            } else if (sales >= 300) {
                score += 10;
            } else if (sales >= 50) {
                score += 5;
            }
        }
        BigDecimal freight = firstDecimal(parsedRoot, "baseFreight");
        if (freight != null) {
            if (freight.compareTo(new BigDecimal("3")) <= 0) {
                score += 10;
            } else if (freight.compareTo(new BigDecimal("8")) <= 0) {
                score += 5;
            }
        }
        Integer moq = resolvedMoqSnapshot != null ? resolvedMoqSnapshot : firstInteger(parsedRoot, "moq");
        if (moq != null) {
            if (moq <= 2) {
                score += 10;
            } else if (moq <= 10) {
                score += 6;
            } else if (moq <= 30) {
                score += 2;
            } else {
                score -= 4;
            }
        }
        BigDecimal minPrice = firstDecimal(parsedRoot, "minPrice");
        if (minPrice != null) {
            if (minPrice.compareTo(new BigDecimal("10")) <= 0) {
                score += 10;
            } else if (minPrice.compareTo(new BigDecimal("30")) <= 0) {
                score += 5;
            }
        }
        return Math.max(0, Math.min(score, 100));
    }

    private String buildScoreDetailJson(JsonNode parsedRoot, Integer resolvedMoqSnapshot, Integer startBatchQtySnapshot) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("rule", "selection_pool_v1");
        detail.put("monthlySales", text(parsedRoot, "monthlySales"));
        detail.put("baseFreight", firstDecimal(parsedRoot, "baseFreight"));
        detail.put("moq", resolvedMoqSnapshot != null ? resolvedMoqSnapshot : firstInteger(parsedRoot, "moq"));
        detail.put("startBatchQty", startBatchQtySnapshot);
        detail.put("minPrice", firstDecimal(parsedRoot, "minPrice"));
        return serialize(detail);
    }

    private String extractSalesTrendJson(JsonNode parsedRoot, JsonNode extractedRoot) {
        String direct = firstText(
                text(parsedRoot, "salesTrendSnapshotJson"),
                text(parsedRoot, "salesTrendJson"),
                text(parsedRoot, "salesCurveJson"),
                text(parsedRoot, "salesCurve"),
                text(extractedRoot, "salesTrendSnapshotJson"),
                text(extractedRoot, "salesTrendJson"),
                text(extractedRoot, "salesCurveJson"),
                text(extractedRoot, "salesCurve")
        );
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        JsonNode candidate = firstNode(parsedRoot, "salesTrend", "salesCurve", "trend");
        if (candidate == null) {
            candidate = firstNode(extractedRoot, "salesTrend", "salesCurve", "trend");
        }
        return serialize(candidate);
    }

    private String extractAssistantExtraJson(JsonNode extractedRoot) {
        JsonNode node = firstNode(extractedRoot, "assistantExtra", "pluginExtra", "selectionPoolExtra");
        return serialize(node);
    }

    private RawHtmlMeta extractRawHtmlMeta(String rawHtml) {
        if (!StringUtils.hasText(rawHtml)) {
            return RawHtmlMeta.empty();
        }
        try {
            Document document = Jsoup.parse(rawHtml);
            String title = firstText(
                    metaContent(document, "meta[property=og:title]"),
                    metaContent(document, "meta[name=og:title]"),
                    safeText(document.title())
            );
            String mainImageUrl = firstText(
                    metaContent(document, "meta[property=og:image]"),
                    metaContent(document, "meta[name=og:image]")
            );
            List<String> carouselImages = new ArrayList<>();
            if (StringUtils.hasText(mainImageUrl)) {
                carouselImages.add(mainImageUrl.trim());
            }
            String companyName = firstText(
                    parseNickMetaCompanyName(metaContent(document, "meta[property=og:product:nick]")),
                    parseNickMetaCompanyName(metaContent(document, "meta[name=og:product:nick]"))
            );
            String shippingLocation = firstText(
                    parseLocationMeta(metaContent(document, "meta[name=location]")),
                    parseLocationMeta(metaContent(document, "meta[property=location]"))
            );
            BigDecimal serviceScore = firstNonNull(
                    parseDecimalText(metaContent(document, "meta[property=og:product:base_score]")),
                    parseDecimalText(metaContent(document, "meta[name=og:product:base_score]"))
            );
            return new RawHtmlMeta(title, mainImageUrl, companyName, shippingLocation, serviceScore, carouselImages);
        } catch (Exception ignored) {
            return RawHtmlMeta.empty();
        }
    }

    private String metaContent(Document document, String selector) {
        if (document == null || !StringUtils.hasText(selector)) {
            return null;
        }
        Element element = document.selectFirst(selector);
        if (element == null) {
            return null;
        }
        return safeText(element.attr("content"));
    }

    private String parseNickMetaCompanyName(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("name=([^;]+)").matcher(content);
        if (matcher.find()) {
            return safeText(matcher.group(1));
        }
        return safeText(content);
    }

    private String parseLocationMeta(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String province = null;
        String city = null;
        java.util.regex.Matcher provinceMatcher = java.util.regex.Pattern.compile("province=([^;]+)").matcher(content);
        if (provinceMatcher.find()) {
            province = safeText(provinceMatcher.group(1));
        }
        java.util.regex.Matcher cityMatcher = java.util.regex.Pattern.compile("city=([^;]+)").matcher(content);
        if (cityMatcher.find()) {
            city = safeText(cityMatcher.group(1));
        }
        return firstText(joinWithSpace(province, city), safeText(content));
    }

    private LocalDateTime parseDateTimeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        if (normalized.matches("^\\d{12,17}$")) {
            try {
                long epochMillis = Long.parseLong(normalized);
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), CHINA_ZONE);
            } catch (Exception ignored) {
                return null;
            }
        }

        for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATTERS) {
            try {
                if (normalized.length() <= 10) {
                    LocalDate date = LocalDate.parse(normalized, formatter);
                    return date.atStartOfDay();
                }
                return LocalDateTime.parse(normalized, formatter);
            } catch (Exception ignored) {
                // try next formatter
            }
        }

        return null;
    }

    private BigDecimal parseDecimalText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("[^0-9.\\-]", "");
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long extractEpochMillisFromRawHtml(String rawHtml) {
        if (!StringUtils.hasText(rawHtml)) {
            return null;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"postDate\"\\s*:\\s*(\\d{12,17})")
                .matcher(rawHtml);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (Exception ignored) {
                return null;
            }
        }

        return null;
    }

    private JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode firstNode(JsonNode root, String... fields) {
        if (root == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode node = root.path(field);
            if (!node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    private String text(JsonNode root, String field) {
        if (root == null || !StringUtils.hasText(field)) {
            return null;
        }
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal decimal(JsonNode root, String field) {
        String value = text(root, field);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal firstDecimal(JsonNode root, String... fields) {
        if (root == null) {
            return null;
        }
        for (String field : fields) {
            BigDecimal value = decimal(root, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer integer(JsonNode root, String field) {
        String value = text(root, field);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer firstInteger(JsonNode root, String... fields) {
        if (root == null) {
            return null;
        }
        for (String field : fields) {
            Integer value = integer(root, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Boolean booleanValue(JsonNode root, String field) {
        if (root == null || !StringUtils.hasText(field)) {
            return null;
        }
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        String value = node.asText(null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private Integer parseFirstInt(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch)) {
                digits.append(ch);
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(digits.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> readStringArray(JsonNode root, String field) {
        if (root == null || !StringUtils.hasText(field)) {
            return Collections.emptyList();
        }
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        Iterator<JsonNode> iterator = node.elements();
        while (iterator.hasNext()) {
            String value = iterator.next().asText(null);
            if (StringUtils.hasText(value)) {
                out.add(value.trim());
            }
        }
        return out;
    }

    private List<String> readStringArrayFromNestedJson(JsonNode root, String field) {
        String raw = text(root, field);
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        JsonNode array = readJson(raw);
        if (array == null || !array.isArray()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        Iterator<JsonNode> iterator = array.elements();
        while (iterator.hasNext()) {
            String value = iterator.next().asText(null);
            if (StringUtils.hasText(value)) {
                out.add(value.trim());
            }
        }
        return out;
    }

    private List<String> readStringArrayFromJsonString(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        JsonNode array = readJson(raw);
        if (array == null || !array.isArray()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        Iterator<JsonNode> iterator = array.elements();
        while (iterator.hasNext()) {
            String value = iterator.next().asText(null);
            if (StringUtils.hasText(value)) {
                out.add(value.trim());
            }
        }
        return out;
    }

    private List<String> resolveDetailImageUrls(List<String> parsedUrls, List<String> extractedUrls, List<String> rawHtmlUrls) {
        List<String> normalizedParsed = normalizeImageUrls(parsedUrls);
        List<String> normalizedExtracted = normalizeImageUrls(extractedUrls);
        List<String> normalizedRawHtml = normalizeImageUrls(rawHtmlUrls);
        if (!normalizedRawHtml.isEmpty()) {
            return normalizedRawHtml;
        }
        if (normalizedParsed.isEmpty()) {
            return normalizedExtracted;
        }
        if (normalizedExtracted.isEmpty()) {
            return normalizedParsed;
        }
        if (containsLikelyDecorativeImages(normalizedParsed)) {
            return normalizedExtracted;
        }
        if (normalizedParsed.size() == 1 && normalizedExtracted.size() > 1) {
            return normalizedExtracted;
        }
        if (normalizedParsed.size() < normalizedExtracted.size()
                && isNormalizedSubset(normalizedParsed, normalizedExtracted)) {
            return normalizedExtracted;
        }
        return normalizedParsed;
    }

    private List<String> resolveCarouselImageUrls(
            List<String> parsedUrls,
            List<String> extractedUrls,
            List<String> rawHtmlParsedUrls,
            List<String> rawHtmlMetaUrls
    ) {
        List<String> normalizedParsed = normalizeCarouselImageUrls(parsedUrls);
        List<String> normalizedExtracted = normalizeCarouselImageUrls(extractedUrls);
        List<String> normalizedRawHtmlParsed = normalizeCarouselImageUrls(rawHtmlParsedUrls);
        List<String> normalizedRawHtmlMeta = normalizeCarouselImageUrls(rawHtmlMetaUrls);
        return firstNonEmptyList(normalizedRawHtmlParsed, normalizedParsed, normalizedExtracted, normalizedRawHtmlMeta);
    }

    private RawHtmlSnapshot extractRawHtmlSnapshot(String rawHtml) {
        if (!StringUtils.hasText(rawHtml)) {
            return RawHtmlSnapshot.empty();
        }
        try {
            Alibaba1688HtmlParser parser = new Alibaba1688HtmlParser(objectMapper);
            Alibaba1688HtmlParser.ParsedProduct parsed = parser.parse(rawHtml);
            return new RawHtmlSnapshot(
                    readStringArrayFromJsonString(parsed.getCarouselImagesJson()),
                    readStringArrayFromJsonString(parsed.getDetailImagesJson())
            );
        } catch (Exception ignored) {
            return RawHtmlSnapshot.empty();
        }
    }

    private List<String> normalizeImageUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String url : urls) {
            String candidate = normalizeImageUrl(url);
            if (StringUtils.hasText(candidate)) {
                normalized.add(candidate);
            }
        }
        return new ArrayList<>(normalized);
    }

    private List<String> normalizeCarouselImageUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String url : urls) {
            String candidate = upgradeAlibabaThumbUrl(normalizeImageUrl(url));
            if (StringUtils.hasText(candidate)) {
                normalized.add(candidate);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeImageUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String candidate = url.trim();
        int queryIndex = candidate.indexOf('?');
        if (queryIndex >= 0) {
            candidate = candidate.substring(0, queryIndex);
        }
        int hashIndex = candidate.indexOf('#');
        if (hashIndex >= 0) {
            candidate = candidate.substring(0, hashIndex);
        }
        return StringUtils.hasText(candidate) ? candidate : null;
    }

    private String upgradeAlibabaThumbUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        Matcher matcher = ALIBABA_THUMB_SUFFIX_PATTERN.matcher(url.trim());
        if (!matcher.find()) {
            return url.trim();
        }
        return matcher.replaceFirst("$1");
    }

    private boolean isNormalizedSubset(List<String> left, List<String> right) {
        if (left == null || left.isEmpty() || right == null || right.isEmpty()) {
            return false;
        }
        LinkedHashSet<String> rightSet = new LinkedHashSet<>(right);
        for (String item : left) {
            if (!rightSet.contains(item)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsLikelyDecorativeImages(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return false;
        }
        for (String url : urls) {
            if (!StringUtils.hasText(url)) {
                continue;
            }
            String lower = url.toLowerCase();
            if (lower.contains("-2-gg_dtc")
                    || lower.contains("-2-tps-")
                    || lower.contains("alicdn.com/tfs/")) {
                return true;
            }
        }
        return false;
    }

    @SafeVarargs
    private final List<String> firstNonEmptyList(List<String>... candidates) {
        if (candidates == null) {
            return Collections.emptyList();
        }
        for (List<String> candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }
        return Collections.emptyList();
    }

    private String firstItem(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return safeText(values.get(0));
    }

    private Integer extractStartBatchQty(String... texts) {
        if (texts == null || texts.length == 0) {
            return null;
        }
        for (String text : texts) {
            if (!StringUtils.hasText(text)) {
                continue;
            }
            Matcher matcher = START_BATCH_QTY_PATTERN.matcher(text);
            if (!matcher.find()) {
                continue;
            }
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private String joinWithSpace(String left, String right) {
        if (StringUtils.hasText(left) && StringUtils.hasText(right)) {
            return left.trim() + " " + right.trim();
        }
        return firstText(left, right);
    }

    private String firstText(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... candidates) {
        if (candidates == null) {
            return null;
        }
        for (T candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParsedMain {
        private Long detailRecordId;
        private String offerId;
        private String detailUrl;
        private String sourcePlatform;
        private String productTitleSnapshot;
        private String mainImageSnapshot;
        private List<String> carouselImageUrls;
        private List<String> detailImageUrls;
        private String companyNameSnapshot;
        private BigDecimal repeatCustomerRateSnapshot;
        private BigDecimal serviceScoreSnapshot;
        private BigDecimal onTimeDeliveryRateSnapshot;
        private BigDecimal shopPositiveRateSnapshot;
        private Boolean powerSellerSnapshot;
        private String settledYearsTextSnapshot;
        private String mainBusinessSnapshot;
        private String shippingLocationSnapshot;
        private String categorySnapshot;
        private LocalDateTime publishedAt1688;
        private BigDecimal baseFreightSnapshot;
        private Integer moqSnapshot;
        private Integer startBatchQtySnapshot;
        private String moqTextSnapshot;
        private String monthlySalesSnapshot;
        private String salesTrendSnapshotJson;
        private String priceStepsSnapshotJson;
        private String assistantExtraJson;
        private LocalDateTime firstSeenAt;
        private LocalDateTime detailLastCollectedAt;
        private Integer suggestedScore;
        private String scoreDetailJson;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParsedSku {
        private String sourceSkuId;
        private String skuSpecText;
        private String skuSpecJson;
        private String skuImage;
        private String skuMainImage;
        private BigDecimal skuPriceSnapshot;
        private Integer pageStockSnapshot;
        private BigDecimal weightValue;
        private String weightSource;
        private BigDecimal dimensionValue;
        private String dimensionSource;
        private String dimensionEvidence;
    }

    private record RawHtmlMeta(
            String title,
            String mainImageUrl,
            String companyName,
            String shippingLocation,
            BigDecimal serviceScore,
            List<String> carouselImages
    ) {
        private static RawHtmlMeta empty() {
            return new RawHtmlMeta(null, null, null, null, null, Collections.emptyList());
        }
    }

    private record RawHtmlSnapshot(
            List<String> carouselImages,
            List<String> detailImages
    ) {
        private static RawHtmlSnapshot empty() {
            return new RawHtmlSnapshot(Collections.emptyList(), Collections.emptyList());
        }
    }

    public record ParsedResult(ParsedMain main, List<ParsedSku> skus) {}
}
