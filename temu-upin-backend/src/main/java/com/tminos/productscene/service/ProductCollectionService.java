package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.ProductCollectionDTO.ProductCollectionResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.ProductCollectionDetailResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.ProductCollectionSkuPropResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.ProductCollectionSkuPropValueResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.ProductCollectionSkuResponse;
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
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
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
import java.util.List;
import java.util.Map;
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
        this.temuSkuRepo = temuSkuRepo;
        this.objectMapper = objectMapper;
        this.importTitleCleanConfig = importTitleCleanConfig;
        this.ocrTaskInsertHelper = ocrTaskInsertHelper;
        this.titleFilterWordRepo = titleFilterWordRepo;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> aiFillTemuAttributes(Long id) {
        ProductCollection pc = get(id);
        String template = getTemuCategoryAttributesRaw(id);
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
    public TemuCategoryDTO.MatchCategoryResponse matchTemuCategory(Long id) {
        ProductCollection pc = get(id);
        return temuCategoryService.matchCategory(pc.getProductName());
    }

    @Transactional(readOnly = true)
    public String getTemuCategoryAttributesRaw(Long id) {
        ProductCollection pc = get(id);
        String catid = pc.getTemuCatid();
        String leaf = extractLastTemuCatId(catid);
        if (leaf == null || leaf.isBlank()) return null;
        return temuCategoryService.getCategoryAttributesRaw(leaf);
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

    @Transactional
    public void saveTemuCategory(Long id, String temuCatid, String temuCatname) {
        ProductCollection pc = get(id);

        // If caller didn't provide a selection, auto-match by title and pick the first option.
        if (temuCatid == null || temuCatid.isBlank() || temuCatname == null || temuCatname.isBlank()) {
            String title = pc.getProductName();
            TemuCategoryDTO.MatchCategoryResponse resp = temuCategoryService.matchCategory(title);
            if (resp == null || !resp.isSuccess() || resp.getOptions() == null || resp.getOptions().isEmpty()) {
                String msg = resp == null ? "matchCategory returned null" : resp.getErrorMsg();
                throw new IllegalStateException("Temu category match failed: " + (msg == null ? "unknown" : msg));
            }

            TemuCategoryDTO.MatchOption first = resp.getOptions().get(0);
            String autoIds = first == null ? null : first.getPathIds();
            String autoNames = first == null ? null : first.getPathNames();
            if (autoIds == null || autoIds.isBlank() || autoNames == null || autoNames.isBlank()) {
                throw new IllegalStateException("Temu category match returned empty first option");
            }

            temuCatid = autoIds;
            temuCatname = autoNames;
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
        Alibaba1688HtmlParser.ParsedProduct parsed = new Alibaba1688HtmlParser(objectMapper).parse(htmlContent);

        String cleanedTitle = cleanImportedTitle(parsed == null ? null : parsed.getProductName());

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
                .sourcePlatform("1688")
                // collectionStatus is used as publish status:
                // 0 未发布, 1 发布中, 2 发布失败
                .collectionStatus(0)
                .execStatus(0)
                .execResult(null)
                .lastPublishRunId(null)
                .ocrStatus(0)
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

        // Auto match TEMU category on import, defaulting to the first suggestion.
        // This mirrors the manual "匹配 TEMU 类目" flow and gives newly imported items a default selection.
        try {
            TemuCategoryDTO.MatchCategoryResponse resp = temuCategoryService.matchCategory(pc.getProductName());
            if (resp != null && resp.isSuccess() && resp.getOptions() != null && !resp.getOptions().isEmpty()) {
                TemuCategoryDTO.MatchOption first = resp.getOptions().get(0);
                if (first != null) {
                    String ids = first.getPathIds();
                    String names = first.getPathNames();
                    if (ids != null && !ids.isBlank() && names != null && !names.isBlank()) {
                        pc.setTemuCatid(ids.trim());
                        pc.setTemuCatname(names.trim());
                    }
                }
            }
        } catch (Exception ignored) {
            // best-effort; do not block import
        }

        // Save SPU first to get id

        pc = repo.save(pc);

        // Enqueue OCR tasks (carousel + detail images), best-effort.
        try {
            enqueueOcrTasksForImportedProduct(pc);
        } catch (Exception ignored) {
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

    private static class ExtractedPayload {
        public List<String> detailImages;
    }

    // list() now uses a native SQL query that includes counts; keep list response mapping in mapRowToResponse.
}
