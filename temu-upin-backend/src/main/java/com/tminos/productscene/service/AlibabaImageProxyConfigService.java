package com.tminos.productscene.service;

import com.tminos.productscene.dto.AlibabaImageProxyConfigDTO;
import com.tminos.productscene.entity.AlibabaImageProxyConfig;
import com.tminos.productscene.repository.AlibabaImageProxyConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

@Service
public class AlibabaImageProxyConfigService {

    private static final String DEFAULT_CONFIG_NAME = "默认配置";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlibabaImageProxyConfigRepository repository;

    public AlibabaImageProxyConfigService(AlibabaImageProxyConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AlibabaImageProxyConfigDTO.View current() {
        return repository.findTopByOrderByUpdatedAtDescIdDesc()
                .map(this::toView)
                .orElseGet(this::buildDefaultView);
    }

    @Transactional
    public AlibabaImageProxyConfigDTO.View saveCurrent(AlibabaImageProxyConfigDTO.UpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        AlibabaImageProxyConfig entity = repository.findTopByOrderByUpdatedAtDescIdDesc()
                .orElseGet(AlibabaImageProxyConfig::new);
        return save(entity, request);
    }

    @Transactional
    public AlibabaImageProxyConfigDTO.View update(Long id, AlibabaImageProxyConfigDTO.UpsertRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("配置 ID 不能为空");
        }
        AlibabaImageProxyConfig entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("阿里图片代理配置不存在: " + id));
        return save(entity, request);
    }

    private AlibabaImageProxyConfigDTO.View save(AlibabaImageProxyConfig entity, AlibabaImageProxyConfigDTO.UpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        String configName = trimToNull(request.getConfigName());
        entity.setConfigName(configName == null ? DEFAULT_CONFIG_NAME : configName);
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        } else if (entity.getEnabled() == null) {
            entity.setEnabled(false);
        }
        entity.setProxyBaseUrl(trimToNull(request.getProxyBaseUrl()));
        entity.setImageProxyPath(trimToNull(request.getImageProxyPath()));
        entity.setAllowedHostsText(normalizeMultilineText(request.getAllowedHostsText()));
        entity.setRemark(normalizeMultilineText(request.getRemark()));
        return toView(repository.save(entity));
    }

    private AlibabaImageProxyConfigDTO.View buildDefaultView() {
        AlibabaImageProxyConfigDTO.View view = new AlibabaImageProxyConfigDTO.View();
        view.setId(null);
        view.setConfigName("");
        view.setEnabled(false);
        view.setProxyBaseUrl("");
        view.setImageProxyPath("");
        view.setAllowedHostsText("");
        view.setRemark("");
        view.setCreatedAt(null);
        view.setUpdatedAt(null);
        return view;
    }

    private AlibabaImageProxyConfigDTO.View toView(AlibabaImageProxyConfig entity) {
        AlibabaImageProxyConfigDTO.View view = new AlibabaImageProxyConfigDTO.View();
        view.setId(entity.getId());
        view.setConfigName(blankIfNull(entity.getConfigName()));
        view.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        view.setProxyBaseUrl(blankIfNull(entity.getProxyBaseUrl()));
        view.setImageProxyPath(blankIfNull(entity.getImageProxyPath()));
        view.setAllowedHostsText(blankIfNull(entity.getAllowedHostsText()));
        view.setRemark(blankIfNull(entity.getRemark()));
        if (entity.getCreatedAt() != null) {
            view.setCreatedAt(entity.getCreatedAt().format(FMT));
        }
        if (entity.getUpdatedAt() != null) {
            view.setUpdatedAt(entity.getUpdatedAt().format(FMT));
        }
        return view;
    }

    private static String normalizeMultilineText(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
