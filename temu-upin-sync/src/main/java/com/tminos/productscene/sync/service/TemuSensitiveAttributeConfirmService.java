package com.tminos.productscene.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.service.TemuOpenApiCredentialService;
import com.tminos.productscene.sync.entity.TemuGoods;
import com.tminos.productscene.sync.entity.TemuGoodsSku;
import com.tminos.productscene.sync.repository.TemuGoodsRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuRepository;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class TemuSensitiveAttributeConfirmService {

    private static final Logger log = LoggerFactory.getLogger(TemuSensitiveAttributeConfirmService.class);
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final int NORMAL_SENSITIVE_CODE = 100000;

    private final TemuGoodsRepository goodsRepository;
    private final TemuGoodsSkuRepository skuRepository;
    private final TemuOpenApiCredentialService credentialService;
    private final ObjectMapper objectMapper;

    public TemuSensitiveAttributeConfirmService(TemuGoodsRepository goodsRepository,
                                                TemuGoodsSkuRepository skuRepository,
                                                TemuOpenApiCredentialService credentialService,
                                                ObjectMapper objectMapper) {
        this.goodsRepository = goodsRepository;
        this.skuRepository = skuRepository;
        this.credentialService = credentialService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> confirmGoods(Long goodsId) {
        TemuGoods goods = goodsRepository.findById(goodsId)
                .orElseThrow(() -> new IllegalArgumentException("TEMU 商品不存在: " + goodsId));
        return confirmGoodsInternal(goods);
    }

    @Transactional
    public Map<String, Object> confirmByShopAndProductId(String shopId, Long productId) {
        TemuGoods goods = goodsRepository.findByShopIdAndProductId(shopId, productId)
                .orElseThrow(() -> new IllegalArgumentException("TEMU 商品不存在: shopId=" + shopId + ", productId=" + productId));
        return confirmGoodsInternal(goods);
    }

    @Transactional
    public Map<String, Object> confirmPendingBatch(int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        LocalDateTime retryBefore = LocalDateTime.now().minusHours(6);
        List<TemuGoods> candidates = goodsRepository.findSensitiveAttrConfirmCandidates(retryBefore, PageRequest.of(0, size));
        int success = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (TemuGoods goods : candidates) {
            try {
                Map<String, Object> result = confirmGoodsInternal(goods);
                details.add(result);
                String status = Objects.toString(result.get("status"), "");
                if (STATUS_CONFIRMED.equals(status)) {
                    success++;
                } else if (STATUS_SKIPPED.equals(status)) {
                    skipped++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("自动确认 TEMU 敏感属性失败 goodsId={}, error={}", goods.getId(), e.getMessage());
            }
        }
        return Map.of(
                "total", candidates.size(),
                "success", success,
                "skipped", skipped,
                "failed", failed,
                "details", details
        );
    }

    private Map<String, Object> confirmGoodsInternal(TemuGoods goods) {
        LocalDateTime now = LocalDateTime.now();
        List<TemuGoodsSku> skus = skuRepository.findByGoodsId(goods.getId()).stream()
                .filter(sku -> sku.getProductSkuId() != null)
                .filter(sku -> sku.getIsSensitive() == null || sku.getIsSensitive() == 0)
                .toList();
        if (skus.isEmpty()) {
            mark(goods, STATUS_SKIPPED, "没有可提交的非敏感 SKU", now);
            return baseResult(goods, STATUS_SKIPPED, 0, "没有可提交的非敏感 SKU", null);
        }
        if (goods.getProductId() == null || goods.getShopId() == null || goods.getShopId().isBlank()) {
            mark(goods, STATUS_SKIPPED, "缺少 productId 或 shopId", now);
            return baseResult(goods, STATUS_SKIPPED, skus.size(), "缺少 productId 或 shopId", null);
        }

        Map<String, Object> params = buildParams(goods.getProductId(), skus, true);
        ApiAttempt attempt = callEditSensitiveAttr(goods.getShopId(), params);
        if (!attempt.success() && shouldRetryWithoutNormalCode(attempt.raw(), attempt.error())) {
            params = buildParams(goods.getProductId(), skus, false);
            attempt = callEditSensitiveAttr(goods.getShopId(), params);
        }

        if (attempt.success() || isAlreadyApproved(attempt.raw(), attempt.error())) {
            String message = attempt.success() ? "敏感属性已提交为非敏感" : "敏感属性已审批通过，无需重复提交";
            mark(goods, STATUS_CONFIRMED, null, now);
            return baseResult(goods, STATUS_CONFIRMED, skus.size(), message, attempt.raw());
        }
        if (isTemporarilyNotEditable(attempt.raw(), attempt.error())) {
            mark(goods, STATUS_SKIPPED, attempt.error(), now);
            return baseResult(goods, STATUS_SKIPPED, skus.size(), attempt.error(), attempt.raw());
        }

        mark(goods, STATUS_FAILED, attempt.error(), now);
        return baseResult(goods, STATUS_FAILED, skus.size(), attempt.error(), attempt.raw());
    }

    private Map<String, Object> buildParams(Long productId, List<TemuGoodsSku> skus, boolean includeNormalCode) {
        List<Map<String, Object>> skuReqList = new ArrayList<>();
        for (TemuGoodsSku sku : skus) {
            Map<String, Object> sensitiveAttr = new LinkedHashMap<>();
            sensitiveAttr.put("isSensitive", 0);
            sensitiveAttr.put("sensitiveTypes", List.of());
            sensitiveAttr.put("sensitiveList", includeNormalCode ? List.of(NORMAL_SENSITIVE_CODE) : List.of());

            Map<String, Object> skuReq = new LinkedHashMap<>();
            skuReq.put("productSkuId", sku.getProductSkuId());
            skuReq.put("productSkuSensitiveLimitReq", Map.of());
            skuReq.put("productSkuSensitiveAttrReq", sensitiveAttr);
            skuReqList.add(skuReq);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("productId", productId);
        params.put("skuReqList", skuReqList);
        return params;
    }

    private ApiAttempt callEditSensitiveAttr(String shopId, Map<String, Object> params) {
        try {
            TemuOpenApiCredentials creds = credentialService.getTemuOpenApiCredentialsByExactShopIdOrThrow(shopId);
            TemuOpenApiClient client = new TemuOpenApiClient(creds);
            List<ApiCandidate> candidates = List.of(
                    new ApiCandidate("商品发布路由", TemuOpenApiEndpoints.API_BASE_URL, true),
                    new ApiCandidate("PA路由", TemuOpenApiEndpoints.API_BASE_URL_PA, true),
                    new ApiCandidate("商品发布路由(无mall_id)", TemuOpenApiEndpoints.API_BASE_URL, false),
                    new ApiCandidate("PA路由(无mall_id)", TemuOpenApiEndpoints.API_BASE_URL_PA, false)
            );
            ApiAttempt last = null;
            for (ApiCandidate candidate : candidates) {
                TemuOpenApiClient.ApiResult result = client.callApiParsed(
                        TemuOpenApiClient.API_GOODS_EDIT_SENSITIVE_ATTR,
                        params,
                        candidate.routerUrl(),
                        candidate.includeMallId()
                );
                last = new ApiAttempt(result.success, normalizeError(candidate.name(), result), result.raw);
                if (result.success || isAlreadyApproved(result.raw, result.errorMsg)) {
                    return last;
                }
                if (isNoAccess(result.raw, result.errorMsg)) {
                    break;
                }
            }
            return last == null ? new ApiAttempt(false, "TEMU 接口未返回结果", null) : last;
        } catch (Exception e) {
            return new ApiAttempt(false, e.getMessage(), null);
        }
    }

    private String normalizeError(String candidateName, TemuOpenApiClient.ApiResult result) {
        String message = result == null ? null : result.errorMsg;
        String raw = result == null ? null : result.raw;
        String code = extractErrorCode(raw);
        if (code != null && message != null && !message.isBlank()) {
            return candidateName + ": errorCode=" + code + ", errorMsg=" + message;
        }
        if (message != null && !message.isBlank()) {
            return candidateName + ": " + message;
        }
        return candidateName + ": " + abbreviate(raw, 1000);
    }

    @SuppressWarnings("unchecked")
    private String extractErrorCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(raw, Map.class);
            Object code = map.containsKey("errorCode") ? map.get("errorCode") : map.get("error_code");
            return code == null ? null : String.valueOf(code);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isAlreadyApproved(String raw, String error) {
        String text = normalizeText(raw, error);
        return text.contains("996000031") || text.contains("审批通过") || text.contains("不能修改");
    }

    private boolean isTemporarilyNotEditable(String raw, String error) {
        String text = normalizeText(raw, error);
        return text.contains("2000152") || text.contains("2000194")
                || text.contains("修改中") || text.contains("审核中") || text.contains("暂不可编辑");
    }

    private boolean shouldRetryWithoutNormalCode(String raw, String error) {
        String text = normalizeText(raw, error).toLowerCase(Locale.ROOT);
        return text.contains("参数") || text.contains("param") || text.contains("sensitive");
    }

    private boolean isNoAccess(String raw, String error) {
        String text = normalizeText(raw, error).toLowerCase(Locale.ROOT);
        return text.contains("access_token don't have this api access") || text.contains("no api access");
    }

    private String normalizeText(String raw, String error) {
        return (Objects.toString(raw, "") + " " + Objects.toString(error, "")).trim();
    }

    private void mark(TemuGoods goods, String status, String error, LocalDateTime now) {
        goods.setSensitiveAttrConfirmStatus(status);
        goods.setSensitiveAttrConfirmAt(now);
        goods.setSensitiveAttrConfirmError(abbreviate(error, 4000));
        goodsRepository.save(goods);
    }

    private Map<String, Object> baseResult(TemuGoods goods, String status, int skuCount, String message, String raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("goodsId", goods.getId());
        result.put("shopId", goods.getShopId());
        result.put("productId", goods.getProductId());
        result.put("productSkcId", goods.getProductSkcId());
        result.put("skuCount", skuCount);
        result.put("status", status);
        result.put("message", message);
        result.put("raw", raw);
        return result;
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private record ApiCandidate(String name, String routerUrl, boolean includeMallId) {
    }

    private record ApiAttempt(boolean success, String error, String raw) {
    }
}
