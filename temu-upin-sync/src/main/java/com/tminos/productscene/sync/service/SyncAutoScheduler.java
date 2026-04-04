package com.tminos.productscene.sync.service;

import com.tminos.productscene.entity.TemuShop;
import com.tminos.productscene.repository.TemuShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SyncAutoScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncAutoScheduler.class);

    private final TemuShopRepository shopRepository;
    private final TemuSyncConfigService syncConfigService;
    private final SyncTaskService syncTaskService;

    public SyncAutoScheduler(TemuShopRepository shopRepository,
                             TemuSyncConfigService syncConfigService,
                             SyncTaskService syncTaskService) {
        this.shopRepository = shopRepository;
        this.syncConfigService = syncConfigService;
        this.syncTaskService = syncTaskService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void runAutoSync() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        List<TemuShop> shops = shopRepository.findByEnabledOrderByIdDesc(true);
        for (TemuShop shop : shops) {
            if (shop == null || shop.getShopId() == null || shop.getShopId().isBlank()) {
                continue;
            }
            String cron = syncConfigService.getConfigValue(shop.getShopId(), "sync_cron");
            if (!matchesCron(cron, now)) {
                continue;
            }
            try {
                syncTaskService.createAutoTasks(shop.getShopId());
            } catch (Exception e) {
                log.warn("自动同步触发失败 shopId={}, error={}", shop.getShopId(), e.getMessage());
            }
        }
    }

    private boolean matchesCron(String cron, LocalDateTime now) {
        String normalized = cron == null || cron.isBlank() ? "0 2 * * *" : cron.trim();
        String[] parts = normalized.split("\\s+");
        if (parts.length != 5) {
            return false;
        }
        return matchesPart(parts[0], now.getMinute())
                && matchesPart(parts[1], now.getHour())
                && matchesPart(parts[2], now.getDayOfMonth())
                && matchesPart(parts[3], now.getMonthValue())
                && matchesPart(parts[4], dayOfWeekValue(now.getDayOfWeek()));
    }

    private int dayOfWeekValue(DayOfWeek dayOfWeek) {
        return dayOfWeek.getValue() % 7;
    }

    private boolean matchesPart(String expr, int actual) {
        if (expr == null || expr.isBlank() || "*".equals(expr)) {
            return true;
        }
        for (String part : expr.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains("/")) {
                String[] stepParts = trimmed.split("/");
                if (stepParts.length == 2 && "*".equals(stepParts[0])) {
                    int step = Integer.parseInt(stepParts[1]);
                    if (step > 0 && actual % step == 0) {
                        return true;
                    }
                }
                continue;
            }
            if (trimmed.contains("-")) {
                String[] rangeParts = trimmed.split("-");
                if (rangeParts.length == 2) {
                    int start = Integer.parseInt(rangeParts[0]);
                    int end = Integer.parseInt(rangeParts[1]);
                    if (actual >= start && actual <= end) {
                        return true;
                    }
                }
                continue;
            }
            if (Integer.parseInt(trimmed) == actual) {
                return true;
            }
        }
        return false;
    }
}