package com.tminos.productscene.service;

import com.tminos.productscene.dto.Alibaba1688SelectionAiReportWorkerDTO;
import com.tminos.productscene.entity.Alibaba1688SelectionAiReportWorkerConfig;
import com.tminos.productscene.repository.Alibaba1688SelectionAiReportWorkerConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

@Service
public class Alibaba1688SelectionAiReportWorkerConfigService {

    public static final long DEFAULT_POLL_MS = 300_000L;
    private static final String DEFAULT_CONFIG_NAME = "默认配置";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Alibaba1688SelectionAiReportWorkerConfigRepository repository;

    public Alibaba1688SelectionAiReportWorkerConfigService(Alibaba1688SelectionAiReportWorkerConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Alibaba1688SelectionAiReportWorkerDTO.ConfigView current() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(this::toView)
                .orElseGet(this::defaultView);
    }

    @Transactional(readOnly = true)
    public boolean currentEnabled() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(Alibaba1688SelectionAiReportWorkerConfig::getEnabled)
                .map(Boolean::booleanValue)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public int currentThreadCount() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(Alibaba1688SelectionAiReportWorkerConfig::getThreadCount)
                .map(this::normalizeThreadCount)
                .orElse(1);
    }

    @Transactional
    public Alibaba1688SelectionAiReportWorkerDTO.ConfigView save(Alibaba1688SelectionAiReportWorkerDTO.UpdateConfigRequest request) {
        Alibaba1688SelectionAiReportWorkerConfig entity = repository.findTopByOrderByUpdatedAtDescIdDesc()
                .orElseGet(Alibaba1688SelectionAiReportWorkerConfig::new);
        String configName = trimToNull(request == null ? null : request.getConfigName());
        entity.setConfigName(configName == null ? DEFAULT_CONFIG_NAME : configName);
        entity.setThreadCount(normalizeThreadCount(request == null ? null : request.getThreadCount()));
        if (entity.getEnabled() == null) {
            entity.setEnabled(false);
        }
        entity.setRemark(trimToNull(request == null ? null : request.getRemark()));
        return toView(repository.save(entity));
    }

    private Alibaba1688SelectionAiReportWorkerDTO.ConfigView defaultView() {
        Alibaba1688SelectionAiReportWorkerDTO.ConfigView view = new Alibaba1688SelectionAiReportWorkerDTO.ConfigView();
        view.setId(null);
        view.setConfigName(DEFAULT_CONFIG_NAME);
        view.setThreadCount(1);
        view.setEnabled(false);
        view.setPollMs(DEFAULT_POLL_MS);
        view.setRemark("");
        return view;
    }

    private Alibaba1688SelectionAiReportWorkerDTO.ConfigView toView(Alibaba1688SelectionAiReportWorkerConfig entity) {
        Alibaba1688SelectionAiReportWorkerDTO.ConfigView view = new Alibaba1688SelectionAiReportWorkerDTO.ConfigView();
        view.setId(entity.getId());
        view.setConfigName(entity.getConfigName() == null ? DEFAULT_CONFIG_NAME : entity.getConfigName());
        view.setThreadCount(normalizeThreadCount(entity.getThreadCount()));
        view.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        view.setPollMs(DEFAULT_POLL_MS);
        view.setRemark(entity.getRemark() == null ? "" : entity.getRemark());
        if (entity.getCreatedAt() != null) {
            view.setCreatedAt(entity.getCreatedAt().format(FMT));
        }
        if (entity.getUpdatedAt() != null) {
            view.setUpdatedAt(entity.getUpdatedAt().format(FMT));
        }
        return view;
    }

    private int normalizeThreadCount(Integer value) {
        if (value == null) {
            return 1;
        }
        return Math.min(Math.max(value, 1), 20);
    }

    @Transactional
    public Alibaba1688SelectionAiReportWorkerDTO.ConfigView updateEnabled(boolean enabled) {
        Alibaba1688SelectionAiReportWorkerConfig entity = repository.findTopByOrderByUpdatedAtDescIdDesc()
                .orElseGet(Alibaba1688SelectionAiReportWorkerConfig::new);
        if (!StringUtils.hasText(entity.getConfigName())) {
            entity.setConfigName(DEFAULT_CONFIG_NAME);
        }
        if (entity.getThreadCount() == null || entity.getThreadCount() < 1) {
            entity.setThreadCount(1);
        }
        entity.setEnabled(enabled);
        return toView(repository.save(entity));
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
