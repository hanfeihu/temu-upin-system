package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.entity.PostImportLog;
import com.tminos.productscene.entity.PostImportRun;
import com.tminos.productscene.service.PostImportLogService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platform/post-import")
public class PostImportLogController {

    private final PostImportLogService logService;

    public PostImportLogController(PostImportLogService logService) {
        this.logService = logService;
    }

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<PostImportRun>>> listRuns(
            @RequestParam(value = "spuId", required = false) Long spuId
    ) {
        return ResponseEntity.ok(ApiResponse.success(logService.listRuns(spuId)));
    }

    @GetMapping("/runs/recent")
    public ResponseEntity<ApiResponse<List<PostImportRun>>> listRecentRuns() {
        return ResponseEntity.ok(ApiResponse.success(logService.listRecentRuns()));
    }

    @GetMapping("/runs/search")
    public ResponseEntity<ApiResponse<Page<PostImportRun>>> searchRuns(
            @RequestParam(value = "spuId", required = false) Long spuId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(logService.searchRuns(spuId, status, page, size)));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<PostImportLog>>> listLogs(@RequestParam("runId") Long runId) {
        return ResponseEntity.ok(ApiResponse.success(logService.listLogs(runId)));
    }

    @GetMapping("/sample")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSample(@RequestParam("runId") Long runId) {
        return ResponseEntity.ok(ApiResponse.success(logService.summarizeRun(runId)));
    }
}
