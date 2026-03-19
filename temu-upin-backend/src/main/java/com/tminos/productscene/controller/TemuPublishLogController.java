package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.entity.TemuPublishLog;
import com.tminos.productscene.entity.TemuPublishRun;
import com.tminos.productscene.service.TemuPublishLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/temu-publish")
public class TemuPublishLogController {

    private final TemuPublishLogService logService;

    public TemuPublishLogController(TemuPublishLogService logService) {
        this.logService = logService;
    }

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<TemuPublishRun>>> listRuns(
            @RequestParam(value = "spuId", required = false) Long spuId
    ) {
        return ResponseEntity.ok(ApiResponse.success(logService.listRuns(spuId)));
    }

    @GetMapping("/runs/recent")
    public ResponseEntity<ApiResponse<List<TemuPublishRun>>> listRecentRuns() {
        return ResponseEntity.ok(ApiResponse.success(logService.listRecentRuns()));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<TemuPublishLog>>> listLogs(@RequestParam("runId") Long runId) {
        return ResponseEntity.ok(ApiResponse.success(logService.listLogs(runId)));
    }
}
