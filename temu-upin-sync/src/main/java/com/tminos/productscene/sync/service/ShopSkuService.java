package com.tminos.productscene.sync.service;

import com.tminos.productscene.entity.TemuShop;
import com.tminos.productscene.repository.TemuShopRepository;
import com.tminos.productscene.sync.dto.ShopSkuDTO;
import com.tminos.productscene.sync.entity.TemuGoods;
import com.tminos.productscene.sync.entity.TemuGoodsSku;
import com.tminos.productscene.sync.entity.TemuGoodsSkuPrice;
import com.tminos.productscene.sync.entity.TemuGoodsSkuSitePrice;
import com.tminos.productscene.sync.entity.TemuGoodsSkuSpec;
import com.tminos.productscene.sync.entity.TemuShopSkuPurchasePrice;
import com.tminos.productscene.sync.repository.TemuGoodsRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuPriceRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuSitePriceRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuSpecRepository;
import com.tminos.productscene.sync.repository.TemuShopSkuPurchasePriceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ShopSkuService {

    private static final int DEFAULT_SITE_ID = 100;

    private final TemuShopRepository shopRepository;
    private final TemuGoodsRepository goodsRepository;
    private final TemuGoodsSkuRepository goodsSkuRepository;
    private final TemuGoodsSkuSpecRepository goodsSkuSpecRepository;
    private final TemuGoodsSkuPriceRepository goodsSkuPriceRepository;
    private final TemuGoodsSkuSitePriceRepository goodsSkuSitePriceRepository;
    private final TemuShopSkuPurchasePriceRepository purchasePriceRepository;

    public ShopSkuService(TemuShopRepository shopRepository,
                          TemuGoodsRepository goodsRepository,
                          TemuGoodsSkuRepository goodsSkuRepository,
                          TemuGoodsSkuSpecRepository goodsSkuSpecRepository,
                          TemuGoodsSkuPriceRepository goodsSkuPriceRepository,
                          TemuGoodsSkuSitePriceRepository goodsSkuSitePriceRepository,
                          TemuShopSkuPurchasePriceRepository purchasePriceRepository) {
        this.shopRepository = shopRepository;
        this.goodsRepository = goodsRepository;
        this.goodsSkuRepository = goodsSkuRepository;
        this.goodsSkuSpecRepository = goodsSkuSpecRepository;
        this.goodsSkuPriceRepository = goodsSkuPriceRepository;
        this.goodsSkuSitePriceRepository = goodsSkuSitePriceRepository;
        this.purchasePriceRepository = purchasePriceRepository;
    }

    public Page<ShopSkuDTO.ShopSkuItem> list(String shopId,
                                             Long productSkcId,
                                             Long productSkuId,
                                             String skuExtCode,
                                             int page,
                                             int pageSize) {
        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), Math.max(pageSize, 1));
        Page<TemuGoodsSku> skuPage = goodsSkuRepository.searchAddedSiteSkus(
                requireShopId(shopId),
                productSkcId,
                productSkuId,
                buildPattern(skuExtCode),
                pageRequest);

        List<ShopSkuDTO.ShopSkuItem> items = buildItems(shopId, skuPage.getContent());
        return new PageImpl<>(items, pageRequest, skuPage.getTotalElements());
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

    private String buildPattern(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
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
}
