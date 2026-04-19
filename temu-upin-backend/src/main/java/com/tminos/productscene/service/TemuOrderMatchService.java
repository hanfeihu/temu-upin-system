package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.ProductCollectionTemuSku;
import com.tminos.productscene.entity.TemuOrder;
import com.tminos.productscene.repository.ProductCollectionRepository;
import com.tminos.productscene.repository.ProductCollectionTemuSkuRepository;
import com.tminos.productscene.sync.entity.TemuGoods;
import com.tminos.productscene.sync.entity.TemuGoodsSku;
import com.tminos.productscene.sync.repository.TemuGoodsRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class TemuOrderMatchService {

    private final TemuGoodsSkuRepository syncedGoodsSkuRepository;
    private final TemuGoodsRepository syncedGoodsRepository;
    private final ProductCollectionTemuSkuRepository temuSkuRepository;
    private final ProductCollectionRepository productCollectionRepository;
    private final ObjectMapper objectMapper;

    public TemuOrderMatchService(TemuGoodsSkuRepository syncedGoodsSkuRepository,
                                 TemuGoodsRepository syncedGoodsRepository,
                                 ProductCollectionTemuSkuRepository temuSkuRepository,
                                 ProductCollectionRepository productCollectionRepository,
                                 ObjectMapper objectMapper) {
        this.syncedGoodsSkuRepository = syncedGoodsSkuRepository;
        this.syncedGoodsRepository = syncedGoodsRepository;
        this.temuSkuRepository = temuSkuRepository;
        this.productCollectionRepository = productCollectionRepository;
        this.objectMapper = objectMapper;
    }

    public MatchResult applyMatch(TemuOrder order) {
        MatchResult result = match(
                order == null ? null : order.getShopId(),
                order == null ? null : order.getProductSkusJson()
        );
        if (order == null) {
            return result;
        }
        order.setMatchedSpuId(result.matchedSpuId());
        order.setMatchedTemuSkuId(result.matchedTemuSkuId());
        order.setMatchedOriginSkuId(result.matchedOriginSkuId());
        order.setMatchedProductName(result.matchedProductName());
        order.setMatchStatus(result.matchStatus());
        order.setMatchMessage(result.matchMessage());
        return result;
    }

    public MatchResult match(String shopId, String productSkusJson) {
        List<String> temuSkuIds = extractTemuSkuIds(productSkusJson);
        if (temuSkuIds.isEmpty()) {
            return MatchResult.unmatched("EMPTY", "订单返回中未找到 productSkuId");
        }

        MatchResult syncedGoodsMatch = matchFromSyncedGoods(shopId, temuSkuIds);
        if (syncedGoodsMatch != null) {
            return syncedGoodsMatch;
        }

        List<ProductCollectionTemuSku> matchedRows = temuSkuRepository.findByTemuSkuIdIn(temuSkuIds);
        if (matchedRows == null || matchedRows.isEmpty()) {
            return MatchResult.unmatched(
                    "UNMATCHED",
                    "未在 TEMU 商品数据或 product_collection_temu_sku 中找到匹配的 productSkuId: " + String.join(", ", temuSkuIds)
            );
        }

        if (matchedRows.size() == 1) {
            return buildMatchedResult(matchedRows.get(0), "MATCHED", "已按 temuSkuId 精确匹配");
        }

        Map<Long, List<ProductCollectionTemuSku>> bySpuId = new LinkedHashMap<>();
        for (ProductCollectionTemuSku row : matchedRows) {
            if (row != null && row.getSpuId() != null) {
                bySpuId.computeIfAbsent(row.getSpuId(), key -> new ArrayList<>()).add(row);
            }
        }
        if (bySpuId.size() == 1) {
            ProductCollectionTemuSku first = matchedRows.get(0);
            return buildMatchedResult(first, "MATCHED_MULTI", "命中多个 SKU 行，但都属于同一个商品 SPU");
        }

        return MatchResult.unmatched("AMBIGUOUS",
                "同一订单 SKU 命中了多个商品 SPU，需人工确认: " + joinSpuIds(bySpuId.keySet()));
    }

    private MatchResult matchFromSyncedGoods(String shopId, List<String> productSkuIds) {
        if (!StringUtils.hasText(shopId) || productSkuIds == null || productSkuIds.isEmpty()) {
            return null;
        }

        List<Long> numericSkuIds = productSkuIds.stream()
                .map(this::toLong)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (numericSkuIds.isEmpty()) {
            return null;
        }

        List<TemuGoodsSku> matchedRows = syncedGoodsSkuRepository.findByShopIdAndProductSkuIdIn(shopId.trim(), numericSkuIds);
        if (matchedRows == null || matchedRows.isEmpty()) {
            return null;
        }

        Map<Long, List<TemuGoodsSku>> byGoodsId = new LinkedHashMap<>();
        for (TemuGoodsSku row : matchedRows) {
            if (row != null && row.getGoodsId() != null) {
                byGoodsId.computeIfAbsent(row.getGoodsId(), key -> new ArrayList<>()).add(row);
            }
        }

        if (byGoodsId.size() > 1) {
            return MatchResult.unmatched(
                    "AMBIGUOUS",
                    "同一订单 SKU 在 TEMU 商品数据中命中了多个商品: " + joinLongValues(byGoodsId.keySet())
            );
        }

        TemuGoodsSku first = matchedRows.get(0);
        TemuGoods goods = null;
        if (!byGoodsId.isEmpty()) {
            Long goodsId = byGoodsId.keySet().iterator().next();
            goods = syncedGoodsRepository.findById(goodsId).orElse(null);
        } else if (first != null && first.getGoodsId() != null) {
            goods = syncedGoodsRepository.findById(first.getGoodsId()).orElse(null);
        }

        String message = matchedRows.size() == 1
                ? "已按 TEMU 商品数据 productSkuId 精确匹配"
                : "命中多个 TEMU SKU，但都属于同一个 TEMU 商品";
        return new MatchResult(
                null,
                first == null || first.getProductSkuId() == null ? null : String.valueOf(first.getProductSkuId()),
                first == null ? null : trim(first.getExtCode()),
                goods == null ? null : trim(goods.getProductName()),
                matchedRows.size() == 1 ? "MATCHED" : "MATCHED_MULTI",
                message
        );
    }

    private MatchResult buildMatchedResult(ProductCollectionTemuSku row, String status, String message) {
        Long matchedSpuId = row == null ? null : row.getSpuId();
        String productName = resolveProductName(matchedSpuId);
        return new MatchResult(
                matchedSpuId,
                row == null ? null : trim(row.getTemuSkuId()),
                row == null ? null : trim(row.getOriginSkuId()),
                productName,
                status,
                message
        );
    }

    private String resolveProductName(Long spuId) {
        if (spuId == null) {
            return null;
        }
        return productCollectionRepository.findById(spuId)
                .map(ProductCollection::getProductName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .orElse(null);
    }

    private List<String> extractTemuSkuIds(String productSkusJson) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (!StringUtils.hasText(productSkusJson)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(productSkusJson);
            if (!root.isArray()) {
                return List.of();
            }
            for (JsonNode skuNode : root) {
                JsonNode productSkuIdNode = skuNode.path("productSkuId");
                String productSkuId = trim(productSkuIdNode.isMissingNode() || productSkuIdNode.isNull() ? null : productSkuIdNode.asText());
                if (StringUtils.hasText(productSkuId)) {
                    out.add(productSkuId);
                }
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.copyOf(out);
    }

    private static String joinSpuIds(Collection<Long> spuIds) {
        List<String> values = new ArrayList<>();
        if (spuIds != null) {
            for (Long spuId : spuIds) {
                if (spuId != null) {
                    values.add(String.valueOf(spuId));
                }
            }
        }
        return values.isEmpty() ? "-" : String.join(", ", values);
    }

    private static String joinLongValues(Collection<Long> values) {
        List<String> items = new ArrayList<>();
        if (values != null) {
            for (Long value : values) {
                if (value != null) {
                    items.add(String.valueOf(value));
                }
            }
        }
        return items.isEmpty() ? "-" : String.join(", ", items);
    }

    private Long toLong(String value) {
        String normalized = trim(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    public record MatchResult(Long matchedSpuId,
                              String matchedTemuSkuId,
                              String matchedOriginSkuId,
                              String matchedProductName,
                              String matchStatus,
                              String matchMessage) {

        public static MatchResult unmatched(String status, String message) {
            return new MatchResult(null, null, null, null, status, message);
        }

        public boolean matched() {
            return StringUtils.hasText(matchStatus) && matchStatus.startsWith("MATCHED");
        }
    }
}
