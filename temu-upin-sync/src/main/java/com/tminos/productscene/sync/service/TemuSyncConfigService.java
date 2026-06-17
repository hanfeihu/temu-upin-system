package com.tminos.productscene.sync.service;

import com.tminos.productscene.sync.entity.TemuSyncConfig;
import com.tminos.productscene.sync.repository.TemuSyncConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TemuSyncConfigService {

    /** 预定义配置项 */
    public static final Map<String, String[]> PRESET_CONFIGS = new LinkedHashMap<>();
    static {
        PRESET_CONFIGS.put("goods_sync_thread_count", new String[]{"5", "商品信息同步线程数"});
        PRESET_CONFIGS.put("lifecycle_sync_thread_count", new String[]{"3", "商品状态同步线程数"});
        PRESET_CONFIGS.put("price_sync_thread_count", new String[]{"3", "价格同步线程数"});
        PRESET_CONFIGS.put("sync_cron", new String[]{"0 2 * * *", "每日同步 cron 表达式"});
        PRESET_CONFIGS.put("goods_auto_sync_days", new String[]{"7", "商品信息自动同步范围（天）"});
        PRESET_CONFIGS.put("price_adjust_auto_sync_days", new String[]{"7", "调价单自动同步范围（天）"});
        PRESET_CONFIGS.put("price_review_auto_reject_threshold", new String[]{"2800", "核价自动拒绝阈值（分）"});
        PRESET_CONFIGS.put("price_review_auto_approve_threshold", new String[]{"", "核价自动同意阈值（分）"});
    }

    private final TemuSyncConfigRepository configRepository;

    public TemuSyncConfigService(TemuSyncConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * 获取某个店铺的所有配置，如果没有则初始化默认值
     */
    @Transactional
    public List<TemuSyncConfig> getOrInitConfigs(String shopId) {
        List<TemuSyncConfig> existing = configRepository.findByShopId(shopId);
        if (existing != null && !existing.isEmpty()) {
            // 补充缺失的预置项
            Set<String> existingKeys = new HashSet<>();
            for (TemuSyncConfig c : existing) existingKeys.add(c.getConfigKey());
            for (Map.Entry<String, String[]> entry : PRESET_CONFIGS.entrySet()) {
                if (!existingKeys.contains(entry.getKey())) {
                    TemuSyncConfig cfg = new TemuSyncConfig();
                    cfg.setShopId(shopId);
                    cfg.setConfigKey(entry.getKey());
                    cfg.setConfigValue(entry.getValue()[0]);
                    cfg.setConfigDesc(entry.getValue()[1]);
                    cfg.setCreatedAt(LocalDateTime.now());
                    cfg.setUpdatedAt(LocalDateTime.now());
                    existing.add(configRepository.save(cfg));
                }
            }
            return existing;
        }
        // 全部初始化
        List<TemuSyncConfig> configs = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : PRESET_CONFIGS.entrySet()) {
            TemuSyncConfig cfg = new TemuSyncConfig();
            cfg.setShopId(shopId);
            cfg.setConfigKey(entry.getKey());
            cfg.setConfigValue(entry.getValue()[0]);
            cfg.setConfigDesc(entry.getValue()[1]);
            cfg.setCreatedAt(LocalDateTime.now());
            cfg.setUpdatedAt(LocalDateTime.now());
            configs.add(configRepository.save(cfg));
        }
        return configs;
    }

    /**
     * 获取单个配置值
     */
    public String getConfigValue(String shopId, String key) {
        return configRepository.findByShopIdAndConfigKey(shopId, key)
                .map(TemuSyncConfig::getConfigValue)
                .orElseGet(() -> {
                    String[] preset = PRESET_CONFIGS.get(key);
                    return preset != null ? preset[0] : null;
                });
    }

    /**
     * 获取整数配置值
     */
    public int getIntConfig(String shopId, String key, int defaultValue) {
        String val = getConfigValue(shopId, key);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 批量保存配置
     */
    @Transactional
    public List<TemuSyncConfig> saveConfigs(String shopId, Map<String, String> configMap) {
        List<TemuSyncConfig> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            TemuSyncConfig cfg = configRepository.findByShopIdAndConfigKey(shopId, entry.getKey())
                    .orElseGet(() -> {
                        TemuSyncConfig c = new TemuSyncConfig();
                        c.setShopId(shopId);
                        c.setConfigKey(entry.getKey());
                        c.setCreatedAt(LocalDateTime.now());
                        return c;
                    });
            cfg.setConfigValue(entry.getValue());
            String[] preset = PRESET_CONFIGS.get(entry.getKey());
            if (preset != null && cfg.getConfigDesc() == null) {
                cfg.setConfigDesc(preset[1]);
            }
            cfg.setUpdatedAt(LocalDateTime.now());
            result.add(configRepository.save(cfg));
        }
        return result;
    }
}
