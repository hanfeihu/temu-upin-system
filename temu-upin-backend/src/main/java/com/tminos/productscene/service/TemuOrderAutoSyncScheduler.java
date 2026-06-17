package com.tminos.productscene.service;

import com.tminos.productscene.dto.TemuOrderAftersaleDTO;
import com.tminos.productscene.dto.TemuOrderDTO;
import com.tminos.productscene.entity.TemuShop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TemuOrderAutoSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(TemuOrderAutoSyncScheduler.class);

    private final TemuShopService shopService;
    private final TemuOrderService orderService;
    private final DianxiaomiOrderSyncService dianxiaomiOrderSyncService;
    private final TemuOrderAftersaleService aftersaleService;

    private final AtomicBoolean incrementalRunning = new AtomicBoolean(false);
    private final AtomicBoolean compensationRunning = new AtomicBoolean(false);
    private final AtomicBoolean aftersaleOpenStatusRefreshRunning = new AtomicBoolean(false);
    private final AtomicBoolean dianxiaomiOrderSyncRunning = new AtomicBoolean(false);

    @Value("${temu.order.auto-sync.enabled:true}")
    private boolean enabled;

    @Value("${temu.order.auto-sync.incremental-hours:24}")
    private int incrementalHours;

    @Value("${temu.order.auto-sync.monthly-backfill-hours:720}")
    private int monthlyBackfillHours;

    @Value("${temu.order.auto-sync.logistics-hours:720}")
    private int logisticsHours;

    @Value("${temu.order.auto-sync.logistics-limit-per-shop:20}")
    private int logisticsLimitPerShop;

    @Value("${temu.order.auto-sync.aftersale-open-refresh-enabled:true}")
    private boolean aftersaleOpenRefreshEnabled;

    @Value("${temu.order.auto-sync.dianxiaomi-enabled:true}")
    private boolean dianxiaomiEnabled;

    public TemuOrderAutoSyncScheduler(TemuShopService shopService,
                                      TemuOrderService orderService,
                                      DianxiaomiOrderSyncService dianxiaomiOrderSyncService,
                                      TemuOrderAftersaleService aftersaleService) {
        this.shopService = shopService;
        this.orderService = orderService;
        this.dianxiaomiOrderSyncService = dianxiaomiOrderSyncService;
        this.aftersaleService = aftersaleService;
    }

    @Scheduled(
            fixedDelayString = "${temu.order.auto-sync.poll-ms:300000}",
            initialDelayString = "${temu.order.auto-sync.initial-delay-ms:60000}"
    )
    public void runIncrementalSync() {
        if (!enabled || !incrementalRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            syncAllEnabledShops(false, incrementalHours, Math.max(1, logisticsLimitPerShop / 2));
        } finally {
            incrementalRunning.set(false);
        }
    }

    @Scheduled(cron = "${temu.order.auto-sync.monthly-backfill-cron:0 35 2 * * *}")
    public void runCompensationSync() {
        if (!enabled || !compensationRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            syncAllEnabledShops(false, monthlyBackfillHours, logisticsLimitPerShop);
        } finally {
            compensationRunning.set(false);
        }
    }

    @Scheduled(
            fixedDelayString = "${temu.order.auto-sync.aftersale-open-refresh-ms:3600000}",
            initialDelayString = "${temu.order.auto-sync.aftersale-open-refresh-initial-delay-ms:120000}"
    )
    public void runAftersaleOpenStatusRefresh() {
        if (!enabled || !aftersaleOpenRefreshEnabled || !aftersaleOpenStatusRefreshRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            List<TemuShop> shops = shopService.listEnabledShops();
            if (shops.isEmpty()) {
                return;
            }
            for (TemuShop shop : shops) {
                TemuOrderAftersaleDTO.SyncResponse result = aftersaleService.refreshOpenStatus(shop.getId());
                if (result.isSuccess() && result.getTotalCount() <= 0) {
                    continue;
                }
                log.info("TEMU 开放售后状态刷新 shopId={}, refreshed={}, created={}, updated={}, success={}, message={}",
                        shop.getShopId(),
                        result.getTotalCount(),
                        result.getCreatedCount(),
                        result.getUpdatedCount(),
                        result.isSuccess(),
                        result.getMessage());
            }
        } finally {
            aftersaleOpenStatusRefreshRunning.set(false);
        }
    }

    @Scheduled(
            fixedDelayString = "${temu.order.auto-sync.dianxiaomi-poll-ms:300000}",
            initialDelayString = "${temu.order.auto-sync.dianxiaomi-initial-delay-ms:90000}"
    )
    public void runDianxiaomiOrderSync() {
        if (!enabled || !dianxiaomiEnabled || !dianxiaomiOrderSyncRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            TemuOrderDTO.SyncRequest request = new TemuOrderDTO.SyncRequest();
            request.setPageSize(100);
            request.setMaxPages(10);
            List<TemuOrderDTO.SyncResponse> results = dianxiaomiOrderSyncService.sync(request);
            for (TemuOrderDTO.SyncResponse result : results) {
                if (result == null || (result.isSuccess() && result.getTotalCount() <= 0)) {
                    continue;
                }
                log.info("店小秘订单自动同步 shopId={}, total={}, created={}, updated={}, matched={}, success={}, message={}",
                        result.getShopId(),
                        result.getTotalCount(),
                        result.getCreatedCount(),
                        result.getUpdatedCount(),
                        result.getMatchedCount(),
                        result.isSuccess(),
                        result.getMessage());
            }
        } finally {
            dianxiaomiOrderSyncRunning.set(false);
        }
    }

    private void syncAllEnabledShops(boolean fullSync, int hoursBack, int logisticsLimit) {
        List<TemuShop> shops = shopService.listEnabledShops();
        if (shops.isEmpty()) {
            return;
        }
        for (TemuShop shop : shops) {
            try {
                TemuOrderDTO.SyncRequest orderRequest = new TemuOrderDTO.SyncRequest();
                orderRequest.setShopRecordId(shop.getId());
                orderRequest.setFullSync(fullSync);
                orderRequest.setHoursBack(hoursBack > 0 ? hoursBack : null);

                TemuOrderAftersaleDTO.SyncRequest aftersaleRequest = new TemuOrderAftersaleDTO.SyncRequest();
                aftersaleRequest.setShopRecordId(shop.getId());
                aftersaleRequest.setFullSync(fullSync);
                aftersaleRequest.setHoursBack(hoursBack > 0 ? hoursBack : null);

                List<TemuOrderDTO.SyncResponse> orderResults = orderService.sync(orderRequest);
                List<TemuOrderAftersaleDTO.SyncResponse> aftersaleResults = aftersaleService.sync(aftersaleRequest);
                int logisticsCount = orderService.refreshRecentLogistics(shop.getId(), logisticsHours, logisticsLimit);

                TemuOrderDTO.SyncResponse orderSummary = orderResults.isEmpty() ? null : orderResults.get(0);
                TemuOrderAftersaleDTO.SyncResponse aftersaleSummary = aftersaleResults.isEmpty() ? null : aftersaleResults.get(0);
                log.info("TEMU 自动同步 shopId={}, fullSync={}, hoursBack={}, orderTotal={}, aftersaleTotal={}, logisticsRefreshed={}",
                        shop.getShopId(),
                        fullSync,
                        hoursBack,
                        orderSummary == null ? 0 : orderSummary.getTotalCount(),
                        aftersaleSummary == null ? 0 : aftersaleSummary.getTotalCount(),
                        logisticsCount);
            } catch (Exception e) {
                log.warn("TEMU 自动同步失败 shopId={}, fullSync={}, hoursBack={}, error={}",
                        shop.getShopId(), fullSync, hoursBack, e.getMessage());
            }
        }
    }
}
