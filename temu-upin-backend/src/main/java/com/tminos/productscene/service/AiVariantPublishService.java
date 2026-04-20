package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.AiVariantPublishDTO;
import com.tminos.productscene.entity.AiVariantPublishRecord;
import com.tminos.productscene.entity.TemuShop;
import com.tminos.productscene.repository.AiVariantPublishRecordRepository;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import com.tminos.temu.upin.sdk.v2.dto.AddGloGoodsRequest;
import com.tminos.temu.upin.sdk.v2.dto.AddGloGoodsResponse;
import com.tminos.temu.upin.sdk.v2.dto.TemuApiResponse;
import com.tminos.temu.upin.sdk.v2.goods.TemuGloGoodsV2Client;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
public class AiVariantPublishService {

    private static final int DEFAULT_LIST_SIZE = 50;
    private static final int MAX_LIST_SIZE = 200;

    private final AiVariantPublishRecordRepository recordRepository;
    private final TemuShopService temuShopService;
    private final TemuOpenApiCredentialService credentialService;
    private final TemuPublishService temuPublishService;
    private final ObjectMapper objectMapper;

    public AiVariantPublishService(AiVariantPublishRecordRepository recordRepository,
                                   TemuShopService temuShopService,
                                   TemuOpenApiCredentialService credentialService,
                                   TemuPublishService temuPublishService,
                                   ObjectMapper objectMapper) {
        this.recordRepository = recordRepository;
        this.temuShopService = temuShopService;
        this.credentialService = credentialService;
        this.temuPublishService = temuPublishService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiVariantPublishDTO.RecordDetail publishRaw(AiVariantPublishDTO.RawPublishRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (request.getShopRecordId() == null) {
            throw new IllegalArgumentException("店铺不能为空");
        }

        TemuShop shop = temuShopService.getByIdOrThrow(request.getShopRecordId());
        AiVariantPublishRecord record = new AiVariantPublishRecord();
        record.setShopRecordId(shop.getId());
        record.setShopId(trimToNull(shop.getShopId()));
        record.setShopName(trimToNull(shop.getShopName()));
        record.setSourceType(normalizeSourceType(request.getSourceType()));
        record.setSourceBizType(normalizeSourceBizType(request.getSourceBizType()));
        record.setSourceBizId(request.getSourceBizId());
        record.setSourceBizName(trimToNull(request.getSourceBizName()));
        record.setSourceNote(trimToNull(request.getSourceNote()));
        record.setStatus("STARTED");
        record.setRequestPayload(trimToNull(request.getRequestPayload()));
        record = recordRepository.save(record);

        try {
            String rawPayload = trimToNull(request.getRequestPayload());
            if (!StringUtils.hasText(rawPayload)) {
                throw new IllegalArgumentException("原始请求 JSON 不能为空");
            }

            JsonNode payloadNode = objectMapper.readTree(rawPayload);
            record.setRequestPayload(objectMapper.writeValueAsString(payloadNode));

            AddGloGoodsRequest publishRequest = objectMapper.treeToValue(payloadNode, AddGloGoodsRequest.class);
            TemuOpenApiCredentials credentials = credentialService.getTemuOpenApiCredentialsByShopRecordIdOrThrow(shop.getId());
            TemuGloGoodsV2Client goodsClient = new TemuGloGoodsV2Client(credentials);
            TemuApiResponse<AddGloGoodsResponse> response = goodsClient.addGloGoods(publishRequest);

            record.setResponsePayload(objectMapper.writeValueAsString(response));
            record.setGoodsId(resolveGoodsId(response));
            if (response != null && response.isSuccess()) {
                record.setStatus("SUCCEEDED");
                record.setErrorMessage(null);
            } else {
                record.setStatus("FAILED");
                record.setErrorMessage(resolveResponseError(response));
            }
        } catch (Exception e) {
            record.setStatus("FAILED");
            record.setErrorMessage(safeMessage(e));
        }

        record = recordRepository.save(record);
        return toDetail(record);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AiVariantPublishDTO.GenerateDraftResponse generateDraftFromProductCollection(AiVariantPublishDTO.GenerateDraftRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (request.getSourceSpuId() == null) {
            throw new IllegalArgumentException("采集商品 SPU 不能为空");
        }
        if (request.getShopRecordId() == null) {
            throw new IllegalArgumentException("店铺不能为空");
        }

        try {
            TemuPublishService.GeneratedDraftResult draftResult = temuPublishService.generatePublishableDraft(
                    request.getSourceSpuId(),
                    request.getShopRecordId()
            );
            return AiVariantPublishDTO.GenerateDraftResponse.builder()
                    .shopRecordId(draftResult.shopRecordId())
                    .shopId(draftResult.shopId())
                    .shopName(draftResult.shopName())
                    .sourceSpuId(draftResult.sourceSpuId())
                    .sourceProductName(draftResult.sourceProductName())
                    .sourceProductMainImage(draftResult.sourceProductMainImage())
                    .sourceType("AI_VARIANT")
                    .sourceBizType("PRODUCT_COLLECTION")
                    .sourceBizId(draftResult.sourceSpuId())
                    .sourceBizName(draftResult.sourceProductName())
                    .sourceNote("采集商品SPU#" + draftResult.sourceSpuId())
                    .requestPayload(draftResult.requestJson())
                    .warnings(draftResult.warnings() == null ? List.of() : draftResult.warnings())
                    .preview(parsePreview(draftResult.requestJson()))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(safeMessage(e), e);
        }
    }

    @Transactional(readOnly = true)
    public List<AiVariantPublishDTO.RecordSummary> listRecords(Long shopRecordId, Integer size) {
        int limit = normalizeSize(size);
        PageRequest pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (shopRecordId != null) {
            return recordRepository.findByShopRecordIdOrderByCreatedAtDesc(shopRecordId, pageable)
                    .stream()
                    .map(this::toSummary)
                    .toList();
        }
        return recordRepository.findAll(pageable)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiVariantPublishDTO.RecordDetail getRecord(Long id) {
        AiVariantPublishRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("发布记录不存在: " + id));
        return toDetail(record);
    }

    private AiVariantPublishDTO.RecordSummary toSummary(AiVariantPublishRecord record) {
        return AiVariantPublishDTO.RecordSummary.builder()
                .id(record.getId())
                .shopRecordId(record.getShopRecordId())
                .shopId(record.getShopId())
                .shopName(record.getShopName())
                .sourceType(record.getSourceType())
                .sourceBizType(record.getSourceBizType())
                .sourceBizId(record.getSourceBizId())
                .sourceBizName(record.getSourceBizName())
                .sourceNote(record.getSourceNote())
                .status(record.getStatus())
                .goodsId(record.getGoodsId())
                .errorMessage(record.getErrorMessage())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .preview(parsePreview(record.getRequestPayload()))
                .build();
    }

    private AiVariantPublishDTO.RecordDetail toDetail(AiVariantPublishRecord record) {
        JsonNode responseNode = readJson(record.getResponsePayload());
        return AiVariantPublishDTO.RecordDetail.builder()
                .id(record.getId())
                .shopRecordId(record.getShopRecordId())
                .shopId(record.getShopId())
                .shopName(record.getShopName())
                .sourceType(record.getSourceType())
                .sourceBizType(record.getSourceBizType())
                .sourceBizId(record.getSourceBizId())
                .sourceBizName(record.getSourceBizName())
                .sourceNote(record.getSourceNote())
                .status(record.getStatus())
                .goodsId(record.getGoodsId())
                .errorMessage(record.getErrorMessage())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .requestPayload(record.getRequestPayload())
                .responsePayload(record.getResponsePayload())
                .responseSuccess(readBoolean(responseNode, "success"))
                .responseErrorCode(readInteger(responseNode, "errorCode"))
                .responseErrorMsg(readText(responseNode, "errorMsg"))
                .requestId(readText(responseNode, "requestId"))
                .preview(parsePreview(record.getRequestPayload()))
                .build();
    }

    private AiVariantPublishDTO.ParsedPreview parsePreview(String requestPayload) {
        JsonNode root = readJson(requestPayload);
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }

        List<String> carouselImageUrls = readStringArray(root.path("carouselImageUrls"));
        List<String> detailImageUrls = new ArrayList<>();
        JsonNode decorationNodes = root.path("goodsLayerDecorationReqs");
        if (decorationNodes.isArray()) {
            for (JsonNode decorationNode : decorationNodes) {
                JsonNode contentListNode = decorationNode.path("contentList");
                if (!contentListNode.isArray()) {
                    continue;
                }
                for (JsonNode contentNode : contentListNode) {
                    String imgUrl = trimToNull(contentNode.path("imgUrl").asText(null));
                    if (imgUrl != null) {
                        detailImageUrls.add(imgUrl);
                    }
                }
            }
        }

        List<AiVariantPublishDTO.SkuPreview> skuList = new ArrayList<>();
        JsonNode skcNodes = root.path("productSkcReqs");
        int skcCount = 0;
        int skuCount = 0;
        String firstSkuThumb = null;
        if (skcNodes.isArray()) {
            skcCount = skcNodes.size();
            for (JsonNode skcNode : skcNodes) {
                String skcExtCode = trimToNull(skcNode.path("extCode").asText(null));
                String fallbackThumb = firstNonBlank(
                        firstString(skcNode.path("previewImgUrls")),
                        trimToNull(skcNode.path("colorImageUrl").asText(null))
                );
                JsonNode skuNodes = skcNode.path("productSkuReqs");
                if (!skuNodes.isArray()) {
                    continue;
                }
                for (JsonNode skuNode : skuNodes) {
                    skuCount++;
                    String thumbUrl = firstNonBlank(
                            trimToNull(skuNode.path("thumbUrl").asText(null)),
                            fallbackThumb
                    );
                    if (firstSkuThumb == null && thumbUrl != null) {
                        firstSkuThumb = thumbUrl;
                    }
                    skuList.add(AiVariantPublishDTO.SkuPreview.builder()
                            .skcExtCode(skcExtCode)
                            .skuExtCode(trimToNull(skuNode.path("extCode").asText(null)))
                            .thumbUrl(thumbUrl)
                            .supplierPriceText(resolveSkuSupplierPriceText(skuNode))
                            .siteSupplierPriceText(resolveSiteSupplierPriceText(skuNode.path("siteSupplierPrices")))
                            .specNames(readSpecNames(skuNode.path("productSkuSpecReqs")))
                            .build());
                }
            }
        }

        List<String> normalizedCarousel = deduplicate(carouselImageUrls);
        List<String> normalizedDetail = deduplicate(detailImageUrls);

        return AiVariantPublishDTO.ParsedPreview.builder()
                .productName(trimToNull(root.path("productName").asText(null)))
                .mainImageUrl(firstNonBlank(
                        firstString(normalizedCarousel),
                        firstString(normalizedDetail),
                        firstSkuThumb
                ))
                .carouselImageUrls(normalizedCarousel)
                .detailImageUrls(normalizedDetail)
                .skcCount(skcCount)
                .skuCount(skuCount)
                .skuList(skuList)
                .build();
    }

    private String resolveGoodsId(TemuApiResponse<AddGloGoodsResponse> response) {
        if (response == null || response.getResult() == null || response.getResult().getGoodsId() == null) {
            return null;
        }
        return String.valueOf(response.getResult().getGoodsId());
    }

    private String resolveResponseError(TemuApiResponse<AddGloGoodsResponse> response) {
        if (response == null) {
            return "TEMU 接口未返回响应";
        }
        String errorMsg = trimToNull(response.getErrorMsg());
        Integer errorCode = response.getErrorCode();
        if (errorCode != null && errorMsg != null) {
            return errorCode + ": " + errorMsg;
        }
        if (errorMsg != null) {
            return errorMsg;
        }
        if (errorCode != null) {
            return String.valueOf(errorCode);
        }
        return "TEMU 接口返回失败";
    }

    private String resolveSkuSupplierPriceText(JsonNode skuNode) {
        if (skuNode == null || skuNode.isMissingNode() || skuNode.isNull()) {
            return null;
        }
        JsonNode directSupplierPrice = skuNode.path("supplierPrice");
        String direct = formatDecimalPrice(directSupplierPrice);
        if (direct != null) {
            return direct;
        }

        JsonNode siteSupplierPrices = skuNode.path("siteSupplierPrices");
        if (!siteSupplierPrices.isArray()) {
            return null;
        }

        for (JsonNode siteSupplierPrice : siteSupplierPrices) {
            String centsPrice = formatCentPrice(siteSupplierPrice.path("supplierPrice"));
            if (centsPrice != null) {
                return centsPrice;
            }
        }
        return null;
    }

    private String resolveSiteSupplierPriceText(JsonNode siteSupplierPricesNode) {
        if (siteSupplierPricesNode == null || !siteSupplierPricesNode.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode siteSupplierPriceNode : siteSupplierPricesNode) {
            String siteId = trimToNull(siteSupplierPriceNode.path("siteId").asText(null));
            String price = formatCentPrice(siteSupplierPriceNode.path("supplierPrice"));
            if (price == null) {
                continue;
            }
            values.add(siteId == null ? price : siteId + ": " + price);
        }
        return values.isEmpty() ? null : String.join(" / ", values);
    }

    private List<String> readSpecNames(JsonNode specNodes) {
        if (specNodes == null || !specNodes.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode specNode : specNodes) {
            String specName = trimToNull(specNode.path("specName").asText(null));
            if (specName != null) {
                values.add(specName);
            }
        }
        return values;
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = trimToNull(item.asText(null));
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private JsonNode readJson(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Boolean readBoolean(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode valueNode = node.path(fieldName);
        return valueNode.isMissingNode() || valueNode.isNull() ? null : valueNode.asBoolean();
    }

    private Integer readInteger(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode valueNode = node.path(fieldName);
        return valueNode.isMissingNode() || valueNode.isNull() ? null : valueNode.asInt();
    }

    private String readText(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        return trimToNull(node.path(fieldName).asText(null));
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_LIST_SIZE;
        }
        return Math.min(Math.max(size, 1), MAX_LIST_SIZE);
    }

    private String normalizeSourceType(String sourceType) {
        String normalized = trimToNull(sourceType);
        return normalized == null ? "RAW_PUBLISH" : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeSourceBizType(String sourceBizType) {
        String normalized = trimToNull(sourceBizType);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String safeMessage(Exception exception) {
        if (exception == null) {
            return "未知异常";
        }
        String message = trimToNull(exception.getMessage());
        if (message != null) {
            return message;
        }
        Throwable cause = exception.getCause();
        if (cause != null && StringUtils.hasText(cause.getMessage())) {
            return cause.getMessage().trim();
        }
        return exception.getClass().getSimpleName();
    }

    private String formatDecimalPrice(JsonNode valueNode) {
        if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        try {
            BigDecimal value = valueNode.decimalValue().stripTrailingZeros();
            return value.scale() < 0 ? value.setScale(0, RoundingMode.UNNECESSARY).toPlainString() : value.toPlainString();
        } catch (Exception ignored) {
            String text = trimToNull(valueNode.asText(null));
            if (!StringUtils.hasText(text)) {
                return null;
            }
            return text;
        }
    }

    private String formatCentPrice(JsonNode valueNode) {
        if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        try {
            BigDecimal cents = valueNode.decimalValue();
            return cents.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> deduplicate(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(values.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .toList()));
    }

    private String firstString(List<String> values) {
        if (values == null || values.isEmpty()) {
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

    private String firstString(JsonNode valuesNode) {
        if (valuesNode == null || !valuesNode.isArray()) {
            return null;
        }
        for (JsonNode item : valuesNode) {
            String value = trimToNull(item.asText(null));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
