package com.tminos.productscene.service;

import com.tminos.productscene.dto.OcrImageTranslateWorkerDTO;
import com.tminos.productscene.entity.ImageOcrTask;
import com.tminos.productscene.entity.OcrImageTranslateWorkerLog;
import com.tminos.productscene.repository.OcrImageTranslateWorkerLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class OcrImageTranslateWorkerLogService {

    private final OcrImageTranslateWorkerLogRepository repository;

    public OcrImageTranslateWorkerLogService(OcrImageTranslateWorkerLogRepository repository) {
        this.repository = repository;
    }

    public OcrImageTranslateWorkerLog save(OcrImageTranslateWorkerLog log) {
        return repository.save(log);
    }

    public OcrImageTranslateWorkerLog build(
            ImageOcrTask task,
            String status,
            String model,
            String translatedUrl,
            String temuUrl,
            String message,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        OcrImageTranslateWorkerLog log = new OcrImageTranslateWorkerLog();
        log.setSpuId(task == null ? null : task.getSpuId());
        log.setProductId(task == null ? null : task.getProductId());
        log.setOcrTaskId(task == null ? null : task.getId());
        log.setImageType(task == null ? null : task.getImageType());
        log.setSourceField(task == null ? null : task.getSourceField());
        log.setSourceIndex(task == null ? null : task.getSourceIndex());
        log.setStatus(status);
        log.setModel(model);
        log.setOriginalUrl(task == null ? null : task.getImageUrl());
        log.setTranslatedUrl(translatedUrl);
        log.setTemuUrl(temuUrl);
        log.setMessage(message);
        log.setErrorMessage(errorMessage);
        log.setStartedAt(startedAt);
        log.setFinishedAt(finishedAt);
        if (startedAt != null && finishedAt != null) {
            log.setDurationMs(Duration.between(startedAt, finishedAt).toMillis());
        }
        return log;
    }

    public Page<OcrImageTranslateWorkerDTO.LogView> list(int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );
        return repository.findAllByOrderByCreatedAtDescIdDesc(pageable).map(this::toView);
    }

    private OcrImageTranslateWorkerDTO.LogView toView(OcrImageTranslateWorkerLog log) {
        OcrImageTranslateWorkerDTO.LogView view = new OcrImageTranslateWorkerDTO.LogView();
        view.setId(log.getId());
        view.setSpuId(log.getSpuId());
        view.setProductId(log.getProductId());
        view.setOcrTaskId(log.getOcrTaskId());
        view.setImageType(log.getImageType());
        view.setSourceField(log.getSourceField());
        view.setSourceIndex(log.getSourceIndex());
        view.setStatus(log.getStatus());
        view.setModel(log.getModel());
        view.setOriginalUrl(log.getOriginalUrl());
        view.setTranslatedUrl(log.getTranslatedUrl());
        view.setTemuUrl(log.getTemuUrl());
        view.setMessage(log.getMessage());
        view.setErrorMessage(log.getErrorMessage());
        view.setStartedAt(log.getStartedAt());
        view.setFinishedAt(log.getFinishedAt());
        view.setDurationMs(log.getDurationMs());
        view.setCreatedAt(log.getCreatedAt());
        return view;
    }
}
