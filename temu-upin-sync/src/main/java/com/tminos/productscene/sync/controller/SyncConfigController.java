package com.tminos.productscene.sync.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.sync.dto.SyncConfigDTO;
import com.tminos.productscene.sync.entity.TemuSyncConfig;
import com.tminos.productscene.sync.service.TemuSyncConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sync/config")
public class SyncConfigController {

    private final TemuSyncConfigService configService;

    public SyncConfigController(TemuSyncConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SyncConfigDTO.ConfigResponse>> getConfigs(
            @RequestParam String shopId) {
        List<TemuSyncConfig> configs = configService.getOrInitConfigs(shopId);
        SyncConfigDTO.ConfigResponse resp = new SyncConfigDTO.ConfigResponse();
        resp.setShopId(shopId);
        resp.setConfigs(configs.stream().map(c -> {
            SyncConfigDTO.ConfigItem item = new SyncConfigDTO.ConfigItem();
            item.setId(c.getId());
            item.setShopId(c.getShopId());
            item.setConfigKey(c.getConfigKey());
            item.setConfigValue(c.getConfigValue());
            item.setConfigDesc(c.getConfigDesc());
            return item;
        }).collect(Collectors.toList()));
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> saveConfigs(@RequestBody SyncConfigDTO.SaveRequest request) {
        Map<String, String> configMap = new LinkedHashMap<>();
        if (request.getConfigs() != null) {
            for (SyncConfigDTO.ConfigItem item : request.getConfigs()) {
                configMap.put(item.getConfigKey(), item.getConfigValue());
            }
        }
        configService.saveConfigs(request.getShopId(), configMap);
        return ResponseEntity.ok(ApiResponse.success("配置已保存", null));
    }
}
