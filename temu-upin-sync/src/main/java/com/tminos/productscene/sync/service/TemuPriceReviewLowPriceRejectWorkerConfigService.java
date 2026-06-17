package com.tminos.productscene.sync.service;

import com.tminos.productscene.sync.dto.PriceReviewDTO;
import com.tminos.productscene.sync.entity.TemuPriceReviewLowPriceRejectWorkerConfig;
import com.tminos.productscene.sync.repository.TemuPriceReviewLowPriceRejectWorkerConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TemuPriceReviewLowPriceRejectWorkerConfigService {

    static final String DEFAULT_CONFIG_NAME = "低价自动拒绝";
    static final int DEFAULT_MAX_SUGGEST_SUPPLY_PRICE = 2000;
    static final long DEFAULT_POLL_MS = 300_000L;
    static final int DEFAULT_BATCH_SIZE = 20;
    static final int DEFAULT_REASON_TYPE = 2;
    static final String DEFAULT_REASON_TEXT = "价格太低";

    private final TemuPriceReviewLowPriceRejectWorkerConfigRepository repository;

    public TemuPriceReviewLowPriceRejectWorkerConfigService(TemuPriceReviewLowPriceRejectWorkerConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PriceReviewDTO.LowPriceRejectWorkerConfigView current() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(this::toView)
                .orElseGet(this::defaultView);
    }

    @Transactional(readOnly = true)
    public boolean currentEnabled() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(TemuPriceReviewLowPriceRejectWorkerConfig::getEnabled)
                .map(Boolean::booleanValue)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public long currentPollMs() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(TemuPriceReviewLowPriceRejectWorkerConfig::getPollMs)
                .map(this::normalizePollMs)
                .orElse(DEFAULT_POLL_MS);
    }

    @Transactional
    public PriceReviewDTO.LowPriceRejectWorkerConfigView save(PriceReviewDTO.UpdateLowPriceRejectWorkerConfigRequest request) {
        TemuPriceReviewLowPriceRejectWorkerConfig entity = repository.findTopByOrderByUpdatedAtDescIdDesc()
                .orElseGet(TemuPriceReviewLowPriceRejectWorkerConfig::new);
        entity.setConfigName(firstNonBlank(request == null ? null : request.getConfigName(), DEFAULT_CONFIG_NAME));
        entity.setMaxSuggestSupplyPrice(normalizeMaxSuggestSupplyPrice(request == null ? null : request.getMaxSuggestSupplyPrice()));
        entity.setPollMs(normalizePollMs(request == null ? null : request.getPollMs()));
        entity.setBatchSize(normalizeBatchSize(request == null ? null : request.getBatchSize()));
        entity.setReasonType(normalizeReasonType(request == null ? null : request.getReasonType()));
        entity.setReasonText(firstNonBlank(request == null ? null : request.getReasonText(), DEFAULT_REASON_TEXT));
        if (entity.getEnabled() == null) {
            entity.setEnabled(false);
        }
        return toView(repository.save(entity));
    }

    @Transactional
    public PriceReviewDTO.LowPriceRejectWorkerConfigView updateEnabled(boolean enabled) {
        TemuPriceReviewLowPriceRejectWorkerConfig entity = repository.findTopByOrderByUpdatedAtDescIdDesc()
                .orElseGet(TemuPriceReviewLowPriceRejectWorkerConfig::new);
        entity.setConfigName(firstNonBlank(entity.getConfigName(), DEFAULT_CONFIG_NAME));
        entity.setMaxSuggestSupplyPrice(normalizeMaxSuggestSupplyPrice(entity.getMaxSuggestSupplyPrice()));
        entity.setPollMs(normalizePollMs(entity.getPollMs()));
        entity.setBatchSize(normalizeBatchSize(entity.getBatchSize()));
        entity.setReasonType(normalizeReasonType(entity.getReasonType()));
        entity.setReasonText(firstNonBlank(entity.getReasonText(), DEFAULT_REASON_TEXT));
        entity.setEnabled(enabled);
        return toView(repository.save(entity));
    }

    PriceReviewDTO.LowPriceRejectWorkerConfigView defaultView() {
        PriceReviewDTO.LowPriceRejectWorkerConfigView view = new PriceReviewDTO.LowPriceRejectWorkerConfigView();
        view.setConfigName(DEFAULT_CONFIG_NAME);
        view.setEnabled(false);
        view.setMaxSuggestSupplyPrice(DEFAULT_MAX_SUGGEST_SUPPLY_PRICE);
        view.setPollMs(DEFAULT_POLL_MS);
        view.setBatchSize(DEFAULT_BATCH_SIZE);
        view.setReasonType(DEFAULT_REASON_TYPE);
        view.setReasonText(DEFAULT_REASON_TEXT);
        return view;
    }

    private PriceReviewDTO.LowPriceRejectWorkerConfigView toView(TemuPriceReviewLowPriceRejectWorkerConfig entity) {
        PriceReviewDTO.LowPriceRejectWorkerConfigView view = new PriceReviewDTO.LowPriceRejectWorkerConfigView();
        view.setId(entity.getId());
        view.setConfigName(firstNonBlank(entity.getConfigName(), DEFAULT_CONFIG_NAME));
        view.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        view.setMaxSuggestSupplyPrice(normalizeMaxSuggestSupplyPrice(entity.getMaxSuggestSupplyPrice()));
        view.setPollMs(normalizePollMs(entity.getPollMs()));
        view.setBatchSize(normalizeBatchSize(entity.getBatchSize()));
        view.setReasonType(normalizeReasonType(entity.getReasonType()));
        view.setReasonText(firstNonBlank(entity.getReasonText(), DEFAULT_REASON_TEXT));
        view.setCreatedAt(entity.getCreatedAt());
        view.setUpdatedAt(entity.getUpdatedAt());
        return view;
    }

    private int normalizeMaxSuggestSupplyPrice(Integer value) {
        return value == null ? DEFAULT_MAX_SUGGEST_SUPPLY_PRICE : Math.min(Math.max(value, 1), 999_999_999);
    }

    private long normalizePollMs(Long value) {
        return value == null ? DEFAULT_POLL_MS : Math.min(Math.max(value, 10_000L), 86_400_000L);
    }

    private int normalizeBatchSize(Integer value) {
        return value == null ? DEFAULT_BATCH_SIZE : Math.min(Math.max(value, 1), 200);
    }

    private int normalizeReasonType(Integer value) {
        return value == null ? DEFAULT_REASON_TYPE : Math.min(Math.max(value, 0), 8);
    }

    private String firstNonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
