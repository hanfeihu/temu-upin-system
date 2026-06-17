package com.tminos.productscene.sync.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TemuSensitiveAttributeConfirmScheduler {

    private static final Logger log = LoggerFactory.getLogger(TemuSensitiveAttributeConfirmScheduler.class);

    private final TemuSensitiveAttributeConfirmService confirmService;

    public TemuSensitiveAttributeConfirmScheduler(TemuSensitiveAttributeConfirmService confirmService) {
        this.confirmService = confirmService;
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void confirmPendingSensitiveAttributes() {
        try {
            confirmService.confirmPendingBatch(20);
        } catch (Exception e) {
            log.warn("自动确认 TEMU 敏感属性任务失败: {}", e.getMessage());
        }
    }
}
