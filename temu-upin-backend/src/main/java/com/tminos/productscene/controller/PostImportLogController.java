package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.entity.PostImportLog;
import com.tminos.productscene.entity.PostImportRun;
import com.tminos.productscene.service.PostImportLogService;
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

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<PostImportLog>>> listLogs(@RequestParam("runId") Long runId) {
        return ResponseEntity.ok(ApiResponse.success(logService.listLogs(runId)));
    }

    @GetMapping("/sample")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSample(@RequestParam("runId") Long runId) {
        return ResponseEntity.ok(ApiResponse.success(logService.summarizeRun(runId)));
    }
}
