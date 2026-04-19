package com.tminos.productscene.service;

import com.tminos.productscene.dto.LogisticsProviderConfigDTO;
import com.tminos.productscene.entity.LogisticsProviderConfig;
import com.tminos.productscene.repository.LogisticsProviderConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

@Service
public class LogisticsProviderConfigService {

    public static final String HAOYUAN_PROVIDER_CODE = "HAOYUAN";
    private static final String LEGACY_ERP_APPLICATION_YAML = "/Users/a1/tminos/tminos-erp/tminos-erp-main/src/main/resources/application.yml";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogisticsProviderConfigRepository repository;

    public LogisticsProviderConfigService(LogisticsProviderConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<LogisticsProviderConfigDTO.View> list(Boolean enabled) {
        List<LogisticsProviderConfig> rows;
        if (enabled == null) {
            rows = repository.findAll();
            rows.sort((a, b) -> Long.compare(
                    b == null || b.getId() == null ? Long.MIN_VALUE : b.getId(),
                    a == null || a.getId() == null ? Long.MIN_VALUE : a.getId()
            ));
        } else {
            rows = repository.findByEnabledOrderByIdDesc(enabled);
        }
        List<LogisticsProviderConfigDTO.View> out = new ArrayList<>();
        for (LogisticsProviderConfig row : rows) {
            out.add(toView(row));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public LogisticsProviderConfig getEnabledByCodeOrThrow(String providerCode) {
        String normalizedCode = normalizeCode(providerCode);
        LogisticsProviderConfig config = repository.findByProviderCode(normalizedCode)
                .orElseThrow(() -> new IllegalStateException("未找到物流服务商配置: " + normalizedCode));
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new IllegalStateException("物流服务商未启用: " + normalizedCode);
        }
        return config;
    }

    @Transactional
    public LogisticsProviderConfigDTO.View upsert(Long id, LogisticsProviderConfigDTO.UpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        String providerCode = normalizeCode(request.getProviderCode());
        String providerName = trim(request.getProviderName());
        if (!StringUtils.hasText(providerCode)) {
            throw new IllegalArgumentException("providerCode 不能为空");
        }
        if (!StringUtils.hasText(providerName)) {
            throw new IllegalArgumentException("providerName 不能为空");
        }

        LogisticsProviderConfig entity;
        if (id != null) {
            entity = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("logistics provider config not found"));
        } else {
            entity = repository.findByProviderCode(providerCode).orElseGet(LogisticsProviderConfig::new);
        }

        entity.setProviderCode(providerCode);
        entity.setProviderName(providerName);
        entity.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        entity.setBaseUrl(trim(request.getBaseUrl()));
        if (StringUtils.hasText(request.getAppToken())) {
            entity.setAppToken(request.getAppToken().trim());
        }
        if (StringUtils.hasText(request.getAppKey())) {
            entity.setAppKey(request.getAppKey().trim());
        }
        entity.setConnectTimeoutMs(request.getConnectTimeoutMs());
        entity.setReadTimeoutMs(request.getReadTimeoutMs());
        entity.setExtraConfigJson(trim(request.getExtraConfigJson()));
        return toView(repository.save(entity));
    }

    @Transactional
    public LogisticsProviderConfigDTO.View importLegacyHaoyuanConfig() {
        Properties properties = loadLegacyYamlProperties();
        LogisticsProviderConfigDTO.UpsertRequest request = new LogisticsProviderConfigDTO.UpsertRequest();
        request.setProviderCode(HAOYUAN_PROVIDER_CODE);
        request.setProviderName("浩远国际");
        request.setEnabled(parseBoolean(properties.getProperty("logistics.haoyuan.enabled"), true));
        request.setBaseUrl(trim(properties.getProperty("logistics.haoyuan.base-url")));
        request.setAppToken(trim(properties.getProperty("logistics.haoyuan.app-token")));
        request.setAppKey(trim(properties.getProperty("logistics.haoyuan.app-key")));
        request.setConnectTimeoutMs(parseInteger(properties.getProperty("logistics.haoyuan.connect-timeout"), 10000));
        request.setReadTimeoutMs(parseInteger(properties.getProperty("logistics.haoyuan.read-timeout"), 30000));
        return upsert(null, request);
    }

    private Properties loadLegacyYamlProperties() {
        FileSystemResource resource = new FileSystemResource(LEGACY_ERP_APPLICATION_YAML);
        if (!resource.exists()) {
            throw new IllegalStateException("未找到旧 ERP 配置文件: " + LEGACY_ERP_APPLICATION_YAML);
        }
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(resource);
        Properties properties = factoryBean.getObject();
        if (properties == null) {
            throw new IllegalStateException("读取旧 ERP logistics 配置失败");
        }
        return properties;
    }

    private static LogisticsProviderConfigDTO.View toView(LogisticsProviderConfig entity) {
        LogisticsProviderConfigDTO.View view = new LogisticsProviderConfigDTO.View();
        view.setId(entity.getId());
        view.setProviderCode(entity.getProviderCode());
        view.setProviderName(entity.getProviderName());
        view.setEnabled(entity.getEnabled());
        view.setBaseUrl(entity.getBaseUrl());
        view.setAppTokenMasked(mask(entity.getAppToken()));
        view.setAppKeyMasked(mask(entity.getAppKey()));
        view.setConnectTimeoutMs(entity.getConnectTimeoutMs());
        view.setReadTimeoutMs(entity.getReadTimeoutMs());
        view.setExtraConfigJson(entity.getExtraConfigJson());
        if (entity.getCreatedAt() != null) {
            view.setCreatedAt(entity.getCreatedAt().format(FMT));
        }
        if (entity.getUpdatedAt() != null) {
            view.setUpdatedAt(entity.getUpdatedAt().format(FMT));
        }
        return view;
    }

    private static String normalizeCode(String providerCode) {
        String text = trim(providerCode);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static Integer parseInteger(String value, int fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Boolean parseBoolean(String value, boolean fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static String mask(String secret) {
        if (!StringUtils.hasText(secret)) {
            return "";
        }
        String text = secret.trim();
        if (text.length() <= 8) {
            return "********";
        }
        return text.substring(0, 3) + "********" + text.substring(text.length() - 3);
    }
}
