package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tminos.productscene.dto.TemuOrderDTO;
import com.tminos.productscene.entity.TemuOrder;
import com.tminos.productscene.entity.TemuShop;
import com.tminos.productscene.repository.TemuOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DianxiaomiOrderSyncService {

    private static final Logger log = LoggerFactory.getLogger(DianxiaomiOrderSyncService.class);
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int DEFAULT_MAX_PAGES = 1000;

    private final TemuShopService shopService;
    private final DianxiaomiPackageClient dianxiaomiPackageClient;
    private final TemuOrderRepository orderRepository;
    private final TemuOrderMatchService orderMatchService;
    private final ObjectMapper objectMapper;

    public DianxiaomiOrderSyncService(TemuShopService shopService,
                                      DianxiaomiPackageClient dianxiaomiPackageClient,
                                      TemuOrderRepository orderRepository,
                                      TemuOrderMatchService orderMatchService,
                                      ObjectMapper objectMapper) {
        this.shopService = shopService;
        this.dianxiaomiPackageClient = dianxiaomiPackageClient;
        this.orderRepository = orderRepository;
        this.orderMatchService = orderMatchService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<TemuOrderDTO.SyncResponse> sync(TemuOrderDTO.SyncRequest request) {
        List<TemuShop> shops = resolveShops(request == null ? null : request.getShopRecordId());
        List<TemuOrderDTO.SyncResponse> responses = new ArrayList<>();
        int pageSize = clamp(request == null ? null : request.getPageSize(), 1, 100, DEFAULT_PAGE_SIZE);
        int maxPages = clamp(request == null ? null : request.getMaxPages(), 1, 1000, DEFAULT_MAX_PAGES);
        for (TemuShop shop : shops) {
            responses.add(syncShop(shop, pageSize, maxPages));
        }
        return responses;
    }

    protected TemuOrderDTO.SyncResponse syncShop(TemuShop shop, int pageSize, int maxPages) {
        if (shop == null || shop.getId() == null) {
            return failed(null, "店铺不存在");
        }
        String cookie = trim(shop.getDianxiaomiCookie());
        String dianxiaomiShopId = trim(shop.getDianxiaomiShopId());
        if (!StringUtils.hasText(cookie) || !StringUtils.hasText(dianxiaomiShopId)) {
            return TemuOrderDTO.SyncResponse.builder()
                    .shopRecordId(shop.getId())
                    .shopId(shop.getShopId())
                    .shopName(shop.getShopName())
                    .success(true)
                    .message("跳过：未配置店小秘 cookie 或店小秘店铺ID")
                    .build();
        }

        int totalCount = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int matchedCount = 0;
        try {
            for (int pageNo = 1; pageNo <= maxPages; pageNo++) {
                DianxiaomiPackageClient.ListResponse response =
                        dianxiaomiPackageClient.listByShopId(cookie, dianxiaomiShopId, pageNo, pageSize);
                JsonNode list = response.list();
                if (!list.isArray() || list.isEmpty()) {
                    break;
                }

                for (JsonNode packageNode : list) {
                    JsonNode productList = resolveProductRows(packageNode);
                    if (productList.isEmpty()) {
                        UpsertResult result = upsertFromDianxiaomi(shop, packageNode, null);
                        if (result == null) {
                            continue;
                        }
                        totalCount++;
                        if (result.created()) createdCount++; else updatedCount++;
                        if (result.matched()) matchedCount++;
                        continue;
                    }
                    Set<String> productOrderSns = new HashSet<>();
                    for (JsonNode productNode : productList) {
                        UpsertResult result = upsertFromDianxiaomi(shop, packageNode, productNode);
                        if (result == null) {
                            continue;
                        }
                        productOrderSns.add(result.orderSn());
                        totalCount++;
                        if (result.created()) createdCount++; else updatedCount++;
                        if (result.matched()) matchedCount++;
                    }
                    cleanupPlaceholderRows(shop.getId(), text(packageNode, "packageNumber"), productOrderSns);
                }

                if (list.size() < pageSize || (response.totalPage() > 0 && pageNo >= response.totalPage())) {
                    break;
                }
            }
            return TemuOrderDTO.SyncResponse.builder()
                    .shopRecordId(shop.getId())
                    .shopId(shop.getShopId())
                    .shopName(shop.getShopName())
                    .success(true)
                    .totalCount(totalCount)
                    .createdCount(createdCount)
                    .updatedCount(updatedCount)
                    .matchedCount(matchedCount)
                    .message("店小秘订单同步完成")
                    .build();
        } catch (Exception e) {
            log.warn("店小秘订单同步失败 shopId={}, dianxiaomiShopId={}, error={}",
                    shop.getShopId(), dianxiaomiShopId, e.getMessage());
            return TemuOrderDTO.SyncResponse.builder()
                    .shopRecordId(shop.getId())
                    .shopId(shop.getShopId())
                    .shopName(shop.getShopName())
                    .success(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    private UpsertResult upsertFromDianxiaomi(TemuShop shop, JsonNode packageNode, JsonNode productNode) {
        if (productNode == null) {
            productNode = objectMapper.nullNode();
        }
        String orderSn = firstNonBlank(
                text(productNode, "orderSn"),
                text(productNode, "splitKey"),
                text(productNode.path("originData"), "itemNo"),
                text(productNode.path("originData"), "splitid"),
                text(productNode, "id"),
                text(packageNode, "id")
        );
        if (!StringUtils.hasText(orderSn)) {
            return null;
        }

        TemuOrder entity = orderRepository.findByShopRecordIdAndOrderSn(shop.getId(), orderSn)
                .orElseGet(TemuOrder::new);
        boolean created = entity.getId() == null;

        entity.setShopRecordId(shop.getId());
        entity.setShopId(trim(shop.getShopId()));
        entity.setShopName(trim(shop.getShopName()));
        entity.setOrderSn(orderSn);
        entity.setParentOrderSn(firstNonBlank(text(packageNode, "orderId"), text(packageNode, "originId"), text(packageNode, "id")));
        entity.setDianxiaomiPackageNumber(text(packageNode, "packageNumber"));
        entity.setGoodsId(firstNonBlank(text(productNode, "productId"), text(productNode.path("originData"), "productId"), text(productNode.path("originData"), "goodsId")));
        entity.setGoodsName(firstNonBlank(text(productNode, "productName"), text(productNode.path("originData"), "goodsName"), text(productNode, "pname")));
        entity.setSpec(firstNonBlank(
                text(productNode, "specification"),
                text(productNode.path("originData"), "spec"),
                attrValue(productNode, "Variants"),
                attrValue(productNode.path("originData"), "Variants"),
                text(productNode, "productDisplaySku")
        ));
        entity.setThumbUrl(firstNonBlank(text(productNode, "productImg"), text(productNode.path("originData"), "thumbUrl"), text(productNode, "oriProductImg")));
        entity.setQuantity(firstPositiveInt(integerValue(productNode, "quantity"), integerValue(productNode.path("originData"), "quantity"), integerValue(packageNode, "skuCount")));
        entity.setOrderStatus(firstPositiveInt(integerValue(productNode.path("originData"), "orderStatus"), integerValue(packageNode, "orderStatus")));
        entity.setParentOrderStatus(entity.getOrderStatus());
        entity.setOrderPaymentType(firstNonBlank(text(packageNode, "orderPaymentType"), text(packageNode, "paymentMethod"), text(packageNode, "payStyle")));
        entity.setInventoryDeductionWarehouseId(firstNonBlank(text(productNode.path("originData"), "inventoryDeductionWarehouseId"), text(packageNode, "destWarehouseCode")));
        entity.setInventoryDeductionWarehouseName(firstNonBlank(text(productNode.path("originData"), "inventoryDeductionWarehouseName"), text(packageNode, "destWarehouseName"), text(packageNode, "storageName")));
        entity.setOrderTimeMs(firstPositiveLong(longValue(packageNode, "orderCreateTime"), longValue(productNode.path("originData"), "createTime"), longValue(packageNode, "createTime")));
        entity.setUpdateTimeMs(firstPositiveLong(longValue(packageNode, "updateTime"), longValue(productNode.path("originData"), "updateTime"), entity.getOrderTimeMs()));
        entity.setEarliestTimeGetShippingDocumentMs(toMillis(firstPositiveLong(longValue(productNode, "earliestTimeShippingDocument"), longValue(productNode.path("originData"), "earliestTimeShippingDocument"), longValue(packageNode, "earliestTimeShippingDocument"))));
        entity.setExpectShipLatestTimeMs(toMillis(longValue(packageNode, "orderTimeoutTime")));
        entity.setRegionId(null);
        entity.setSiteId(shop.getSiteId());

        entity.setProductSkusJson(buildProductSkusJson(productNode));
        entity.setRawJson(buildRawJson(packageNode, productNode));

        TemuOrderMatchService.MatchResult matchResult = orderMatchService.applyMatch(entity);
        TemuOrder saved = orderRepository.save(entity);
        return new UpsertResult(created, matchResult.matched(), saved.getOrderSn());
    }

    private String buildProductSkusJson(JsonNode productNode) {
        ArrayNode array = objectMapper.createArrayNode();
        ObjectNode sku = objectMapper.createObjectNode();
        sku.put("productSkuId", firstNonBlank(
                text(productNode, "stockProductId"),
                text(productNode.path("originData"), "productSkuId"),
                attrValue(productNode, "SKUID"),
                attrValue(productNode.path("originData"), "SKUID"),
                text(productNode.path("originData"), "skuId"),
                text(productNode, "vid")
        ));
        sku.put("extCode", firstNonBlank(text(productNode, "productSku"), text(productNode.path("originData"), "extCode"), text(productNode, "displaySku")));
        sku.put("quantity", firstPositiveInt(integerValue(productNode, "quantity"), integerValue(productNode.path("originData"), "quantity")));
        array.add(sku);
        return array.toString();
    }

    private JsonNode resolveProductRows(JsonNode packageNode) {
        JsonNode productList = packageNode.path("productList");
        if (productList.isArray() && !productList.isEmpty()) {
            return productList;
        }
        JsonNode cancelProductList = packageNode.path("cancelProductList");
        if (cancelProductList.isArray() && !cancelProductList.isEmpty()) {
            return cancelProductList;
        }
        JsonNode otherProductList = packageNode.path("otherProductList");
        if (otherProductList.isArray() && !otherProductList.isEmpty()) {
            return otherProductList;
        }
        return objectMapper.createArrayNode();
    }

    private String buildRawJson(JsonNode packageNode, JsonNode productNode) {
        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("source", "DIANXIAOMI_PACKAGE_LIST");
        raw.set("packageNode", packageNode == null ? objectMapper.nullNode() : packageNode);
        raw.set("productNode", productNode == null ? objectMapper.nullNode() : productNode);
        return raw.toString();
    }

    private void cleanupPlaceholderRows(Long shopRecordId, String packageNumber, Set<String> productOrderSns) {
        if (shopRecordId == null || !StringUtils.hasText(packageNumber) || productOrderSns == null || productOrderSns.isEmpty()) {
            return;
        }
        List<TemuOrder> rows = orderRepository.findByShopRecordIdAndDianxiaomiPackageNumber(shopRecordId, packageNumber);
        for (TemuOrder row : rows) {
            if (row == null || productOrderSns.contains(trim(row.getOrderSn()))) {
                continue;
            }
            if (!StringUtils.hasText(row.getGoodsId()) && isEmptySkuPlaceholder(row.getProductSkusJson())) {
                orderRepository.delete(row);
            }
        }
    }

    private List<TemuShop> resolveShops(Long shopRecordId) {
        if (shopRecordId != null) {
            return List.of(shopService.getEnabledShopByIdOrThrow(shopRecordId));
        }
        return shopService.listEnabledShops().stream()
                .filter(shop -> StringUtils.hasText(shop.getDianxiaomiShopId()))
                .toList();
    }

    private TemuOrderDTO.SyncResponse failed(TemuShop shop, String message) {
        return TemuOrderDTO.SyncResponse.builder()
                .shopRecordId(shop == null ? null : shop.getId())
                .shopId(shop == null ? null : shop.getShopId())
                .shopName(shop == null ? null : shop.getShopName())
                .success(false)
                .message(message)
                .build();
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        int n = value == null ? fallback : value;
        return Math.min(Math.max(n, min), max);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull() || !StringUtils.hasText(field)) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return trim(value.asText(null));
    }

    private static Integer integerValue(JsonNode node, String field) {
        String value = text(node, field);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.replaceAll("[^0-9-]", ""));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Long longValue(JsonNode node, String field) {
        String value = text(node, field);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.replaceAll("[^0-9-]", ""));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String attrValue(JsonNode node, String attrName) {
        if (node == null || node.isMissingNode() || node.isNull() || !StringUtils.hasText(attrName)) {
            return null;
        }
        JsonNode attrList = node.path("attrList");
        if (!attrList.isArray()) {
            return null;
        }
        for (JsonNode attr : attrList) {
            String name = firstNonBlank(text(attr, "pName"), text(attr, "name"), text(attr, "attrName"));
            if (attrName.equalsIgnoreCase(trim(name))) {
                return firstNonBlank(text(attr, "pValue"), text(attr, "value"), text(attr, "attrValue"));
            }
        }
        return null;
    }

    private static boolean isEmptySkuPlaceholder(String productSkusJson) {
        String value = trim(productSkusJson);
        return !StringUtils.hasText(value) || value.contains("\"productSkuId\":null");
    }

    private static Integer firstPositiveInt(Integer... values) {
        if (values != null) {
            for (Integer value : values) {
                if (value != null && value > 0) {
                    return value;
                }
            }
        }
        return values == null || values.length == 0 ? null : values[0];
    }

    private static Long firstPositiveLong(Long... values) {
        if (values != null) {
            for (Long value : values) {
                if (value != null && value > 0) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Long toMillis(Long value) {
        if (value == null || value <= 0) {
            return null;
        }
        return value < 10_000_000_000L ? value * 1000L : value;
    }

    private static String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                String trimmed = trim(value);
                if (StringUtils.hasText(trimmed)) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record UpsertResult(boolean created, boolean matched, String orderSn) {
    }
}
