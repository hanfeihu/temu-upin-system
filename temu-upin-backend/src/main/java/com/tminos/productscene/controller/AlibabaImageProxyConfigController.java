package com.tminos.productscene.controller;

import com.tminos.productscene.dto.AlibabaImageProxyConfigDTO;
import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.service.AlibabaImageProxyConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/alibaba-image-proxy-config")
public class AlibabaImageProxyConfigController {

    private final AlibabaImageProxyConfigService alibabaImageProxyConfigService;

    public AlibabaImageProxyConfigController(AlibabaImageProxyConfigService alibabaImageProxyConfigService) {
        this.alibabaImageProxyConfigService = alibabaImageProxyConfigService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AlibabaImageProxyConfigDTO.View>> current() {
        return ResponseEntity.ok(ApiResponse.success(alibabaImageProxyConfigService.current()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AlibabaImageProxyConfigDTO.View>> saveCurrent(
            @RequestBody AlibabaImageProxyConfigDTO.UpsertRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("保存成功", alibabaImageProxyConfigService.saveCurrent(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AlibabaImageProxyConfigDTO.View>> update(
            @PathVariable Long id,
            @RequestBody AlibabaImageProxyConfigDTO.UpsertRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("更新成功", alibabaImageProxyConfigService.update(id, request)));
    }
}
