package com.tminos.productscene.sync.executor;

import com.tminos.productscene.sync.entity.TemuSyncTask;
import com.tminos.productscene.sync.service.TemuGoodsAggregateService;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GoodsSyncExecutor extends AbstractSyncExecutor<Map<String, Object>> {

    private static final Pattern LAST_N_DAYS_PATTERN = Pattern.compile("LAST_(\\d+)_DAYS");
    private static final int PAGE_SIZE = 100;

    @Autowired private TemuGoodsAggregateService goodsAggregateService;

    @Override
    protected String getSyncType() { return "GOODS"; }

    @Override
    protected List<Map<String, Object>> doDownload(TemuSyncTask task, TemuOpenApiClient client) throws Exception {
        List<TimeWindow> windows = buildWindows(task.getSyncScope());
        List<Map<String, Object>> allData = new ArrayList<>();
        int total = 0;
        int completed = 0;

        for (int windowIndex = 0; windowIndex < windows.size(); windowIndex++) {
            TimeWindow window = windows.get(windowIndex);
            logStep(task, "DOWNLOAD", "INFO",
                    String.format("开始同步时间窗口 %d/%d: %s ~ %s",
                            windowIndex + 1,
                            windows.size(),
                            formatWindow(window.startEpochMillis()),
                            formatWindow(window.endEpochMillis())));

            int page = 1;
            int totalPages = 1;
            do {
                Map<String, Object> params = new HashMap<>();
                params.put("createdAtStart", window.startEpochMillis());
                params.put("createdAtEnd", window.endEpochMillis());
                params.put("page", page);
                params.put("pageSize", PAGE_SIZE);

                TemuOpenApiClient.ApiResult result = client.callApiParsed(TemuOpenApiClient.API_GOODS_LIST, params);
                if (!result.success) {
                    throw new RuntimeException("API " + TemuOpenApiClient.API_GOODS_LIST + " 第" + page + "页失败: " + result.errorMsg);
                }

                Map<String, Object> resultMap = result.resultAsMap();
                if (resultMap == null) {
                    break;
                }

                Integer windowTotal = toInt(resultMap.getOrDefault("totalCount", resultMap.get("total")));
                if (page == 1 && windowTotal != null) {
                    total += windowTotal;
                    task.setDownloadTotal(total);
                    taskRepo.save(task);
                }

                List<Map<String, Object>> list = extractList(resultMap, "goodsSkcList", "data", "goodsList");
                if (list == null || list.isEmpty()) {
                    break;
                }

                allData.addAll(list);
                completed += list.size();
                task.setDownloadCompleted(completed);
                taskRepo.save(task);

                totalPages = windowTotal == null ? page : Math.max(1, (int) Math.ceil((double) windowTotal / PAGE_SIZE));
                if (page % 5 == 0 || page == totalPages) {
                    logStep(task, "DOWNLOAD", "INFO",
                            String.format("时间窗口 %d/%d 已完成 %d/%d 页，累计 %d 条",
                                    windowIndex + 1,
                                    windows.size(),
                                    page,
                                    totalPages,
                                    completed));
                }
                page++;
            } while (page <= totalPages);
        }

        return deduplicateByProductSkcId(task, allData);
    }

    private List<Map<String, Object>> deduplicateByProductSkcId(TemuSyncTask task, List<Map<String, Object>> allData) {
        if (allData == null || allData.isEmpty()) {
            return List.of();
        }

        Map<Long, Map<String, Object>> deduplicated = new LinkedHashMap<>();
        int skipped = 0;
        for (Map<String, Object> raw : allData) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }

            Long productSkcId = parseLong(raw.get("productSkcId"));
            if (productSkcId == null) {
                continue;
            }

            if (deduplicated.put(productSkcId, raw) != null) {
                skipped++;
            }
        }

        if (skipped > 0) {
            logStep(task, "DOWNLOAD", "WARN",
                    String.format("检测到 %d 条重复商品记录，已按 productSkcId 去重后再入库", skipped));
        }

        return new ArrayList<>(deduplicated.values());
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
        if ("LAST_MONTH".equals(normalized)) {
            return end.minusMonths(1);
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

    private long toEpochMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            try {
                return (long) Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private String formatWindow(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).toString();
    }

    private record TimeWindow(long startEpochMillis, long endEpochMillis) {}

    @Override
    @Transactional
    protected void doPersistBatch(TemuSyncTask task, List<Map<String, Object>> batch, int batchIndex) {
        for (Map<String, Object> raw : batch) {
            goodsAggregateService.upsertGoodsAggregate(task.getShopId(), raw);
        }
    }
}
