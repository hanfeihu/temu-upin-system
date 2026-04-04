package com.tminos.productscene.sync.service;

import com.tminos.productscene.sync.entity.TemuSyncLog;
import com.tminos.productscene.sync.repository.TemuSyncLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TemuSyncLogService {

    private final TemuSyncLogRepository logRepository;

    public TemuSyncLogService(TemuSyncLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Transactional
    public TemuSyncLog startLog(String shopId, String syncType) {
        TemuSyncLog log = TemuSyncLog.builder()
                .shopId(shopId)
                .syncType(syncType)
                .status("STARTED")
                .totalCount(0)
                .successCount(0)
                .failCount(0)
                .startedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return logRepository.save(log);
    }

    @Transactional
    public void succeedLog(Long logId, int totalCount, int successCount, int failCount) {
        logRepository.findById(logId).ifPresent(log -> {
            log.setStatus("SUCCEEDED");
            log.setTotalCount(totalCount);
            log.setSuccessCount(successCount);
            log.setFailCount(failCount);
            log.setFinishedAt(LocalDateTime.now());
            log.setUpdatedAt(LocalDateTime.now());
            logRepository.save(log);
        });
    }

    @Transactional
    public void failLog(Long logId, String errorMsg) {
        logRepository.findById(logId).ifPresent(log -> {
            log.setStatus("FAILED");
            log.setErrorMsg(errorMsg);
            log.setFinishedAt(LocalDateTime.now());
            log.setUpdatedAt(LocalDateTime.now());
            logRepository.save(log);
        });
    }

    @Transactional
    public void updateCounts(Long logId, int totalCount, int successCount, int failCount) {
        logRepository.findById(logId).ifPresent(log -> {
            log.setTotalCount(totalCount);
            log.setSuccessCount(successCount);
            log.setFailCount(failCount);
            log.setUpdatedAt(LocalDateTime.now());
            logRepository.save(log);
        });
    }

    public Page<TemuSyncLog> getLogs(String shopId, String syncType, int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "startedAt"));
        if (syncType != null && !syncType.isBlank()) {
            return logRepository.findByShopIdAndSyncType(shopId, syncType, pageRequest);
        }
        return logRepository.findByShopId(shopId, pageRequest);
    }
}
