package com.tminos.productscene.sync.executor;

import com.tminos.productscene.sync.entity.TemuPriceAdjustOrder;
import com.tminos.productscene.sync.entity.TemuPriceAdjustSku;
import com.tminos.productscene.sync.entity.TemuSyncTask;
import com.tminos.productscene.sync.repository.TemuPriceAdjustOrderRepository;
import com.tminos.productscene.sync.repository.TemuPriceAdjustSkuRepository;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PriceAdjustSyncExecutor extends AbstractSyncExecutor<Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(PriceAdjustSyncExecutor.class);
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.systemDefault();
    private static final Pattern LAST_N_DAYS_PATTERN = Pattern.compile("LAST_(\\d+)_DAYS");
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGE_NO = 100;
    private static final int MAX_RETRIES = 3;

    @Autowired private TemuPriceAdjustOrderRepository adjustOrderRepo;
    @Autowired private TemuPriceAdjustSkuRepository adjustSkuRepo;

    @Override
    protected String getSyncType() { return "PRICE_ADJUST"; }

    @Override
    protected String getPageParamName() { return "pageNo"; }

    @Override
    protected List<Map<String, Object>> doDownload(TemuSyncTask task, TemuOpenApiClient client) throws Exception {
        List<TimeWindow> windows = buildWindows(task.getSyncScope());
        List<Map<String, Object>> allData = new ArrayList<>();

        for (int windowIndex = 0; windowIndex < windows.size(); windowIndex++) {
            TimeWindow window = windows.get(windowIndex);
            logStep(task, "DOWNLOAD", "INFO",
                String.format("开始同步调价单时间窗口 %d/%d: %s ~ %s",
                    windowIndex + 1,
                    windows.size(),
                    formatWindow(window.startEpochMillis()),
                    formatWindow(window.endEpochMillis())));

            List<Map<String, Object>> windowData = downloadWindow(task, client, window);
            allData.addAll(windowData);

            logStep(task, "DOWNLOAD", "INFO",
                String.format("调价单时间窗口 %d/%d 下载完成，本窗口 %d 条，累计 %d 条",
                    windowIndex + 1,
                    windows.size(),
                    windowData.size(),
                    allData.size()));
        }
        return allData;
    }

    private List<Map<String, Object>> downloadWindow(TemuSyncTask task,
                                                     TemuOpenApiClient client,
                                                     TimeWindow window) throws Exception {
        Map<String, Object> firstPageParams = new HashMap<>();
        firstPageParams.put("createdAtBegin", window.startEpochMillis());
        firstPageParams.put("createdAtEnd", window.endEpochMillis());
        firstPageParams.put("pageNo", 1);
        firstPageParams.put("pageSize", PAGE_SIZE);

        TemuOpenApiClient.ApiResult firstResult = callPageWithRetry(client, firstPageParams, 1);
        Map<String, Object> firstMap = firstResult.resultAsMap();
        if (firstMap == null) {
            return new ArrayList<>();
        }

        int totalCount = Math.max(0, toInt(firstMap.getOrDefault("totalCount", firstMap.get("total"))) == null
                ? 0
                : toInt(firstMap.getOrDefault("totalCount", firstMap.get("total"))));
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / PAGE_SIZE));

        if (totalPages > MAX_PAGE_NO) {
            List<TimeWindow> subWindows = splitWindow(window);
            if (subWindows.size() < 2) {
                throw new RuntimeException(String.format(
                        "API %s 在窗口 %s ~ %s 内总页数 %d 超过上限 %d，且无法继续拆分",
                        TemuOpenApiClient.API_PRICE_ADJUST_QUERY,
                        formatWindow(window.startEpochMillis()),
                        formatWindow(window.endEpochMillis()),
                        totalPages,
                        MAX_PAGE_NO));
            }

            logStep(task, "DOWNLOAD", "WARN",
                    String.format("调价单窗口 %s ~ %s 预计 %d 页，超过上限 %d，自动拆分为 %d 个子窗口",
                            formatWindow(window.startEpochMillis()),
                            formatWindow(window.endEpochMillis()),
                            totalPages,
                            MAX_PAGE_NO,
                            subWindows.size()));

            List<Map<String, Object>> merged = new ArrayList<>();
            for (TimeWindow subWindow : subWindows) {
                merged.addAll(downloadWindow(task, client, subWindow));
            }
            return merged;
        }

        return consumeWindowPages(task, client, window, firstMap, totalCount, totalPages);
    }

    private List<Map<String, Object>> consumeWindowPages(TemuSyncTask task,
                                                         TemuOpenApiClient client,
                                                         TimeWindow window,
                                                         Map<String, Object> firstMap,
                                                         int totalCount,
                                                         int totalPages) throws Exception {
        List<Map<String, Object>> allData = new ArrayList<>();
        task.setDownloadTotal((task.getDownloadTotal() == null ? 0 : task.getDownloadTotal()) + totalCount);
        taskRepo.save(task);

        List<Map<String, Object>> firstPageData = extractList(firstMap, "priceAdjustOrderList", "orderList");
        if (firstPageData == null || firstPageData.isEmpty()) {
            return allData;
        }

        allData.addAll(firstPageData);
        task.setDownloadCompleted((task.getDownloadCompleted() == null ? 0 : task.getDownloadCompleted()) + firstPageData.size());
        taskRepo.save(task);
        logStep(task, "DOWNLOAD", "INFO",
                String.format("调价单窗口 %s ~ %s 第 1/%d 页下载成功，当前窗口累计 %d 条",
                        formatWindow(window.startEpochMillis()),
                        formatWindow(window.endEpochMillis()),
                        totalPages,
                        allData.size()));

        for (int pageNo = 2; pageNo <= totalPages; pageNo++) {
            Map<String, Object> reqParams = new HashMap<>();
            reqParams.put("createdAtBegin", window.startEpochMillis());
            reqParams.put("createdAtEnd", window.endEpochMillis());
            reqParams.put("pageNo", pageNo);
            reqParams.put("pageSize", PAGE_SIZE);

            TemuOpenApiClient.ApiResult result = callPageWithRetry(client, reqParams, pageNo);
            Map<String, Object> resultMap = result.resultAsMap();
            if (resultMap == null) {
                break;
            }
            List<Map<String, Object>> pageData = extractList(resultMap, "priceAdjustOrderList", "orderList");
            if (pageData == null || pageData.isEmpty()) {
                break;
            }

            allData.addAll(pageData);
            task.setDownloadCompleted((task.getDownloadCompleted() == null ? 0 : task.getDownloadCompleted()) + pageData.size());
            taskRepo.save(task);

            logStep(task, "DOWNLOAD", "INFO",
                    String.format("调价单第 %d/%d 页下载成功，当前窗口累计 %d 条",
                            pageNo,
                            totalPages,
                            allData.size()));

            if (pageData.size() < PAGE_SIZE) {
                break;
            }
        }

        return allData;
    }

    private List<TimeWindow> splitWindow(TimeWindow window) {
        long start = window.startEpochMillis();
        long end = window.endEpochMillis();
        long midpoint = start + ((end - start) / 2);
        if (midpoint <= start || midpoint >= end) {
            return List.of(window);
        }
        return List.of(
                new TimeWindow(start, midpoint),
                new TimeWindow(midpoint, end)
        );
    }

    private TemuOpenApiClient.ApiResult callPageWithRetry(TemuOpenApiClient client,
                                                           Map<String, Object> reqParams,
                                                           int pageNo) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                TemuOpenApiClient.ApiResult result = client.callApiParsed(TemuOpenApiClient.API_PRICE_ADJUST_QUERY, reqParams);
                if (result.success) {
                    return result;
                }
                String errorMsg = result.errorMsg == null || result.errorMsg.isBlank() ? "UNKNOWN_ERROR" : result.errorMsg;
                if (attempt >= MAX_RETRIES) {
                    throw new RuntimeException("API " + TemuOpenApiClient.API_PRICE_ADJUST_QUERY + " 第" + pageNo + "页失败: " + errorMsg);
                }
                sleepBeforeRetry(attempt, pageNo, errorMsg);
            } catch (Exception e) {
                lastException = e;
                if (attempt >= MAX_RETRIES) {
                    throw e;
                }
                sleepBeforeRetry(attempt, pageNo, e.getMessage());
            }
        }
        throw lastException == null
                ? new RuntimeException("API " + TemuOpenApiClient.API_PRICE_ADJUST_QUERY + " 第" + pageNo + "页失败")
                : lastException;
    }

    private void sleepBeforeRetry(int attempt, int pageNo, String errorMsg) throws InterruptedException {
        long waitMillis = 800L * attempt;
        logger.warn("调价单接口第{}页第{}次重试，原因: {}", pageNo, attempt, errorMsg);
        Thread.sleep(waitMillis);
    }

    private long toEpochMillis(LocalDateTime time) {
        return time.atZone(DEFAULT_ZONE_ID).toInstant().toEpochMilli();
    }

    private List<TimeWindow> buildWindows(String syncScope) {
        LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime start = resolveStart(syncScope, end);

        List<TimeWindow> windows = new ArrayList<>();
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            LocalDateTime next = cursor.plusWeeks(1);
            if (next.isAfter(end)) {
                next = end;
            }
            windows.add(new TimeWindow(toEpochMillis(cursor), toEpochMillis(next)));
            cursor = next;
        }
        return windows;
    }

    private LocalDateTime resolveStart(String syncScope, LocalDateTime end) {
        String normalized = syncScope == null ? "LAST_WEEK" : syncScope.trim().toUpperCase(Locale.ROOT);
        if ("LAST_YEAR".equals(normalized)) {
            return end.minusYears(1);
        }
        if ("LAST_WEEK".equals(normalized)) {
            return end.minusWeeks(1);
        }
        Matcher matcher = LAST_N_DAYS_PATTERN.matcher(normalized);
        if (matcher.matches()) {
            int days = Integer.parseInt(matcher.group(1));
            return end.minusDays(days);
        }
        return end.minusWeeks(1);
    }

    private String formatWindow(long epochMillis) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), DEFAULT_ZONE_ID).toString();
    }

    private record TimeWindow(long startEpochMillis, long endEpochMillis) {}

    @Override
    @Transactional
    protected void doPersistBatch(TemuSyncTask task, List<Map<String, Object>> batch, int batchIndex) {
        for (Map<String, Object> raw : batch) {
            String priceOrderSn = toStr(raw.get("priceOrderSn"));
            if (priceOrderSn == null) continue;

            TemuPriceAdjustOrder order = adjustOrderRepo.findByShopIdAndPriceOrderSn(task.getShopId(), priceOrderSn)
                    .orElse(new TemuPriceAdjustOrder());

            order.setShopId(task.getShopId());
            order.setPriceOrderSn(priceOrderSn);
            order.setSkcId(toLong(raw.get("skcId")));
            order.setProductName(toStr(raw.get("productName")));
            order.setPriceType(toInt(raw.get("priceType")));
            order.setSource(toStr(raw.get("source")));
            order.setAdjustReason(toStr(raw.get("adjustReason")));
            order.setNewSupplyPrice(toStr(raw.get("newSupplyPrice")));
            order.setPriceCurrency(toStr(raw.get("priceCurrency")));
            order.setRejectReason(toStr(raw.get("rejectReason")));
            order.setTrafficLowExpose(toBool(raw.get("trafficLowExpose")));
            order.setStatus(toInt(raw.get("status")));
            order.setSiteNamesJson(toJson(getFirst(raw, "siteNameList", "siteNames")));
            order.setSyncedAt(LocalDateTime.now());

            adjustOrderRepo.save(order);

            adjustSkuRepo.deleteByAdjustOrderId(order.getId());
            List<TemuPriceAdjustSku> skuRows = buildSkuRows(order.getId(), raw);
            if (!skuRows.isEmpty()) {
                adjustSkuRepo.saveAll(skuRows);
            }
        }
    }

    private List<TemuPriceAdjustSku> buildSkuRows(Long adjustOrderId, Map<String, Object> raw) {
        Object skuInfoList = getFirst(raw, "skuInfoList", "skuList");
        if (!(skuInfoList instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }

        List<TemuPriceAdjustSku> rows = new ArrayList<>();
        LinkedHashSet<String> seenKeys = new LinkedHashSet<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> skuRawMap)) {
                continue;
            }
            Long productSkuId = toLong(skuRawMap.get("productSkuId"));
            if (productSkuId == null) {
                continue;
            }
            Integer price = toInt(skuRawMap.get("price"));
            String spec = toStr(skuRawMap.get("spec"));
            String dedupeKey = String.valueOf(productSkuId) + '|' + String.valueOf(price) + '|' + String.valueOf(spec);
            if (!seenKeys.add(dedupeKey)) {
                continue;
            }
            rows.add(TemuPriceAdjustSku.builder()
                    .adjustOrderId(adjustOrderId)
                    .productSkuId(productSkuId)
                    .price(price)
                    .spec(spec)
                    .build());
        }
        return rows;
    }
}
