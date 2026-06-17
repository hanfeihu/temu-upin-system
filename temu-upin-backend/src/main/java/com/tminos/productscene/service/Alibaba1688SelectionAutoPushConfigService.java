package com.tminos.productscene.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.Alibaba1688SelectionAutoPushDTO;
import com.tminos.productscene.entity.Alibaba1688SelectionAutoPushConfig;
import com.tminos.productscene.entity.TemuShop;
import com.tminos.productscene.repository.Alibaba1688SelectionAutoPushConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class Alibaba1688SelectionAutoPushConfigService {

    static final int DEFAULT_BATCH_SIZE = 1;
    static final long DEFAULT_POLL_MS = 60_000L;
    private static final String DEFAULT_CONFIG_NAME = "默认配置";
    private static final List<String> DEFAULT_TARGET_SHOP_IDS = List.of("1");
    private static final String DEFAULT_TARGET_SHOP_NAME = "1店";

    private final Alibaba1688SelectionAutoPushConfigRepository repository;
    private final TargetShopBindingService targetShopBindingService;
    private final TemuShopService temuShopService;
    private final ObjectMapper objectMapper;

    public Alibaba1688SelectionAutoPushConfigService(
            Alibaba1688SelectionAutoPushConfigRepository repository,
            TargetShopBindingService targetShopBindingService,
            TemuShopService temuShopService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.targetShopBindingService = targetShopBindingService;
        this.temuShopService = temuShopService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Alibaba1688SelectionAutoPushDTO.ConfigView current() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(this::toView)
                .orElseGet(this::defaultView);
    }

    @Transactional(readOnly = true)
    public boolean currentEnabled() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(Alibaba1688SelectionAutoPushConfig::getEnabled)
                .map(Boolean::booleanValue)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public int currentBatchSize() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(Alibaba1688SelectionAutoPushConfig::getBatchSize)
                .map(this::normalizeBatchSize)
                .orElse(DEFAULT_BATCH_SIZE);
    }

    @Transactional(readOnly = true)
    public long currentPollMs() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(Alibaba1688SelectionAutoPushConfig::getPollMs)
                .map(this::normalizePollMs)
                .orElse(DEFAULT_POLL_MS);
    }

    @Transactional(readOnly = true)
    public boolean currentForceCreate() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(Alibaba1688SelectionAutoPushConfig::getForceCreate)
                .map(Boolean::booleanValue)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<String> currentTargetShopIds() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(entity -> parseJsonArray(entity.getTargetShopIdsJson()))
                .filter(list -> !list.isEmpty())
                .orElseGet(() -> defaultTargetShopBinding().shopIds());
    }

    @Transactional
    public Alibaba1688SelectionAutoPushDTO.ConfigView save(Alibaba1688SelectionAutoPushDTO.UpdateConfigRequest request) {
        Alibaba1688SelectionAutoPushConfig entity = repository.findTopByOrderByUpdatedAtDescIdDesc()
                .orElseGet(Alibaba1688SelectionAutoPushConfig::new);
        String configName = trimToNull(request == null ? null : request.getConfigName());
        entity.setConfigName(configName == null ? DEFAULT_CONFIG_NAME : configName);
        TargetShopBindingService.TargetShopBinding binding = request == null || request.getTargetShopIds() == null
                ? defaultTargetShopBinding()
                : targetShopBindingService.resolve(request.getTargetShopIds());
        if (binding.shopIds().isEmpty()) {
            throw new IllegalArgumentException("自动推送必须选择至少一个店铺");
        }
        entity.setTargetShopIdsJson(toJson(binding.shopIds()));
        entity.setTargetShopNamesJson(toJson(binding.shopNames()));
        entity.setBatchSize(normalizeBatchSize(request == null ? null : request.getBatchSize()));
        entity.setPollMs(normalizePollMs(request == null ? null : request.getPollMs()));
        entity.setForceCreate(request != null && Boolean.TRUE.equals(request.getForceCreate()));
        if (entity.getEnabled() == null) {
            entity.setEnabled(false);
        }
        entity.setRemark(trimToNull(request == null ? null : request.getRemark()));
        return toView(repository.save(entity));
    }

    @Transactional
    public Alibaba1688SelectionAutoPushDTO.ConfigView updateEnabled(boolean enabled) {
        Alibaba1688SelectionAutoPushConfig entity = repository.findTopByOrderByUpdatedAtDescIdDesc()
                .orElseGet(Alibaba1688SelectionAutoPushConfig::new);
        if (!StringUtils.hasText(entity.getConfigName())) {
            entity.setConfigName(DEFAULT_CONFIG_NAME);
        }
        if (parseJsonArray(entity.getTargetShopIdsJson()).isEmpty()) {
            TargetShopBindingService.TargetShopBinding binding = defaultTargetShopBinding();
            entity.setTargetShopIdsJson(toJson(binding.shopIds()));
            entity.setTargetShopNamesJson(toJson(binding.shopNames()));
        }
        entity.setBatchSize(normalizeBatchSize(entity.getBatchSize()));
        entity.setPollMs(normalizePollMs(entity.getPollMs()));
        entity.setEnabled(enabled);
        if (entity.getForceCreate() == null) {
            entity.setForceCreate(false);
        }
        return toView(repository.save(entity));
    }

    Alibaba1688SelectionAutoPushDTO.ConfigView defaultView() {
        Alibaba1688SelectionAutoPushDTO.ConfigView view = new Alibaba1688SelectionAutoPushDTO.ConfigView();
        TargetShopBindingService.TargetShopBinding binding = defaultTargetShopBinding();
        view.setConfigName(DEFAULT_CONFIG_NAME);
        view.setEnabled(false);
        view.setTargetShopIds(binding.shopIds());
        view.setTargetShopNames(binding.shopNames());
        view.setBatchSize(DEFAULT_BATCH_SIZE);
        view.setPollMs(DEFAULT_POLL_MS);
        view.setForceCreate(false);
        view.setRemark("");
        return view;
    }

    private TargetShopBindingService.TargetShopBinding defaultTargetShopBinding() {
        try {
            return targetShopBindingService.resolve(DEFAULT_TARGET_SHOP_IDS);
        } catch (RuntimeException ignored) {
            // Some environments name the default shop "1店" but use a real TEMU shopId.
        }
        List<TemuShop> enabledShops = temuShopService.listEnabledShops();
        if (enabledShops == null || enabledShops.isEmpty()) {
            return new TargetShopBindingService.TargetShopBinding(List.of(), List.of());
        }
        TemuShop selected = enabledShops.stream()
                .filter(shop -> shop != null
                        && (DEFAULT_TARGET_SHOP_NAME.equalsIgnoreCase(trimToNull(shop.getShopName()))
                        || DEFAULT_TARGET_SHOP_IDS.get(0).equals(trimToNull(shop.getShopId()))))
                .findFirst()
                .orElse(enabledShops.get(0));
        String shopId = trimToNull(selected.getShopId());
        if (shopId == null) {
            return new TargetShopBindingService.TargetShopBinding(List.of(), List.of());
        }
        String shopName = trimToNull(selected.getShopName());
        return new TargetShopBindingService.TargetShopBinding(
                List.of(shopId),
                List.of(shopName == null ? shopId : shopName)
        );
    }

    private Alibaba1688SelectionAutoPushDTO.ConfigView toView(Alibaba1688SelectionAutoPushConfig entity) {
        Alibaba1688SelectionAutoPushDTO.ConfigView view = new Alibaba1688SelectionAutoPushDTO.ConfigView();
        view.setId(entity.getId());
        view.setConfigName(entity.getConfigName() == null ? DEFAULT_CONFIG_NAME : entity.getConfigName());
        view.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        view.setTargetShopIds(parseJsonArray(entity.getTargetShopIdsJson()));
        view.setTargetShopNames(parseJsonArray(entity.getTargetShopNamesJson()));
        view.setBatchSize(normalizeBatchSize(entity.getBatchSize()));
        view.setPollMs(normalizePollMs(entity.getPollMs()));
        view.setForceCreate(Boolean.TRUE.equals(entity.getForceCreate()));
        view.setRemark(entity.getRemark() == null ? "" : entity.getRemark());
        view.setCreatedAt(entity.getCreatedAt());
        view.setUpdatedAt(entity.getUpdatedAt());
        return view;
    }

    private int normalizeBatchSize(Integer value) {
        return value == null ? DEFAULT_BATCH_SIZE : Math.min(Math.max(value, 1), 20);
    }

    private long normalizePollMs(Long value) {
        return value == null ? DEFAULT_POLL_MS : Math.min(Math.max(value, 10_000L), 3_600_000L);
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            return "[]";
        }
    }

    List<String> parseJsonArray(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<>() {});
            return values == null ? new ArrayList<>() : values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
