package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.Alibaba1688SelectionPoolDTO;
import com.tminos.productscene.entity.Alibaba1688DetailRecord;
import com.tminos.productscene.entity.Alibaba1688SelectionPool;
import com.tminos.productscene.entity.Alibaba1688SelectionPoolReport;
import com.tminos.productscene.entity.Alibaba1688SelectionPoolSku;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.TemuSitePublishException;
import com.tminos.productscene.repository.Alibaba1688DetailRecordRepository;
import com.tminos.productscene.repository.Alibaba1688SelectionPoolFilterCategoryRepository;
import com.tminos.productscene.repository.Alibaba1688SelectionPoolReportRepository;
import com.tminos.productscene.repository.Alibaba1688SelectionPoolRepository;
import com.tminos.productscene.repository.Alibaba1688SelectionPoolSkuRepository;
import com.tminos.productscene.repository.ProductCollectionRepository;
import com.tminos.productscene.repository.TemuSitePublishExceptionRepository;
import com.tminos.productscene.service.selection.Alibaba1688SelectionPoolParser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class Alibaba1688SelectionPoolService {

    private static final Pattern SALES_NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private static final Comparator<Alibaba1688SelectionPoolDTO.ListItem> LIST_SORT_COMPARATOR =
            Comparator.comparingInt(Alibaba1688SelectionPoolService::aiDecisionPriority)
                    .thenComparingInt((Alibaba1688SelectionPoolDTO.ListItem item) -> nullAsMax(item == null ? null : item.getMoqSnapshot()))
                    .thenComparing(
                            Alibaba1688SelectionPoolService::maxWeightOf,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    )
                    .thenComparing(
                            Alibaba1688SelectionPoolService::highestSkuPriceOf,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    )
                    .thenComparingInt((Alibaba1688SelectionPoolDTO.ListItem item) -> nullAsMax(item.getSkuCount()))
                    .thenComparing(
                            Alibaba1688SelectionPoolService::publishedDateOf,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(
                            Alibaba1688SelectionPoolService::parseMonthlySalesSortValue,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(
                            Alibaba1688SelectionPoolDTO.ListItem::getServiceScoreSnapshot,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(
                            Alibaba1688SelectionPoolDTO.ListItem::getShopPositiveRateSnapshot,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(
                            Alibaba1688SelectionPoolDTO.ListItem::getRepeatCustomerRateSnapshot,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(
                            Alibaba1688SelectionPoolDTO.ListItem::getOnTimeDeliveryRateSnapshot,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(
                            Alibaba1688SelectionPoolDTO.ListItem::getPublishedAt1688,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(
                            Alibaba1688SelectionPoolDTO.ListItem::getUpdatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(
                            Alibaba1688SelectionPoolDTO.ListItem::getId,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    );

    private final Alibaba1688SelectionPoolRepository poolRepository;
    private final Alibaba1688SelectionPoolSkuRepository skuRepository;
    private final Alibaba1688SelectionPoolReportRepository reportRepository;
    private final Alibaba1688SelectionPoolFilterCategoryRepository filterCategoryRepository;
    private final Alibaba1688DetailRecordRepository detailRecordRepository;
    private final ProductCollectionRepository productCollectionRepository;
    private final TemuSitePublishExceptionRepository temuSitePublishExceptionRepository;
    private final TargetShopBindingService targetShopBindingService;
    private final ProductCollectionService productCollectionService;
    private final Alibaba1688SelectionPoolParser parser;
    private final Alibaba1688SelectionAiReportService selectionAiReportService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public Alibaba1688SelectionPoolService(
            Alibaba1688SelectionPoolRepository poolRepository,
            Alibaba1688SelectionPoolSkuRepository skuRepository,
            Alibaba1688SelectionPoolReportRepository reportRepository,
            Alibaba1688SelectionPoolFilterCategoryRepository filterCategoryRepository,
            Alibaba1688DetailRecordRepository detailRecordRepository,
            ProductCollectionRepository productCollectionRepository,
            TemuSitePublishExceptionRepository temuSitePublishExceptionRepository,
            TargetShopBindingService targetShopBindingService,
            ProductCollectionService productCollectionService,
            Alibaba1688SelectionPoolParser parser,
            Alibaba1688SelectionAiReportService selectionAiReportService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.poolRepository = poolRepository;
        this.skuRepository = skuRepository;
        this.reportRepository = reportRepository;
        this.filterCategoryRepository = filterCategoryRepository;
        this.detailRecordRepository = detailRecordRepository;
        this.productCollectionRepository = productCollectionRepository;
        this.temuSitePublishExceptionRepository = temuSitePublishExceptionRepository;
        this.targetShopBindingService = targetShopBindingService;
        this.productCollectionService = productCollectionService;
        this.parser = parser;
        this.selectionAiReportService = selectionAiReportService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public Page<Alibaba1688SelectionPoolDTO.ListItem> list(
            String keyword,
            String category,
            String poolStatus,
            Long detailRecordId,
            BigDecimal skuPriceMin,
            BigDecimal skuPriceMax,
            Integer moqMin,
            Integer moqMax,
            Integer startBatchQtyMin,
            Integer startBatchQtyMax,
            BigDecimal aiMaxDimensionCmMin,
            BigDecimal aiMaxDimensionCmMax,
            BigDecimal aiMaxWeightGMin,
            BigDecimal aiMaxWeightGMax,
            String aiSelectionDecision,
            Boolean aiContainsLiquid,
            Boolean aiFragile,
            Boolean temuSiteExceptionBlocked,
            Boolean pushed,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100)
        );
        BigDecimal normalizedSkuPriceMin = skuPriceMin;
        BigDecimal normalizedSkuPriceMax = skuPriceMax;
        if (normalizedSkuPriceMin != null && normalizedSkuPriceMax != null && normalizedSkuPriceMin.compareTo(normalizedSkuPriceMax) > 0) {
            normalizedSkuPriceMin = skuPriceMax;
            normalizedSkuPriceMax = skuPriceMin;
        }
        Integer normalizedMoqMin = moqMin;
        Integer normalizedMoqMax = moqMax;
        if (normalizedMoqMin != null && normalizedMoqMax != null && normalizedMoqMin > normalizedMoqMax) {
            normalizedMoqMin = moqMax;
            normalizedMoqMax = moqMin;
        }
        Integer normalizedStartBatchQtyMin = startBatchQtyMin;
        Integer normalizedStartBatchQtyMax = startBatchQtyMax;
        if (normalizedStartBatchQtyMin != null && normalizedStartBatchQtyMax != null && normalizedStartBatchQtyMin > normalizedStartBatchQtyMax) {
            normalizedStartBatchQtyMin = startBatchQtyMax;
            normalizedStartBatchQtyMax = startBatchQtyMin;
        }
        BigDecimal normalizedAiMaxDimensionCmMin = aiMaxDimensionCmMin;
        BigDecimal normalizedAiMaxDimensionCmMax = aiMaxDimensionCmMax;
        if (normalizedAiMaxDimensionCmMin != null
                && normalizedAiMaxDimensionCmMax != null
                && normalizedAiMaxDimensionCmMin.compareTo(normalizedAiMaxDimensionCmMax) > 0) {
            normalizedAiMaxDimensionCmMin = aiMaxDimensionCmMax;
            normalizedAiMaxDimensionCmMax = aiMaxDimensionCmMin;
        }
        BigDecimal normalizedAiMaxWeightGMin = aiMaxWeightGMin;
        BigDecimal normalizedAiMaxWeightGMax = aiMaxWeightGMax;
        if (normalizedAiMaxWeightGMin != null
                && normalizedAiMaxWeightGMax != null
                && normalizedAiMaxWeightGMin.compareTo(normalizedAiMaxWeightGMax) > 0) {
            normalizedAiMaxWeightGMin = aiMaxWeightGMax;
            normalizedAiMaxWeightGMax = aiMaxWeightGMin;
        }
        Integer finalMoqMin = normalizedMoqMin;
        Integer finalMoqMax = normalizedMoqMax;
        Integer finalStartBatchQtyMin = normalizedStartBatchQtyMin;
        Integer finalStartBatchQtyMax = normalizedStartBatchQtyMax;
        BigDecimal finalAiMaxDimensionCmMin = normalizedAiMaxDimensionCmMin;
        BigDecimal finalAiMaxDimensionCmMax = normalizedAiMaxDimensionCmMax;
        BigDecimal finalAiMaxWeightGMin = normalizedAiMaxWeightGMin;
        BigDecimal finalAiMaxWeightGMax = normalizedAiMaxWeightGMax;
        Set<Long> filteredPoolIds = null;
        if (normalizedSkuPriceMin != null || normalizedSkuPriceMax != null) {
            filteredPoolIds = new HashSet<>(skuRepository.findDistinctPoolIdsBySkuPriceRange(normalizedSkuPriceMin, normalizedSkuPriceMax));
            if (filteredPoolIds.isEmpty()) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
        }
        Set<String> enabledFilteredCategories = loadEnabledFilteredCategories();
        Set<String> blockedOfferIdsForFilter = null;
        if (temuSiteExceptionBlocked != null) {
            blockedOfferIdsForFilter = temuSitePublishExceptionRepository
                    .findAll((root, query, cb) -> cb.isTrue(root.get("active")))
                    .stream()
                    .map(TemuSitePublishException::getOfferId)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            if (Boolean.TRUE.equals(temuSiteExceptionBlocked) && blockedOfferIdsForFilter.isEmpty()) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
        }
        Set<Long> finalFilteredPoolIds = filteredPoolIds;
        Set<String> finalBlockedOfferIdsForFilter = blockedOfferIdsForFilter;
        Specification<Alibaba1688SelectionPool> specification = (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (StringUtils.hasText(poolStatus)) {
                predicate = cb.and(predicate, cb.equal(cb.lower(root.get("poolStatus")), poolStatus.trim().toLowerCase()));
            }
            if (detailRecordId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("detailRecordId"), detailRecordId));
            }
            if (StringUtils.hasText(keyword)) {
                String likeValue = "%" + keyword.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("offerId")), likeValue),
                        cb.like(cb.lower(root.get("detailUrl")), likeValue),
                        cb.like(cb.lower(root.get("productTitleSnapshot")), likeValue),
                        cb.like(cb.lower(root.get("categorySnapshot")), likeValue),
                        cb.like(cb.lower(root.get("companyNameSnapshot")), likeValue),
                        cb.like(cb.lower(root.get("remark")), likeValue),
                        cb.like(cb.lower(root.get("selectedReason")), likeValue),
                        cb.like(cb.lower(root.get("rejectReason")), likeValue)
                ));
            }
            if (StringUtils.hasText(category)) {
                String likeValue = "%" + category.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("categorySnapshot")), likeValue));
            }
            if (finalMoqMin != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("moqSnapshot"), finalMoqMin));
            }
            if (finalMoqMax != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("moqSnapshot"), finalMoqMax));
            }
            if (finalStartBatchQtyMin != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("startBatchQtySnapshot"), finalStartBatchQtyMin));
            }
            if (finalStartBatchQtyMax != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("startBatchQtySnapshot"), finalStartBatchQtyMax));
            }
            if (finalAiMaxDimensionCmMin != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("aiMaxDimensionCm"), finalAiMaxDimensionCmMin));
            }
            if (finalAiMaxDimensionCmMax != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("aiMaxDimensionCm"), finalAiMaxDimensionCmMax));
            }
            if (finalAiMaxWeightGMin != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("aiMaxWeightG"), finalAiMaxWeightGMin));
            }
            if (finalAiMaxWeightGMax != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("aiMaxWeightG"), finalAiMaxWeightGMax));
            }
            if (StringUtils.hasText(aiSelectionDecision)) {
                String normalizedDecision = aiSelectionDecision.trim().toUpperCase(Locale.ROOT);
                if ("UNANALYZED".equals(normalizedDecision)) {
                    predicate = cb.and(predicate, cb.or(
                            cb.isNull(root.get("aiSelectionDecision")),
                            cb.equal(cb.trim(root.get("aiSelectionDecision")), "")
                    ));
                } else if ("REVIEW".equals(normalizedDecision)) {
                    predicate = cb.and(
                            predicate,
                            cb.isNotNull(root.get("aiSelectionDecision")),
                            cb.not(cb.upper(root.get("aiSelectionDecision")).in("SUITABLE", "UNSUITABLE"))
                    );
                } else {
                    predicate = cb.and(predicate, cb.equal(
                            cb.upper(root.get("aiSelectionDecision")),
                            normalizedDecision
                    ));
                }
            }
            if (aiContainsLiquid != null) {
                if (Boolean.TRUE.equals(aiContainsLiquid)) {
                    predicate = cb.and(predicate, cb.isTrue(root.get("aiContainsLiquid")));
                } else {
                    predicate = cb.and(predicate, cb.or(
                            cb.isNull(root.get("aiContainsLiquid")),
                            cb.isFalse(root.get("aiContainsLiquid"))
                    ));
                }
            }
            if (aiFragile != null) {
                if (Boolean.TRUE.equals(aiFragile)) {
                    predicate = cb.and(predicate, cb.isTrue(root.get("aiFragile")));
                } else {
                    predicate = cb.and(predicate, cb.or(
                            cb.isNull(root.get("aiFragile")),
                            cb.isFalse(root.get("aiFragile"))
                    ));
                }
            }
            if (temuSiteExceptionBlocked != null) {
                if (Boolean.TRUE.equals(temuSiteExceptionBlocked)) {
                    predicate = cb.and(predicate, root.get("offerId").in(finalBlockedOfferIdsForFilter));
                } else if (finalBlockedOfferIdsForFilter != null && !finalBlockedOfferIdsForFilter.isEmpty()) {
                    predicate = cb.and(predicate, cb.or(
                            cb.isNull(root.get("offerId")),
                            cb.not(root.get("offerId").in(finalBlockedOfferIdsForFilter))
                    ));
                }
            }
            if (finalFilteredPoolIds != null) {
                predicate = cb.and(predicate, root.get("id").in(finalFilteredPoolIds));
            }
            if (pushed != null) {
                var pushedPredicate = cb.or(
                        cb.isNotNull(root.get("pushedProductCollectionId")),
                        cb.equal(cb.lower(root.get("pushStatus")), "pushed")
                );
                if (Boolean.TRUE.equals(pushed)) {
                    predicate = cb.and(predicate, pushedPredicate);
                } else {
                    predicate = cb.and(
                            predicate,
                            cb.isNull(root.get("pushedProductCollectionId")),
                            cb.or(
                                    cb.isNull(root.get("pushStatus")),
                                    cb.notEqual(cb.lower(root.get("pushStatus")), "pushed")
                            )
                    );
                }
            }
            if (!enabledFilteredCategories.isEmpty()) {
                predicate = cb.and(
                        predicate,
                        cb.or(
                                cb.isNull(root.get("categorySnapshot")),
                                cb.not(root.get("categorySnapshot").in(enabledFilteredCategories))
                        )
                );
            }
            return predicate;
        };
        List<Alibaba1688SelectionPool> data = poolRepository.findAll(specification, Sort.by(
                Sort.Order.desc("publishedAt1688").nullsLast(),
                Sort.Order.desc("updatedAt"),
                Sort.Order.desc("id")
        ));
        if (data.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        Map<Long, Integer> skuCountMap = poolIdsToCountMap(data);
        Map<Long, Integer> reportCountMap = new HashMap<>();
        Map<Long, List<String>> targetShopIdsMap = new HashMap<>();
        Map<Long, List<String>> targetShopNamesMap = new HashMap<>();
        Map<Long, List<String>> tagsMap = new HashMap<>();
        Map<Long, Alibaba1688SelectionPoolReport> latestReportMap = new HashMap<>();
        Map<String, TemuSitePublishException> exceptionByOfferId = loadExceptionByOfferId(data);
        Set<Long> poolIds = data.stream()
                .map(Alibaba1688SelectionPool::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SkuPriceRange> priceRangeMap = poolIds.isEmpty()
                ? Map.of()
                : skuRepository.summarizePriceRangeByPoolIds(poolIds).stream()
                .collect(Collectors.toMap(
                        Alibaba1688SelectionPoolSkuRepository.PoolSkuPriceRangeView::getPoolId,
                        item -> new SkuPriceRange(item.getMinPrice(), item.getMaxPrice())
                ));
        List<Alibaba1688SelectionPoolReport> allReports = poolIds.isEmpty()
                ? List.of()
                : reportRepository.findAllByPoolIdInOrderByCreatedAtDescIdDesc(poolIds);
        for (Alibaba1688SelectionPoolReport report : allReports) {
            if (report.getPoolId() == null) {
                continue;
            }
            reportCountMap.merge(report.getPoolId(), 1, Integer::sum);
            latestReportMap.putIfAbsent(report.getPoolId(), report);
        }
        for (Alibaba1688SelectionPool item : data) {
            targetShopIdsMap.put(item.getId(), parseJsonArray(item.getTargetShopIdsJson()));
            targetShopNamesMap.put(item.getId(), parseJsonArray(item.getTargetShopNamesJson()));
            tagsMap.put(item.getId(), parseJsonArray(item.getTagsJson()));
        }
        List<Alibaba1688SelectionPoolDTO.ListItem> sortedItems = data.stream()
                .map(item -> toListItem(
                        item,
                        targetShopIdsMap.getOrDefault(item.getId(), List.of()),
                        targetShopNamesMap.getOrDefault(item.getId(), List.of()),
                        tagsMap.getOrDefault(item.getId(), List.of()),
                        skuCountMap.getOrDefault(item.getId(), 0),
                        reportCountMap.getOrDefault(item.getId(), 0),
                        latestReportMap.get(item.getId()),
                        priceRangeMap.get(item.getId()),
                        exceptionByOfferId.get(item.getOfferId())
                ))
                .sorted(LIST_SORT_COMPARATOR)
                .toList();
        int fromIndex = (int) pageable.getOffset();
        if (fromIndex >= sortedItems.size()) {
            return new PageImpl<>(List.of(), pageable, sortedItems.size());
        }
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), sortedItems.size());
        return new PageImpl<>(sortedItems.subList(fromIndex, toIndex), pageable, sortedItems.size());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCategories() {
        List<Object[]> rows = poolRepository.findDistinctCategories();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<String> enabledFilteredCategories = loadEnabledFilteredCategories();
        Map<String, Long> distinct = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 1 || row[0] == null) {
                continue;
            }
            String category = String.valueOf(row[0]).trim();
            if (category.isEmpty()) {
                continue;
            }
            if (enabledFilteredCategories.contains(category)) {
                continue;
            }
            long count = 0L;
            if (row.length > 1 && row[1] instanceof Number number) {
                count = number.longValue();
            }
            distinct.putIfAbsent(category, count);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : distinct.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", entry.getKey());
            item.put("label", entry.getKey());
            item.put("count", entry.getValue());
            result.add(item);
        }
        return result;
    }

    private Set<String> loadEnabledFilteredCategories() {
        return filterCategoryRepository.findEnabledCategoryNames().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public Alibaba1688SelectionPoolDTO.DetailResponse getDetail(Long id) {
        Alibaba1688SelectionPool pool = loadPool(id);
        List<Alibaba1688SelectionPoolSku> skus = skuRepository.findAllByPoolIdOrderByIsPrimarySkuDescIdAsc(id);
        List<Alibaba1688SelectionPoolReport> reports = reportRepository.findAllByPoolIdOrderByCreatedAtDescIdDesc(id);
        return toDetailResponse(pool, skus, reports);
    }

    @Transactional
    public Alibaba1688SelectionPoolDTO.ImportResponse importFromDetail(Alibaba1688SelectionPoolDTO.ImportRequest request) {
        Long detailRecordId = request == null ? null : request.getDetailRecordId();
        if (detailRecordId == null) {
            throw new IllegalArgumentException("请选择 1688 详情记录");
        }
        Alibaba1688DetailRecord detailRecord = detailRecordRepository.findById(detailRecordId)
                .orElseThrow(() -> new IllegalArgumentException("1688 详情记录不存在: " + detailRecordId));
        if (!"READY".equalsIgnoreCase(detailRecord.getStatus())) {
            throw new IllegalArgumentException("当前详情记录还未准备完成，不能入池");
        }
        validateDetailRecordForSelectionPool(detailRecord);

        Alibaba1688SelectionPoolParser.ParsedResult parsed = parser.parse(detailRecord);
        String titleKey = buildProductTitleKey(parsed.main().getProductTitleSnapshot());
        Alibaba1688SelectionPool pool = poolRepository.findByDetailRecordId(detailRecordId)
                .or(() -> poolRepository.findByOfferId(detailRecord.getOfferId()))
                .or(() -> StringUtils.hasText(titleKey) ? poolRepository.findDuplicateByProductTitleKey(titleKey) : Optional.empty())
                .orElseGet(Alibaba1688SelectionPool::new);
        boolean created = pool.getId() == null;
        boolean forceRefresh = request != null && Boolean.TRUE.equals(request.getForceRefresh());
        if (!created && StringUtils.hasText(titleKey) && titleKey.equals(pool.getProductTitleKey())
                && !Objects.equals(pool.getDetailRecordId(), detailRecordId)
                && !Objects.equals(pool.getOfferId(), detailRecord.getOfferId())
                && !forceRefresh) {
            return Alibaba1688SelectionPoolDTO.ImportResponse.builder()
                    .id(pool.getId())
                    .created(false)
                    .refreshed(false)
                    .skipped(true)
                    .message("标题名称已在选品池存在，已跳过")
                    .poolStatus(pool.getPoolStatus())
                    .skuCount(skuRepository.findAllByPoolIdOrderByIsPrimarySkuDescIdAsc(pool.getId()).size())
                    .offerId(pool.getOfferId())
                    .duplicateReason("TITLE")
                    .duplicateTitle(pool.getProductTitleSnapshot())
                    .build();
        }

        applyParsedMain(pool, parsed.main());
        if (!StringUtils.hasText(pool.getPoolStatus())) {
            pool.setPoolStatus("NEW");
        }
        if (forceRefresh || pool.getOverallScore() == null) {
            pool.setOverallScore(parsed.main().getSuggestedScore());
        }
        if (forceRefresh || !StringUtils.hasText(pool.getScoreDetailJson())) {
            pool.setScoreDetailJson(parsed.main().getScoreDetailJson());
        }
        if (created && !StringUtils.hasText(pool.getSourcePlatform())) {
            pool.setSourcePlatform("1688");
        }
        applyManualImportFields(pool, request);

        Alibaba1688SelectionPool savedPool = poolRepository.save(pool);
        syncSkus(savedPool.getId(), parsed.skus());

        return Alibaba1688SelectionPoolDTO.ImportResponse.builder()
                .id(savedPool.getId())
                .created(created)
                .refreshed(!created)
                .skipped(false)
                .message(created ? "1688 详情已加入选品池" : "1688 详情已刷新选品池")
                .poolStatus(savedPool.getPoolStatus())
                .skuCount(skuRepository.findAllByPoolIdOrderByIsPrimarySkuDescIdAsc(savedPool.getId()).size())
                .offerId(savedPool.getOfferId())
                .build();
    }

    public Alibaba1688SelectionPoolDTO.BatchImportResponse batchImportFromDetail(Alibaba1688SelectionPoolDTO.BatchImportRequest request) {
        List<Long> detailRecordIds = resolveBatchImportDetailRecordIds(request);
        if (detailRecordIds.isEmpty()) {
            return Alibaba1688SelectionPoolDTO.BatchImportResponse.builder()
                    .total(0)
                    .successCount(0)
                    .createdCount(0)
                    .refreshedCount(0)
                    .skippedCount(0)
                    .failedCount(0)
                    .failures(List.of())
                    .build();
        }

        int successCount = 0;
        int createdCount = 0;
        int refreshedCount = 0;
        int skippedCount = 0;
        List<Alibaba1688SelectionPoolDTO.BatchImportFailure> failures = new ArrayList<>();
        String note = request == null ? null : request.getNote();
        for (Long detailRecordId : detailRecordIds) {
            try {
                Alibaba1688SelectionPoolDTO.ImportResponse response = transactionTemplate.execute(status -> importFromDetail(
                        Alibaba1688SelectionPoolDTO.ImportRequest.builder()
                                .detailRecordId(detailRecordId)
                                .note(note)
                                .build()
                ));
                if (response == null) {
                    failures.add(Alibaba1688SelectionPoolDTO.BatchImportFailure.builder()
                            .detailRecordId(detailRecordId)
                            .message("导入没有返回结果")
                            .build());
                    continue;
                }
                if (Boolean.TRUE.equals(response.getSkipped())) {
                    skippedCount++;
                    continue;
                }
                successCount++;
                if (Boolean.TRUE.equals(response.getCreated())) {
                    createdCount++;
                } else if (Boolean.TRUE.equals(response.getRefreshed())) {
                    refreshedCount++;
                }
            } catch (Exception ex) {
                failures.add(Alibaba1688SelectionPoolDTO.BatchImportFailure.builder()
                        .detailRecordId(detailRecordId)
                        .message(ex.getMessage() == null ? "导入失败" : ex.getMessage())
                        .build());
            }
        }

        return Alibaba1688SelectionPoolDTO.BatchImportResponse.builder()
                .total(detailRecordIds.size())
                .successCount(successCount)
                .createdCount(createdCount)
                .refreshedCount(refreshedCount)
                .skippedCount(skippedCount)
                .failedCount(failures.size())
                .failures(failures)
                .build();
    }

    @Transactional(readOnly = true)
    protected List<Long> resolveBatchImportDetailRecordIds(Alibaba1688SelectionPoolDTO.BatchImportRequest request) {
        if (request == null) {
            return List.of();
        }
        if (!Boolean.TRUE.equals(request.getAllMatching())) {
            return request.getDetailRecordIds() == null
                    ? List.of()
                    : request.getDetailRecordIds().stream()
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .distinct()
                    .toList();
        }
        return detailRecordRepository.findReadyUnimportedIds(
                StringUtils.hasText(request.getKeyword()) ? request.getKeyword().trim().toLowerCase(Locale.ROOT) : null,
                request.getDetailRecordId()
        );
    }

    @Transactional
    public Alibaba1688SelectionPoolDTO.DetailResponse update(Long id, Alibaba1688SelectionPoolDTO.UpdateRequest request) {
        Alibaba1688SelectionPool pool = loadPool(id);
        if (request != null) {
            if (request.getOverallScore() != null) {
                pool.setOverallScore(request.getOverallScore());
            }
            if (request.getTargetShopIds() != null) {
                TargetShopBindingService.TargetShopBinding binding = targetShopBindingService.resolve(request.getTargetShopIds());
                pool.setTargetShopIdsJson(targetShopBindingService.toJson(binding.shopIds()));
                pool.setTargetShopNamesJson(targetShopBindingService.toJson(binding.shopNames()));
            }
            if (request.getTags() != null) {
                pool.setTagsJson(toJson(request.getTags()));
            }
            if (request.getSelectedReason() != null) {
                pool.setSelectedReason(trimToNull(request.getSelectedReason()));
            }
            if (request.getRejectReason() != null) {
                pool.setRejectReason(trimToNull(request.getRejectReason()));
            }
            if (request.getRemark() != null) {
                pool.setRemark(trimToNull(request.getRemark()));
            }
            if (StringUtils.hasText(request.getPoolStatus())) {
                pool.setPoolStatus(request.getPoolStatus().trim());
            }
            if (request.getScoreDetailJson() != null) {
                pool.setScoreDetailJson(trimToNull(request.getScoreDetailJson()));
            }
        }
        Alibaba1688SelectionPool saved = poolRepository.save(pool);
        return getDetail(saved.getId());
    }

    @Transactional
    public Alibaba1688SelectionPoolDTO.PushToProductCollectionResponse pushToProductCollection(
            Long id,
            Alibaba1688SelectionPoolDTO.PushToProductCollectionRequest request
    ) throws Exception {
        Alibaba1688SelectionPool pool = loadPool(id);
        boolean forceCreate = request != null && Boolean.TRUE.equals(request.getForceCreate());
        List<String> requestedTargetShopIds = request != null && request.getTargetShopIds() != null
                ? request.getTargetShopIds()
                : parseJsonArray(pool.getTargetShopIdsJson());
        TargetShopBindingService.TargetShopBinding targetShopBinding = resolveRequiredSingleTargetShop(requestedTargetShopIds);
        pool.setTargetShopIdsJson(targetShopBindingService.toJson(targetShopBinding.shopIds()));
        pool.setTargetShopNamesJson(targetShopBindingService.toJson(targetShopBinding.shopNames()));

        if (!forceCreate) {
            ProductCollection existing = findExistingProductCollection(pool);
            if (existing != null) {
                bindExistingProductCollectionToShop(existing, targetShopBinding);
                markPushed(pool, existing);
                return buildPushResponse(pool, existing, false, true);
            }
        }

        Alibaba1688DetailRecord detailRecord = loadDetailRecord(pool.getDetailRecordId());
        if (detailRecord == null) {
            throw new IllegalArgumentException("选品池关联的 1688 详情记录不存在: " + pool.getDetailRecordId());
        }
        if (!"READY".equalsIgnoreCase(detailRecord.getStatus())) {
            throw new IllegalArgumentException("当前 1688 详情记录还未准备完成，不能推送到商品采集库");
        }
        if (!StringUtils.hasText(detailRecord.getRawHtml())) {
            throw new IllegalArgumentException("当前 1688 详情记录缺少 rawHtml，请先重新采集详情页");
        }

        ProductCollection created = productCollectionService.importFromHtml(
                detailRecord.getRawHtml(),
                detailRecord.getExtractedJson(),
                targetShopBinding.shopIds()
        );
        markPushed(pool, created);
        return buildPushResponse(pool, created, true, false);
    }

    @Transactional(readOnly = true)
    public List<Alibaba1688SelectionPoolDTO.ListItem> findAutoPushCandidates(int limit) {
        int normalizedLimit = Math.min(Math.max(limit, 1), 100);
        int scanSize = Math.min(Math.max(normalizedLimit * 5, 20), 100);
        Page<Alibaba1688SelectionPoolDTO.ListItem> page = list(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                0,
                scanSize
        );
        if (page == null || page.isEmpty()) {
            return List.of();
        }
        return page.getContent().stream()
                .filter(this::isAutoPushEligible)
                .limit(normalizedLimit)
                .toList();
    }

    @Transactional
    public void markPushFailed(Long poolId, String errorMessage) {
        if (poolId == null) {
            return;
        }
        poolRepository.findById(poolId).ifPresent(pool -> {
            if (pool.getPushedProductCollectionId() != null || "PUSHED".equalsIgnoreCase(pool.getPushStatus())) {
                return;
            }
            pool.setPushStatus("FAILED");
            pool.setPushError(trimToNull(errorMessage));
            poolRepository.save(pool);
        });
    }

    @Transactional
    public Alibaba1688SelectionPoolDTO.SkuItem updateSku(Long skuId, Alibaba1688SelectionPoolDTO.UpdateSkuRequest request) {
        Alibaba1688SelectionPoolSku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new IllegalArgumentException("选品池 SKU 不存在: " + skuId));
        if (request != null) {
            if (request.getManualStockQty() != null) {
                sku.setManualStockQty(request.getManualStockQty());
                sku.setStockCheckedAt(LocalDateTime.now());
            }
            if (request.getStockCheckedBy() != null) {
                sku.setStockCheckedBy(trimToNull(request.getStockCheckedBy()));
                if (StringUtils.hasText(request.getStockCheckedBy())) {
                    sku.setStockCheckedAt(LocalDateTime.now());
                }
            }
            if (request.getWeightValue() != null) {
                sku.setWeightValue(request.getWeightValue());
            }
            if (request.getWeightSource() != null) {
                sku.setWeightSource(trimToNull(request.getWeightSource()));
            }
            if (request.getDimensionValue() != null) {
                sku.setDimensionValue(request.getDimensionValue());
            }
            if (request.getDimensionSource() != null) {
                sku.setDimensionSource(trimToNull(request.getDimensionSource()));
            }
            if (request.getDimensionEvidence() != null) {
                sku.setDimensionEvidence(trimToNull(request.getDimensionEvidence()));
            }
            if (request.getEstimatedPurchasePrice() != null) {
                sku.setEstimatedPurchasePrice(request.getEstimatedPurchasePrice());
            }
            if (request.getEstimatedFirstLegFee() != null) {
                sku.setEstimatedFirstLegFee(request.getEstimatedFirstLegFee());
            }
            if (request.getEstimatedTemuPrice() != null) {
                sku.setEstimatedTemuPrice(request.getEstimatedTemuPrice());
            }
            if (request.getTemuFinalPrice() != null) {
                sku.setTemuFinalPrice(request.getTemuFinalPrice());
            }
            if (request.getSelectionStatus() != null) {
                sku.setSelectionStatus(trimToNull(request.getSelectionStatus()));
            }
            if (request.getIsPrimarySku() != null) {
                sku.setIsPrimarySku(request.getIsPrimarySku());
            }
            if (request.getStockRiskLevel() != null) {
                sku.setStockRiskLevel(trimToNull(request.getStockRiskLevel()));
            }
            if (request.getRemark() != null) {
                sku.setRemark(trimToNull(request.getRemark()));
            }
        }
        recalculateEstimatedProfit(sku);
        Alibaba1688SelectionPoolSku saved = skuRepository.save(sku);
        return toSkuItem(saved);
    }

    private ProductCollection findExistingProductCollection(Alibaba1688SelectionPool pool) {
        if (pool == null) {
            return null;
        }
        if (pool.getPushedProductCollectionId() != null) {
            Optional<ProductCollection> pushed = productCollectionRepository.findById(pool.getPushedProductCollectionId());
            if (pushed.isPresent() && !Boolean.TRUE.equals(pushed.get().getDeleted())) {
                return pushed.get();
            }
        }
        String offerId = trimToNull(pool.getOfferId());
        if (offerId == null) {
            return null;
        }
        Optional<ProductCollection> byAlibabaId = productCollectionRepository
                .findFirstByAlibabaProductIdAndDeletedFalseOrderByIdDesc(offerId);
        if (byAlibabaId.isPresent()) {
            return byAlibabaId.get();
        }
        return productCollectionRepository
                .findFirstByProductIdAndSourcePlatformAndDeletedFalseOrderByIdDesc(offerId, "1688")
                .orElse(null);
    }

    private void markPushed(Alibaba1688SelectionPool pool, ProductCollection productCollection) {
        if (pool == null || productCollection == null) {
            return;
        }
        pool.setPushedProductCollectionId(productCollection.getId());
        pool.setPushStatus("PUSHED");
        pool.setPushedAt(LocalDateTime.now());
        pool.setPushError(null);
        poolRepository.save(pool);
    }

    private TargetShopBindingService.TargetShopBinding resolveRequiredSingleTargetShop(List<String> targetShopIds) {
        TargetShopBindingService.TargetShopBinding binding = targetShopBindingService.resolve(targetShopIds);
        if (binding.shopIds().isEmpty()) {
            throw new IllegalArgumentException("推送商品库前必须选择一个店铺");
        }
        return binding;
    }

    private boolean isAutoPushEligible(Alibaba1688SelectionPoolDTO.ListItem item) {
        if (item == null || item.getId() == null) {
            return false;
        }
        String pushStatus = item.getPushStatus();
        if (item.getPushedProductCollectionId() != null
                || "PUSHED".equalsIgnoreCase(pushStatus)
                || "FAILED".equalsIgnoreCase(pushStatus)) {
            return false;
        }
        if (Boolean.TRUE.equals(item.getTemuSiteExceptionBlocked())) {
            return false;
        }
        return "SUITABLE".equalsIgnoreCase(item.getAiSelectionDecision())
                && item.getMoqSnapshot() != null
                && item.getMoqSnapshot() == 1;
    }

    private void bindExistingProductCollectionToShop(
            ProductCollection productCollection,
            TargetShopBindingService.TargetShopBinding selectedBinding
    ) {
        List<String> selectedShopIds = selectedBinding.shopIds();
        List<String> selectedShopNames = selectedBinding.shopNames();
        String selectedShopId = selectedShopIds.isEmpty() ? null : selectedShopIds.get(0);
        List<String> existingShopIds = parseJsonArray(productCollection.getTargetShopIds());
        if (!existingShopIds.isEmpty() && (existingShopIds.size() != 1 || !Objects.equals(existingShopIds.get(0), selectedShopId))) {
            throw new IllegalArgumentException("商品库记录已绑定其他店铺，不能重复绑定到新店铺");
        }
        if (existingShopIds.isEmpty() || parseJsonArray(productCollection.getTargetShopNames()).isEmpty()) {
            productCollection.setTargetShopIds(targetShopBindingService.toJson(selectedShopIds));
            productCollection.setTargetShopNames(targetShopBindingService.toJson(selectedShopNames));
            productCollection.setUpdatedAt(LocalDateTime.now());
            productCollectionRepository.save(productCollection);
        }
    }

    private Alibaba1688SelectionPoolDTO.PushToProductCollectionResponse buildPushResponse(
            Alibaba1688SelectionPool pool,
            ProductCollection productCollection,
            boolean created,
            boolean existing
    ) {
        return Alibaba1688SelectionPoolDTO.PushToProductCollectionResponse.builder()
                .poolId(pool == null ? null : pool.getId())
                .offerId(pool == null ? null : pool.getOfferId())
                .productCollectionId(productCollection == null ? null : productCollection.getId())
                .productId(productCollection == null ? null : productCollection.getProductId())
                .productName(productCollection == null ? null : productCollection.getProductName())
                .created(created)
                .existing(existing)
                .pushStatus(pool == null ? null : pool.getPushStatus())
                .build();
    }

    public Alibaba1688SelectionPoolDTO.ReportItem saveReport(Long poolId, Alibaba1688SelectionPoolDTO.SaveReportRequest request) {
        String reportType = normalizeReportType(request == null ? null : request.getReportType());
        Alibaba1688SelectionAiReportService.AnalysisResult aiAnalysis = null;

        if ("AI_SELECTION".equalsIgnoreCase(reportType)) {
            ReportGenerationContext context = loadReportGenerationContext(poolId);
            aiAnalysis = resolveAiAnalysis(context.pool(), context.detailRecord(), context.skus(), request);
        }

        Alibaba1688SelectionAiReportService.AnalysisResult resolvedAiAnalysis = aiAnalysis;
        return Objects.requireNonNull(transactionTemplate.execute(
                status -> persistReport(poolId, request, reportType, resolvedAiAnalysis)
        ));
    }

    private void applyParsedMain(Alibaba1688SelectionPool pool, Alibaba1688SelectionPoolParser.ParsedMain main) {
        pool.setDetailRecordId(main.getDetailRecordId());
        pool.setOfferId(main.getOfferId());
        pool.setDetailUrl(main.getDetailUrl());
        pool.setSourcePlatform(firstText(main.getSourcePlatform(), "1688"));
        pool.setProductTitleSnapshot(main.getProductTitleSnapshot());
        pool.setProductTitleKey(buildProductTitleKey(main.getProductTitleSnapshot()));
        pool.setMainImageSnapshot(main.getMainImageSnapshot());
        pool.setCarouselImageUrlsJson(toJson(main.getCarouselImageUrls()));
        pool.setDetailImageUrlsJson(toJson(main.getDetailImageUrls()));
        pool.setCompanyNameSnapshot(main.getCompanyNameSnapshot());
        pool.setRepeatCustomerRateSnapshot(main.getRepeatCustomerRateSnapshot());
        pool.setServiceScoreSnapshot(main.getServiceScoreSnapshot());
        pool.setOnTimeDeliveryRateSnapshot(main.getOnTimeDeliveryRateSnapshot());
        pool.setShopPositiveRateSnapshot(main.getShopPositiveRateSnapshot());
        pool.setPowerSellerSnapshot(main.getPowerSellerSnapshot());
        pool.setSettledYearsTextSnapshot(main.getSettledYearsTextSnapshot());
        pool.setMainBusinessSnapshot(main.getMainBusinessSnapshot());
        pool.setShippingLocationSnapshot(main.getShippingLocationSnapshot());
        pool.setCategorySnapshot(main.getCategorySnapshot());
        if (main.getPublishedAt1688() != null) {
            pool.setPublishedAt1688(main.getPublishedAt1688());
        }
        pool.setBaseFreightSnapshot(main.getBaseFreightSnapshot());
        pool.setMoqSnapshot(main.getMoqSnapshot());
        pool.setStartBatchQtySnapshot(main.getStartBatchQtySnapshot());
        pool.setMoqTextSnapshot(main.getMoqTextSnapshot());
        pool.setMonthlySalesSnapshot(main.getMonthlySalesSnapshot());
        if (StringUtils.hasText(main.getSalesTrendSnapshotJson())) {
            pool.setSalesTrendSnapshotJson(main.getSalesTrendSnapshotJson());
        }
        if (StringUtils.hasText(main.getPriceStepsSnapshotJson())) {
            pool.setPriceStepsSnapshotJson(main.getPriceStepsSnapshotJson());
        }
        pool.setFirstSeenAt(pool.getFirstSeenAt() == null ? main.getFirstSeenAt() : pool.getFirstSeenAt());
        pool.setDetailLastCollectedAt(main.getDetailLastCollectedAt());
        if (StringUtils.hasText(main.getAssistantExtraJson())) {
            pool.setAssistantExtraJson(main.getAssistantExtraJson());
        }
        applyAiOverrides(pool);
    }

    private void applyManualImportFields(Alibaba1688SelectionPool pool, Alibaba1688SelectionPoolDTO.ImportRequest request) {
        if (request == null) {
            return;
        }
        if (request.getTargetShopIds() != null) {
            TargetShopBindingService.TargetShopBinding binding = targetShopBindingService.resolve(request.getTargetShopIds());
            pool.setTargetShopIdsJson(targetShopBindingService.toJson(binding.shopIds()));
            pool.setTargetShopNamesJson(targetShopBindingService.toJson(binding.shopNames()));
        }
        if (request.getTags() != null) {
            pool.setTagsJson(toJson(request.getTags()));
        }
        if (StringUtils.hasText(request.getSelectedReason())) {
            pool.setSelectedReason(request.getSelectedReason().trim());
        }
        if (StringUtils.hasText(request.getRemark())) {
            pool.setRemark(request.getRemark().trim());
        } else if (StringUtils.hasText(request.getNote())) {
            pool.setRemark(request.getNote().trim());
        }
    }

    private String buildProductTitleKey(String title) {
        String normalized = trimToNull(title);
        if (normalized == null) {
            return null;
        }
        normalized = normalized
                .replace('\u00A0', ' ')
                .replaceAll("[\\s\\p{Zs}]+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (normalized.length() > 512) {
            return normalized.substring(0, 512);
        }
        return normalized;
    }

    private void syncSkus(Long poolId, List<Alibaba1688SelectionPoolParser.ParsedSku> parsedSkus) {
        List<Alibaba1688SelectionPoolSku> existing = skuRepository.findAllByPoolIdOrderByIsPrimarySkuDescIdAsc(poolId);
        Map<String, Alibaba1688SelectionPoolSku> byKey = existing.stream()
                .collect(Collectors.toMap(this::buildSkuMatchKey, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        List<Alibaba1688SelectionPoolSku> toSave = new ArrayList<>();
        Set<String> matchedKeys = new LinkedHashSet<>();
        for (Alibaba1688SelectionPoolParser.ParsedSku parsedSku : parsedSkus) {
            String key = buildSkuMatchKey(parsedSku.getSourceSkuId(), parsedSku.getSkuSpecText());
            matchedKeys.add(key);
            Alibaba1688SelectionPoolSku entity = byKey.getOrDefault(key, new Alibaba1688SelectionPoolSku());
            entity.setPoolId(poolId);
            entity.setSourceSkuId(trimToNull(parsedSku.getSourceSkuId()));
            entity.setSkuSpecText(trimToNull(parsedSku.getSkuSpecText()));
            entity.setSkuSpecJson(trimToNull(parsedSku.getSkuSpecJson()));
            entity.setSkuImage(trimToNull(parsedSku.getSkuImage()));
            entity.setSkuMainImage(trimToNull(parsedSku.getSkuMainImage()));
            entity.setSkuPriceSnapshot(parsedSku.getSkuPriceSnapshot());
            entity.setPageStockSnapshot(parsedSku.getPageStockSnapshot());
            entity.setDimensionValue(parsedSku.getDimensionValue());
            entity.setDimensionSource(trimToNull(parsedSku.getDimensionSource()));
            entity.setDimensionEvidence(trimToNull(parsedSku.getDimensionEvidence()));
            if (entity.getWeightValue() == null && parsedSku.getWeightValue() != null) {
                entity.setWeightValue(parsedSku.getWeightValue());
            }
            if (!StringUtils.hasText(entity.getWeightSource()) && StringUtils.hasText(parsedSku.getWeightSource())) {
                entity.setWeightSource(parsedSku.getWeightSource());
            }
            if (entity.getEstimatedPurchasePrice() == null && parsedSku.getSkuPriceSnapshot() != null) {
                entity.setEstimatedPurchasePrice(parsedSku.getSkuPriceSnapshot());
            }
            if (!StringUtils.hasText(entity.getSelectionStatus())) {
                entity.setSelectionStatus("PENDING_SELECTION");
            }
            if (!StringUtils.hasText(entity.getStockRiskLevel())) {
                entity.setStockRiskLevel(defaultStockRisk(parsedSku.getPageStockSnapshot()));
            }
            recalculateEstimatedProfit(entity);
            toSave.add(entity);
        }
        if (!toSave.isEmpty()) {
            skuRepository.saveAll(toSave);
        }
        if (!matchedKeys.isEmpty()) {
            List<Alibaba1688SelectionPoolSku> staleItems = existing.stream()
                    .filter(item -> !matchedKeys.contains(buildSkuMatchKey(item)))
                    .toList();
            if (!staleItems.isEmpty()) {
                skuRepository.deleteAll(staleItems);
            }
        }
    }

    private void recalculateEstimatedProfit(Alibaba1688SelectionPoolSku sku) {
        BigDecimal salePrice = firstNonNull(sku.getTemuFinalPrice(), sku.getEstimatedTemuPrice());
        BigDecimal purchase = sku.getEstimatedPurchasePrice();
        BigDecimal firstLeg = sku.getEstimatedFirstLegFee();
        if (salePrice == null || purchase == null || firstLeg == null) {
            sku.setEstimatedUnitProfit(null);
            return;
        }
        BigDecimal profit = salePrice.subtract(purchase.add(firstLeg));
        sku.setEstimatedUnitProfit(profit.setScale(4, RoundingMode.HALF_UP));
    }

    private void syncLatestReportToPool(Alibaba1688SelectionPool pool, Alibaba1688SelectionPoolReport report) {
        if ("TEMU_COMPETE".equalsIgnoreCase(report.getReportType())) {
            pool.setTemuCompeteAnalysisStatus(firstText(report.getStatus(), "READY"));
            pool.setTemuCompeteReportTitle(report.getReportTitle());
            pool.setTemuCompeteAnalysisSummary(report.getReportSummary());
            pool.setTemuCompeteReportContent(report.getReportContent());
            pool.setTemuCompeteReportJson(report.getReportJson());
            pool.setTemuCompeteScore(report.getScore());
            pool.setTemuCompeteAnalysisAt(report.getCreatedAt());
            return;
        }
        if ("AI_SELECTION".equalsIgnoreCase(report.getReportType())) {
            pool.setAiSelectionAnalysisStatus(firstText(report.getStatus(), "READY"));
            pool.setAiSelectionReportTitle(report.getReportTitle());
            pool.setAiSelectionAnalysisSummary(report.getReportSummary());
            pool.setAiSelectionReportContent(report.getReportContent());
            pool.setAiSelectionReportJson(report.getReportJson());
            pool.setAiSelectionScore(report.getScore());
            pool.setAiSelectionAnalysisAt(report.getCreatedAt());
        }
    }

    private Alibaba1688SelectionPoolDTO.ReportItem persistReport(
            Long poolId,
            Alibaba1688SelectionPoolDTO.SaveReportRequest request,
            String reportType,
            Alibaba1688SelectionAiReportService.AnalysisResult aiAnalysis
    ) {
        Alibaba1688SelectionPool pool = loadPool(poolId);
        if ("AI_SELECTION".equalsIgnoreCase(reportType) && aiAnalysis != null) {
            applyAiSelectionToPool(pool, aiAnalysis);
        }

        String summary = firstText(
                trimToNull(request == null ? null : request.getReportSummary()),
                aiAnalysis == null ? null : trimToNull(aiAnalysis.getSummary()),
                buildAutoReportSummary(pool, reportType)
        );
        String content = firstText(
                trimToNull(request == null ? null : request.getReportContent()),
                aiAnalysis == null ? null : trimToNull(aiAnalysis.getAnalysisReport()),
                buildAutoReportContent(pool, reportType)
        );
        String title = firstText(trimToNull(request == null ? null : request.getReportTitle()), defaultReportTitle(pool, reportType));
        String reportJson = firstText(
                trimToNull(request == null ? null : request.getReportJson()),
                aiAnalysis == null ? null : toJson(aiAnalysis.toMap())
        );
        Integer score = request != null && request.getScore() != null
                ? request.getScore()
                : aiAnalysis == null ? null : aiAnalysis.getScore();
        String sourceType = firstText(
                trimToNull(request == null ? null : request.getSourceType()),
                aiAnalysis == null ? null : "AI",
                "MANUAL"
        );
        String modelName = firstText(
                trimToNull(request == null ? null : request.getModelName()),
                aiAnalysis == null ? null : aiAnalysis.getModelName()
        );

        Alibaba1688SelectionPoolReport report = Alibaba1688SelectionPoolReport.builder()
                .poolId(poolId)
                .reportType(reportType)
                .reportTitle(title)
                .reportSummary(summary)
                .reportContent(content)
                .reportJson(reportJson)
                .status(firstText(trimToNull(request == null ? null : request.getStatus()), "READY"))
                .score(score)
                .sourceType(sourceType)
                .modelName(modelName)
                .versionNo((int) reportRepository.countByPoolIdAndReportType(poolId, reportType) + 1)
                .build();
        Alibaba1688SelectionPoolReport saved = reportRepository.save(report);
        syncLatestReportToPool(pool, saved);
        poolRepository.save(pool);
        return toReportItem(saved);
    }

    private ReportGenerationContext loadReportGenerationContext(Long poolId) {
        Alibaba1688SelectionPool pool = loadPool(poolId);
        Alibaba1688DetailRecord detailRecord = loadDetailRecord(pool.getDetailRecordId());
        List<Alibaba1688SelectionPoolSku> skus = skuRepository.findAllByPoolIdOrderByIsPrimarySkuDescIdAsc(poolId);
        return new ReportGenerationContext(pool, detailRecord, skus);
    }

    private Alibaba1688SelectionAiReportService.AnalysisResult resolveAiAnalysis(
            Alibaba1688SelectionPool pool,
            Alibaba1688DetailRecord detailRecord,
            List<Alibaba1688SelectionPoolSku> skus,
            Alibaba1688SelectionPoolDTO.SaveReportRequest request
    ) {
        String manualJson = trimToNull(request == null ? null : request.getReportJson());
        String manualContent = trimToNull(request == null ? null : request.getReportContent());
        String manualSummary = trimToNull(request == null ? null : request.getReportSummary());
        if (manualJson != null || manualContent != null || manualSummary != null) {
            Alibaba1688SelectionAiReportService.AnalysisResult parsed = selectionAiReportService.parseStoredReportJson(manualJson);
            return parsed;
        }
        return selectionAiReportService.analyze(pool, detailRecord, skus);
    }

    private void applyAiSelectionToPool(
            Alibaba1688SelectionPool pool,
            Alibaba1688SelectionAiReportService.AnalysisResult analysis
    ) {
        if (pool == null || analysis == null) {
            return;
        }
        pool.setAiSelectionDecision(trimToNull(analysis.getDecision()));
        pool.setAiContainsLiquid(analysis.getContainsLiquid());
        pool.setAiContainsBattery(analysis.getContainsBattery());
        pool.setAiFragile(analysis.getFragile());
        pool.setAiPotentialBrandInfringement(analysis.getPotentialBrandInfringement());
        pool.setAiMaxWeightG(analysis.getMaxWeightG());
        pool.setAiMaxDimensionCm(analysis.getMaxDimensionCm());
        pool.setAiMaxDimensionSource(normalizeAiDimensionSource(analysis.getDimensionSource()));
        pool.setAiDimensionEvidence(mergeAiDimensionEvidence(
                analysis.getDimensionSource(),
                analysis.getEvidence().get("dimension")
        ));
        pool.setAiDetectedMoq(analysis.getNormalizedMoq());
        pool.setAiDetectedSalesVolume(analysis.getNormalizedSalesVolume());
        pool.setAiDetectedSalesText(trimToNull(analysis.getNormalizedSalesText()));
        applyAiOverrides(pool);
    }

    private String normalizeAiDimensionSource(String source) {
        String normalized = trimToNull(source);
        if (normalized == null) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (normalized.contains("SKU") || lower.contains("sku")) {
            return "SKU";
        }
        if (normalized.contains("包装")) {
            return "包装字段";
        }
        if (normalized.contains("商品属性") || normalized.contains("属性")) {
            return "商品属性";
        }
        if (normalized.contains("估算") || normalized.contains("推测") || normalized.contains("常识")) {
            return "估算";
        }
        if (normalized.length() <= 64) {
            return normalized;
        }
        return normalized.substring(0, 64);
    }

    private String mergeAiDimensionEvidence(String source, String evidence) {
        String normalizedSource = trimToNull(source);
        String normalizedEvidence = trimToNull(evidence);
        if (normalizedSource == null) {
            return normalizedEvidence;
        }
        if (normalizedEvidence == null) {
            return normalizedSource;
        }
        if (normalizedEvidence.contains(normalizedSource)) {
            return normalizedEvidence;
        }
        return normalizedEvidence + "\n来源：" + normalizedSource;
    }

    private void applyAiOverrides(Alibaba1688SelectionPool pool) {
        if (pool == null) {
            return;
        }
        if (pool.getAiDetectedMoq() != null) {
            pool.setMoqSnapshot(pool.getAiDetectedMoq());
        }
        if (pool.getStartBatchQtySnapshot() != null && pool.getAiDetectedMoq() == null && pool.getMoqSnapshot() == null) {
            pool.setMoqSnapshot(pool.getStartBatchQtySnapshot());
        }
        String aiSalesText = firstText(
                trimToNull(pool.getAiDetectedSalesText()),
                formatAiSalesText(pool.getAiDetectedSalesVolume())
        );
        if (StringUtils.hasText(aiSalesText)) {
            pool.setMonthlySalesSnapshot(aiSalesText);
        }
    }

    private String formatAiSalesText(Integer salesVolume) {
        if (salesVolume == null) {
            return null;
        }
        return salesVolume + "+";
    }

    private record ReportGenerationContext(
            Alibaba1688SelectionPool pool,
            Alibaba1688DetailRecord detailRecord,
            List<Alibaba1688SelectionPoolSku> skus
    ) {
    }

    private Alibaba1688DetailRecord loadDetailRecord(Long detailRecordId) {
        if (detailRecordId == null) {
            return null;
        }
        return detailRecordRepository.findById(detailRecordId).orElse(null);
    }

    private Alibaba1688SelectionPool loadPool(Long id) {
        return poolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("1688 选品池记录不存在: " + id));
    }

    private void validateDetailRecordForSelectionPool(Alibaba1688DetailRecord detailRecord) {
        if (detailRecord == null) {
            throw new IllegalArgumentException("1688 详情记录不存在");
        }

        String expectedOfferId = trimToNull(detailRecord.getOfferId());
        JsonNode extractedRoot = readJson(detailRecord.getExtractedJson());
        JsonNode parsedRoot = readJson(detailRecord.getParsedJson());

        String title = firstText(
                detailRecord.getProductName(),
                text(extractedRoot, "title"),
                text(extractedRoot, "pageTitle"),
                text(parsedRoot, "productName")
        );
        if (isKnownInvalid1688Title(title)) {
            throw new IllegalArgumentException("当前详情记录不是有效的 1688 商品详情页，请先重新采集");
        }

        boolean anyMatchedUrl = containsExpectedOfferId(detailRecord.getCanonicalUrl(), expectedOfferId)
                || containsExpectedOfferId(text(extractedRoot, "pageUrl"), expectedOfferId)
                || containsExpectedOfferId(text(extractedRoot, "canonicalUrl"), expectedOfferId)
                || containsExpectedOfferId(text(parsedRoot, "productUrl"), expectedOfferId)
                || containsExpectedOfferId(detailRecord.getDetailUrl(), expectedOfferId);
        if (StringUtils.hasText(expectedOfferId) && !anyMatchedUrl) {
            throw new IllegalArgumentException("当前详情记录疑似采集到了首页/跳转页，请先重新采集");
        }
    }

    private Alibaba1688SelectionPoolDTO.ListItem toListItem(
            Alibaba1688SelectionPool item,
            List<String> targetShopIds,
            List<String> targetShopNames,
            List<String> tags,
            int skuCount,
            int reportCount,
            Alibaba1688SelectionPoolReport latestReport,
            SkuPriceRange skuPriceRange,
            TemuSitePublishException siteException
    ) {
        return Alibaba1688SelectionPoolDTO.ListItem.builder()
                .id(item.getId())
                .detailRecordId(item.getDetailRecordId())
                .offerId(item.getOfferId())
                .detailUrl(item.getDetailUrl())
                .canonicalUrl(item.getDetailUrl())
                .sourcePlatform(item.getSourcePlatform())
                .productTitleSnapshot(item.getProductTitleSnapshot())
                .productName(item.getProductTitleSnapshot())
                .mainImageSnapshot(item.getMainImageSnapshot())
                .productMainImage(item.getMainImageSnapshot())
                .companyNameSnapshot(item.getCompanyNameSnapshot())
                .companyName(item.getCompanyNameSnapshot())
                .repeatCustomerRateSnapshot(item.getRepeatCustomerRateSnapshot())
                .serviceScoreSnapshot(item.getServiceScoreSnapshot())
                .onTimeDeliveryRateSnapshot(item.getOnTimeDeliveryRateSnapshot())
                .shopPositiveRateSnapshot(item.getShopPositiveRateSnapshot())
                .powerSellerSnapshot(item.getPowerSellerSnapshot())
                .settledYearsTextSnapshot(item.getSettledYearsTextSnapshot())
                .mainBusinessSnapshot(item.getMainBusinessSnapshot())
                .categorySnapshot(item.getCategorySnapshot())
                .baseFreightSnapshot(item.getBaseFreightSnapshot())
                .minPrice(skuPriceRange == null ? null : skuPriceRange.minPrice())
                .maxPrice(skuPriceRange == null ? null : skuPriceRange.maxPrice())
                .moqSnapshot(item.getMoqSnapshot())
                .startBatchQtySnapshot(item.getStartBatchQtySnapshot())
                .monthlySalesSnapshot(item.getMonthlySalesSnapshot())
                .overallScore(item.getOverallScore())
                .poolStatus(item.getPoolStatus())
                .status(item.getPoolStatus())
                .targetShopIds(targetShopIds)
                .targetShopNames(targetShopNames)
                .tags(tags)
                .skuCount(skuCount)
                .reportCount(reportCount)
                .temuCompeteScore(item.getTemuCompeteScore())
                .aiSelectionScore(item.getAiSelectionScore())
                .aiSelectionDecision(item.getAiSelectionDecision())
                .aiContainsLiquid(item.getAiContainsLiquid())
                .aiContainsBattery(item.getAiContainsBattery())
                .aiFragile(item.getAiFragile())
                .aiPotentialBrandInfringement(item.getAiPotentialBrandInfringement())
                .aiMaxWeightG(item.getAiMaxWeightG())
                .aiMaxDimensionCm(item.getAiMaxDimensionCm())
                .aiMaxDimensionSource(item.getAiMaxDimensionSource())
                .aiDimensionEvidence(item.getAiDimensionEvidence())
                .aiDetectedMoq(item.getAiDetectedMoq())
                .aiDetectedSalesVolume(item.getAiDetectedSalesVolume())
                .aiDetectedSalesText(item.getAiDetectedSalesText())
                .pushedProductCollectionId(item.getPushedProductCollectionId())
                .pushStatus(item.getPushStatus())
                .pushedAt(item.getPushedAt())
                .pushError(item.getPushError())
                .temuSiteExceptionBlocked(siteException != null)
                .temuSiteExceptionReason(siteException == null ? null : siteException.getReasonText())
                .note(item.getRemark())
                .latestReportStatus(latestReport == null ? null : latestReport.getStatus())
                .latestReportAt(latestReport == null ? null : latestReport.getCreatedAt())
                .publishedAt1688(item.getPublishedAt1688())
                .detailLastCollectedAt(item.getDetailLastCollectedAt())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private record SkuPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
    }

    private Map<String, TemuSitePublishException> loadExceptionByOfferId(List<Alibaba1688SelectionPool> pools) {
        if (pools == null || pools.isEmpty()) {
            return Map.of();
        }
        List<String> offerIds = pools.stream()
                .map(Alibaba1688SelectionPool::getOfferId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (offerIds.isEmpty()) {
            return Map.of();
        }
        Map<String, TemuSitePublishException> result = new HashMap<>();
        for (TemuSitePublishException exception : temuSitePublishExceptionRepository.findAllByOfferIdInAndActiveTrue(offerIds)) {
            if (StringUtils.hasText(exception.getOfferId())) {
                result.putIfAbsent(exception.getOfferId(), exception);
            }
        }
        return result;
    }

    private Map<Long, Integer> poolIdsToCountMap(List<Alibaba1688SelectionPool> pools) {
        Set<Long> poolIds = pools.stream()
                .map(Alibaba1688SelectionPool::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (poolIds.isEmpty()) {
            return Map.of();
        }
        return skuRepository.countByPoolIds(poolIds).stream()
                .collect(Collectors.toMap(
                        Alibaba1688SelectionPoolSkuRepository.PoolSkuCountView::getPoolId,
                        item -> Math.toIntExact(item.getSkuCount())
                ));
    }

    private static int nullAsMax(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private static int aiDecisionPriority(Alibaba1688SelectionPoolDTO.ListItem item) {
        if (item == null || !StringUtils.hasText(item.getAiSelectionDecision())) {
            return 1;
        }
        String normalized = item.getAiSelectionDecision().trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SUITABLE", "适合", "合适" -> 0;
            default -> 1;
        };
    }

    private static java.time.LocalDate publishedDateOf(Alibaba1688SelectionPoolDTO.ListItem item) {
        if (item == null || item.getPublishedAt1688() == null) {
            return null;
        }
        return item.getPublishedAt1688().toLocalDate();
    }

    private static BigDecimal maxWeightOf(Alibaba1688SelectionPoolDTO.ListItem item) {
        return item == null ? null : item.getAiMaxWeightG();
    }

    private static BigDecimal highestSkuPriceOf(Alibaba1688SelectionPoolDTO.ListItem item) {
        return item == null ? null : item.getMaxPrice();
    }

    private static BigDecimal parseMonthlySalesSortValue(Alibaba1688SelectionPoolDTO.ListItem item) {
        return item == null ? null : parseMonthlySalesSortValue(item.getMonthlySalesSnapshot());
    }

    private static BigDecimal parseMonthlySalesSortValue(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim().replace(",", "");
        Matcher matcher = SALES_NUMBER_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        BigDecimal base = new BigDecimal(matcher.group(1));
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("万") || lower.contains("w")) {
            return base.multiply(BigDecimal.valueOf(10_000L));
        }
        if (lower.contains("千") || lower.contains("k")) {
            return base.multiply(BigDecimal.valueOf(1_000L));
        }
        return base;
    }

    private Alibaba1688SelectionPoolDTO.DetailResponse toDetailResponse(
            Alibaba1688SelectionPool pool,
            List<Alibaba1688SelectionPoolSku> skus,
            List<Alibaba1688SelectionPoolReport> reports
    ) {
        Alibaba1688SelectionPoolReport latestReport = reports.isEmpty() ? null : reports.get(0);
        Alibaba1688SelectionPoolReport latestAiReport = findLatestReportByType(reports, "AI_SELECTION");
        Alibaba1688SelectionPoolReport latestTemuReport = findLatestReportByType(reports, "TEMU_COMPETE");
        List<Alibaba1688SelectionPoolDTO.SkuItem> skuItems = skus.stream().map(this::toSkuItem).toList();
        List<Alibaba1688SelectionPoolDTO.ReportItem> reportItems = reports.stream().map(this::toReportItem).toList();
        return Alibaba1688SelectionPoolDTO.DetailResponse.builder()
                .id(pool.getId())
                .detailRecordId(pool.getDetailRecordId())
                .offerId(pool.getOfferId())
                .detailUrl(pool.getDetailUrl())
                .canonicalUrl(pool.getDetailUrl())
                .sourcePlatform(pool.getSourcePlatform())
                .productTitleSnapshot(pool.getProductTitleSnapshot())
                .productName(pool.getProductTitleSnapshot())
                .mainImageSnapshot(pool.getMainImageSnapshot())
                .productMainImage(pool.getMainImageSnapshot())
                .carouselImageUrls(parseJsonArray(pool.getCarouselImageUrlsJson()))
                .detailImageUrls(parseJsonArray(pool.getDetailImageUrlsJson()))
                .companyNameSnapshot(pool.getCompanyNameSnapshot())
                .companyName(pool.getCompanyNameSnapshot())
                .repeatCustomerRateSnapshot(pool.getRepeatCustomerRateSnapshot())
                .serviceScoreSnapshot(pool.getServiceScoreSnapshot())
                .onTimeDeliveryRateSnapshot(pool.getOnTimeDeliveryRateSnapshot())
                .shopPositiveRateSnapshot(pool.getShopPositiveRateSnapshot())
                .powerSellerSnapshot(pool.getPowerSellerSnapshot())
                .settledYearsTextSnapshot(pool.getSettledYearsTextSnapshot())
                .mainBusinessSnapshot(pool.getMainBusinessSnapshot())
                .shippingLocationSnapshot(pool.getShippingLocationSnapshot())
                .categorySnapshot(pool.getCategorySnapshot())
                .publishedAt1688(pool.getPublishedAt1688())
                .baseFreightSnapshot(pool.getBaseFreightSnapshot())
                .moqSnapshot(pool.getMoqSnapshot())
                .startBatchQtySnapshot(pool.getStartBatchQtySnapshot())
                .moqTextSnapshot(pool.getMoqTextSnapshot())
                .monthlySalesSnapshot(pool.getMonthlySalesSnapshot())
                .salesTrendSnapshotJson(pool.getSalesTrendSnapshotJson())
                .priceStepsSnapshotJson(pool.getPriceStepsSnapshotJson())
                .overallScore(pool.getOverallScore())
                .scoreDetailJson(pool.getScoreDetailJson())
                .targetShopIds(parseJsonArray(pool.getTargetShopIdsJson()))
                .targetShopNames(parseJsonArray(pool.getTargetShopNamesJson()))
                .tags(parseJsonArray(pool.getTagsJson()))
                .selectedReason(pool.getSelectedReason())
                .rejectReason(pool.getRejectReason())
                .remark(pool.getRemark())
                .note(pool.getRemark())
                .poolStatus(pool.getPoolStatus())
                .status(pool.getPoolStatus())
                .firstSeenAt(pool.getFirstSeenAt())
                .detailLastCollectedAt(pool.getDetailLastCollectedAt())
                .assistantExtraJson(pool.getAssistantExtraJson())
                .temuCompeteAnalysisStatus(firstText(pool.getTemuCompeteAnalysisStatus(), latestTemuReport == null ? null : latestTemuReport.getStatus()))
                .temuCompeteAnalysisSummary(firstText(pool.getTemuCompeteAnalysisSummary(), latestTemuReport == null ? null : latestTemuReport.getReportSummary()))
                .temuCompeteReportTitle(firstText(pool.getTemuCompeteReportTitle(), latestTemuReport == null ? null : latestTemuReport.getReportTitle()))
                .temuCompeteReportContent(firstText(pool.getTemuCompeteReportContent(), latestTemuReport == null ? null : latestTemuReport.getReportContent()))
                .temuCompeteReportJson(firstText(pool.getTemuCompeteReportJson(), latestTemuReport == null ? null : latestTemuReport.getReportJson()))
                .temuCompeteScore(pool.getTemuCompeteScore() != null ? pool.getTemuCompeteScore() : latestTemuReport == null ? null : latestTemuReport.getScore())
                .temuCompeteAnalysisAt(pool.getTemuCompeteAnalysisAt() != null ? pool.getTemuCompeteAnalysisAt() : latestTemuReport == null ? null : latestTemuReport.getCreatedAt())
                .aiSelectionAnalysisStatus(firstText(pool.getAiSelectionAnalysisStatus(), latestAiReport == null ? null : latestAiReport.getStatus()))
                .aiSelectionAnalysisSummary(firstText(pool.getAiSelectionAnalysisSummary(), latestAiReport == null ? null : latestAiReport.getReportSummary()))
                .aiSelectionReportTitle(firstText(pool.getAiSelectionReportTitle(), latestAiReport == null ? null : latestAiReport.getReportTitle()))
                .aiSelectionReportContent(firstText(pool.getAiSelectionReportContent(), latestAiReport == null ? null : latestAiReport.getReportContent()))
                .aiSelectionReportJson(firstText(pool.getAiSelectionReportJson(), latestAiReport == null ? null : latestAiReport.getReportJson()))
                .aiSelectionScore(pool.getAiSelectionScore() != null ? pool.getAiSelectionScore() : latestAiReport == null ? null : latestAiReport.getScore())
                .aiSelectionDecision(pool.getAiSelectionDecision())
                .aiContainsLiquid(pool.getAiContainsLiquid())
                .aiContainsBattery(pool.getAiContainsBattery())
                .aiFragile(pool.getAiFragile())
                .aiPotentialBrandInfringement(pool.getAiPotentialBrandInfringement())
                .aiMaxWeightG(pool.getAiMaxWeightG())
                .aiMaxDimensionCm(pool.getAiMaxDimensionCm())
                .aiMaxDimensionSource(pool.getAiMaxDimensionSource())
                .aiDimensionEvidence(pool.getAiDimensionEvidence())
                .aiDetectedMoq(pool.getAiDetectedMoq())
                .aiDetectedSalesVolume(pool.getAiDetectedSalesVolume())
                .aiDetectedSalesText(pool.getAiDetectedSalesText())
                .aiSelectionAnalysisAt(pool.getAiSelectionAnalysisAt() != null ? pool.getAiSelectionAnalysisAt() : latestAiReport == null ? null : latestAiReport.getCreatedAt())
                .pushedProductCollectionId(pool.getPushedProductCollectionId())
                .pushStatus(pool.getPushStatus())
                .pushedAt(pool.getPushedAt())
                .pushError(pool.getPushError())
                .latestReportStatus(latestReport == null ? null : latestReport.getStatus())
                .latestReportAt(latestReport == null ? null : latestReport.getCreatedAt())
                .createdAt(pool.getCreatedAt())
                .updatedAt(pool.getUpdatedAt())
                .skus(skuItems)
                .skuRows(skuItems)
                .reports(reportItems)
                .reportList(reportItems)
                .build();
    }

    private Alibaba1688SelectionPoolDTO.SkuItem toSkuItem(Alibaba1688SelectionPoolSku item) {
        return Alibaba1688SelectionPoolDTO.SkuItem.builder()
                .id(item.getId())
                .poolId(item.getPoolId())
                .sourceSkuId(item.getSourceSkuId())
                .skuId(item.getSourceSkuId())
                .skuSpecText(item.getSkuSpecText())
                .specKey(item.getSkuSpecText())
                .skuSpecJson(item.getSkuSpecJson())
                .specJson(item.getSkuSpecJson())
                .skuImage(item.getSkuImage())
                .image(item.getSkuImage())
                .skuMainImage(item.getSkuMainImage())
                .skuPriceSnapshot(item.getSkuPriceSnapshot())
                .price(item.getSkuPriceSnapshot())
                .pageStockSnapshot(item.getPageStockSnapshot())
                .stock(item.getPageStockSnapshot())
                .manualStockQty(item.getManualStockQty())
                .stockCheckedBy(item.getStockCheckedBy())
                .stockCheckedAt(item.getStockCheckedAt())
                .weightValue(item.getWeightValue())
                .weightSource(item.getWeightSource())
                .dimensionValue(item.getDimensionValue())
                .dimensionSource(item.getDimensionSource())
                .dimensionEvidence(item.getDimensionEvidence())
                .estimatedPurchasePrice(item.getEstimatedPurchasePrice())
                .estimatedFirstLegFee(item.getEstimatedFirstLegFee())
                .estimatedTemuPrice(item.getEstimatedTemuPrice())
                .estimatedUnitProfit(item.getEstimatedUnitProfit())
                .temuFinalPrice(item.getTemuFinalPrice())
                .selectionStatus(item.getSelectionStatus())
                .isPrimarySku(item.getIsPrimarySku())
                .stockRiskLevel(item.getStockRiskLevel())
                .remark(item.getRemark())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private Alibaba1688SelectionPoolDTO.ReportItem toReportItem(Alibaba1688SelectionPoolReport item) {
        return Alibaba1688SelectionPoolDTO.ReportItem.builder()
                .id(item.getId())
                .poolId(item.getPoolId())
                .reportType(item.getReportType())
                .reportTitle(item.getReportTitle())
                .title(item.getReportTitle())
                .reportSummary(item.getReportSummary())
                .summary(item.getReportSummary())
                .reportContent(item.getReportContent())
                .reportJson(item.getReportJson())
                .dataJson(item.getReportJson())
                .contentJson(item.getReportContent())
                .status(item.getStatus())
                .versionNo(item.getVersionNo())
                .sourceType(item.getSourceType())
                .modelName(item.getModelName())
                .score(item.getScore())
                .note(null)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 JSON 失败", e);
        }
    }

    private List<String> parseJsonArray(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<?> values = objectMapper.readValue(json, List.class);
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (Object value : values) {
                if (value == null) {
                    continue;
                }
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
            return List.copyOf(result);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode child = node.path(fieldName);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        return trimToNull(child.asText());
    }

    private String buildSkuMatchKey(Alibaba1688SelectionPoolSku item) {
        return buildSkuMatchKey(item.getSourceSkuId(), item.getSkuSpecText());
    }

    private String buildSkuMatchKey(String sourceSkuId, String skuSpecText) {
        if (StringUtils.hasText(sourceSkuId)) {
            return "ID:" + sourceSkuId.trim();
        }
        return "SPEC:" + firstText(skuSpecText, "-");
    }

    private String defaultStockRisk(Integer stock) {
        if (stock == null) {
            return "UNKNOWN";
        }
        if (stock <= 0) {
            return "OUT";
        }
        if (stock <= 20) {
            return "LOW";
        }
        return "NORMAL";
    }

    private BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstText(String... values) {
        if (values == null || values.length == 0) {
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

    private Alibaba1688SelectionPoolReport findLatestReportByType(List<Alibaba1688SelectionPoolReport> reports, String reportType) {
        if (reports == null || reports.isEmpty() || !StringUtils.hasText(reportType)) {
            return null;
        }
        for (Alibaba1688SelectionPoolReport report : reports) {
            if (reportType.equalsIgnoreCase(report.getReportType())) {
                return report;
            }
        }
        return null;
    }

    private String normalizeReportType(String rawReportType) {
        String reportType = trimToNull(rawReportType);
        if (!StringUtils.hasText(reportType)) {
            return "AI_SELECTION";
        }
        String normalized = reportType.trim().toUpperCase(Locale.ROOT);
        if ("AI_SELECTION".equals(normalized) || "TEMU_COMPETE".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("仅支持 AI_SELECTION 和 TEMU_COMPETE 两种报告类型");
    }

    private String defaultReportTitle(Alibaba1688SelectionPool pool, String reportType) {
        String titleBase = StringUtils.hasText(pool.getProductTitleSnapshot()) ? pool.getProductTitleSnapshot().trim() : pool.getOfferId();
        if ("TEMU_COMPETE".equalsIgnoreCase(reportType)) {
            return titleBase + " - TEMU竞品分析";
        }
        return titleBase + " - AI选品分析";
    }

    private String buildAutoReportSummary(Alibaba1688SelectionPool pool, String reportType) {
        List<String> parts = new ArrayList<>();
        parts.add("TEMU_COMPETE".equalsIgnoreCase(reportType) ? "自动生成的 TEMU 竞品分析占位报告" : "自动生成的 AI 选品分析占位报告");
        if (StringUtils.hasText(pool.getMonthlySalesSnapshot())) {
            parts.add("月销量快照：" + pool.getMonthlySalesSnapshot());
        }
        if (pool.getMoqSnapshot() != null) {
            parts.add("MOQ：" + pool.getMoqSnapshot());
        }
        if (pool.getBaseFreightSnapshot() != null) {
            parts.add("基础快递费：" + pool.getBaseFreightSnapshot());
        }
        return String.join("；", parts);
    }

    private String buildAutoReportContent(Alibaba1688SelectionPool pool, String reportType) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("reportType", reportType);
        content.put("offerId", pool.getOfferId());
        content.put("productTitle", pool.getProductTitleSnapshot());
        content.put("companyName", pool.getCompanyNameSnapshot());
        content.put("monthlySalesSnapshot", pool.getMonthlySalesSnapshot());
        content.put("moqSnapshot", pool.getMoqSnapshot());
        content.put("baseFreightSnapshot", pool.getBaseFreightSnapshot());
        content.put("overallScore", pool.getOverallScore());
        content.put("poolStatus", pool.getPoolStatus());
        content.put("remark", "第一版先落占位报告，后续再对接真实 AI 选品分析和 TEMU 竞品分析。");
        return toJson(content);
    }

    private boolean containsExpectedOfferId(String url, String expectedOfferId) {
        if (!StringUtils.hasText(url) || !StringUtils.hasText(expectedOfferId)) {
            return false;
        }
        return url.contains("/offer/" + expectedOfferId + ".html")
                || url.contains("offerId=" + expectedOfferId);
    }

    private boolean isKnownInvalid1688Title(String title) {
        String normalized = trimToNull(title);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return normalized.contains("阿里1688首页")
                || normalized.contains("1688首页")
                || normalized.contains("全球领先的采购批发平台");
    }
}
