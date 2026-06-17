package com.tminos.productscene.service;

import com.tminos.productscene.dto.OcrImageTranslateWorkerDTO;
import com.tminos.productscene.entity.OcrImageTranslateWorkerConfig;
import com.tminos.productscene.repository.OcrImageTranslateWorkerConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OcrImageTranslateWorkerConfigService {

    static final int DEFAULT_MAX_CHINESE_IMAGE_COUNT = 5;
    static final int DEFAULT_BATCH_SIZE = 1;
    static final long DEFAULT_POLL_MS = 60_000L;
    static final String DEFAULT_PROVIDER = "ai";
    static final String DEFAULT_MODEL = "gpt-image-2";
    static final String DEFAULT_QUALITY = "medium";
    private static final String DEFAULT_CONFIG_NAME = "默认配置";

    private final OcrImageTranslateWorkerConfigRepository repository;

    public OcrImageTranslateWorkerConfigService(OcrImageTranslateWorkerConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public OcrImageTranslateWorkerDTO.ConfigView current() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(this::toView)
                .orElseGet(this::defaultView);
    }

    @Transactional(readOnly = true)
    public boolean currentEnabled() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(OcrImageTranslateWorkerConfig::getEnabled)
                .map(Boolean::booleanValue)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public long currentPollMs() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(OcrImageTranslateWorkerConfig::getPollMs)
                .map(this::normalizePollMs)
                .orElse(DEFAULT_POLL_MS);
    }

    @Transactional
    public OcrImageTranslateWorkerDTO.ConfigView save(OcrImageTranslateWorkerDTO.UpdateConfigRequest request) {
        OcrImageTranslateWorkerConfig entity = repository.findTopByOrderByUpdatedAtDescIdDesc()
                .orElseGet(OcrImageTranslateWorkerConfig::new);
        entity.setConfigName(firstNonBlank(request == null ? null : request.getConfigName(), DEFAULT_CONFIG_NAME));
        entity.setMaxChineseImageCount(normalizeMaxChineseImageCount(request == null ? null : request.getMaxChineseImageCount()));
        entity.setBatchSize(normalizeBatchSize(request == null ? null : request.getBatchSize()));
        entity.setPollMs(normalizePollMs(request == null ? null : request.getPollMs()));
        entity.setProvider(normalizeProvider(request == null ? null : request.getProvider()));
        entity.setModel(firstNonBlank(request == null ? null : request.getModel(), DEFAULT_MODEL));
        entity.setQuality(normalizeQuality(request == null ? null : request.getQuality()));
        entity.setRemark(trimToNull(request == null ? null : request.getRemark()));
        if (entity.getEnabled() == null) {
            entity.setEnabled(false);
        }
        return toView(repository.save(entity));
    }

    @Transactional
    public OcrImageTranslateWorkerDTO.ConfigView updateEnabled(boolean enabled) {
        OcrImageTranslateWorkerConfig entity = repository.findTopByOrderByUpdatedAtDescIdDesc()
                .orElseGet(OcrImageTranslateWorkerConfig::new);
        entity.setConfigName(firstNonBlank(entity.getConfigName(), DEFAULT_CONFIG_NAME));
        entity.setMaxChineseImageCount(normalizeMaxChineseImageCount(entity.getMaxChineseImageCount()));
        entity.setBatchSize(normalizeBatchSize(entity.getBatchSize()));
        entity.setPollMs(normalizePollMs(entity.getPollMs()));
        entity.setProvider(normalizeProvider(entity.getProvider()));
        entity.setModel(firstNonBlank(entity.getModel(), DEFAULT_MODEL));
        entity.setQuality(normalizeQuality(entity.getQuality()));
        entity.setEnabled(enabled);
        return toView(repository.save(entity));
    }

    OcrImageTranslateWorkerDTO.ConfigView defaultView() {
        OcrImageTranslateWorkerDTO.ConfigView view = new OcrImageTranslateWorkerDTO.ConfigView();
        view.setConfigName(DEFAULT_CONFIG_NAME);
        view.setEnabled(false);
        view.setMaxChineseImageCount(DEFAULT_MAX_CHINESE_IMAGE_COUNT);
        view.setBatchSize(DEFAULT_BATCH_SIZE);
        view.setPollMs(DEFAULT_POLL_MS);
        view.setProvider(DEFAULT_PROVIDER);
        view.setModel(DEFAULT_MODEL);
        view.setQuality(DEFAULT_QUALITY);
        view.setRemark("");
        return view;
    }

    private OcrImageTranslateWorkerDTO.ConfigView toView(OcrImageTranslateWorkerConfig entity) {
        OcrImageTranslateWorkerDTO.ConfigView view = new OcrImageTranslateWorkerDTO.ConfigView();
        view.setId(entity.getId());
        view.setConfigName(firstNonBlank(entity.getConfigName(), DEFAULT_CONFIG_NAME));
        view.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        view.setMaxChineseImageCount(normalizeMaxChineseImageCount(entity.getMaxChineseImageCount()));
        view.setBatchSize(normalizeBatchSize(entity.getBatchSize()));
        view.setPollMs(normalizePollMs(entity.getPollMs()));
        view.setProvider(normalizeProvider(entity.getProvider()));
        view.setModel(firstNonBlank(entity.getModel(), DEFAULT_MODEL));
        view.setQuality(normalizeQuality(entity.getQuality()));
        view.setRemark(entity.getRemark() == null ? "" : entity.getRemark());
        view.setCreatedAt(entity.getCreatedAt());
        view.setUpdatedAt(entity.getUpdatedAt());
        return view;
    }

    private int normalizeMaxChineseImageCount(Integer value) {
        return value == null ? DEFAULT_MAX_CHINESE_IMAGE_COUNT : Math.min(Math.max(value, 1), 200);
    }

    private int normalizeBatchSize(Integer value) {
        return value == null ? DEFAULT_BATCH_SIZE : Math.min(Math.max(value, 1), 20);
    }

    private long normalizePollMs(Long value) {
        return value == null ? DEFAULT_POLL_MS : Math.min(Math.max(value, 10_000L), 3_600_000L);
    }

    private String normalizeQuality(String value) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            return DEFAULT_QUALITY;
        }
        normalized = normalized.toLowerCase();
        return ("auto".equals(normalized) || "low".equals(normalized) || "medium".equals(normalized) || "high".equals(normalized))
                ? normalized
                : DEFAULT_QUALITY;
    }

    private String normalizeProvider(String value) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            return DEFAULT_PROVIDER;
        }
        normalized = normalized.toLowerCase();
        return ("ai".equals(normalized) || "aliyun".equals(normalized) || "ali".equals(normalized))
                ? ("ali".equals(normalized) ? "aliyun" : normalized)
                : DEFAULT_PROVIDER;
    }

    private String firstNonBlank(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
