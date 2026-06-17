package com.tminos.productscene.sync.service;

import com.tminos.productscene.entity.TemuShop;
import com.tminos.productscene.repository.TemuShopRepository;
import com.tminos.productscene.sync.dto.PriceReviewDTO;
import com.tminos.productscene.sync.entity.TemuPriceReviewOrder;
import com.tminos.productscene.sync.repository.TemuPriceReviewOrderRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TemuPriceReviewLowPriceRejectWorkerService {

    private static final Logger log = LoggerFactory.getLogger(TemuPriceReviewLowPriceRejectWorkerService.class);

    private final TemuPriceReviewLowPriceRejectWorkerConfigService configService;
    private final TemuPriceReviewOrderRepository reviewOrderRepository;
    private final TemuShopRepository shopRepository;
    private final TemuPriceReviewService priceReviewService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong skippedCount = new AtomicLong(0);

    private volatile Thread workerThread;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime stoppedAt;
    private volatile LocalDateTime lastScanAt;
    private volatile LocalDateTime lastWorkAt;
    private volatile LocalDateTime lastErrorAt;
    private volatile String lastError;
    private volatile Long lastOrderId;
    private volatile String lastShopId;

    public TemuPriceReviewLowPriceRejectWorkerService(
            TemuPriceReviewLowPriceRejectWorkerConfigService configService,
            TemuPriceReviewOrderRepository reviewOrderRepository,
            TemuShopRepository shopRepository,
            TemuPriceReviewService priceReviewService
    ) {
        this.configService = configService;
        this.reviewOrderRepository = reviewOrderRepository;
        this.shopRepository = shopRepository;
        this.priceReviewService = priceReviewService;
    }

    public synchronized PriceReviewDTO.LowPriceRejectWorkerStatusView start() {
        if (running.get() || (workerThread != null && workerThread.isAlive())) {
            return status();
        }
        running.set(true);
        startedAt = LocalDateTime.now();
        stoppedAt = null;
        workerThread = new Thread(this::loop, "price-review-low-price-reject-worker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("PriceReviewLowPriceRejectWorker started");
        return status();
    }

    public synchronized PriceReviewDTO.LowPriceRejectWorkerStatusView stop() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
        stoppedAt = LocalDateTime.now();
        log.info("PriceReviewLowPriceRejectWorker stopping");
        return status();
    }

    public PriceReviewDTO.LowPriceRejectWorkerStatusView restartIfRunning() {
        if (!running.get()) {
            return status();
        }
        stop();
        return start();
    }

    public PriceReviewDTO.LowPriceRejectWorkerStatusView status() {
        PriceReviewDTO.LowPriceRejectWorkerConfigView config = configService.current();
        PriceReviewDTO.LowPriceRejectWorkerStatusView view = new PriceReviewDTO.LowPriceRejectWorkerStatusView();
        view.setRunning(running.get());
        view.setEnabled(Boolean.TRUE.equals(config.getEnabled()));
        view.setMaxSuggestSupplyPrice(config.getMaxSuggestSupplyPrice());
        view.setPollMs(config.getPollMs());
        view.setBatchSize(config.getBatchSize());
        view.setReasonType(config.getReasonType());
        view.setReasonText(config.getReasonText());
        view.setSuccessCount(successCount.get());
        view.setFailureCount(failureCount.get());
        view.setSkippedCount(skippedCount.get());
        view.setLastOrderId(lastOrderId);
        view.setLastShopId(lastShopId);
        view.setStartedAt(startedAt);
        view.setStoppedAt(stoppedAt);
        view.setLastScanAt(lastScanAt);
        view.setLastWorkAt(lastWorkAt);
        view.setLastErrorAt(lastErrorAt);
        view.setLastError(lastError);
        return view;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void autoStartIfEnabled() {
        if (configService.currentEnabled()) {
            try {
                start();
            } catch (Exception e) {
                onError(e);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        stop();
    }

    private void loop() {
        while (running.get()) {
            try {
                runOnce();
                sleep(configService.currentPollMs());
            } catch (Throwable e) {
                onError(e);
                sleep(10_000L);
            }
        }
    }

    private void runOnce() {
        lastScanAt = LocalDateTime.now();
        PriceReviewDTO.LowPriceRejectWorkerConfigView config = configService.current();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            skippedCount.incrementAndGet();
            return;
        }

        List<TemuShop> shops = shopRepository.findByEnabledOrderByIdDesc(true);
        for (TemuShop shop : shops) {
            if (!running.get()) {
                break;
            }
            if (shop == null || shop.getShopId() == null || shop.getShopId().isBlank()) {
                continue;
            }
            processShop(shop.getShopId(), config);
        }
    }

    private void processShop(String shopId, PriceReviewDTO.LowPriceRejectWorkerConfigView config) {
        List<TemuPriceReviewOrder> orders = reviewOrderRepository.findLowPricePendingForWorker(
                shopId,
                config.getMaxSuggestSupplyPrice(),
                PageRequest.of(0, config.getBatchSize())
        );
        if (orders.isEmpty()) {
            return;
        }

        lastShopId = shopId;
        List<Long> localOrderIds = new ArrayList<>();
        for (TemuPriceReviewOrder order : orders) {
            if (order == null || order.getId() == null) {
                continue;
            }
            localOrderIds.add(order.getId());
            lastOrderId = order.getOrderId();
        }
        if (localOrderIds.isEmpty()) {
            return;
        }

        PriceReviewDTO.BatchReviewRequest request = new PriceReviewDTO.BatchReviewRequest();
        request.setShopId(shopId);
        request.setOrderIds(localOrderIds);
        request.setAction("REJECT");
        request.setBargainReasonList(buildReason(config));

        Map<String, Object> result = priceReviewService.batchReview(request);
        long success = toLong(result.get("successCount")) + toLong(result.get("autoCompletedCount"));
        long fail = toLong(result.get("failCount"));
        successCount.addAndGet(success);
        failureCount.addAndGet(fail);
        lastWorkAt = LocalDateTime.now();

        if (!Boolean.TRUE.equals(result.get("success"))) {
            String message = String.valueOf(result.getOrDefault("message", "低价自动拒绝失败"));
            onError(new IllegalStateException(message));
        }
        log.info("PriceReviewLowPriceRejectWorker processed shopId={}, total={}, success={}, fail={}, result={}",
                shopId, localOrderIds.size(), success, fail, summarizeResult(result));
    }

    private List<PriceReviewDTO.BargainReasonItem> buildReason(PriceReviewDTO.LowPriceRejectWorkerConfigView config) {
        PriceReviewDTO.RejectReasonComponent component = new PriceReviewDTO.RejectReasonComponent();
        component.setType(config.getReasonType());
        component.setReason(config.getReasonText());

        PriceReviewDTO.BargainReasonItem item = new PriceReviewDTO.BargainReasonItem();
        item.setComponentList(List.of(component));
        item.setExternalLinkList(List.of());
        return List.of(item);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(Math.max(ms, 1_000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void onError(Throwable e) {
        failureCount.incrementAndGet();
        lastErrorAt = LocalDateTime.now();
        lastError = e == null ? null : e.getMessage();
        log.warn("PriceReviewLowPriceRejectWorker error: {}", lastError, e);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private Map<String, Object> summarizeResult(Map<String, Object> result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (result == null) {
            return summary;
        }
        summary.put("success", result.get("success"));
        summary.put("total", result.get("total"));
        summary.put("successCount", result.get("successCount"));
        summary.put("autoCompletedCount", result.get("autoCompletedCount"));
        summary.put("failCount", result.get("failCount"));
        summary.put("message", result.get("message"));
        return summary;
    }
}
