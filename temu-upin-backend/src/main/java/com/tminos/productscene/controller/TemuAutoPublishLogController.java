package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.entity.TemuAutoPublishLog;
import com.tminos.productscene.entity.TemuAutoPublishRun;
import com.tminos.productscene.service.TemuAutoPublishLogService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platform/temu-auto-publish")
public class TemuAutoPublishLogController {

    private final TemuAutoPublishLogService logService;

    public TemuAutoPublishLogController(TemuAutoPublishLogService logService) {
        this.logService = logService;
    }

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<TemuAutoPublishRun>>> listRuns(
            @RequestParam(value = "spuId", required = false) Long spuId
    ) {
        return ResponseEntity.ok(ApiResponse.success(logService.listRuns(spuId)));
    }

    @GetMapping("/runs/recent")
    public ResponseEntity<ApiResponse<List<TemuAutoPublishRun>>> listRecentRuns() {
        return ResponseEntity.ok(ApiResponse.success(logService.listRecentRuns()));
    }

    @GetMapping("/runs/search")
    public ResponseEntity<ApiResponse<Page<TemuAutoPublishRun>>> searchRuns(
            @RequestParam(value = "spuId", required = false) Long spuId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(logService.searchRuns(spuId, status, action, q, page, size)));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<TemuAutoPublishLog>>> listLogs(@RequestParam("runId") Long runId) {
        return ResponseEntity.ok(ApiResponse.success(logService.listLogs(runId)));
    }

    @GetMapping("/sample")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSample(@RequestParam("runId") Long runId) {
        return ResponseEntity.ok(ApiResponse.success(logService.summarizeRun(runId)));
    }

    /**
     * Convenience endpoint for UI: get linked TEMU publish runId from an auto-publish run.
     */
    @GetMapping("/linked-publish-run")
    public ResponseEntity<ApiResponse<Map<String, Object>>> linkedPublishRun(@RequestParam("runId") Long runId) {
        Map<String, Object> s = logService.summarizeRun(runId);
        if (s == null) {
            return ResponseEntity.ok(ApiResponse.error("run not found"));
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("runId", runId);
        out.put("publishRunId", s.get("publishRunId"));
        out.put("spuId", s.get("spuId"));
        return ResponseEntity.ok(ApiResponse.success(out));
    }
}
