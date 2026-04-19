package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.LogisticsProviderConfig;
import com.tminos.productscene.entity.TemuOrderLogistics;
import com.tminos.productscene.repository.TemuOrderLogisticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HaoyuanLogisticsService {

    private static final Logger log = LoggerFactory.getLogger(HaoyuanLogisticsService.class);

    private final HaoyuanLogisticsClient client;
    private final LogisticsProviderConfigService logisticsProviderConfigService;
    private final TemuOrderLogisticsRepository logisticsRepository;
    private final ObjectMapper objectMapper;

    public HaoyuanLogisticsService(HaoyuanLogisticsClient client,
                                   LogisticsProviderConfigService logisticsProviderConfigService,
                                   TemuOrderLogisticsRepository logisticsRepository,
                                   ObjectMapper objectMapper) {
        this.client = client;
        this.logisticsProviderConfigService = logisticsProviderConfigService;
        this.logisticsRepository = logisticsRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TemuOrderLogistics refresh(Long shopRecordId,
                                      String shopId,
                                      String shopName,
                                      String parentOrderSn,
                                      String referenceNo,
                                      String shippingMethodNo,
                                      String trackingNumber) {
        String normalizedParentOrderSn = trim(parentOrderSn);
        if (!StringUtils.hasText(normalizedParentOrderSn)) {
            throw new IllegalArgumentException("parentOrderSn 不能为空");
        }

        TemuOrderLogistics entity = logisticsRepository
                .findByShopRecordIdAndParentOrderSnAndProviderCode(shopRecordId, normalizedParentOrderSn, LogisticsProviderConfigService.HAOYUAN_PROVIDER_CODE)
                .orElseGet(TemuOrderLogistics::new);
        entity.setShopRecordId(shopRecordId);
        entity.setShopId(trim(shopId));
        entity.setShopName(trim(shopName));
        entity.setParentOrderSn(normalizedParentOrderSn);
        entity.setProviderCode(LogisticsProviderConfigService.HAOYUAN_PROVIDER_CODE);
        entity.setProviderName("浩远国际");

        String finalReferenceNo = firstNonBlank(referenceNo, entity.getReferenceNo());
        String finalShippingMethodNo = firstNonBlank(shippingMethodNo, entity.getShippingMethodNo());
        String finalTrackingNumber = firstNonBlank(trackingNumber, entity.getTrackingNumber());
        if (!StringUtils.hasText(finalReferenceNo) && looksLikeDianxiaomiPackageNumber(finalShippingMethodNo)) {
            finalReferenceNo = finalShippingMethodNo;
        }
        entity.setReferenceNo(finalReferenceNo);
        entity.setShippingMethodNo(finalShippingMethodNo);
        entity.setTrackingNumber(finalTrackingNumber);

        LogisticsProviderConfig config = logisticsProviderConfigService.getEnabledByCodeOrThrow(LogisticsProviderConfigService.HAOYUAN_PROVIDER_CODE);
        Map<String, Object> rawMap = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        JsonNode trackingNumberRoot = null;
        if (!StringUtils.hasText(finalTrackingNumber) && StringUtils.hasText(finalReferenceNo)) {
            trackingNumberRoot = callSafely(config, "gettrackingnumber", buildJson("reference_no", finalReferenceNo),
                    rawMap, "trackingNumber", errors);
            String resolvedReferenceNo = extractReferenceNo(trackingNumberRoot);
            if (StringUtils.hasText(resolvedReferenceNo)) {
                finalReferenceNo = resolvedReferenceNo;
                entity.setReferenceNo(resolvedReferenceNo);
            }
            String resolvedShippingMethodNo = extractShippingMethodNo(trackingNumberRoot);
            if (StringUtils.hasText(resolvedShippingMethodNo)) {
                finalShippingMethodNo = resolvedShippingMethodNo;
                entity.setShippingMethodNo(resolvedShippingMethodNo);
            }
            String resolvedTrackingNumber = extractTrackingNumber(trackingNumberRoot);
            if (StringUtils.hasText(resolvedTrackingNumber)) {
                finalTrackingNumber = resolvedTrackingNumber;
                entity.setTrackingNumber(resolvedTrackingNumber);
            }
        }

        JsonNode trackingRoot = callSafely(config, "gettrack", buildJson("tracking_number", finalTrackingNumber), rawMap, "tracking", errors);
        String businessLookupJson = buildBusinessLookupJson(finalShippingMethodNo, finalReferenceNo);
        JsonNode feeDetailRoot = callSafely(config, "getbusinessfee_detail", businessLookupJson, rawMap, "feeDetail", errors);
        JsonNode weightRoot = callSafely(config, "getbusinessweight", businessLookupJson, rawMap, "weight", errors);

        if (trackingRoot == null && feeDetailRoot == null && weightRoot == null) {
            if (isNoDataResult(errors)) {
                if (!hasExistingUsefulSnapshot(entity)) {
                    entity.setTrackStatus("NO_DATA");
                    entity.setTrackStatusName("浩远暂无对应物流数据");
                }
                rawMap.put("errors", errors);
                entity.setRawJson(writeJson(rawMap));
                entity.setLastSyncedAt(LocalDateTime.now());
                return logisticsRepository.save(entity);
            }
            throw new IllegalStateException(errors.isEmpty() ? "未获取到任何浩远物流数据" : String.join("；", errors));
        }

        applyTracking(entity, trackingRoot);
        applyFeeDetail(entity, feeDetailRoot);
        applyWeight(entity, weightRoot);

        if (!errors.isEmpty()) {
            rawMap.put("errors", errors);
            log.debug("浩远物流部分接口调用失败 parentOrderSn={}, errors={}", normalizedParentOrderSn, errors);
        }
        entity.setRawJson(writeJson(rawMap));
        entity.setLastSyncedAt(LocalDateTime.now());
        return logisticsRepository.save(entity);
    }

    private JsonNode callSafely(LogisticsProviderConfig config,
                                String method,
                                String paramsJson,
                                Map<String, Object> rawMap,
                                String rawKey,
                                List<String> errors) {
        if (!StringUtils.hasText(paramsJson)) {
            return null;
        }
        try {
            JsonNode root = client.sendRequest(config, method, paramsJson);
            rawMap.put(rawKey, objectMapper.convertValue(root, Object.class));
            return root;
        } catch (Exception e) {
            errors.add(method + " 失败: " + e.getMessage());
            return null;
        }
    }

    private void applyTracking(TemuOrderLogistics entity, JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return;
        }
        JsonNode firstData = firstArrayItem(root.path("data"));
        if (firstData == null) {
            return;
        }
        entity.setDestinationCountry(text(firstData, "destination_country"));
        entity.setTrackStatus(text(firstData, "track_status"));
        entity.setTrackStatusName(text(firstData, "track_status_name"));
        JsonNode details = firstData.path("details");
        if (!details.isMissingNode() && !details.isNull()) {
            entity.setTrackDetailsJson(details.toString());
        }
    }

    private String extractTrackingNumber(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return null;
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return null;
        }
        if (data.isArray()) {
            for (JsonNode item : data) {
                String value = extractTrackingNumberFromNode(item);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
            return null;
        }
        return extractTrackingNumberFromNode(data);
    }

    private String extractReferenceNo(JsonNode root) {
        return extractSingleField(root, "refrence_no", "reference_no", "referenceNo");
    }

    private String extractShippingMethodNo(JsonNode root) {
        return extractSingleField(root, "shipping_method_no", "shippingMethodNo");
    }

    private String extractTrackingNumberFromNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return firstNonBlank(
                text(node, "channel_hawbcode"),
                text(node, "tracking_number"),
                text(node, "trackingNumber"),
                text(node, "server_hawbcode")
        );
    }

    private String extractSingleField(JsonNode root, String... fieldNames) {
        if (root == null || root.isMissingNode() || fieldNames == null || fieldNames.length == 0) {
            return null;
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return null;
        }
        if (data.isArray()) {
            for (JsonNode item : data) {
                String value = firstNonBlankFromNode(item, fieldNames);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
            return null;
        }
        return firstNonBlankFromNode(data, fieldNames);
    }

    private void applyFeeDetail(TemuOrderLogistics entity, JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return;
        }
        JsonNode data = root.path("data");
        if (!data.isMissingNode() && !data.isNull()) {
            entity.setOrderFeeDetailJson(data.toString());
            BigDecimal fee = sumFeeDetailAmounts(data);
            if (fee == null) {
                fee = extractDecimal(data,
                        "business_fee", "total_fee", "pay_fee", "fee_total", "totalAmount", "amount", "fee", "price");
            }
            if (fee != null) {
                entity.setFirstLegLogisticsFee(fee);
            }
        }
    }

    private void applyWeight(TemuOrderLogistics entity, JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return;
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return;
        }
        entity.setOrderWeightInfoJson(data.toString());
        entity.setGrossWeight(decimal(text(data, "grossweight")));
        entity.setVolumeWeight(decimal(text(data, "volumeweight")));
        entity.setChargeWeight(decimal(text(data, "chargeweight")));
    }

    private String buildJson(String key, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return writeJson(Map.of(key, value.trim()));
    }

    private String buildJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                if (!StringUtils.hasText(text)) {
                    return null;
                }
                normalized.put(entry.getKey(), text.trim());
            } else if (value != null) {
                normalized.put(entry.getKey(), value);
            }
        }
        return normalized.isEmpty() ? null : writeJson(normalized);
    }

    private String buildBusinessLookupJson(String shippingMethodNo, String referenceNo) {
        if (StringUtils.hasText(shippingMethodNo)) {
            return buildJson("shipping_method_no", shippingMethodNo);
        }
        if (StringUtils.hasText(referenceNo)) {
            return buildJson("reference_no", referenceNo);
        }
        return null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化物流数据失败", e);
        }
    }

    private static JsonNode firstArrayItem(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty() ? node.get(0) : null;
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        return field.isMissingNode() || field.isNull() ? null : trim(field.asText());
    }

    private static String firstNonBlankFromNode(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull() || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static BigDecimal extractDecimal(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            BigDecimal direct = decimal(text(node, fieldName));
            if (direct != null) {
                return direct;
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                BigDecimal found = extractDecimal(child, fieldNames);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isObject()) {
            for (JsonNode child : node) {
                BigDecimal found = extractDecimal(child, fieldNames);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static BigDecimal sumFeeDetailAmounts(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            BigDecimal total = null;
            for (JsonNode child : node) {
                BigDecimal amount = firstAvailableDecimal(child, "amount", "currency_amount", "business_fee", "fee", "price");
                if (amount == null) {
                    continue;
                }
                total = total == null ? amount : total.add(amount);
            }
            return total;
        }
        return firstAvailableDecimal(node, "total_fee", "pay_fee", "fee_total", "totalAmount", "amount", "currency_amount", "business_fee", "fee", "price");
    }

    private static BigDecimal firstAvailableDecimal(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull() || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            BigDecimal value = decimal(text(node, fieldName));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trim(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private static boolean isNoDataResult(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return false;
        }
        for (String error : errors) {
            String normalized = trim(error);
            if (!StringUtils.hasText(normalized)) {
                return false;
            }
            if (!(normalized.contains("不存在，或者货物未到我司")
                    || normalized.contains("服务商单号[")
                    || normalized.contains("参考单号[")
                    || normalized.contains("获取的跟踪单号为空"))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasExistingUsefulSnapshot(TemuOrderLogistics entity) {
        if (entity == null) {
            return false;
        }
        return StringUtils.hasText(entity.getTrackingNumber())
                || StringUtils.hasText(entity.getTrackStatusName())
                || entity.getFirstLegLogisticsFee() != null
                || entity.getChargeWeight() != null;
    }

    private static boolean looksLikeDianxiaomiPackageNumber(String value) {
        String normalized = trim(value);
        return StringUtils.hasText(normalized) && normalized.toUpperCase().startsWith("XM");
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
