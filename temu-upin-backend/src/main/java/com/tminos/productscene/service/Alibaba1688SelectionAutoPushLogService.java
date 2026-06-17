package com.tminos.productscene.service;

import com.tminos.productscene.dto.Alibaba1688SelectionAutoPushDTO;
import com.tminos.productscene.entity.Alibaba1688SelectionAutoPushLog;
import com.tminos.productscene.repository.Alibaba1688SelectionAutoPushLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class Alibaba1688SelectionAutoPushLogService {

    private final Alibaba1688SelectionAutoPushLogRepository repository;
    private final Alibaba1688SelectionAutoPushConfigService configService;

    public Alibaba1688SelectionAutoPushLogService(
            Alibaba1688SelectionAutoPushLogRepository repository,
            Alibaba1688SelectionAutoPushConfigService configService
    ) {
        this.repository = repository;
        this.configService = configService;
    }

    public Alibaba1688SelectionAutoPushLog save(Alibaba1688SelectionAutoPushLog log) {
        return repository.save(log);
    }

    public Page<Alibaba1688SelectionAutoPushDTO.LogView> list(int page, int size) {
        return repository.findAllByOrderByCreatedAtDescIdDesc(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))
        ).map(this::toView);
    }

    Alibaba1688SelectionAutoPushLog build(
            Long poolId,
            String offerId,
            String status,
            List<String> targetShopIds,
            List<String> targetShopNames,
            String message,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        Long durationMs = null;
        if (startedAt != null && finishedAt != null) {
            durationMs = java.time.Duration.between(startedAt, finishedAt).toMillis();
        }
        Alibaba1688SelectionAutoPushLog log = new Alibaba1688SelectionAutoPushLog();
        log.setPoolId(poolId);
        log.setOfferId(offerId);
        log.setStatus(status);
        log.setTargetShopIdsJson(toJson(targetShopIds));
        log.setTargetShopNamesJson(toJson(targetShopNames));
        log.setMessage(message);
        log.setErrorMessage(errorMessage);
        log.setStartedAt(startedAt);
        log.setFinishedAt(finishedAt);
        log.setDurationMs(durationMs);
        return log;
    }

    private Alibaba1688SelectionAutoPushDTO.LogView toView(Alibaba1688SelectionAutoPushLog log) {
        Alibaba1688SelectionAutoPushDTO.LogView view = new Alibaba1688SelectionAutoPushDTO.LogView();
        view.setId(log.getId());
        view.setPoolId(log.getPoolId());
        view.setOfferId(log.getOfferId());
        view.setProductCollectionId(log.getProductCollectionId());
        view.setStatus(log.getStatus());
        view.setTargetShopIds(configService.parseJsonArray(log.getTargetShopIdsJson()));
        view.setTargetShopNames(configService.parseJsonArray(log.getTargetShopNamesJson()));
        view.setMessage(log.getMessage());
        view.setErrorMessage(log.getErrorMessage());
        view.setStartedAt(log.getStartedAt());
        view.setFinishedAt(log.getFinishedAt());
        view.setDurationMs(log.getDurationMs());
        view.setCreatedAt(log.getCreatedAt());
        return view;
    }

    private String toJson(List<String> values) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            return "[]";
        }
    }
}
