package com.tminos.productscene.sync.service;

import com.tminos.productscene.service.TemuOpenApiCredentialService;
import com.tminos.productscene.entity.TemuShop;
import com.tminos.productscene.repository.TemuShopRepository;
import com.tminos.productscene.sync.dto.ShopSkuDTO;
import com.tminos.productscene.sync.entity.TemuGoods;
import com.tminos.productscene.sync.entity.TemuGoodsSku;
import com.tminos.productscene.sync.entity.TemuGoodsSkuPrice;
import com.tminos.productscene.sync.entity.TemuGoodsSkuSitePrice;
import com.tminos.productscene.sync.entity.TemuGoodsSkuSpec;
import com.tminos.productscene.sync.entity.TemuWarehouse;
import com.tminos.productscene.sync.entity.TemuShopSkuPurchasePrice;
import com.tminos.productscene.sync.repository.TemuGoodsRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuPriceRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuSitePriceRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuSpecRepository;
import com.tminos.productscene.sync.repository.TemuWarehouseRepository;
import com.tminos.productscene.sync.repository.TemuShopSkuPurchasePriceRepository;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ShopSkuService {

    private static final Logger log = LoggerFactory.getLogger(ShopSkuService.class);
    private static final int DEFAULT_SITE_ID = 100;

    private final TemuShopRepository shopRepository;
    private final TemuGoodsRepository goodsRepository;
    private final TemuGoodsSkuRepository goodsSkuRepository;
    private final TemuGoodsSkuSpecRepository goodsSkuSpecRepository;
    private final TemuGoodsSkuPriceRepository goodsSkuPriceRepository;
    private final TemuGoodsSkuSitePriceRepository goodsSkuSitePriceRepository;
    private final TemuShopSkuPurchasePriceRepository purchasePriceRepository;
    private final TemuWarehouseRepository warehouseRepository;
    private final TemuOpenApiCredentialService credentialService;
    private final TemuPriceSyncTransactionalService priceSyncTransactionalService;

    public ShopSkuService(TemuShopRepository shopRepository,
                          TemuGoodsRepository goodsRepository,
                          TemuGoodsSkuRepository goodsSkuRepository,
                          TemuGoodsSkuSpecRepository goodsSkuSpecRepository,
                          TemuGoodsSkuPriceRepository goodsSkuPriceRepository,
                          TemuGoodsSkuSitePriceRepository goodsSkuSitePriceRepository,
                          TemuShopSkuPurchasePriceRepository purchasePriceRepository,
                          TemuWarehouseRepository warehouseRepository,
                          TemuOpenApiCredentialService credentialService,
                          TemuPriceSyncTransactionalService priceSyncTransactionalService) {
        this.shopRepository = shopRepository;
        this.goodsRepository = goodsRepository;
        this.goodsSkuRepository = goodsSkuRepository;
        this.goodsSkuSpecRepository = goodsSkuSpecRepository;
        this.goodsSkuPriceRepository = goodsSkuPriceRepository;
        this.goodsSkuSitePriceRepository = goodsSkuSitePriceRepository;
        this.purchasePriceRepository = purchasePriceRepository;
        this.warehouseRepository = warehouseRepository;
        this.credentialService = credentialService;
        this.priceSyncTransactionalService = priceSyncTransactionalService;
    }

    public Page<ShopSkuDTO.ShopSkuItem> list(String shopId,
                                             Long productSkcId,
                                             Long productSkuId,
                                             String skuExtCode,
                                             Boolean virtualStockGtZero,
                                             Integer minSupplierPrice,
                                             Integer maxSupplierPrice,
                                             int page,
                                             int pageSize) {
        validateSupplierPriceRange(minSupplierPrice, maxSupplierPrice);
        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), Math.max(pageSize, 1));
        Page<TemuGoodsSku> skuPage = goodsSkuRepository.searchAddedSiteSkus(
                requireShopId(shopId),
                productSkcId,
                productSkuId,
                buildPattern(skuExtCode),
                Boolean.TRUE.equals(virtualStockGtZero),
                minSupplierPrice,
                maxSupplierPrice,
                pageRequest);

        List<ShopSkuDTO.ShopSkuItem> items = buildItems(shopId, skuPage.getContent());
        return new PageImpl<>(items, pageRequest, skuPage.getTotalElements());
    }

    public List<ShopSkuDTO.WarehouseOption> listWarehouses(String shopId) {
        TemuShop shop = shopRepository.findByShopId(requireShopId(shopId))
                .orElseThrow(() -> new IllegalArgumentException("店铺不存在: " + shopId));
        String defaultWarehouseId = trimToNull(shop.getWarehouseId());

        return listShopWarehouses(shop).stream()
                .filter(Objects::nonNull)
                .filter(warehouse -> trimToNull(warehouse.getWarehouseId()) != null)
                .filter(warehouse -> !Boolean.TRUE.equals(warehouse.getWarehouseDisable()))
                .sorted(
                        Comparator.comparing((TemuWarehouse warehouse) ->
                                        !Objects.equals(trimToNull(warehouse.getWarehouseId()), defaultWarehouseId))
                                .thenComparing(
                                        warehouse -> {
                                            String warehouseName = trimToNull(warehouse.getWarehouseName());
                                            return warehouseName == null ? trimToNull(warehouse.getWarehouseId()) : warehouseName;
                                        },
                                        Comparator.nullsLast(String::compareTo)
                                )
                )
                .map(warehouse -> {
                    ShopSkuDTO.WarehouseOption item = new ShopSkuDTO.WarehouseOption();
                    item.setSiteId(warehouse.getSiteId());
                    item.setSiteName(warehouse.getSiteName());
                    item.setWarehouseId(warehouse.getWarehouseId());
                    item.setWarehouseName(warehouse.getWarehouseName());
                    item.setManagementType(warehouse.getManagementType());
                    item.setDefaultWarehouse(Objects.equals(trimToNull(warehouse.getWarehouseId()), defaultWarehouseId));
                    return item;
                })
                .toList();
    }

    @Transactional
    public ShopSkuDTO.ShopSkuItem updatePurchasePrice(Long productSkuId, ShopSkuDTO.PurchasePriceUpdateRequest request) {
        String shopId = requireShopId(request == null ? null : request.getShopId());
        Integer purchasePrice = request == null ? null : request.getPurchasePrice();
        if (purchasePrice != null && purchasePrice < 0) {
            throw new IllegalArgumentException("采购价不能小于 0");
        }

        TemuGoodsSku sku = goodsSkuRepository.findByShopIdAndProductSkuId(shopId, productSkuId)
                .orElseThrow(() -> new IllegalArgumentException("SKU 不存在: " + productSkuId));
        TemuGoods goods = goodsRepository.findById(sku.getGoodsId())
                .orElseThrow(() -> new IllegalArgumentException("SKU 对应商品不存在: " + productSkuId));
        if (!Objects.equals(1, normalizeAddedSiteStatus(goods.getSkcSiteStatus()))) {
            throw new IllegalArgumentException("仅允许维护已加站 SKU 的采购价");
        }

        Optional<TemuShopSkuPurchasePrice> existing = purchasePriceRepository.findByShopIdAndProductSkuId(shopId, productSkuId);
        if (purchasePrice == null) {
            existing.ifPresent(purchasePriceRepository::delete);
        } else {
            TemuShopSkuPurchasePrice entity = existing.orElseGet(() -> TemuShopSkuPurchasePrice.builder()
                    .shopId(shopId)
                    .productSkuId(productSkuId)
                    .build());
            entity.setProductSkcId(goods.getProductSkcId());
            entity.setSkuExtCode(sku.getExtCode());
            entity.setPurchasePrice(purchasePrice);
            purchasePriceRepository.save(entity);
        }

        return buildItems(shopId, List.of(sku)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("采购价更新后未找到 SKU: " + productSkuId));
    }

    @Transactional
    public ShopSkuDTO.ShopSkuItem refreshSupplierPrice(Long productSkuId, ShopSkuDTO.SupplierPriceRefreshRequest request) {
        String shopId = requireShopId(request == null ? null : request.getShopId());
        TemuGoodsSku sku = goodsSkuRepository.findByShopIdAndProductSkuId(shopId, productSkuId)
                .orElseThrow(() -> new IllegalArgumentException("SKU 不存在: " + productSkuId));

        TemuOpenApiClient.ApiResult result;
        try {
            TemuOpenApiClient client = new TemuOpenApiClient(credentialService.getTemuOpenApiCredentialsByShopIdOrThrow(shopId));
            Map<String, Object> params = Map.of("productSkuIds", List.of(productSkuId));
            result = client.callApiParsed(TemuOpenApiClient.API_GOODS_PRICE_LIST, params);
        } catch (Exception ex) {
            throw new IllegalStateException("查询供货价失败: " + ex.getMessage(), ex);
        }
        if (!result.success) {
            throw new IllegalStateException("查询供货价失败: " + trimToNull(result.errorMsg));
        }

        List<Map<String, Object>> rows = extractPriceRows(result.resultAsMap()).stream()
                .filter(row -> Objects.equals(toLong(row.get("productSkuId")), productSkuId))
                .toList();
        if (rows.isEmpty()) {
            throw new IllegalStateException("未查询到该 SKU 的供货价: " + productSkuId);
        }

        priceSyncTransactionalService.persistShopBatch(
                shopId,
                rows,
                this::toLong,
                this::toInt,
                this::toStr,
                this::extractSiteSupplierPrices
        );

        return buildItems(shopId, List.of(sku)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("供货价刷新后未找到 SKU: " + productSkuId));
    }

    public ShopSkuDTO.BatchZeroVirtualStockResult batchZeroVirtualStock(ShopSkuDTO.BatchZeroVirtualStockRequest request) {
        String shopId = requireShopId(request == null ? null : request.getShopId());
        Long productSkcId = request == null ? null : request.getProductSkcId();
        Long productSkuId = request == null ? null : request.getProductSkuId();
        String skuExtCode = request == null ? null : request.getSkuExtCode();
        Boolean virtualStockGtZero = request == null ? null : request.getVirtualStockGtZero();
        Integer minSupplierPrice = request == null ? null : request.getMinSupplierPrice();
        Integer maxSupplierPrice = request == null ? null : request.getMaxSupplierPrice();
        String warehouseId = request == null ? null : request.getWarehouseId();
        validateSupplierPriceRange(minSupplierPrice, maxSupplierPrice);
        if (productSkcId == null
                && productSkuId == null
                && trimToNull(skuExtCode) == null
                && !Boolean.TRUE.equals(virtualStockGtZero)
                && minSupplierPrice == null
                && maxSupplierPrice == null) {
            throw new IllegalArgumentException("请至少填写一个筛选条件后再批量置0库存");
        }

        List<TemuGoodsSku> matchedSkus = goodsSkuRepository.findAddedSiteSkusByFilters(
                shopId,
                productSkcId,
                productSkuId,
                buildPattern(skuExtCode),
                Boolean.TRUE.equals(virtualStockGtZero),
                minSupplierPrice,
                maxSupplierPrice
        );

        ShopSkuDTO.BatchZeroVirtualStockResult result = new ShopSkuDTO.BatchZeroVirtualStockResult();
        result.setMatchedCount(matchedSkus.size());
        result.setMessages(new java.util.ArrayList<>());

        if (matchedSkus.isEmpty()) {
            result.getMessages().add("当前筛选条件下没有可处理的 SKU");
            return result;
        }

        Map<Long, TemuGoods> goodsById = goodsRepository.findAllById(
                        matchedSkus.stream()
                                .map(TemuGoodsSku::getGoodsId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(TemuGoods::getId, goods -> goods, (left, right) -> left, LinkedHashMap::new));

        Map<Long, TemuGoodsSku> skuByProductSkuId = matchedSkus.stream()
                .filter(item -> item.getProductSkuId() != null)
                .collect(Collectors.toMap(TemuGoodsSku::getProductSkuId, item -> item, (left, right) -> left, LinkedHashMap::new));

        Map<Long, List<SkuStockChange>> pendingChangesBySkcId = new LinkedHashMap<>();
        int alreadyZeroCount = 0;
        for (TemuGoodsSku sku : matchedSkus) {
            if (sku == null || sku.getProductSkuId() == null) {
                continue;
            }
            TemuGoods goods = goodsById.get(sku.getGoodsId());
            if (goods == null || goods.getProductSkcId() == null) {
                result.getMessages().add("SKUID " + sku.getProductSkuId() + " 缺少 productSkcId，已跳过");
                continue;
            }
            int currentVirtualStock = Optional.ofNullable(sku.getVirtualStock()).orElse(0);
            if (currentVirtualStock <= 0) {
                alreadyZeroCount++;
                continue;
            }
            pendingChangesBySkcId.computeIfAbsent(goods.getProductSkcId(), key -> new java.util.ArrayList<>())
                    .add(new SkuStockChange(goods.getProductSkcId(), sku.getProductSkuId(), currentVirtualStock));
        }

        result.setAlreadyZeroCount(alreadyZeroCount);

        if (pendingChangesBySkcId.isEmpty()) {
            result.getMessages().add("符合条件的 SKU 库存已经全部为 0");
            return result;
        }

        TemuOpenApiClient client;
        try {
            client = new TemuOpenApiClient(credentialService.getTemuOpenApiCredentialsByShopIdOrThrow(shopId));
        } catch (Exception ex) {
            throw new IllegalStateException("获取店铺凭证失败: " + ex.getMessage(), ex);
        }

        int updatedCount = 0;
        int failedCount = 0;
        for (Map.Entry<Long, List<SkuStockChange>> entry : pendingChangesBySkcId.entrySet()) {
            BatchApplyResult applyResult = applyBatchZeroForSkc(client, shopId, warehouseId, entry.getKey(), entry.getValue());
            updatedCount += applyResult.updatedSkuIds().size();
            failedCount += applyResult.failedMessages().size();
            if (!applyResult.updatedSkuIds().isEmpty()) {
                List<TemuGoodsSku> updatedRows = applyResult.updatedSkuIds().stream()
                        .map(skuByProductSkuId::get)
                        .filter(Objects::nonNull)
                        .peek(item -> item.setVirtualStock(0))
                        .toList();
                if (!updatedRows.isEmpty()) {
                    goodsSkuRepository.saveAll(updatedRows);
                }
            }
            result.getMessages().addAll(applyResult.messages());
            result.getMessages().addAll(applyResult.failedMessages());
        }

        result.setUpdatedCount(updatedCount);
        result.setFailedCount(failedCount);
        if (result.getMessages().isEmpty()) {
            result.getMessages().add("批量置0完成");
        }
        return result;
    }

    private List<ShopSkuDTO.ShopSkuItem> buildItems(String shopId, List<TemuGoodsSku> skus) {
        if (skus == null || skus.isEmpty()) {
            return List.of();
        }

        Map<Long, TemuGoods> goodsById = goodsRepository.findAllById(
                        skus.stream()
                                .map(TemuGoodsSku::getGoodsId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(TemuGoods::getId, goods -> goods, (left, right) -> left, LinkedHashMap::new));

        List<Long> skuIds = skus.stream()
                .map(TemuGoodsSku::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, List<TemuGoodsSkuSpec>> specsBySkuId = goodsSkuSpecRepository.findBySkuIdIn(skuIds).stream()
                .filter(spec -> spec.getSkuId() != null)
                .collect(Collectors.groupingBy(TemuGoodsSkuSpec::getSkuId, LinkedHashMap::new, Collectors.toList()));

        List<Long> productSkuIds = skus.stream()
                .map(TemuGoodsSku::getProductSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, TemuGoodsSkuPrice> priceByProductSkuId = goodsSkuPriceRepository.findByShopIdAndProductSkuIdIn(requireShopId(shopId), productSkuIds).stream()
                .filter(price -> price.getProductSkuId() != null)
                .collect(Collectors.toMap(TemuGoodsSkuPrice::getProductSkuId, price -> price, (left, right) -> left, LinkedHashMap::new));

        List<Long> skuPriceIds = priceByProductSkuId.values().stream()
                .map(TemuGoodsSkuPrice::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Map<Integer, TemuGoodsSkuSitePrice>> sitePriceByPriceId = goodsSkuSitePriceRepository.findBySkuPriceIdIn(skuPriceIds).stream()
                .filter(sitePrice -> sitePrice.getSkuPriceId() != null && sitePrice.getSiteId() != null)
                .collect(Collectors.groupingBy(
                        TemuGoodsSkuSitePrice::getSkuPriceId,
                        LinkedHashMap::new,
                        Collectors.toMap(TemuGoodsSkuSitePrice::getSiteId, sitePrice -> sitePrice, (left, right) -> left, LinkedHashMap::new)));

        Map<Long, TemuShopSkuPurchasePrice> purchasePriceByProductSkuId = purchasePriceRepository.findByShopIdAndProductSkuIdIn(requireShopId(shopId), productSkuIds).stream()
                .filter(item -> item.getProductSkuId() != null)
                .collect(Collectors.toMap(TemuShopSkuPurchasePrice::getProductSkuId, item -> item, (left, right) -> left, LinkedHashMap::new));

        int siteId = resolveSiteId(shopId);

        return skus.stream().map(sku -> {
            TemuGoods goods = goodsById.get(sku.getGoodsId());
            TemuGoodsSkuPrice price = priceByProductSkuId.get(sku.getProductSkuId());
            TemuShopSkuPurchasePrice purchasePrice = purchasePriceByProductSkuId.get(sku.getProductSkuId());

            ShopSkuDTO.ShopSkuItem item = new ShopSkuDTO.ShopSkuItem();
            item.setId(sku.getId());
            item.setShopId(sku.getShopId());
            item.setProductId(goods == null ? null : goods.getProductId());
            item.setProductName(goods == null ? null : goods.getProductName());
            item.setProductSkcId(goods == null ? null : goods.getProductSkcId());
            item.setProductSkuId(sku.getProductSkuId());
            item.setSkuExtCode(sku.getExtCode());
            item.setSkuSpecName(buildSpecName(specsBySkuId.get(sku.getId())));
            item.setMainImageUrl(goods == null ? null : goods.getMainImageUrl());
            item.setVirtualStock(sku.getVirtualStock());
            item.setPurchasePrice(purchasePrice == null ? null : purchasePrice.getPurchasePrice());
            item.setReferenceSupplierPrice(resolveReferenceSupplierPrice(siteId, price, sitePriceByPriceId));
            item.setUsSiteSupplierPrice(resolveReferenceSupplierPrice(DEFAULT_SITE_ID, price, sitePriceByPriceId));
            return item;
        }).toList();
    }

    private Integer resolveReferenceSupplierPrice(int siteId,
                                                  TemuGoodsSkuPrice price,
                                                  Map<Long, Map<Integer, TemuGoodsSkuSitePrice>> sitePriceByPriceId) {
        if (price == null) {
            return null;
        }
        if (price.getId() != null) {
            TemuGoodsSkuSitePrice sitePrice = Optional.ofNullable(sitePriceByPriceId.get(price.getId()))
                    .map(sitePriceMap -> sitePriceMap.get(siteId))
                    .orElse(null);
            if (sitePrice != null && sitePrice.getSupplierPrice() != null) {
                return sitePrice.getSupplierPrice();
            }
        }
        return price.getSupplierPrice();
    }

    private String buildSpecName(List<TemuGoodsSkuSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return "-";
        }
        return specs.stream()
                .sorted(Comparator.comparing(TemuGoodsSkuSpec::getId, Comparator.nullsLast(Long::compareTo)))
                .map(spec -> {
                    String parentName = trimToNull(spec.getParentSpecName());
                    String specName = trimToNull(spec.getSpecName());
                    if (parentName != null && specName != null) {
                        return parentName + ": " + specName;
                    }
                    return parentName != null ? parentName : (specName != null ? specName : "-");
                })
                .collect(Collectors.joining(" / "));
    }

    private int resolveSiteId(String shopId) {
        return shopRepository.findByShopId(requireShopId(shopId))
                .map(TemuShop::getSiteId)
                .filter(Objects::nonNull)
                .orElse(DEFAULT_SITE_ID);
    }

    private Integer normalizeAddedSiteStatus(Integer skcSiteStatus) {
        return skcSiteStatus == null ? 0 : skcSiteStatus;
    }

    private void validateSupplierPriceRange(Integer minSupplierPrice, Integer maxSupplierPrice) {
        if (minSupplierPrice != null && minSupplierPrice < 0) {
            throw new IllegalArgumentException("最低供货价不能小于 0");
        }
        if (maxSupplierPrice != null && maxSupplierPrice < 0) {
            throw new IllegalArgumentException("最高供货价不能小于 0");
        }
        if (minSupplierPrice != null && maxSupplierPrice != null && minSupplierPrice > maxSupplierPrice) {
            throw new IllegalArgumentException("最低供货价不能大于最高供货价");
        }
    }

    private String buildPattern(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
    }

    private List<Map<String, Object>> extractPriceRows(Map<String, Object> resultMap) {
        if (resultMap == null || resultMap.isEmpty()) {
            return List.of();
        }
        for (String key : List.of("productSkuSupplierPriceList", "goodsSkuPriceList", "priceList")) {
            List<Map<String, Object>> rows = toMapList(resultMap.get(key));
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        return List.of();
    }

    private List<Map<String, Object>> extractSiteSupplierPrices(Object rawSitePrices) {
        return toMapList(rawSitePrices);
    }

    private List<Map<String, Object>> toMapList(Object rawValue) {
        if (!(rawValue instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        return rawList.stream()
                .map(this::toMap)
                .filter(Objects::nonNull)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        return value instanceof Map<?, ?> rawMap ? (Map<String, Object>) rawMap : null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = trimToNull(String.valueOf(value));
        if (text == null) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = trimToNull(String.valueOf(value));
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String toStr(Object value) {
        return value == null ? null : trimToNull(String.valueOf(value));
    }

    private BatchApplyResult applyBatchZeroForSkc(TemuOpenApiClient client,
                                                  String shopId,
                                                  String warehouseId,
                                                  Long productSkcId,
                                                  List<SkuStockChange> changes) {
        if (productSkcId == null || changes == null || changes.isEmpty()) {
            return new BatchApplyResult(List.of(), List.of(), List.of());
        }
        ApiCallOutcome batchOutcome = callVirtualInventoryEdit(client, shopId, warehouseId, productSkcId, changes);
        if (batchOutcome.success()) {
            List<Long> updatedSkuIds = changes.stream().map(SkuStockChange::productSkuId).toList();
            return new BatchApplyResult(
                    updatedSkuIds,
                    List.of("SKC " + productSkcId + " 已批量置0 " + updatedSkuIds.size() + " 个 SKU"),
                    List.of()
            );
        }
        if (changes.size() == 1) {
            SkuStockChange change = changes.get(0);
            return new BatchApplyResult(
                    List.of(),
                    List.of(),
                    List.of("SKUID " + change.productSkuId() + " 置0失败: " + batchOutcome.message())
            );
        }

        List<Long> updatedSkuIds = new java.util.ArrayList<>();
        List<String> messages = new java.util.ArrayList<>();
        List<String> failedMessages = new java.util.ArrayList<>();
        messages.add("SKC " + productSkcId + " 批量置0失败，已自动拆分单 SKU 重试: " + batchOutcome.message());
        for (SkuStockChange change : changes) {
            ApiCallOutcome singleOutcome = callVirtualInventoryEdit(client, shopId, warehouseId, productSkcId, List.of(change));
            if (singleOutcome.success()) {
                updatedSkuIds.add(change.productSkuId());
                messages.add("SKUID " + change.productSkuId() + " 已置0");
            } else {
                failedMessages.add("SKUID " + change.productSkuId() + " 置0失败: " + singleOutcome.message());
            }
        }
        return new BatchApplyResult(updatedSkuIds, messages, failedMessages);
    }

    private ApiCallOutcome callVirtualInventoryEdit(TemuOpenApiClient client,
                                                    String shopId,
                                                    String warehouseId,
                                                    Long productSkcId,
                                                    List<SkuStockChange> changes) {
        List<Map<String, Object>> skuChangeList = changes.stream()
                .map(change -> Map.<String, Object>of(
                        "productSkuId", change.productSkuId(),
                        "virtualStockDiff", -change.currentVirtualStock()
                ))
                .toList();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("productSkcId", productSkcId);
        params.put("skuVirtualStockChangeList", skuChangeList);

        ApiCallOutcome jitOutcome = tryInventoryCandidates(
                client,
                productSkcId,
                changes,
                params,
                List.of(
                new ApiCandidate("JIT兼容路由", TemuOpenApiClient.API_VIRTUAL_INVENTORY_JIT_EDIT, TemuOpenApiEndpoints.API_BASE_URL_PA, false),
                new ApiCandidate("JIT兼容路由(mall_id)", TemuOpenApiClient.API_VIRTUAL_INVENTORY_JIT_EDIT, TemuOpenApiEndpoints.API_BASE_URL_PA, true),
                new ApiCandidate("JIT伙伴路由", TemuOpenApiClient.API_VIRTUAL_INVENTORY_JIT_EDIT, TemuOpenApiEndpoints.API_BASE_URL, false),
                new ApiCandidate("JIT伙伴路由(mall_id)", TemuOpenApiClient.API_VIRTUAL_INVENTORY_JIT_EDIT, TemuOpenApiEndpoints.API_BASE_URL, true)
                )
        );
        if (jitOutcome.success()) {
            return jitOutcome;
        }

        if (!shouldTryQtgFallback(jitOutcome.message())) {
            return jitOutcome;
        }

        ApiCallOutcome qtgOutcome = tryInventoryCandidates(
                client,
                productSkcId,
                changes,
                params,
                List.of(
                        new ApiCandidate("商品发布路由(mall_id)", TemuOpenApiClient.API_VIRTUAL_INVENTORY_QTG_EDIT, TemuOpenApiEndpoints.API_BASE_URL, true)
                )
        );
        if (qtgOutcome.success()) {
            return qtgOutcome;
        }

        ApiCallOutcome btgOutcome = null;
        if (shouldTryBtgFallback(qtgOutcome.message())) {
            try {
                btgOutcome = tryInventoryCandidates(
                client,
                productSkcId,
                changes,
                buildSemiStockQuantityParams(shopId, warehouseId, changes),
                List.of(
                        new ApiCandidate("半托管库存伙伴路由", TemuOpenApiClient.API_SEMI_STOCK_QUANTITY_UPDATE, TemuOpenApiEndpoints.API_BASE_URL, false)
                )
        );
            } catch (Exception ex) {
                btgOutcome = new ApiCallOutcome(false, "半托管库存接口准备失败: " + ex.getMessage());
            }
            if (btgOutcome.success()) {
                return btgOutcome;
            }
            return btgOutcome.message() == null ? (qtgOutcome.message() == null ? jitOutcome : qtgOutcome) : btgOutcome;
        }
        return qtgOutcome.message() == null ? jitOutcome : qtgOutcome;
    }

    private Map<String, Object> buildSemiStockQuantityParams(String shopId,
                                                             String requestedWarehouseId,
                                                             List<SkuStockChange> changes) {
        TemuShop shop = shopRepository.findByShopId(requireShopId(shopId))
                .orElseThrow(() -> new IllegalArgumentException("店铺不存在: " + shopId));
        String warehouseId = resolveSemiStockWarehouseId(shop, requestedWarehouseId);

        long supplierId;
        try {
            supplierId = Long.parseLong(requireShopId(shop.getShopId()));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("店铺ID不是有效数字，无法作为 supplierId 调用半托管库存接口: " + shop.getShopId(), ex);
        }

        Map<String, Object> openApiUser = new LinkedHashMap<>();
        openApiUser.put("supplierId", supplierId);

        List<Map<String, Object>> skuStockChangeList = changes.stream()
                .map(change -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productSkuId", change.productSkuId());
                    item.put("warehouseId", warehouseId);
                    item.put("targetStockAvailable", 0);
                    return item;
                })
                .toList();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("openApiUser", openApiUser);
        params.put("quantityChangeMode", 2);
        params.put("skuStockChangeList", skuStockChangeList);
        return params;
    }

    private String resolveSemiStockWarehouseId(TemuShop shop, String requestedWarehouseId) {
        String warehouseId = trimToNull(requestedWarehouseId);
        if (warehouseId == null) {
            warehouseId = trimToNull(shop.getWarehouseId());
        }
        if (warehouseId == null) {
            throw new IllegalArgumentException("请选择仓库后再批量置0库存");
        }
        String selectedWarehouseId = warehouseId;

        TemuWarehouse warehouse = listShopWarehouses(shop).stream()
                .filter(Objects::nonNull)
                .filter(item -> Objects.equals(trimToNull(item.getWarehouseId()), selectedWarehouseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("所选仓库不属于当前店铺或当前站点: " + selectedWarehouseId));
        if (Boolean.TRUE.equals(warehouse.getWarehouseDisable())) {
            throw new IllegalArgumentException("所选仓库已停用: " + selectedWarehouseId);
        }
        return selectedWarehouseId;
    }

    private List<TemuWarehouse> listShopWarehouses(TemuShop shop) {
        String shopId = requireShopId(shop == null ? null : shop.getShopId());
        Integer siteId = shop == null ? null : shop.getSiteId();
        if (siteId != null) {
            List<TemuWarehouse> siteWarehouses = warehouseRepository.findByShopIdAndSiteId(shopId, siteId);
            if (!siteWarehouses.isEmpty()) {
                return siteWarehouses;
            }
        }
        return warehouseRepository.findByShopId(shopId);
    }

    private ApiCallOutcome tryInventoryCandidates(TemuOpenApiClient client,
                                                  Long productSkcId,
                                                  List<SkuStockChange> changes,
                                                  Map<String, Object> params,
                                                  List<ApiCandidate> candidates) {
        Exception firstError = null;
        Exception lastError = null;
        for (ApiCandidate candidate : candidates) {
            try {
                log.info("Shop SKU zero stock trying candidate={}, apiType={}, includeMallId={}, productSkcId={}, skuCount={}",
                        candidate.candidateName(), candidate.apiType(), candidate.includeMallId(), productSkcId, changes.size());
                TemuOpenApiClient.ApiResult result = client.callApiParsed(
                        candidate.apiType(),
                        params,
                        candidate.routerUrl(),
                        candidate.includeMallId()
                );
                if (result.success) {
                    log.info("Shop SKU zero stock succeeded with candidate={}, apiType={}, includeMallId={}, productSkcId={}, skuCount={}",
                            candidate.candidateName(), candidate.apiType(), candidate.includeMallId(), productSkcId, changes.size());
                    return new ApiCallOutcome(true, "Success");
                }

                String detail = trimToNull(result.errorMsg) == null ? "调用失败" : trimToNull(result.errorMsg);
                IllegalStateException routeError = new IllegalStateException(candidate.candidateName() + "调用失败: " + detail);
                lastError = routeError;
                log.warn("Shop SKU zero stock failed with candidate={}, apiType={}, includeMallId={}, productSkcId={}, skuCount={}, detail={}",
                        candidate.candidateName(), candidate.apiType(), candidate.includeMallId(), productSkcId, changes.size(), detail);
                if (!shouldContinueInventoryFallback(routeError)) {
                    return new ApiCallOutcome(false, routeError.getMessage());
                }
                if (firstError == null) {
                    firstError = routeError;
                }
            } catch (Exception ex) {
                IllegalStateException routeError = new IllegalStateException(
                        candidate.candidateName() + "调用异常: " + ex.getMessage(),
                        ex
                );
                lastError = routeError;
                log.warn("Shop SKU zero stock exception with candidate={}, apiType={}, includeMallId={}, productSkcId={}, skuCount={}, detail={}",
                        candidate.candidateName(), candidate.apiType(), candidate.includeMallId(), productSkcId, changes.size(), ex.getMessage());
                if (!shouldContinueInventoryFallback(routeError)) {
                    return new ApiCallOutcome(false, routeError.getMessage());
                }
                if (firstError == null) {
                    firstError = routeError;
                }
            }
        }
        if (lastError != null) {
            return new ApiCallOutcome(false, lastError.getMessage());
        }
        if (firstError != null) {
            return new ApiCallOutcome(false, firstError.getMessage());
        }
        return new ApiCallOutcome(false, "调用失败");
    }

    private boolean shouldTryQtgFallback(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("type not exists")
                || normalized.contains("access_token not exists");
    }

    private boolean isNonQtgShopError(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("店铺类型非全托管");
    }

    private boolean shouldTryBtgFallback(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("店铺类型非全托管")
                || normalized.contains("type not exists")
                || normalized.contains("access_token not exists");
    }

    private boolean shouldContinueInventoryFallback(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("type not exists")
                        || normalized.contains("access_token not exists")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String requireShopId(String shopId) {
        String trimmed = trimToNull(shopId);
        if (trimmed == null) {
            throw new IllegalArgumentException("店铺不能为空");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record SkuStockChange(Long productSkcId, Long productSkuId, int currentVirtualStock) {
    }

    private record ApiCallOutcome(boolean success, String message) {
    }

    private record BatchApplyResult(List<Long> updatedSkuIds, List<String> messages, List<String> failedMessages) {
    }

    private record ApiCandidate(String candidateName, String apiType, String routerUrl, boolean includeMallId) {
    }
}
