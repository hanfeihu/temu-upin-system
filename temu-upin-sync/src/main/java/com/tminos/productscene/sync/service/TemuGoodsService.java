package com.tminos.productscene.sync.service;

import com.tminos.productscene.sync.dto.TemuGoodsDTO;
import com.tminos.productscene.sync.entity.*;
import com.tminos.productscene.sync.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TemuGoodsService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final DateTimeFormatter EXPORT_FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter EXPORT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Integer EXPORT_SITE_ID_US = 100;
    private static final int EXPORT_PAGE_SIZE = 200;
        private static final Comparator<ExportSkuRow> EXPORT_ROW_COMPARATOR = Comparator
            .comparing(ExportSkuRow::sortSupplierPrice, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(ExportSkuRow::sortStock, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(ExportSkuRow::goodsId, Comparator.nullsLast(Long::compareTo))
            .thenComparing(ExportSkuRow::productSkuId, Comparator.nullsLast(Long::compareTo));

    private final TemuGoodsRepository goodsRepository;
    private final TemuGoodsPropertyRepository propertyRepository;
    private final TemuGoodsSiteRepository siteRepository;
    private final TemuGoodsSkuRepository skuRepository;
    private final TemuGoodsSkuSpecRepository skuSpecRepository;
    private final TemuGoodsSkuPriceRepository skuPriceRepository;
    private final TemuGoodsSkuPriceChangeRepository skuPriceChangeRepository;
    private final TemuGoodsSkuSitePriceRepository skuSitePriceRepository;
    private final TemuActivityBlacklistRepository activityBlacklistRepository;
    @SuppressWarnings("unused")
    private final TemuGoodsDecorationRepository decorationRepository;
    @SuppressWarnings("unused")
    private final TemuFreightTemplateRepository freightTemplateRepository;
    @SuppressWarnings("unused")
    private final TemuWarehouseRepository warehouseRepository;
    public TemuGoodsService(TemuGoodsRepository goodsRepository,
                            TemuGoodsPropertyRepository propertyRepository,
                            TemuGoodsSiteRepository siteRepository,
                            TemuGoodsSkuRepository skuRepository,
                             TemuGoodsSkuSpecRepository skuSpecRepository,
                             TemuGoodsSkuPriceRepository skuPriceRepository,
                             TemuGoodsSkuPriceChangeRepository skuPriceChangeRepository,
                             TemuGoodsSkuSitePriceRepository skuSitePriceRepository,
                             TemuActivityBlacklistRepository activityBlacklistRepository,
                             TemuGoodsDecorationRepository decorationRepository,
                             TemuFreightTemplateRepository freightTemplateRepository,
                             TemuWarehouseRepository warehouseRepository) {
        this.goodsRepository = goodsRepository;
        this.propertyRepository = propertyRepository;
        this.siteRepository = siteRepository;
        this.skuRepository = skuRepository;
        this.skuSpecRepository = skuSpecRepository;
        this.skuPriceRepository = skuPriceRepository;
        this.skuPriceChangeRepository = skuPriceChangeRepository;
        this.skuSitePriceRepository = skuSitePriceRepository;
        this.activityBlacklistRepository = activityBlacklistRepository;
        this.decorationRepository = decorationRepository;
        this.freightTemplateRepository = freightTemplateRepository;
        this.warehouseRepository = warehouseRepository;
    }

    // ==================== 查询方法 ====================

    public Page<TemuGoods> listGoods(String shopId, String keyword, Long productSkuId,
                                     Integer skcSiteStatus,
                                     Boolean activityBlacklisted,
                                     Boolean allSkuOutOfStock,
                                     Integer minSupplierPrice, Integer maxSupplierPrice,
                                     int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(
            page - 1,
            pageSize,
            Sort.by(
                Sort.Order.desc("temuCreatedAt"),
                Sort.Order.desc("id")
            )
        );
        String keywordPattern = buildKeywordPattern(keyword);
        Page<TemuGoods> goodsPage = goodsRepository.searchGoods(
            shopId,
            keywordPattern,
            productSkuId,
            skcSiteStatus,
            activityBlacklisted,
            allSkuOutOfStock,
            minSupplierPrice,
            maxSupplierPrice,
            pageRequest);
        attachUsSitePriceRange(goodsPage.getContent());
        attachActivityBlacklistFlag(goodsPage.getContent());
        return goodsPage;
    }

    private void attachActivityBlacklistFlag(List<TemuGoods> goodsList) {
        if (goodsList == null || goodsList.isEmpty()) {
            return;
        }
        String shopId = goodsList.get(0).getShopId();
        List<Long> productIds = goodsList.stream().map(TemuGoods::getProductId).filter(Objects::nonNull).distinct().toList();
        Set<Long> blacklistedProductIds = productIds.isEmpty()
                ? Set.of()
                : activityBlacklistRepository.findByShopIdAndProductIdIn(shopId, productIds).stream()
                .map(TemuActivityBlacklist::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (TemuGoods goods : goodsList) {
            goods.setActivityBlacklisted(goods.getProductId() != null && blacklistedProductIds.contains(goods.getProductId()));
        }
    }

    private void attachUsSitePriceRange(List<TemuGoods> goodsList) {
        if (goodsList == null || goodsList.isEmpty()) {
            return;
        }

        List<Long> goodsIds = goodsList.stream()
                .map(TemuGoods::getId)
                .filter(Objects::nonNull)
                .toList();
        if (goodsIds.isEmpty()) {
            return;
        }

        List<TemuGoodsSku> skuList = skuRepository.findByGoodsIdIn(goodsIds);
        if (skuList.isEmpty()) {
            return;
        }

        Map<Long, Long> productSkuIdToGoodsId = skuList.stream()
                .filter(sku -> sku.getProductSkuId() != null && sku.getGoodsId() != null)
                .collect(Collectors.toMap(TemuGoodsSku::getProductSkuId, TemuGoodsSku::getGoodsId, (left, right) -> left, LinkedHashMap::new));

        List<Long> productSkuIds = new ArrayList<>(productSkuIdToGoodsId.keySet());
        if (productSkuIds.isEmpty()) {
            return;
        }

        Map<Long, TemuGoodsSkuPrice> skuPriceMap = skuPriceRepository.findByShopIdAndProductSkuIdIn(goodsList.get(0).getShopId(), productSkuIds).stream()
                .filter(price -> price.getId() != null && price.getProductSkuId() != null)
                .collect(Collectors.toMap(TemuGoodsSkuPrice::getProductSkuId, price -> price, (left, right) -> left, LinkedHashMap::new));

        Map<Long, Long> skuPriceIdToGoodsId = skuPriceMap.values().stream()
            .filter(price -> price.getId() != null)
            .collect(Collectors.toMap(
                TemuGoodsSkuPrice::getId,
                price -> productSkuIdToGoodsId.get(price.getProductSkuId()),
                (left, right) -> left,
                LinkedHashMap::new));

        List<Long> skuPriceIds = skuPriceMap.values().stream()
                .map(TemuGoodsSkuPrice::getId)
                .filter(Objects::nonNull)
                .toList();
        if (skuPriceIds.isEmpty()) {
            return;
        }

        Map<Long, IntSummaryStatistics> statisticsByGoodsId = skuSitePriceRepository.findBySkuPriceIdIn(skuPriceIds).stream()
                .filter(sitePrice -> Objects.equals(EXPORT_SITE_ID_US, sitePrice.getSiteId()))
                .filter(sitePrice -> sitePrice.getSupplierPrice() != null)
                .map(sitePrice -> {
                Long goodsId = skuPriceIdToGoodsId.get(sitePrice.getSkuPriceId());
                    return goodsId == null ? null : Map.entry(goodsId, sitePrice.getSupplierPrice());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        LinkedHashMap::new,
                        Collectors.summarizingInt(Map.Entry::getValue)));

        for (TemuGoods goods : goodsList) {
            IntSummaryStatistics statistics = statisticsByGoodsId.get(goods.getId());
            if (statistics == null || statistics.getCount() <= 0) {
                goods.setSite100MinSupplierPrice(null);
                goods.setSite100MaxSupplierPrice(null);
                continue;
            }
            goods.setSite100MinSupplierPrice((int) statistics.getMin());
            goods.setSite100MaxSupplierPrice((int) statistics.getMax());
        }
    }

    public ExportFile exportGoodsBySku(String shopId, String keyword, Long productSkuId,
                                       Integer skcSiteStatus,
                                       Boolean activityBlacklisted,
                                       Boolean allSkuOutOfStock,
                                       Integer minSupplierPrice, Integer maxSupplierPrice) {
        String keywordPattern = buildKeywordPattern(keyword);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.writeBytes(UTF8_BOM);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            writeCsvRow(writer,
                    "店铺ID", "商品数据库ID", "商品ID", "SKC ID", "商品名称", "商品外部编码", "主图", "加站状态", "叶子类目ID", "叶子类目", "类目路径",
                    "生命周期选品状态", "JIT匹配", "SKC JIT匹配", "支持定制", "JIT申请状态", "建议关闭JIT", "运费模板ID", "保证发货秒数", "TEMU创建时间",
                    "商品同步时间", "站点列表", "属性列表", "SKU数据库ID", "SKU ID", "SKU外部编码", "SKU规格", "库存", "重量(mg)", "长(mm)", "宽(mm)",
                    "高(mm)", "敏感货", "易碎", "发货模式", "币种", "供货价(分)", "美国站供货价(分)", "美国站审核状态", "站点价格汇总");

            int currentPage = 0;
            Page<TemuGoods> goodsPage;
            List<ExportSkuRow> exportRows = new ArrayList<>();
            do {
                goodsPage = goodsRepository.searchGoods(
                        shopId,
                        keywordPattern,
                        productSkuId,
                        skcSiteStatus,
                        activityBlacklisted,
                        allSkuOutOfStock,
                        minSupplierPrice,
                        maxSupplierPrice,
                        PageRequest.of(currentPage, EXPORT_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id")));
                exportRows.addAll(buildExportRows(shopId, goodsPage.getContent()));
                currentPage++;
            } while (currentPage < goodsPage.getTotalPages());

            exportRows.sort(EXPORT_ROW_COMPARATOR);
            for (ExportSkuRow row : exportRows) {
                writeCsvRow(writer, row.values());
            }

            writer.flush();
        } catch (Exception e) {
            throw new IllegalStateException("导出商品数据失败", e);
        }

        return new ExportFile(
                "temu-goods-sku-export-" + EXPORT_FILE_TIME_FORMATTER.format(java.time.LocalDateTime.now()) + ".csv",
                outputStream.toByteArray());
    }

    public TemuGoodsDTO.GoodsDetail getGoodsDetail(Long goodsId) {
        Long safeGoodsId = Objects.requireNonNull(goodsId, "goodsId must not be null");
        TemuGoods goods = goodsRepository.findById(safeGoodsId)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在: " + goodsId));

        TemuGoodsDTO.GoodsDetail detail = new TemuGoodsDTO.GoodsDetail();
        detail.setId(goods.getId());
        detail.setShopId(goods.getShopId());
        detail.setProductId(goods.getProductId());
        detail.setProductSkcId(goods.getProductSkcId());
        detail.setProductName(goods.getProductName());
        detail.setExtCode(goods.getExtCode());
        detail.setMainImageUrl(goods.getMainImageUrl());
        detail.setSkcSiteStatus(goods.getSkcSiteStatus());
        detail.setLeafCatName(goods.getLeafCatName());
        detail.setLeafCatId(goods.getLeafCatId());
        detail.setCategoriesJson(goods.getCategoriesJson());
        detail.setIsSupportPersonalization(goods.getIsSupportPersonalization());
        detail.setMatchJitMode(goods.getMatchJitMode());
        detail.setMatchSkcJitMode(goods.getMatchSkcJitMode());
        detail.setFreightTemplateId(goods.getFreightTemplateId());
        detail.setShipmentLimitSecond(goods.getShipmentLimitSecond());
        detail.setTemuCreatedAt(goods.getTemuCreatedAt());
        detail.setSyncedAt(goods.getSyncedAt());
        detail.setSelectStatus(goods.getSelectStatus());
        detail.setApplyJitStatus(goods.getApplyJitStatus());
        detail.setSuggestCloseJit(goods.getSuggestCloseJit());

        // 站点
        List<TemuGoodsSite> sites = siteRepository.findByGoodsId(safeGoodsId);
        detail.setSiteList(sites.stream().map(s -> {
            TemuGoodsDTO.SiteItem si = new TemuGoodsDTO.SiteItem();
            si.setSiteId(s.getSiteId());
            si.setSiteName(s.getSiteName());
            return si;
        }).collect(Collectors.toList()));
        Map<Integer, String> siteNameMap = sites.stream()
                .filter(site -> site.getSiteId() != null)
                .collect(Collectors.toMap(
                        TemuGoodsSite::getSiteId,
                        site -> site.getSiteName() == null || site.getSiteName().isBlank() ? String.valueOf(site.getSiteId()) : site.getSiteName(),
                        (left, right) -> left,
                        LinkedHashMap::new));

        // 属性
        List<TemuGoodsProperty> properties = propertyRepository.findByGoodsId(safeGoodsId);
        detail.setPropertyList(properties.stream().map(p -> {
            TemuGoodsDTO.PropertyItem pi = new TemuGoodsDTO.PropertyItem();
            pi.setPid(p.getPid());
            pi.setPropName(p.getPropName());
            pi.setVid(p.getVid());
            pi.setPropValue(p.getPropValue());
            pi.setValueUnit(p.getValueUnit());
            return pi;
        }).collect(Collectors.toList()));

        // SKU 列表
        List<TemuGoodsSku> skus = skuRepository.findByGoodsId(safeGoodsId);
        List<TemuGoodsDTO.SkuItem> skuItems = new ArrayList<>();
        for (TemuGoodsSku sku : skus) {
            TemuGoodsDTO.SkuItem si = new TemuGoodsDTO.SkuItem();
            si.setId(sku.getId());
            si.setProductSkuId(sku.getProductSkuId());
            si.setExtCode(sku.getExtCode());
            si.setImageUrl(goods.getMainImageUrl());
            si.setVirtualStock(sku.getVirtualStock());
            si.setWeightMg(sku.getWeightMg());
            si.setLengthMm(sku.getLengthMm());
            si.setWidthMm(sku.getWidthMm());
            si.setHeightMm(sku.getHeightMm());
            si.setIsSensitive(sku.getIsSensitive() != null && sku.getIsSensitive() == 1);
            si.setIsFragile(sku.getIsFragile());
            si.setShippingMode(sku.getShippingMode());

            // 规格
            List<TemuGoodsSkuSpec> specs = skuSpecRepository.findBySkuId(sku.getId());
            si.setSpecList(specs.stream().map(sp -> {
                TemuGoodsDTO.SkuSpecItem ssi = new TemuGoodsDTO.SkuSpecItem();
                ssi.setSpecId(sp.getSpecId());
                ssi.setSpecName(sp.getSpecName());
                ssi.setParentSpecId(sp.getParentSpecId());
                ssi.setParentSpecName(sp.getParentSpecName());
                return ssi;
            }).collect(Collectors.toList()));

            // 价格
            skuPriceRepository.findByShopIdAndProductSkuId(goods.getShopId(), sku.getProductSkuId())
                    .ifPresent(price -> {
                        TemuGoodsDTO.SkuPriceItem pi = new TemuGoodsDTO.SkuPriceItem();
                        pi.setSupplierPrice(price.getSupplierPrice());
                        pi.setCurrencyType(price.getCurrencyType());
                        List<TemuGoodsSkuSitePrice> sitePrices = skuSitePriceRepository.findBySkuPriceId(price.getId());
                        pi.setSitePrices(sitePrices.stream().map(sp -> {
                            TemuGoodsDTO.SitePriceItem spi = new TemuGoodsDTO.SitePriceItem();
                            spi.setSiteId(sp.getSiteId());
                            spi.setSupplierPrice(sp.getSupplierPrice());
                            spi.setPriceReviewStatus(sp.getPriceReviewStatus());
                            return spi;
                        }).collect(Collectors.toList()));
                        si.setPrice(pi);
                    });

            skuItems.add(si);
        }
        detail.setSkuList(skuItems);

        Map<Long, String> skuImageMap = skuItems.stream()
                .filter(item -> item.getProductSkuId() != null)
                .collect(Collectors.toMap(TemuGoodsDTO.SkuItem::getProductSkuId, TemuGoodsDTO.SkuItem::getImageUrl, (left, right) -> left, LinkedHashMap::new));
        List<Long> productSkuIds = skuItems.stream().map(TemuGoodsDTO.SkuItem::getProductSkuId).filter(Objects::nonNull).distinct().toList();
        List<TemuGoodsSkuPriceChange> priceChanges = productSkuIds.isEmpty()
                ? List.of()
                : skuPriceChangeRepository.findTop200ByShopIdAndProductSkuIdInOrderByChangedAtDesc(goods.getShopId(), productSkuIds);
        detail.setPriceChangeList(priceChanges.stream().map(change -> {
            TemuGoodsDTO.SkuPriceChangeItem item = new TemuGoodsDTO.SkuPriceChangeItem();
            item.setId(change.getId());
            item.setProductSkuId(change.getProductSkuId());
            item.setImageUrl(skuImageMap.get(change.getProductSkuId()));
            item.setSiteId(change.getSiteId());
            item.setSiteName(change.getSiteId() == null ? "默认" : siteNameMap.getOrDefault(change.getSiteId(), String.valueOf(change.getSiteId())));
            item.setOldSupplierPrice(change.getOldSupplierPrice());
            item.setNewSupplierPrice(change.getNewSupplierPrice());
            item.setChangedAt(change.getChangedAt());
            return item;
        }).collect(Collectors.toList()));

        return detail;
    }

    private List<ExportSkuRow> buildExportRows(String shopId, List<TemuGoods> goodsList) {
        if (goodsList == null || goodsList.isEmpty()) {
            return List.of();
        }

        List<Long> goodsIds = goodsList.stream().map(TemuGoods::getId).filter(Objects::nonNull).toList();
        Map<Long, List<TemuGoodsSite>> siteMap = goodsIds.isEmpty()
            ? Map.of()
            : siteRepository.findByGoodsIdIn(goodsIds).stream()
            .collect(Collectors.groupingBy(TemuGoodsSite::getGoodsId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<TemuGoodsProperty>> propertyMap = goodsIds.isEmpty()
            ? Map.of()
            : propertyRepository.findByGoodsIdIn(goodsIds).stream()
            .collect(Collectors.groupingBy(TemuGoodsProperty::getGoodsId, LinkedHashMap::new, Collectors.toList()));
        List<TemuGoodsSku> skuList = goodsIds.isEmpty() ? List.of() : skuRepository.findByGoodsIdIn(goodsIds);
        Map<Long, List<TemuGoodsSku>> skuMap = skuList.stream()
                .collect(Collectors.groupingBy(TemuGoodsSku::getGoodsId, LinkedHashMap::new, Collectors.toList()));

        List<Long> skuIds = skuList.stream().map(TemuGoodsSku::getId).filter(Objects::nonNull).toList();
        Map<Long, List<TemuGoodsSkuSpec>> specMap = skuIds.isEmpty()
                ? Map.of()
                : skuSpecRepository.findBySkuIdIn(skuIds).stream()
                .collect(Collectors.groupingBy(TemuGoodsSkuSpec::getSkuId, LinkedHashMap::new, Collectors.toList()));

        List<Long> productSkuIds = skuList.stream().map(TemuGoodsSku::getProductSkuId).filter(Objects::nonNull).distinct().toList();
        List<TemuGoodsSkuPrice> skuPriceList = productSkuIds.isEmpty()
                ? List.of()
                : skuPriceRepository.findByShopIdAndProductSkuIdIn(shopId, productSkuIds);
        Map<Long, TemuGoodsSkuPrice> priceMap = skuPriceList.stream()
                .collect(Collectors.toMap(TemuGoodsSkuPrice::getProductSkuId, item -> item, (left, right) -> left, LinkedHashMap::new));

        List<Long> skuPriceIds = skuPriceList.stream().map(TemuGoodsSkuPrice::getId).filter(Objects::nonNull).toList();
        Map<Long, List<TemuGoodsSkuSitePrice>> sitePriceMap = skuPriceIds.isEmpty()
                ? Map.of()
                : skuSitePriceRepository.findBySkuPriceIdIn(skuPriceIds).stream()
                .collect(Collectors.groupingBy(TemuGoodsSkuSitePrice::getSkuPriceId, LinkedHashMap::new, Collectors.toList()));

        List<ExportSkuRow> rows = new ArrayList<>();
        for (TemuGoods goods : goodsList) {
            String categoryPath = formatCategories(goods.getCategoriesJson());
            String siteSummary = formatSites(siteMap.get(goods.getId()));
            String propertySummary = formatProperties(propertyMap.get(goods.getId()));
            List<TemuGoodsSku> goodsSkus = skuMap.getOrDefault(goods.getId(), List.of());

            if (goodsSkus.isEmpty()) {
            rows.add(new ExportSkuRow(
                null,
                null,
                goods.getId(),
                null,
                        goods.getShopId(), toStringValue(goods.getId()), toStringValue(goods.getProductId()), toStringValue(goods.getProductSkcId()),
                        goods.getProductName(), goods.getExtCode(), goods.getMainImageUrl(), formatSkcSiteStatus(goods.getSkcSiteStatus()),
                        toStringValue(goods.getLeafCatId()), goods.getLeafCatName(), categoryPath, toStringValue(goods.getSelectStatus()),
                        formatBoolean(goods.getMatchJitMode()), formatBoolean(goods.getMatchSkcJitMode()), formatBoolean(goods.getIsSupportPersonalization()),
                        toStringValue(goods.getApplyJitStatus()), formatBoolean(goods.getSuggestCloseJit()),
                        goods.getFreightTemplateId(), toStringValue(goods.getShipmentLimitSecond()), formatEpochMillis(goods.getTemuCreatedAt()),
                        formatDateTime(goods.getSyncedAt()), siteSummary, propertySummary,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""));
                continue;
            }

            for (TemuGoodsSku sku : goodsSkus) {
                TemuGoodsSkuPrice price = priceMap.get(sku.getProductSkuId());
                List<TemuGoodsSkuSitePrice> sitePrices = price == null ? List.of() : sitePriceMap.getOrDefault(price.getId(), List.of());
                TemuGoodsSkuSitePrice usSitePrice = sitePrices.stream()
                        .filter(item -> Objects.equals(EXPORT_SITE_ID_US, item.getSiteId()))
                        .findFirst()
                        .orElse(null);

            rows.add(new ExportSkuRow(
                price != null ? price.getSupplierPrice() : (usSitePrice == null ? null : usSitePrice.getSupplierPrice()),
                sku.getVirtualStock(),
                goods.getId(),
                sku.getProductSkuId(),
                        goods.getShopId(), toStringValue(goods.getId()), toStringValue(goods.getProductId()), toStringValue(goods.getProductSkcId()),
                        goods.getProductName(), goods.getExtCode(), goods.getMainImageUrl(), formatSkcSiteStatus(goods.getSkcSiteStatus()),
                        toStringValue(goods.getLeafCatId()), goods.getLeafCatName(), categoryPath, toStringValue(goods.getSelectStatus()),
                        formatBoolean(goods.getMatchJitMode()), formatBoolean(goods.getMatchSkcJitMode()), formatBoolean(goods.getIsSupportPersonalization()),
                        toStringValue(goods.getApplyJitStatus()), formatBoolean(goods.getSuggestCloseJit()),
                        goods.getFreightTemplateId(), toStringValue(goods.getShipmentLimitSecond()), formatEpochMillis(goods.getTemuCreatedAt()),
                        formatDateTime(goods.getSyncedAt()), siteSummary, propertySummary, toStringValue(sku.getId()), toStringValue(sku.getProductSkuId()),
                        sku.getExtCode(), formatSpecs(specMap.get(sku.getId())), toStringValue(sku.getVirtualStock()), toStringValue(sku.getWeightMg()),
                        toStringValue(sku.getLengthMm()), toStringValue(sku.getWidthMm()), toStringValue(sku.getHeightMm()), formatSensitive(sku.getIsSensitive()),
                        formatBoolean(sku.getIsFragile()), toStringValue(sku.getShippingMode()), price == null ? "" : price.getCurrencyType(),
                        toStringValue(price == null ? null : price.getSupplierPrice()), toStringValue(usSitePrice == null ? null : usSitePrice.getSupplierPrice()),
                toStringValue(usSitePrice == null ? null : usSitePrice.getPriceReviewStatus()), formatSitePrices(sitePrices)));
            }
        }
        return rows;
    }

    private String buildKeywordPattern(String keyword) {
        String normalizedKeyword = keyword != null && !keyword.isBlank() ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        return normalizedKeyword == null ? null : "%" + normalizedKeyword + "%";
    }

    private void writeCsvRow(BufferedWriter writer, String... values) throws java.io.IOException {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                writer.write(',');
            }
            writer.write(escapeCsv(values[index]));
        }
        writer.write("\r\n");
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r", " ").replace("\n", " ");
        if (normalized.contains(",") || normalized.contains("\"") || normalized.contains(" ")) {
            return '"' + normalized.replace("\"", "\"\"") + '"';
        }
        return normalized;
    }

    private String formatSites(List<TemuGoodsSite> sites) {
        if (sites == null || sites.isEmpty()) {
            return "";
        }
        return sites.stream()
                .map(site -> (site.getSiteName() == null || site.getSiteName().isBlank())
                        ? String.valueOf(site.getSiteId())
                        : site.getSiteName() + "(" + site.getSiteId() + ")")
                .collect(Collectors.joining(" | "));
    }

    private String formatProperties(List<TemuGoodsProperty> properties) {
        if (properties == null || properties.isEmpty()) {
            return "";
        }
        return properties.stream()
                .map(property -> {
                    String name = property.getPropName() == null ? "" : property.getPropName();
                    String value = property.getPropValue() == null ? "" : property.getPropValue();
                    String unit = property.getValueUnit() == null ? "" : property.getValueUnit();
                    return name + ':' + value + unit;
                })
                .collect(Collectors.joining(" | "));
    }

    private String formatSpecs(List<TemuGoodsSkuSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return "";
        }
        return specs.stream()
                .map(spec -> {
                    String parentSpecName = spec.getParentSpecName() == null ? "" : spec.getParentSpecName();
                    String specName = spec.getSpecName() == null ? "" : spec.getSpecName();
                    return parentSpecName + ':' + specName;
                })
                .collect(Collectors.joining(" | "));
    }

    private String formatSitePrices(List<TemuGoodsSkuSitePrice> sitePrices) {
        if (sitePrices == null || sitePrices.isEmpty()) {
            return "";
        }
        return sitePrices.stream()
                .map(item -> "站点" + item.getSiteId() + ':' + toStringValue(item.getSupplierPrice()) + "/状态" + toStringValue(item.getPriceReviewStatus()))
                .collect(Collectors.joining(" | "));
    }

    private String formatCategories(String categoriesJson) {
        if (categoriesJson == null || categoriesJson.isBlank()) {
            return "";
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(categoriesJson);
            List<String> names = new ArrayList<>();
            for (int index = 1; index <= 10; index++) {
                JsonNode catNode = root.path("cat" + index);
                if (!catNode.isMissingNode()) {
                    String catName = catNode.path("catName").asText(null);
                    if (catName != null && !catName.isBlank()) {
                        names.add(catName);
                    }
                }
            }
            return String.join(" / ", names);
        } catch (Exception e) {
            return categoriesJson;
        }
    }

    private String formatSkcSiteStatus(Integer skcSiteStatus) {
        if (skcSiteStatus == null) {
            return "";
        }
        return skcSiteStatus == 1 ? "已加站" : "未加站";
    }

    private String formatSensitive(Integer isSensitive) {
        if (isSensitive == null) {
            return "";
        }
        return isSensitive == 1 ? "是" : "否";
    }

    private String formatBoolean(Boolean value) {
        if (value == null) {
            return "";
        }
        return Boolean.TRUE.equals(value) ? "是" : "否";
    }

    private String formatEpochMillis(Long epochMillis) {
        if (epochMillis == null) {
            return "";
        }
        return java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(EXPORT_DATE_TIME_FORMATTER);
    }

    private String formatDateTime(java.time.LocalDateTime time) {
        return time == null ? "" : time.format(EXPORT_DATE_TIME_FORMATTER);
    }

    private String toStringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record ExportFile(String fileName, byte[] content) {
    }

    private record ExportSkuRow(Integer sortSupplierPrice,
                                Integer sortStock,
                                Long goodsId,
                                Long productSkuId,
                                String... values) {
    }
}
