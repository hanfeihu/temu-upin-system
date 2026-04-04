package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.ProductCollectionDTO.ProductCollectionResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.ProductCollectionDetailResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.ProductCollectionSkuPropResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.ProductCollectionSkuPropValueResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.ProductCollectionSkuResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.TemuTitleOptimizationResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.UpdateProductCollectionRequest;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.ProductCollectionSku;
import com.tminos.productscene.entity.ProductCollectionSkuProp;
import com.tminos.productscene.entity.ProductCollectionSkuPropValue;
import com.tminos.productscene.entity.ProductCollectionTemuSku;
import com.tminos.productscene.repository.ProductCollectionRepository;
import com.tminos.productscene.repository.ProductCollectionSkuPropRepository;
import com.tminos.productscene.repository.ProductCollectionSkuPropValueRepository;
import com.tminos.productscene.repository.ProductCollectionSkuRepository;
import com.tminos.productscene.repository.ProductCollectionTemuSkuRepository;
import com.tminos.productscene.repository.ImportTitleFilterWordRepository;
import com.tminos.productscene.dto.TemuCategoryDTO;
import com.tminos.productscene.config.ImportTitleCleanConfig;
import com.tminos.productscene.service.pull.parser.Alibaba1688HtmlParser;
import com.tminos.productscene.service.pull.parser.TemuHtmlParser;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProductCollectionService {

    private static final Logger log = LoggerFactory.getLogger(ProductCollectionService.class);

    private final ProductCollectionRepository repo;
    private final ProductCollectionSkuRepository skuRepo;
    private final ProductCollectionSkuPropRepository skuPropRepo;
    private final ProductCollectionSkuPropValueRepository skuPropValueRepo;
    private final TemuCategoryService temuCategoryService;
    private final TemuAttributeAiService temuAttributeAiService;
    private final TemuTitleOptimizationService temuTitleOptimizationService;
    private final ProductCollectionTemuSkuRepository temuSkuRepo;
    private final ObjectMapper objectMapper;
    private final ImportTitleCleanConfig importTitleCleanConfig;
    private final OcrTaskInsertHelper ocrTaskInsertHelper;
    private final ImportTitleFilterWordRepository titleFilterWordRepo;

    public ProductCollectionService(
            ProductCollectionRepository repo,
            ProductCollectionSkuRepository skuRepo,
            ProductCollectionSkuPropRepository skuPropRepo,
            ProductCollectionSkuPropValueRepository skuPropValueRepo,
            TemuCategoryService temuCategoryService,
            TemuAttributeAiService temuAttributeAiService,
            TemuTitleOptimizationService temuTitleOptimizationService,
            ProductCollectionTemuSkuRepository temuSkuRepo,
            ObjectMapper objectMapper,
            ImportTitleCleanConfig importTitleCleanConfig,
            OcrTaskInsertHelper ocrTaskInsertHelper,
            ImportTitleFilterWordRepository titleFilterWordRepo
    ) {
        this.repo = repo;
        this.skuRepo = skuRepo;
        this.skuPropRepo = skuPropRepo;
        this.skuPropValueRepo = skuPropValueRepo;
        this.temuCategoryService = temuCategoryService;
        this.temuAttributeAiService = temuAttributeAiService;
        this.temuTitleOptimizationService = temuTitleOptimizationService;
        this.temuSkuRepo = temuSkuRepo;
        this.objectMapper = objectMapper;
        this.importTitleCleanConfig = importTitleCleanConfig;
        this.ocrTaskInsertHelper = ocrTaskInsertHelper;
        this.titleFilterWordRepo = titleFilterWordRepo;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> aiFillTemuAttributes(Long id) {
        ProductCollection pc = get(id);
        TemuCategoryAttributesFetchResult templateFetch = getTemuCategoryAttributesFetchResult(id);
        String template = templateFetch.rawTemplate();
        Map<String, Object> skuSummary = buildSkuSummaryForAi(id);
        TemuAttributeAiService.AiFillResult r = temuAttributeAiService.fill(
                pc.getProductName(),
                pc.getAttributesData(),
                pc.getOriginalContent(),
                template,
                skuSummary
        );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", r.isSuccess());
        out.put("errorMsg", r.getErrorMsg());
        out.put("properties", r.getProperties() == null ? Collections.emptyList() : r.getProperties());
        out.put("missingRequiredPids", r.getMissingRequiredPids() == null ? Collections.emptyList() : r.getMissingRequiredPids());
        out.put("warnings", r.getWarnings() == null ? Collections.emptyList() : r.getWarnings());
        out.put("templateFetch", templateFetch.toLogMap());
        return out;
    }

    private Map<String, Object> buildSkuSummaryForAi(Long spuId) {
        if (spuId == null) return Collections.emptyMap();
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            List<ProductCollectionTemuSku> temuSkus = temuSkuRepo.findBySpuIdOrderByIdAsc(spuId);
            if (temuSkus == null || temuSkus.isEmpty()) {
                List<ProductCollectionSku> skus = skuRepo.findBySpuId(spuId);
                if (skus == null || skus.isEmpty()) return Collections.emptyMap();
                out.put("skuCount", skus.size());
                List<String> specKeys = new ArrayList<>();
                for (ProductCollectionSku s : skus) {
                    if (s == null) continue;
                    String k = s.getSpecKey();
                    if (k != null && !k.isBlank()) {
                        specKeys.add(k.trim());
                        if (specKeys.size() >= 20) break;
                    }
                }
                out.put("specKeys", specKeys);
                return out;
            }
            out.put("skuCount", temuSkus.size());
            List<String> specKeys = new ArrayList<>();
            for (ProductCollectionTemuSku s : temuSkus) {
                if (s == null) continue;
                String k = s.getSpecKey();
                if (k != null && !k.isBlank()) {
                    specKeys.add(k.trim());
                    if (specKeys.size() >= 20) break;
                }
            }
            out.put("specKeys", specKeys);
            return out;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    @Transactional(readOnly = true)
    public Page<ProductCollectionResponse> list(
            String q,
            String sourcePlatform,
            Integer collectionStatus,
            Boolean showDeleted,
            String temuCatid,
            Integer moqMin,
            Integer moqMax,
            Integer carouselImageCountMin,
            Integer carouselImageCountMax,
            Integer detailImageCountMin,
            Integer detailImageCountMax,
            Integer skuCountMin,
            Integer skuCountMax,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));

        boolean sd = showDeleted != null && showDeleted;
        String q2 = (q == null || q.isBlank()) ? null : q.trim();
        String sp = (sourcePlatform == null || sourcePlatform.isBlank()) ? null : sourcePlatform.trim();
        String tc = (temuCatid == null || temuCatid.isBlank()) ? null : temuCatid.trim();

        Page<Object[]> pageRows = repo.searchWithCounts(
                q2,
                sp,
                collectionStatus,
                sd,
                tc,
                moqMin,
                moqMax,
                carouselImageCountMin,
                carouselImageCountMax,
                detailImageCountMin,
                detailImageCountMax,
                skuCountMin,
                skuCountMax,
                pageable
        );

        List<ProductCollectionResponse> out = new ArrayList<>();
        if (pageRows != null && pageRows.getContent() != null) {
            for (Object[] r : pageRows.getContent()) {
                ProductCollectionResponse resp = mapRowToResponse(r);
                if (resp != null) out.add(resp);
            }
        }

        // Preserve original updatedAt DESC ordering from SQL.
        return new PageImpl<>(out, pageable, pageRows == null ? 0 : pageRows.getTotalElements());
    }

    private ProductCollectionResponse mapRowToResponse(Object[] r) {
        if (r == null || r.length < 28) return null;
        // Column order defined in ProductCollectionRepository.searchWithCounts
        return ProductCollectionResponse.builder()
                .id(asLong(r[0]))
                .productId(asString(r[1]))
                .productName(asString(r[2]))
                .sourcePlatform(asString(r[3]))
                .collectionStatus(asInteger(r[4]))
                .execStatus(asInteger(r[5]))
                .execResult(asString(r[6]))
                .lastPublishRunId(asLong(r[7]))
                .collectCount(asInteger(r[8]))
                .companyName(asString(r[9]))
                .productMainImage(asString(r[10]))
                .temuCatid(asString(r[11]))
                .temuCatname(asString(r[12]))
                .temuPublished(asBoolean(r[13]))
                .temuGoodsId(asString(r[14]))
                .minPrice(asBigDecimal(r[15]))
                .maxPrice(asBigDecimal(r[16]))
                .ocrStatus(asInteger(r[17]))
                .carouselImageCount(asInteger(r[18]))
                .detailImageCount(asInteger(r[19]))
                .skuCount(asInteger(r[20]))
                .moq(asInteger(r[21]))
                .moqText(asString(r[22]))
                .netWeight(asBigDecimal(r[23]))
                .packagingWeight(asBigDecimal(r[24]))
                .deleted(asBoolean(r[25]))
                .createdAt(asLocalDateTime(r[26]))
                .updatedAt(asLocalDateTime(r[27]))
                .build();
    }

    private String asString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s;
    }

    private Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.valueOf(String.valueOf(v)); } catch (Exception ignored) { return null; }
    }

    private Integer asInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.valueOf(String.valueOf(v)); } catch (Exception ignored) { return null; }
    }

    private Boolean asBoolean(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim().toLowerCase();
        if ("true".equals(s) || "t".equals(s) || "1".equals(s) || "yes".equals(s) || "y".equals(s)) return true;
        if ("false".equals(s) || "f".equals(s) || "0".equals(s) || "no".equals(s) || "n".equals(s)) return false;
        return null;
    }

    private java.math.BigDecimal asBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof java.math.BigDecimal bd) return bd;
        if (v instanceof Number n) return new java.math.BigDecimal(String.valueOf(n));
        try {
            String s = String.valueOf(v).trim();
            if (s.isBlank()) return null;
            return new java.math.BigDecimal(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private java.time.LocalDateTime asLocalDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof java.time.LocalDateTime dt) return dt;
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (v instanceof java.util.Date d) {
            return java.time.LocalDateTime.ofInstant(d.toInstant(), java.time.ZoneId.systemDefault());
        }
        try {
            return java.time.LocalDateTime.parse(String.valueOf(v));
        } catch (Exception ignored) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public ProductCollection get(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("product_collection not found: " + id));
    }

    @Transactional(readOnly = true)
    public ProductCollectionDetailResponse getDetail(Long id) {
        ProductCollection pc = get(id);
        List<ProductCollectionSkuProp> props = skuPropRepo.findBySpuIdOrderBySortAsc(id);
        List<Long> propIds = props.stream().map(ProductCollectionSkuProp::getId).toList();
        List<ProductCollectionSkuPropValue> propValues = propIds.isEmpty()
            ? Collections.emptyList()
            : skuPropValueRepo.findByPropIdIn(propIds);
        Map<Long, List<ProductCollectionSkuPropValue>> propValueMap = propValues.stream()
            .collect(Collectors.groupingBy(
                ProductCollectionSkuPropValue::getPropId,
                LinkedHashMap::new,
                Collectors.collectingAndThen(Collectors.toList(), list -> {
                    list.sort(Comparator.comparing(ProductCollectionSkuPropValue::getSort, Comparator.nullsLast(Integer::compareTo)));
                    return list;
                })
            ));
        List<ProductCollectionSku> skuRows = skuRepo.findBySpuId(id);
        skuRows.sort(Comparator.comparing(ProductCollectionSku::getId, Comparator.nullsLast(Long::compareTo)));

        List<ProductCollectionTemuSku> temuSkus = temuSkuRepo.findBySpuIdOrderByIdAsc(id);

        return ProductCollectionDetailResponse.builder()
                .id(pc.getId())
                .createdAt(pc.getCreatedAt())
                .createdBy(pc.getCreatedBy())
                .deleted(pc.getDeleted())
                .updatedAt(pc.getUpdatedAt())
                .updatedBy(pc.getUpdatedBy())
                .version(pc.getVersion())
                .annualSales(pc.getAnnualSales())
                .attributesData(pc.getAttributesData())
                .carouselImages(pc.getCarouselImages())
                .collectCount(pc.getCollectCount())
                .collectionStatus(pc.getCollectionStatus())
                .execStatus(pc.getExecStatus())
                .execResult(pc.getExecResult())
                .lastPublishRunId(pc.getLastPublishRunId())
                .collectionTime(pc.getCollectionTime())
                .companyLocation(pc.getCompanyLocation())
                .companyName(pc.getCompanyName())
                .detailImages(pc.getDetailImages())
                .hasSevereInventory(pc.getHasSevereInventory())
                .maxPrice(pc.getMaxPrice())
                .minPrice(pc.getMinPrice())
                .monthlyConsignment(pc.getMonthlyConsignment())
                .monthlySales(pc.getMonthlySales())
                .moq(pc.getMoq())
                .netWeight(pc.getNetWeight())
                .originalCategory(pc.getOriginalCategory())
                .originalContent(pc.getOriginalContent())
                .packagingDimensions(pc.getPackagingDimensions())
                .packagingHeight(pc.getPackagingHeight())
                .packagingLength(pc.getPackagingLength())
                .packagingWeight(pc.getPackagingWeight())
                .packagingWidth(pc.getPackagingWidth())
                .productCategory(pc.getProductCategory())
                .productId(pc.getProductId())
                .productMainImage(pc.getProductMainImage())
                .productName(pc.getProductName())
                .productUrl(pc.getProductUrl())
                .ratingScore(pc.getRatingScore())
                .repeatCustomerRate(pc.getRepeatCustomerRate())
                .reviewCount(pc.getReviewCount())
                .serviceScore(pc.getServiceScore())
                .shippingLocation(pc.getShippingLocation())
                .skuData(pc.getSkuData())
                .skuModel(pc.getSkuModel())
                .sourcePlatform(pc.getSourcePlatform())
                .temuCatid(pc.getTemuCatid())
                .temuCatname(pc.getTemuCatname())
                .temuOptimizedTitleEn(pc.getTemuOptimizedTitleEn())
                .temuOptimizedTitleZh(pc.getTemuOptimizedTitleZh())
                .temuCategoryKeywords(pc.getTemuCategoryKeywords())
                .carouselThumbImages(pc.getCarouselThumbImages())
                .carouselVideo(pc.getCarouselVideo())
                .baseFreight(pc.getBaseFreight())
                .customMadeSpecs(pc.getCustomMadeSpecs())
                .shippingServicesInfo(pc.getShippingServicesInfo())
                .moqText(pc.getMoqText())
                .priceSteps(pc.getPriceSteps())
                .alibabaProductId(pc.getAlibabaProductId())
                .originalHtml(pc.getOriginalHtml())
                .temuAttributes(pc.getTemuAttributes())
                .temuSkus(buildTemuSkuResponses(temuSkus))
                .skuPropsExt(buildSkuPropResponses(props, propValueMap))
                .skuRows(buildSkuResponses(skuRows))
                .build();
    }

    @Transactional(readOnly = true)
    public com.tminos.productscene.dto.ProductCollectionDTO.TemuPublishPayloadResponse getTemuPublishPayload(Long spuId) {
        ProductCollection pc = get(spuId);
        List<ProductCollectionTemuSku> temuSkus = temuSkuRepo.findBySpuIdOrderByIdAsc(spuId);

        return com.tminos.productscene.dto.ProductCollectionDTO.TemuPublishPayloadResponse.builder()
                .spuId(pc.getId())
                .temuCatid(pc.getTemuCatid())
                .sourceProductName(pc.getProductName())
                .translatedProductName(firstNonBlank(pc.getTemuOptimizedTitleEn(), translateTitleForTemuPublish(pc.getProductName())))
                .carouselImages(parseJsonStringArraySafe(pc.getCarouselImages()))
                .detailImages(parseJsonStringArraySafe(pc.getDetailImages()))
                .temuAttributes(parseJsonObjectSafe(pc.getTemuAttributes()))
                .temuSkus(buildTemuSkuResponses(temuSkus))
                .build();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTemuCategories(Boolean showDeleted) {
        boolean sd = showDeleted != null && showDeleted;
        List<Object[]> rows = repo.findDistinctTemuCategories(sd);
        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        // De-duplicate by temuCatid (keep the first name)
        Map<String, String> uniq = new LinkedHashMap<>();
        for (Object[] r : rows) {
            if (r == null || r.length < 2) continue;
            String id = r[0] == null ? null : String.valueOf(r[0]).trim();
            String name = r[1] == null ? null : String.valueOf(r[1]).trim();
            if (id == null || id.isBlank() || name == null || name.isBlank()) continue;
            uniq.putIfAbsent(id, name);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (var e : uniq.entrySet()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("temuCatid", e.getKey());
            o.put("temuCatname", e.getValue());
            out.add(o);
        }
        return out;
    }

    private List<com.tminos.productscene.dto.TemuSkuDTO.TemuSkuRow> buildTemuSkuResponses(List<ProductCollectionTemuSku> rows) {
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        List<com.tminos.productscene.dto.TemuSkuDTO.TemuSkuRow> out = new ArrayList<>();
        for (ProductCollectionTemuSku r : rows) {
            if (r == null) continue;
            com.tminos.productscene.dto.TemuSkuDTO.TemuSkuRow o = new com.tminos.productscene.dto.TemuSkuDTO.TemuSkuRow();
            o.setId(r.getId());
            o.setTemuSkuId(r.getTemuSkuId());
            o.setOriginSkuId(r.getOriginSkuId());
            o.setSpecKey(r.getSpecKey());
            o.setSpecJson(r.getSpecJson());
            o.setImage(r.getImage());
            o.setOriginPrice(r.getOriginPrice());
            o.setSupplyPrice(r.getSupplyPrice());
            o.setWeightG(r.getWeightG());
            o.setLengthCm(r.getLengthCm());
            o.setWidthCm(r.getWidthCm());
            o.setHeightCm(r.getHeightCm());
            out.add(o);
        }
        return out;
    }

    @Transactional
    public ProductCollection update(Long id, UpdateProductCollectionRequest req) {
        ProductCollection pc = get(id);

        if (req.getProductName() != null) pc.setProductName(req.getProductName());

        if (req.getCollectionStatus() != null) pc.setCollectionStatus(req.getCollectionStatus());
        if (req.getCollectCount() != null) pc.setCollectCount(req.getCollectCount());
        if (req.getDeleted() != null) pc.setDeleted(req.getDeleted());

        if (req.getCompanyName() != null) pc.setCompanyName(req.getCompanyName());
        if (req.getCompanyLocation() != null) pc.setCompanyLocation(req.getCompanyLocation());
        if (req.getShippingLocation() != null) pc.setShippingLocation(req.getShippingLocation());

        if (req.getProductCategory() != null) pc.setProductCategory(req.getProductCategory());
        if (req.getOriginalCategory() != null) pc.setOriginalCategory(req.getOriginalCategory());
        if (req.getTemuCatid() != null) pc.setTemuCatid(req.getTemuCatid());
        if (req.getTemuCatname() != null) pc.setTemuCatname(req.getTemuCatname());
        if (req.getTemuOptimizedTitleEn() != null) pc.setTemuOptimizedTitleEn(req.getTemuOptimizedTitleEn());
        if (req.getTemuOptimizedTitleZh() != null) pc.setTemuOptimizedTitleZh(req.getTemuOptimizedTitleZh());
        if (req.getTemuCategoryKeywords() != null) pc.setTemuCategoryKeywords(req.getTemuCategoryKeywords());

        if (req.getProductMainImage() != null) pc.setProductMainImage(req.getProductMainImage());
        if (req.getProductUrl() != null) pc.setProductUrl(req.getProductUrl());
        if (req.getCarouselVideo() != null) pc.setCarouselVideo(req.getCarouselVideo());

        if (req.getAnnualSales() != null) pc.setAnnualSales(req.getAnnualSales());
        if (req.getMonthlySales() != null) pc.setMonthlySales(req.getMonthlySales());
        if (req.getMonthlyConsignment() != null) pc.setMonthlyConsignment(req.getMonthlyConsignment());
        if (req.getMoq() != null) pc.setMoq(req.getMoq());
        if (req.getMoqText() != null) pc.setMoqText(req.getMoqText());

        if (req.getMinPrice() != null) pc.setMinPrice(req.getMinPrice());
        if (req.getMaxPrice() != null) pc.setMaxPrice(req.getMaxPrice());
        if (req.getBaseFreight() != null) pc.setBaseFreight(req.getBaseFreight());

        if (req.getNetWeight() != null) pc.setNetWeight(req.getNetWeight());
        if (req.getPackagingWeight() != null) pc.setPackagingWeight(req.getPackagingWeight());
        if (req.getPackagingLength() != null) pc.setPackagingLength(req.getPackagingLength());
        if (req.getPackagingWidth() != null) pc.setPackagingWidth(req.getPackagingWidth());
        if (req.getPackagingHeight() != null) pc.setPackagingHeight(req.getPackagingHeight());

        if (req.getHasSevereInventory() != null) pc.setHasSevereInventory(req.getHasSevereInventory());

        if (req.getAttributesData() != null) pc.setAttributesData(req.getAttributesData());
        if (req.getSkuData() != null) pc.setSkuData(req.getSkuData());
        if (req.getSkuModel() != null) pc.setSkuModel(req.getSkuModel());
        if (req.getCarouselImages() != null) pc.setCarouselImages(req.getCarouselImages());
        if (req.getCarouselThumbImages() != null) pc.setCarouselThumbImages(req.getCarouselThumbImages());
        if (req.getDetailImages() != null) pc.setDetailImages(req.getDetailImages());
        if (req.getCustomMadeSpecs() != null) pc.setCustomMadeSpecs(req.getCustomMadeSpecs());
        if (req.getShippingServicesInfo() != null) pc.setShippingServicesInfo(req.getShippingServicesInfo());
        if (req.getPriceSteps() != null) pc.setPriceSteps(req.getPriceSteps());
        if (req.getOriginalContent() != null) pc.setOriginalContent(req.getOriginalContent());
        if (req.getOriginalHtml() != null) pc.setOriginalHtml(req.getOriginalHtml());
        if (req.getTemuAttributes() != null) pc.setTemuAttributes(req.getTemuAttributes());

        return repo.save(pc);
    }

    @Transactional
    public com.tminos.productscene.dto.ProductCollectionDTO.SplitProductResponse splitProduct(
            Long id,
            com.tminos.productscene.dto.ProductCollectionDTO.SplitProductRequest request
    ) {
        ProductCollection source = get(id);
        List<ProductCollectionSku> sourceSkuRows = skuRepo.findBySpuId(id);
        sourceSkuRows.sort(Comparator.comparing(ProductCollectionSku::getId, Comparator.nullsLast(Long::compareTo)));
        if (sourceSkuRows.isEmpty()) {
            throw new IllegalArgumentException("当前商品没有可拆分的 SKU");
        }

        List<com.tminos.productscene.dto.ProductCollectionDTO.SplitGroupRequest> requestGroups = request == null
                ? Collections.emptyList()
                : request.getGroups();
        if (requestGroups == null || requestGroups.isEmpty()) {
            throw new IllegalArgumentException("请至少提供两个拆分商品分组");
        }

        Map<Long, ProductCollectionSku> skuByRowId = sourceSkuRows.stream()
                .filter(row -> row.getId() != null)
                .collect(Collectors.toMap(ProductCollectionSku::getId, row -> row, (left, right) -> left, LinkedHashMap::new));

        List<SplitGroupSelection> selections = new ArrayList<>();
        Set<Long> assignedSkuRowIds = new LinkedHashSet<>();
        for (int index = 0; index < requestGroups.size(); index++) {
            com.tminos.productscene.dto.ProductCollectionDTO.SplitGroupRequest groupRequest = requestGroups.get(index);
            if (groupRequest == null) {
                continue;
            }
            String groupName = firstNonBlank(groupRequest.getName(), "拆分商品" + (index + 1));
            List<Long> rowIds = groupRequest.getSkuRowIds() == null ? Collections.emptyList() : groupRequest.getSkuRowIds();
            LinkedHashSet<Long> distinctRowIds = new LinkedHashSet<>();
            for (Long rowId : rowIds) {
                if (rowId == null) {
                    continue;
                }
                ProductCollectionSku row = skuByRowId.get(rowId);
                if (row == null) {
                    throw new IllegalArgumentException("存在无效的 SKU 行 id=" + rowId);
                }
                if (!assignedSkuRowIds.add(rowId)) {
                    throw new IllegalArgumentException("SKU 只能分配到一个新商品中，重复的 skuRowId=" + rowId);
                }
                distinctRowIds.add(rowId);
            }
            if (distinctRowIds.isEmpty()) {
                continue;
            }
            List<ProductCollectionSku> rows = distinctRowIds.stream().map(skuByRowId::get).filter(Objects::nonNull).toList();
            selections.add(new SplitGroupSelection(groupName, rows));
        }

        if (selections.size() < 2) {
            throw new IllegalArgumentException("至少拆分成两个新商品，且每个商品都要包含 SKU");
        }
        if (assignedSkuRowIds.size() != sourceSkuRows.size()) {
            throw new IllegalArgumentException("请先把所有 SKU 都分配到新商品中，当前已分配 " + assignedSkuRowIds.size() + " / " + sourceSkuRows.size());
        }

        List<ProductCollectionTemuSku> sourceTemuSkus = temuSkuRepo.findBySpuIdOrderByIdAsc(id);
        List<ProductCollectionSkuProp> sourceProps = skuPropRepo.findBySpuIdOrderBySortAsc(id);
        List<Long> sourcePropIds = sourceProps.stream().map(ProductCollectionSkuProp::getId).filter(Objects::nonNull).toList();
        List<ProductCollectionSkuPropValue> sourcePropValues = sourcePropIds.isEmpty()
                ? Collections.emptyList()
                : skuPropValueRepo.findByPropIdIn(sourcePropIds);
        Map<Long, List<ProductCollectionSkuPropValue>> sourcePropValueMap = sourcePropValues.stream()
                .collect(Collectors.groupingBy(ProductCollectionSkuPropValue::getPropId, LinkedHashMap::new, Collectors.toList()));

        List<com.tminos.productscene.dto.ProductCollectionDTO.SplitCreatedProductResponse> createdProducts = new ArrayList<>();
        int groupIndex = 1;
        for (SplitGroupSelection selection : selections) {
            List<ProductCollectionSku> groupSkuRows = selection.skuRows();
            List<ProductCollectionTemuSku> groupTemuSkus = pickTemuSkusForGroup(groupSkuRows, sourceTemuSkus);

            ProductCollection target = cloneProductForSplit(source, selection.groupName(), groupIndex, groupSkuRows, groupTemuSkus);
            target.setSkuData(buildSkuDataJson(groupSkuRows));
            target.setSkuModel(buildSkuModelJson(groupSkuRows, sourceProps, sourcePropValueMap));
            target = repo.save(target);

            saveSplitSkuRows(target.getId(), groupSkuRows);
            rebuildSkuPropsForSubset(target.getId(), groupSkuRows, sourceProps, sourcePropValueMap);
            saveSplitTemuSkuRows(target.getId(), groupTemuSkus);

            createdProducts.add(com.tminos.productscene.dto.ProductCollectionDTO.SplitCreatedProductResponse.builder()
                    .id(target.getId())
                    .productId(target.getProductId())
                    .productName(target.getProductName())
                    .skuCount(groupSkuRows.size())
                    .build());
            groupIndex++;
        }

        deleteHard(id);

        return com.tminos.productscene.dto.ProductCollectionDTO.SplitProductResponse.builder()
                .sourceSpuId(id)
                .sourceProductName(source.getProductName())
                .splitCount(createdProducts.size())
                .products(createdProducts)
                .build();
    }

    @Transactional
    public TemuCategoryDTO.MatchCategoryResponse matchTemuCategory(Long id) {
        ProductCollection pc = get(id);
        TemuTitleOptimizationService.TitleOptimizationResult optimization = temuTitleOptimizationService.generateAndMatch(pc);
        applyTemuTitleOptimization(pc, optimization);
        repo.save(pc);
        return toMatchCategoryResponse(optimization);
    }

    @Transactional
    public TemuTitleOptimizationResponse generateTemuTitleOptimization(Long id) {
        ProductCollection pc = get(id);
        TemuTitleOptimizationService.TitleOptimizationResult optimization = temuTitleOptimizationService.generateAndMatch(pc);
        applyTemuTitleOptimization(pc, optimization);
        repo.save(pc);
        return TemuTitleOptimizationResponse.builder()
                .spuId(pc.getId())
                .sourceTitle(pc.getProductName())
                .optimizedTitleEn(optimization.getOptimizedTitleEn())
                .optimizedTitleZh(optimization.getOptimizedTitleZh())
                .categoryKeywords(optimization.getCategoryKeywords())
                .attemptCount(optimization.getAttemptCount())
                .matchedKeyword(optimization.getMatchedKeyword())
                .categoryMatched(Boolean.TRUE.equals(optimization.getCategoryMatched()))
                .matchedTemuCatid(optimization.getMatchedTemuCatid())
                .matchedTemuCatname(optimization.getMatchedTemuCatname())
                .errorMsg(optimization.getErrorMsg())
                .failedKeywords(optimization.getFailedKeywords())
                .build();
    }

    @Transactional(readOnly = true)
    public String getTemuCategoryAttributesRaw(Long id) {
        return getTemuCategoryAttributesFetchResult(id).rawTemplate();
    }

    @Transactional(readOnly = true)
    public TemuCategoryAttributesFetchResult getTemuCategoryAttributesFetchResult(Long id) {
        ProductCollection pc = get(id);
        String catid = pc.getTemuCatid();
        String leaf = extractLastTemuCatId(catid);
        if (leaf == null || leaf.isBlank()) {
            return new TemuCategoryAttributesFetchResult(id, catid, null, null, "temuCatid missing or leafCatId parse failed");
        }
        TemuCategoryService.CategoryAttributesFetchResult fetch = temuCategoryService.fetchCategoryAttributesRaw(leaf);
        String raw = fetch == null ? null : fetch.raw();
        String error = fetch == null ? "fetch result is null" : fetch.errorMsg();
        if (!StringUtils.hasText(raw)) {
            log.warn("getTemuCategoryAttributesFetchResult spuId={} temuCatid={} leafCatId={} rawEmpty=true error={}", id, catid, leaf, error);
        }
        return new TemuCategoryAttributesFetchResult(id, catid, leaf, raw, error);
    }

    @Transactional
    public void saveTemuAttributes(Long id, String temuAttributesJson) {
        ProductCollection pc = get(id);
        int oldLen = pc.getTemuAttributes() == null ? 0 : pc.getTemuAttributes().length();
        int newLen = temuAttributesJson == null ? 0 : temuAttributesJson.length();
        log.info("saveTemuAttributes spuId={} oldLen={} newLen={} thread={}", id, oldLen, newLen, Thread.currentThread().getName());
        pc.setTemuAttributes(temuAttributesJson);
        repo.save(pc);
    }

    private String extractLastTemuCatId(String catid) {
        if (catid == null) return null;
        String s = catid.trim();
        if (s.isBlank()) return null;
        String[] parts = s.split(",");
        if (parts.length == 0) return null;
        return parts[parts.length - 1].trim();
    }

    public record TemuCategoryAttributesFetchResult(Long spuId,
                                                    String temuCatid,
                                                    String leafCatId,
                                                    String rawTemplate,
                                                    String errorMsg) {
        public Map<String, Object> toLogMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("spuId", spuId);
            out.put("temuCatid", temuCatid);
            out.put("leafCatId", leafCatId);
            out.put("rawLen", rawTemplate == null ? 0 : rawTemplate.length());
            out.put("errorMsg", errorMsg);
            out.put("rawPreview", preview(rawTemplate));
            return out;
        }

        private static String preview(String rawTemplate) {
            if (!StringUtils.hasText(rawTemplate)) {
                return null;
            }
            return rawTemplate.length() <= 500 ? rawTemplate : rawTemplate.substring(0, 500);
        }
    }

    @Transactional
    public void saveTemuCategory(Long id, String temuCatid, String temuCatname) {
        ProductCollection pc = get(id);

        // If caller didn't provide a selection, auto-match by title and pick the first option.
        if (temuCatid == null || temuCatid.isBlank() || temuCatname == null || temuCatname.isBlank()) {
            TemuTitleOptimizationService.TitleOptimizationResult optimization = temuTitleOptimizationService.generateAndMatch(pc);
            applyTemuTitleOptimization(pc, optimization);
            if (!Boolean.TRUE.equals(optimization.getCategoryMatched())
                    || !StringUtils.hasText(optimization.getMatchedTemuCatid())
                    || !StringUtils.hasText(optimization.getMatchedTemuCatname())) {
                throw new IllegalStateException("Temu category match failed: " + firstNonBlank(optimization.getErrorMsg(), "unknown"));
            }
            temuCatid = optimization.getMatchedTemuCatid();
            temuCatname = optimization.getMatchedTemuCatname();
        }

        // Store FULL path:
        // - temuCatid: comma-separated catIds (e.g. "9711,11730,11731,11800")
        // - temuCatname: slash-separated catNames (e.g. "家居、厨房用品/浴室用品/浴室配件/化妆品收纳盒")
        pc.setTemuCatid(temuCatid.trim());
        pc.setTemuCatname(temuCatname.trim());
        repo.save(pc);
    }

    @Transactional
    public void deleteHard(Long id) {
        ProductCollection pc = get(id);

        // Delete all associated data first (keep origin tables consistent)
        // 1) sku prop values -> 2) sku rows / sku props -> 3) temu sku -> 4) product collection
        try {
            List<ProductCollectionSkuProp> props = skuPropRepo.findBySpuIdOrderBySortAsc(id);
            List<Long> propIds = props == null ? Collections.emptyList() : props.stream().map(ProductCollectionSkuProp::getId).toList();
            if (propIds != null && !propIds.isEmpty()) {
                skuPropValueRepo.deleteByPropIdIn(propIds);
            }
        } catch (Exception ignored) {
            // best-effort: do not block delete if some child rows are already missing
        }

        try {
            skuRepo.deleteBySpuId(id);
        } catch (Exception ignored) {
        }
        try {
            skuPropRepo.deleteBySpuId(id);
        } catch (Exception ignored) {
        }
        try {
            temuSkuRepo.deleteBySpuId(id);
        } catch (Exception ignored) {
        }

        // OCR tasks
        try {
            if (ocrTaskInsertHelper != null) {
                ocrTaskInsertHelper.deleteBySpuId(id);
            }
        } catch (Exception ignored) {
        }

        repo.delete(pc);
    }

    /**
     * Re-queue a failed post-import automation task.
     *
     * execStatus semantics:
     * - 0: pending
     * - 1: running
     * - 2: success
     * - 3: failed
     */
    @Transactional
    public void requeuePostImportTask(Long id, boolean force) {
        ProductCollection pc = get(id);
        Integer st = pc.getExecStatus();
        int status = st == null ? 0 : st;

        // In UI we allow re-queue for:
        // - failed (3)
        // - running (1)  -> admin might want to stop/retry (may cause duplicate run)
        // Keep success (2) protected unless force=true.
        if (!force && status == 2) {
            throw new IllegalArgumentException("Only failed/running tasks can be re-queued (execStatus=1 or 3)");
        }

        // For pending (0) there is nothing to re-queue; keep it as-is unless force.
        if (!force && status == 0) {
            return;
        }

        pc.setExecStatus(0);
        pc.setExecResult(null);
        pc.setUpdatedAt(java.time.LocalDateTime.now());
        repo.save(pc);
    }

    // =============== Import from HTML (Local Parse -> Entity) ===============

    @Transactional
    public ProductCollection importFromHtml(String htmlContent, String extractedJson) throws Exception {
        Alibaba1688HtmlParser.ParsedProduct parsed;
        String sourcePlatform;
        if (TemuHtmlParser.looksLikeTemuHtml(htmlContent)) {
            parsed = new TemuHtmlParser(objectMapper).parse(htmlContent);
            sourcePlatform = "TEMU";
        } else {
            parsed = new Alibaba1688HtmlParser(objectMapper).parse(htmlContent);
            sourcePlatform = "1688";
        }

        parsed = Objects.requireNonNull(parsed, "解析导入 HTML 失败");
        boolean skipOcrForTemu = isTemuSourcePlatform(sourcePlatform);

        String cleanedTitle = cleanImportedTitle(parsed.getProductName());

        String detailImagesJsonOverride = null;
        if (extractedJson != null && !extractedJson.isBlank()) {
            try {
                ExtractedPayload payload = objectMapper.readValue(extractedJson, ExtractedPayload.class);
                if (payload != null && payload.detailImages != null) {
                    detailImagesJsonOverride = objectMapper.writeValueAsString(payload.detailImages);
                }
            } catch (Exception ex) {
                // extractedJson might be blank/invalid; fall back to HTML parsed detail images
            }
        }

        ProductCollection pc = ProductCollection.builder()
                .productId(parsed.getProductId())
                .alibabaProductId(parsed.getAlibabaProductId())
                .productUrl(parsed.getProductUrl())
                .productName(cleanedTitle)
                .companyName(parsed.getCompanyName())
                .shippingLocation(parsed.getShippingLocation())
                .originalCategory(parsed.getOriginalCategory())
                .productCategory(parsed.getProductCategory())
                .productMainImage(parsed.getProductMainImage())
                .carouselImages(parsed.getCarouselImagesJson())
                .carouselThumbImages(parsed.getCarouselThumbImagesJson())
                .carouselVideo(parsed.getCarouselVideoUrl())
                .detailImages(detailImagesJsonOverride != null ? detailImagesJsonOverride : parsed.getDetailImagesJson())
                .packagingLength(parsed.getPackagingLength())
                .packagingWidth(parsed.getPackagingWidth())
                .packagingHeight(parsed.getPackagingHeight())
                .packagingWeight(parsed.getPackagingWeight())
                .packagingDimensions(parsed.getPackagingDimensions())
                .minPrice(parsed.getMinPrice())
                .maxPrice(parsed.getMaxPrice())
                .moq(parsed.getMoq())
                .moqText(parsed.getMoqText())
                .priceSteps(parsed.getPriceStepsJson())
                .skuData(parsed.getSkuDataJson())
                .skuModel(parsed.getSkuModelJson())
                .attributesData(parsed.getAttributesDataJson())
                .customMadeSpecs(parsed.getCustomMadeSpecsJson())
                .monthlySales(parsed.getMonthlySales())
                .monthlyConsignment(parsed.getMonthlyConsignment())
                .annualSales(parsed.getAnnualSales())
                .serviceScore(parsed.getServiceScore())
                .ratingScore(parsed.getRatingScore())
                .reviewCount(parsed.getReviewCount())
                .collectCount(parsed.getCollectCount())
                .repeatCustomerRate(parsed.getRepeatCustomerRate())
                .hasSevereInventory(parsed.getHasSevereInventory())
                .shippingServicesInfo(parsed.getShippingServicesInfo())
                .baseFreight(parsed.getBaseFreight())
                .originalContent(parsed.getOriginalContent())
                .originalHtml(parsed.getOriginalHtml())
                .sourcePlatform(sourcePlatform)
                // collectionStatus is used as publish status:
                // 0 未发布, 1 发布中, 2 发布失败
                .collectionStatus(0)
                .execStatus(0)
                .execResult(null)
                .lastPublishRunId(null)
                .ocrStatus(skipOcrForTemu ? 2 : 0)
                .build();

        // Backfill packagingWeight from originalContent.packaging.weightG when missing.
        // Some sources only provide weight in the summary JSON.
        if (pc.getPackagingWeight() == null) {
            BigDecimal g = tryExtractWeightGFromOriginalContent(pc.getOriginalContent());
            if (g != null) {
                // keep DB unit consistent with existing UI (g)
                pc.setPackagingWeight(g);
            }
        }

        try {
            TemuTitleOptimizationService.TitleOptimizationResult optimization = temuTitleOptimizationService.generateAndMatch(pc);
            applyTemuTitleOptimization(pc, optimization);
            if (!Boolean.TRUE.equals(optimization.getCategoryMatched())) {
                log.warn("importFromHtml temu title optimization no category match productId={} keyword={} error={}",
                        pc.getProductId(), optimization.getCategoryKeywords(), optimization.getErrorMsg());
            }
        } catch (Exception ex) {
            log.warn("importFromHtml temu title optimization failed productId={} error={}", pc.getProductId(), ex.getMessage());
        }

        // Save SPU first to get id

        pc = repo.save(pc);

        // TEMU 来源默认跳过 OCR；1688 维持原有 OCR 入队逻辑。
        if (!skipOcrForTemu) {
            try {
                enqueueOcrTasksForImportedProduct(pc);
            } catch (Exception ignored) {
            }
        }

        // Rebuild SKU tables for this SPU (tests only; no history concerns)
        saveSkuTablesFromSkuModelJson(pc.getId(), parsed.getSkuModelJson());

        // Best-effort: if skuModel didn't carry per-sku price, fill from parsed spu price.
        // Some 1688 pages only expose ladder price and a price range string.
        if (pc.getMinPrice() != null) {
            backfillSkuPriceIfMissing(pc.getId(), pc.getMinPrice());
        }

        return pc;
    }

    private void enqueueOcrTasksForImportedProduct(ProductCollection pc) {
        if (pc == null || pc.getId() == null) return;
        Long spuId = pc.getId();
        String productId = pc.getProductId();

        // Parse JSON arrays from stored fields.
        List<String> carousel = parseJsonStringArraySafe(pc.getCarouselImages());
        List<String> detail = parseJsonStringArraySafe(pc.getDetailImages());

        // Insert tasks: one url per row.
        List<com.tminos.productscene.entity.ImageOcrTask> tasks = new ArrayList<>();
        for (String url : carousel) {
            if (url == null || url.isBlank()) continue;
            com.tminos.productscene.entity.ImageOcrTask t = new com.tminos.productscene.entity.ImageOcrTask();
            t.setSpuId(spuId);
            t.setProductId(productId);
            t.setImageType(com.tminos.productscene.entity.ImageOcrTask.IMAGE_TYPE_CAROUSEL);
            t.setImageUrl(url.trim());
            t.setExecStatus(com.tminos.productscene.entity.ImageOcrTask.STATUS_PENDING);
            t.setFiltered(false);
            tasks.add(t);
        }
        for (String url : detail) {
            if (url == null || url.isBlank()) continue;
            com.tminos.productscene.entity.ImageOcrTask t = new com.tminos.productscene.entity.ImageOcrTask();
            t.setSpuId(spuId);
            t.setProductId(productId);
            t.setImageType(com.tminos.productscene.entity.ImageOcrTask.IMAGE_TYPE_DETAIL);
            t.setImageUrl(url.trim());
            t.setExecStatus(com.tminos.productscene.entity.ImageOcrTask.STATUS_PENDING);
            t.setFiltered(false);
            tasks.add(t);
        }

        if (tasks.isEmpty()) {
            // No tasks -> treat as done.
            pc.setOcrStatus(2);
            pc.setUpdatedAt(LocalDateTime.now());
            repo.save(pc);
            return;
        }

        // Save tasks via EntityManager to avoid adding new dependency here.
        // We can use Spring Data repository if available; keep as minimal coupling.
        // NOTE: This service already uses @PersistenceContext elsewhere? Not here.
        // We'll use repo save by accessing ImageOcrTaskRepository through Spring context is not available.
        // Instead, do nothing here if repository is not wired.
        // Actual enqueue is done in ImageOcrTaskService when called from controller layer.
        // For now, persist via a local EntityManager provided by JPA.
        // (Implemented via a lightweight helper injected below)
        ocrTaskInsertHelper.insertAll(tasks);

        // Set product OCR status pending.
        pc.setOcrStatus(0);
        pc.setUpdatedAt(LocalDateTime.now());
        repo.save(pc);
    }

    private boolean isTemuSourcePlatform(String sourcePlatform) {
        return sourcePlatform != null && "TEMU".equalsIgnoreCase(sourcePlatform.trim());
    }

    private List<String> parseJsonStringArraySafe(String json) {
        if (json == null) return Collections.emptyList();
        String s = json.trim();
        if (s.isBlank()) return Collections.emptyList();
        try {
            List<?> arr = objectMapper.readValue(s, List.class);
            if (arr == null || arr.isEmpty()) return Collections.emptyList();
            List<String> out = new ArrayList<>();
            for (Object o : arr) {
                if (o == null) continue;
                String v = String.valueOf(o).trim();
                if (!v.isBlank()) out.add(v);
            }
            return out;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private Object parseJsonObjectSafe(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ignored) {
            return json;
        }
    }

    private String translateTitleForTemuPublish(String title) {
        List<String> warnings = new ArrayList<>();
        return sanitizeEnglishName(maybeTranslateTitleToEn(title, warnings), warnings);
    }

    private void applyTemuTitleOptimization(ProductCollection pc,
                                            TemuTitleOptimizationService.TitleOptimizationResult optimization) {
        if (pc == null || optimization == null) {
            return;
        }
        if (StringUtils.hasText(optimization.getOptimizedTitleEn())) {
            pc.setTemuOptimizedTitleEn(optimization.getOptimizedTitleEn().trim());
        }
        if (StringUtils.hasText(optimization.getOptimizedTitleZh())) {
            pc.setTemuOptimizedTitleZh(optimization.getOptimizedTitleZh().trim());
        }
        if (StringUtils.hasText(optimization.getCategoryKeywords())) {
            pc.setTemuCategoryKeywords(optimization.getCategoryKeywords().trim());
        }
        if (Boolean.TRUE.equals(optimization.getCategoryMatched())
                && StringUtils.hasText(optimization.getMatchedTemuCatid())
                && StringUtils.hasText(optimization.getMatchedTemuCatname())) {
            pc.setTemuCatid(optimization.getMatchedTemuCatid().trim());
            pc.setTemuCatname(optimization.getMatchedTemuCatname().trim());
        }
    }

    private TemuCategoryDTO.MatchCategoryResponse toMatchCategoryResponse(TemuTitleOptimizationService.TitleOptimizationResult optimization) {
        TemuCategoryDTO.MatchCategoryResponse source = optimization == null ? null : optimization.getMatchCategoryResponse();
        TemuCategoryDTO.MatchCategoryResponse.MatchCategoryResponseBuilder builder = TemuCategoryDTO.MatchCategoryResponse.builder();
        if (source != null) {
            builder.requestId(source.getRequestId())
                    .success(source.isSuccess())
                    .errorCode(source.getErrorCode())
                    .errorMsg(source.getErrorMsg())
                    .categoryPaths(source.getCategoryPaths())
                    .options(source.getOptions());
        } else {
                    builder.success(optimization != null && Boolean.TRUE.equals(optimization.getCategoryMatched()))
                    .errorMsg(optimization == null ? "optimization result is null" : optimization.getErrorMsg())
                    .options(Collections.emptyList())
                    .categoryPaths(Collections.emptyList());
        }
        if (optimization != null) {
            builder.generatedOptimizedTitleEn(optimization.getOptimizedTitleEn())
                    .generatedOptimizedTitleZh(optimization.getOptimizedTitleZh())
                    .generatedCategoryKeywords(optimization.getCategoryKeywords())
                    .matchedKeyword(optimization.getMatchedKeyword())
                    .attemptCount(optimization.getAttemptCount())
                    .failedKeywords(optimization.getFailedKeywords());
        }
        return builder.build();
    }

    private String maybeTranslateTitleToEn(String title, List<String> warnings) {
        if (!StringUtils.hasText(title)) return title;
        String t = title.trim();
        if (!containsCjk(t)) return t;

        String[] candidates = {
                "com.tminos.erp.common.util.AliyunTranslateUtil",
                "com.tminos.temu.openapi.util.AliyunTranslateUtil",
                "com.tminos.temu.openapi.util.aliyun.AliyunTranslateUtil",
                "com.tminos.productscene.util.AliyunTranslateUtil",
                "AliyunTranslateUtil"
        };

        for (String clzName : candidates) {
            try {
                Class<?> clz = Class.forName(clzName);
                java.lang.reflect.Method m = clz.getMethod("translateToEn", String.class);
                Object out = m.invoke(null, t);
                if (out instanceof String s && StringUtils.hasText(s)) {
                    return s.trim();
                }
            } catch (Exception ignored) {
            }
        }

        if (warnings != null) {
            warnings.add("title translation skipped: AliyunTranslateUtil.translateToEn not found");
        }
        return t;
    }

    private String sanitizeEnglishName(String s, List<String> warnings) {
        if (!StringUtils.hasText(s)) return s;
        String input = s.trim();
        String cleaned = input
                .replaceAll("[^A-Za-z0-9\\-\\_\\.\\,\\/\\(\\)\\[\\]\\+\\&\\%\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!StringUtils.hasText(cleaned)) {
            cleaned = "Product";
        }
        if (!cleaned.equals(input) && warnings != null) {
            warnings.add("english name sanitized");
        }
        if (cleaned.length() > 200) {
            cleaned = cleaned.substring(0, 200).trim();
        }
        return cleaned;
    }

    private boolean containsCjk(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') {
                return true;
            }
        }
        return false;
    }

    /**
     * Clean imported product title by removing configured keywords.
     * This runs at import time so later flows (category match, AI fill, publish) see a cleaner title.
     */
    private String cleanImportedTitle(String title) {
        if (title == null) return null;
        String t = title.trim();
        if (t.isBlank()) return t;

        // Prefer DB-managed title filter words. Fall back to config if DB empty.
        List<String> kws = null;
        try {
            if (titleFilterWordRepo != null) {
                kws = titleFilterWordRepo.findAllOrdered().stream()
                        .map(w -> w == null ? null : w.getWord())
                        .filter(s -> s != null && !s.trim().isEmpty())
                        .map(String::trim)
                        .toList();
            }
        } catch (Exception ignored) {
        }
        if (kws == null || kws.isEmpty()) {
            kws = importTitleCleanConfig == null ? null : importTitleCleanConfig.getRemoveKeywords();
        }
        if (kws == null || kws.isEmpty()) return t;

        String out = t;
        for (String kw : kws) {
            if (kw == null) continue;
            String k = kw.trim();
            if (k.isBlank()) continue;
            // Case-insensitive removal (covers TEMU/Temu/temu) and is safe for CJK too.
            out = out.replaceAll("(?i)" + Pattern.quote(k), " ");
        }
        out = out.replaceAll("\\s+", " ").trim();
        // Avoid empty title (would break matching/UX)
        return out.isBlank() ? t : out;
    }

    private BigDecimal tryExtractWeightGFromOriginalContent(String originalContent) {
        if (originalContent == null || originalContent.isBlank()) return null;
        try {
            // Prefer JSON parsing
            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.readValue(originalContent, Map.class);
            if (root == null) return null;
            Object packaging = root.get("packaging");
            if (!(packaging instanceof Map<?, ?> p)) return null;
            Object weightG = p.get("weightG");
            if (weightG == null) return null;
            if (weightG instanceof Number n) return new BigDecimal(String.valueOf(n));
            String s = String.valueOf(weightG).trim();
            if (s.isBlank()) return null;
            return new BigDecimal(s);
        } catch (Exception ignored) {
            // Fallback: regex
            try {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\\\"weightG\\\"\\s*:\\s*(\\d+(?:\\.\\d+)?)")
                        .matcher(originalContent);
                if (m.find()) {
                    return new BigDecimal(m.group(1));
                }
            } catch (Exception ignored2) {
            }
            return null;
        }
    }

    @Transactional
    public void backfillSkuPriceIfMissing(Long spuId, BigDecimal fallbackPrice) {
        if (spuId == null || fallbackPrice == null) return;
        List<ProductCollectionSku> skus = skuRepo.findBySpuId(spuId);
        boolean changed = false;
        for (ProductCollectionSku sku : skus) {
            if (sku == null) continue;
            if (sku.getPrice() == null) {
                sku.setPrice(fallbackPrice);
                changed = true;
            }
        }
        if (changed) {
            skuRepo.saveAll(skus);
        }
    }

    @Transactional
    public void saveSkuTablesFromSkuModelJson(Long spuId, String skuModelJson) {
        List<ProductCollectionSkuProp> existingProps = skuPropRepo.findBySpuIdOrderBySortAsc(spuId);
        List<Long> existingPropIds = existingProps.stream().map(ProductCollectionSkuProp::getId).toList();
        if (!existingPropIds.isEmpty()) {
            skuPropValueRepo.deleteByPropIdIn(existingPropIds);
        }
        skuRepo.deleteBySpuId(spuId);
        skuPropRepo.deleteBySpuId(spuId);

        if (skuModelJson == null || skuModelJson.isBlank()) {
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> model = objectMapper.readValue(skuModelJson, java.util.Map.class);
            Object propsObj = model.get("props");
            Object skuMapObj = model.get("skuMap");
            if (!(propsObj instanceof java.util.List) || !(skuMapObj instanceof java.util.Map)) {
                return;
            }

            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> props = (java.util.List<java.util.Map<String, Object>>) propsObj;
            @SuppressWarnings("unchecked")
            java.util.Map<String, java.util.Map<String, Object>> skuMap = (java.util.Map<String, java.util.Map<String, Object>>) skuMapObj;

            java.util.List<Long> propIds = new java.util.ArrayList<>();
            for (java.util.Map<String, Object> p : props) {
                if (p == null) continue;
                String name = p.get("name") == null ? null : String.valueOf(p.get("name"));
                if (name == null || name.isBlank()) continue;
                Integer sort = p.get("sort") instanceof Number ? ((Number) p.get("sort")).intValue() : 0;
                Integer fid = p.get("fid") instanceof Number ? ((Number) p.get("fid")).intValue() : null;

                com.tminos.productscene.entity.ProductCollectionSkuProp prop = com.tminos.productscene.entity.ProductCollectionSkuProp.builder()
                        .spuId(spuId)
                        .fid(fid)
                        .name(name)
                        .sort(sort)
                        .build();
                prop = skuPropRepo.save(prop);
                propIds.add(prop.getId());

                Object valuesObj = p.get("values");
                if (valuesObj instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<java.util.Map<String, Object>> values = (java.util.List<java.util.Map<String, Object>>) valuesObj;
                    for (java.util.Map<String, Object> v : values) {
                        if (v == null) continue;
                        String valName = v.get("name") == null ? null : String.valueOf(v.get("name"));
                        if (valName == null || valName.isBlank()) continue;
                        Integer vSort = v.get("sort") instanceof Number ? ((Number) v.get("sort")).intValue() : 0;
                        String image = v.get("image") == null ? null : String.valueOf(v.get("image"));
                        skuPropValueRepo.save(com.tminos.productscene.entity.ProductCollectionSkuPropValue.builder()
                                .propId(prop.getId())
                                .value(valName)
                                .image(image)
                                .sort(vSort)
                                .build());
                    }
                }
            }

            // Save SKUs
            for (java.util.Map.Entry<String, java.util.Map<String, Object>> e : skuMap.entrySet()) {
                String specKey = e.getKey();
                java.util.Map<String, Object> v = e.getValue();
                if (specKey == null || specKey.isBlank() || v == null) continue;

                String skuId = v.get("skuId") == null ? null : String.valueOf(v.get("skuId"));
                Integer stock = v.get("stock") instanceof Number ? ((Number) v.get("stock")).intValue() : null;
                BigDecimal price = parseDecimalValue(v.get("price"));

                // derive specJson from key and props order
                String specJson = null;
                try {
                    String[] parts = specKey.split(">");
                    java.util.LinkedHashMap<String, Object> spec = new java.util.LinkedHashMap<>();
                    int i = 0;
                    for (java.util.Map<String, Object> p : props) {
                        String n = p.get("name") == null ? null : String.valueOf(p.get("name"));
                        if (n == null || n.isBlank()) continue;
                        spec.put(n, i < parts.length ? parts[i] : "");
                        i++;
                    }
                    specJson = objectMapper.writeValueAsString(spec);
                } catch (Exception ignored) {
                }

                skuRepo.save(com.tminos.productscene.entity.ProductCollectionSku.builder()
                        .spuId(spuId)
                        .skuId(skuId)
                        .specKey(specKey)
                        .specJson(specJson)
                        .stock(stock)
                        .price(price)
                        .image(v.get("image") == null ? null : String.valueOf(v.get("image")))
                        .build());
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private List<ProductCollectionSkuPropResponse> buildSkuPropResponses(
            List<ProductCollectionSkuProp> props,
            Map<Long, List<ProductCollectionSkuPropValue>> propValueMap
    ) {
        if (props == null || props.isEmpty()) return Collections.emptyList();

        List<ProductCollectionSkuPropResponse> out = new ArrayList<>();
        for (ProductCollectionSkuProp prop : props) {
            List<ProductCollectionSkuPropValueResponse> values = propValueMap
                    .getOrDefault(prop.getId(), Collections.emptyList())
                    .stream()
                    .map(value -> ProductCollectionSkuPropValueResponse.builder()
                            .id(value.getId())
                            .value(value.getValue())
                            .image(value.getImage())
                            .sort(value.getSort())
                            .build())
                    .toList();

            out.add(ProductCollectionSkuPropResponse.builder()
                    .id(prop.getId())
                    .fid(prop.getFid())
                    .name(prop.getName())
                    .sort(prop.getSort())
                    .values(values)
                    .build());
        }
        return out;
    }

    private List<ProductCollectionSkuResponse> buildSkuResponses(List<ProductCollectionSku> skuRows) {
        if (skuRows == null || skuRows.isEmpty()) return Collections.emptyList();
        return skuRows.stream()
                .map(row -> ProductCollectionSkuResponse.builder()
                        .id(row.getId())
                        .skuId(row.getSkuId())
                        .specKey(row.getSpecKey())
                        .specJson(row.getSpecJson())
                        .stock(row.getStock())
                        .price(row.getPrice())
                        .image(row.getImage())
                        .build())
                .toList();
    }

    private BigDecimal parseDecimalValue(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(String.valueOf(number));

        String text = String.valueOf(value).trim();
        if (text.isBlank()) return null;
        String cleaned = text.replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ProductCollection cloneProductForSplit(ProductCollection source,
                                                   String groupName,
                                                   int groupIndex,
                                                   List<ProductCollectionSku> skuRows,
                                                   List<ProductCollectionTemuSku> temuSkus) {
        LocalDateTime now = LocalDateTime.now();
        ProductCollection target = new ProductCollection();
        target.setCreatedAt(now);
        target.setUpdatedAt(now);
        target.setCreatedBy(source.getCreatedBy());
        target.setUpdatedBy(source.getUpdatedBy());
        target.setDeleted(Boolean.FALSE);
        target.setVersion(0);
        target.setAnnualSales(source.getAnnualSales());
        target.setAttributesData(source.getAttributesData());
        target.setCarouselImages(source.getCarouselImages());
        target.setCollectCount(source.getCollectCount());
        target.setCollectionStatus(0);
        target.setCollectionTime(source.getCollectionTime());
        target.setCompanyLocation(source.getCompanyLocation());
        target.setCompanyName(source.getCompanyName());
        target.setDetailImages(source.getDetailImages());
        target.setHasSevereInventory(source.getHasSevereInventory());
        target.setMonthlyConsignment(source.getMonthlyConsignment());
        target.setMonthlySales(source.getMonthlySales());
        target.setMoq(source.getMoq());
        target.setMoqText(source.getMoqText());
        target.setNetWeight(source.getNetWeight());
        target.setOriginalCategory(source.getOriginalCategory());
        target.setOriginalContent(source.getOriginalContent());
        target.setPackagingDimensions(source.getPackagingDimensions());
        target.setPackagingHeight(source.getPackagingHeight());
        target.setPackagingLength(source.getPackagingLength());
        target.setPackagingWeight(source.getPackagingWeight());
        target.setPackagingWidth(source.getPackagingWidth());
        target.setProductCategory(source.getProductCategory());
        target.setProductId(buildSplitProductId(source.getProductId(), groupIndex));
        target.setProductMainImage(resolveSplitMainImage(skuRows, temuSkus, source.getProductMainImage()));
        target.setProductName(buildSplitProductName(source.getProductName(), groupName));
        target.setProductUrl(source.getProductUrl());
        target.setRatingScore(source.getRatingScore());
        target.setRepeatCustomerRate(source.getRepeatCustomerRate());
        target.setReviewCount(source.getReviewCount());
        target.setServiceScore(source.getServiceScore());
        target.setShippingLocation(source.getShippingLocation());
        target.setSourcePlatform(source.getSourcePlatform());
        target.setTemuCatid(source.getTemuCatid());
        target.setTemuCatname(source.getTemuCatname());
        target.setCarouselThumbImages(source.getCarouselThumbImages());
        target.setCarouselVideo(source.getCarouselVideo());
        target.setBaseFreight(source.getBaseFreight());
        target.setCustomMadeSpecs(source.getCustomMadeSpecs());
        target.setShippingServicesInfo(source.getShippingServicesInfo());
        target.setPriceSteps(source.getPriceSteps());
        target.setAlibabaProductId(source.getAlibabaProductId());
        target.setOriginalHtml(source.getOriginalHtml());
        target.setTemuAttributes(source.getTemuAttributes());
        target.setTemuPublished(Boolean.FALSE);
        target.setTemuGoodsId(null);
        target.setTemuPublishedAt(null);
        target.setTemuPublishRaw(null);
        target.setExecStatus(source.getExecStatus());
        target.setOcrStatus(source.getOcrStatus());
        target.setExecResult(source.getExecResult());
        target.setLastPublishRunId(null);

        List<BigDecimal> prices = skuRows.stream().map(ProductCollectionSku::getPrice).filter(Objects::nonNull).toList();
        target.setMinPrice(prices.isEmpty() ? source.getMinPrice() : prices.stream().min(BigDecimal::compareTo).orElse(source.getMinPrice()));
        target.setMaxPrice(prices.isEmpty() ? source.getMaxPrice() : prices.stream().max(BigDecimal::compareTo).orElse(source.getMaxPrice()));
        return target;
    }

    private String buildSplitProductId(String sourceProductId, int groupIndex) {
        String base = firstNonBlank(sourceProductId, "product");
        return base + "-split-" + groupIndex + "-" + (System.currentTimeMillis() % 1_000_000);
    }

    private String buildSplitProductName(String sourceProductName, String groupName) {
        String base = firstNonBlank(sourceProductName, "拆分商品");
        String suffix = firstNonBlank(groupName, "新分组");
        return base + " - " + suffix;
    }

    private String resolveSplitMainImage(List<ProductCollectionSku> skuRows,
                                         List<ProductCollectionTemuSku> temuSkus,
                                         String fallback) {
        for (ProductCollectionTemuSku temuSku : temuSkus) {
            String image = trimToNull(temuSku == null ? null : temuSku.getImage());
            if (image != null) {
                return image;
            }
        }
        for (ProductCollectionSku skuRow : skuRows) {
            String image = trimToNull(skuRow == null ? null : skuRow.getImage());
            if (image != null) {
                return image;
            }
        }
        return fallback;
    }

    private List<ProductCollectionTemuSku> pickTemuSkusForGroup(List<ProductCollectionSku> skuRows,
                                                                 List<ProductCollectionTemuSku> sourceTemuSkus) {
        if (sourceTemuSkus == null || sourceTemuSkus.isEmpty() || skuRows == null || skuRows.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> addedIds = new LinkedHashSet<>();
        List<ProductCollectionTemuSku> out = new ArrayList<>();
        for (ProductCollectionSku skuRow : skuRows) {
            String skuId = trimToNull(skuRow == null ? null : skuRow.getSkuId());
            String specKey = trimToNull(skuRow == null ? null : skuRow.getSpecKey());
            for (ProductCollectionTemuSku temuSku : sourceTemuSkus) {
                if (temuSku == null || temuSku.getId() == null || addedIds.contains(temuSku.getId())) {
                    continue;
                }
                boolean sameOriginSku = skuId != null && skuId.equals(trimToNull(temuSku.getOriginSkuId()));
                boolean sameSpecKey = specKey != null && specKey.equals(trimToNull(temuSku.getSpecKey()));
                if (sameOriginSku || sameSpecKey) {
                    addedIds.add(temuSku.getId());
                    out.add(temuSku);
                }
            }
        }
        return out;
    }

    private void saveSplitSkuRows(Long targetSpuId, List<ProductCollectionSku> sourceSkuRows) {
        List<ProductCollectionSku> copies = new ArrayList<>();
        for (ProductCollectionSku sourceSkuRow : sourceSkuRows) {
            if (sourceSkuRow == null) {
                continue;
            }
            ProductCollectionSku copy = new ProductCollectionSku();
            copy.setSpuId(targetSpuId);
            copy.setSkuId(sourceSkuRow.getSkuId());
            copy.setSpecKey(sourceSkuRow.getSpecKey());
            copy.setSpecJson(sourceSkuRow.getSpecJson());
            copy.setStock(sourceSkuRow.getStock());
            copy.setPrice(sourceSkuRow.getPrice());
            copy.setImage(sourceSkuRow.getImage());
            copies.add(copy);
        }
        skuRepo.saveAll(copies);
    }

    private void saveSplitTemuSkuRows(Long targetSpuId, List<ProductCollectionTemuSku> sourceTemuSkus) {
        if (sourceTemuSkus == null || sourceTemuSkus.isEmpty()) {
            return;
        }
        List<ProductCollectionTemuSku> copies = new ArrayList<>();
        for (ProductCollectionTemuSku sourceTemuSku : sourceTemuSkus) {
            if (sourceTemuSku == null) {
                continue;
            }
            ProductCollectionTemuSku copy = new ProductCollectionTemuSku();
            copy.setSpuId(targetSpuId);
            copy.setTemuSkuId(sourceTemuSku.getTemuSkuId());
            copy.setOriginSkuId(sourceTemuSku.getOriginSkuId());
            copy.setSpecKey(sourceTemuSku.getSpecKey());
            copy.setSpecJson(sourceTemuSku.getSpecJson());
            copy.setImage(sourceTemuSku.getImage());
            copy.setOriginPrice(sourceTemuSku.getOriginPrice());
            copy.setSupplyPrice(sourceTemuSku.getSupplyPrice());
            copy.setWeightG(sourceTemuSku.getWeightG());
            copy.setLengthCm(sourceTemuSku.getLengthCm());
            copy.setWidthCm(sourceTemuSku.getWidthCm());
            copy.setHeightCm(sourceTemuSku.getHeightCm());
            copies.add(copy);
        }
        temuSkuRepo.saveAll(copies);
    }

    private void rebuildSkuPropsForSubset(Long targetSpuId,
                                          List<ProductCollectionSku> skuRows,
                                          List<ProductCollectionSkuProp> sourceProps,
                                          Map<Long, List<ProductCollectionSkuPropValue>> sourcePropValueMap) {
        if (skuRows == null || skuRows.isEmpty()) {
            return;
        }
        List<ProductCollectionSkuProp> propTemplates = (sourceProps == null || sourceProps.isEmpty())
                ? deriveSkuPropsFromSpecJson(skuRows)
                : sourceProps;
        Map<String, Map<String, ProductCollectionSkuPropValue>> valueMetaByProp = buildPropValueMetaMap(propTemplates, sourcePropValueMap);

        for (ProductCollectionSkuProp sourceProp : propTemplates) {
            if (sourceProp == null || !hasText(sourceProp.getName())) {
                continue;
            }
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (ProductCollectionSku skuRow : skuRows) {
                String value = trimToNull(parseSpecJsonOrderedMap(skuRow == null ? null : skuRow.getSpecJson()).get(sourceProp.getName()));
                if (value != null) {
                    values.add(value);
                }
            }
            if (values.isEmpty()) {
                continue;
            }

            ProductCollectionSkuProp newProp = ProductCollectionSkuProp.builder()
                    .spuId(targetSpuId)
                    .fid(sourceProp.getFid())
                    .name(sourceProp.getName())
                    .sort(sourceProp.getSort())
                    .build();
            newProp = skuPropRepo.save(newProp);

            int sort = 0;
            Map<String, ProductCollectionSkuPropValue> valueMeta = valueMetaByProp.getOrDefault(sourceProp.getName(), Collections.emptyMap());
            for (String value : values) {
                ProductCollectionSkuPropValue sourceValue = valueMeta.get(value);
                skuPropValueRepo.save(ProductCollectionSkuPropValue.builder()
                        .propId(newProp.getId())
                        .value(value)
                        .image(sourceValue == null ? null : sourceValue.getImage())
                        .sort(sourceValue == null || sourceValue.getSort() == null ? sort++ : sourceValue.getSort())
                        .build());
            }
        }
    }

    private String buildSkuDataJson(List<ProductCollectionSku> skuRows) {
        List<Map<String, Object>> skus = new ArrayList<>();
        for (ProductCollectionSku skuRow : skuRows) {
            if (skuRow == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skuId", skuRow.getSkuId());
            row.put("specKey", skuRow.getSpecKey());
            row.put("specJson", parseSpecJsonOrderedMap(skuRow.getSpecJson()));
            row.put("stock", skuRow.getStock());
            row.put("price", skuRow.getPrice());
            row.put("image", skuRow.getImage());
            skus.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("skus", skus);
        return writeJsonSilently(out);
    }

    private String buildSkuModelJson(List<ProductCollectionSku> skuRows,
                                     List<ProductCollectionSkuProp> sourceProps,
                                     Map<Long, List<ProductCollectionSkuPropValue>> sourcePropValueMap) {
        List<ProductCollectionSkuProp> propTemplates = (sourceProps == null || sourceProps.isEmpty())
                ? deriveSkuPropsFromSpecJson(skuRows)
                : sourceProps;
        Map<String, Map<String, ProductCollectionSkuPropValue>> valueMetaByProp = buildPropValueMetaMap(propTemplates, sourcePropValueMap);

        List<Map<String, Object>> props = new ArrayList<>();
        for (ProductCollectionSkuProp sourceProp : propTemplates) {
            if (sourceProp == null || !hasText(sourceProp.getName())) {
                continue;
            }
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (ProductCollectionSku skuRow : skuRows) {
                String value = trimToNull(parseSpecJsonOrderedMap(skuRow == null ? null : skuRow.getSpecJson()).get(sourceProp.getName()));
                if (value != null) {
                    values.add(value);
                }
            }
            if (values.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> valueItems = new ArrayList<>();
            int sort = 0;
            Map<String, ProductCollectionSkuPropValue> valueMeta = valueMetaByProp.getOrDefault(sourceProp.getName(), Collections.emptyMap());
            for (String value : values) {
                ProductCollectionSkuPropValue sourceValue = valueMeta.get(value);
                Map<String, Object> valueItem = new LinkedHashMap<>();
                valueItem.put("name", value);
                valueItem.put("image", sourceValue == null ? null : sourceValue.getImage());
                valueItem.put("sort", sourceValue == null || sourceValue.getSort() == null ? sort++ : sourceValue.getSort());
                valueItems.add(valueItem);
            }
            Map<String, Object> propItem = new LinkedHashMap<>();
            propItem.put("fid", sourceProp.getFid());
            propItem.put("name", sourceProp.getName());
            propItem.put("sort", sourceProp.getSort());
            propItem.put("values", valueItems);
            props.add(propItem);
        }

        Map<String, Object> skuMap = new LinkedHashMap<>();
        for (ProductCollectionSku skuRow : skuRows) {
            if (skuRow == null || !hasText(skuRow.getSpecKey())) {
                continue;
            }
            Map<String, Object> skuItem = new LinkedHashMap<>();
            skuItem.put("skuId", skuRow.getSkuId());
            skuItem.put("stock", skuRow.getStock());
            skuItem.put("price", skuRow.getPrice());
            skuItem.put("image", skuRow.getImage());
            skuMap.put(skuRow.getSpecKey(), skuItem);
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("keySep", ">");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("props", props);
        out.put("skuMap", skuMap);
        out.put("meta", meta);
        return writeJsonSilently(out);
    }

    private List<ProductCollectionSkuProp> deriveSkuPropsFromSpecJson(List<ProductCollectionSku> skuRows) {
        LinkedHashMap<String, Integer> orders = new LinkedHashMap<>();
        int index = 0;
        for (ProductCollectionSku skuRow : skuRows) {
            LinkedHashMap<String, String> specMap = parseSpecJsonOrderedMap(skuRow == null ? null : skuRow.getSpecJson());
            for (String key : specMap.keySet()) {
                if (hasText(key) && !orders.containsKey(key)) {
                    orders.put(key, index++);
                }
            }
        }
        List<ProductCollectionSkuProp> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : orders.entrySet()) {
            ProductCollectionSkuProp prop = new ProductCollectionSkuProp();
            prop.setName(entry.getKey());
            prop.setSort(entry.getValue());
            out.add(prop);
        }
        return out;
    }

    private Map<String, Map<String, ProductCollectionSkuPropValue>> buildPropValueMetaMap(List<ProductCollectionSkuProp> props,
                                                                                           Map<Long, List<ProductCollectionSkuPropValue>> sourcePropValueMap) {
        Map<String, Map<String, ProductCollectionSkuPropValue>> out = new LinkedHashMap<>();
        for (ProductCollectionSkuProp prop : props) {
            if (prop == null || prop.getId() == null || !hasText(prop.getName())) {
                continue;
            }
            Map<String, ProductCollectionSkuPropValue> values = new LinkedHashMap<>();
            for (ProductCollectionSkuPropValue value : sourcePropValueMap.getOrDefault(prop.getId(), Collections.emptyList())) {
                if (value != null && hasText(value.getValue())) {
                    values.putIfAbsent(value.getValue(), value);
                }
            }
            out.put(prop.getName(), values);
        }
        return out;
    }

    private LinkedHashMap<String, String> parseSpecJsonOrderedMap(String specJson) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (!hasText(specJson)) {
            return out;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = objectMapper.readValue(specJson, LinkedHashMap.class);
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                String key = trimToNull(entry.getKey());
                String value = trimToNull(entry.getValue() == null ? null : String.valueOf(entry.getValue()));
                if (key != null) {
                    out.put(key, value == null ? "" : value);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private String writeJsonSilently(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static class ExtractedPayload {
        public List<String> detailImages;
    }

    private record SplitGroupSelection(
            String groupName,
            List<ProductCollectionSku> skuRows
    ) {
    }

    // list() now uses a native SQL query that includes counts; keep list response mapping in mapRowToResponse.
}
