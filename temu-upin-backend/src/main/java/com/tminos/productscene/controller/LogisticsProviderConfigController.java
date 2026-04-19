package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.dto.LogisticsProviderConfigDTO;
import com.tminos.productscene.service.LogisticsProviderConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/logistics-provider-configs")
public class LogisticsProviderConfigController {

    private final LogisticsProviderConfigService logisticsProviderConfigService;

    public LogisticsProviderConfigController(LogisticsProviderConfigService logisticsProviderConfigService) {
        this.logisticsProviderConfigService = logisticsProviderConfigService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LogisticsProviderConfigDTO.View>>> list(
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        return ResponseEntity.ok(ApiResponse.success(logisticsProviderConfigService.list(enabled)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LogisticsProviderConfigDTO.View>> create(
            @RequestBody LogisticsProviderConfigDTO.UpsertRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Created", logisticsProviderConfigService.upsert(null, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LogisticsProviderConfigDTO.View>> update(
            @PathVariable Long id,
            @RequestBody LogisticsProviderConfigDTO.UpsertRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Updated", logisticsProviderConfigService.upsert(id, request)));
    }

    @PostMapping("/import-legacy/haoyuan")
    public ResponseEntity<ApiResponse<LogisticsProviderConfigDTO.View>> importLegacyHaoyuan() {
        return ResponseEntity.ok(ApiResponse.success("Imported", logisticsProviderConfigService.importLegacyHaoyuanConfig()));
    }
}
