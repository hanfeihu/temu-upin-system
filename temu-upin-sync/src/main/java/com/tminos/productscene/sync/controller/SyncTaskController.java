package com.tminos.productscene.sync.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.sync.dto.SyncTaskDTO;
import com.tminos.productscene.sync.entity.TemuSyncLog;
import com.tminos.productscene.sync.entity.TemuSyncTask;
import com.tminos.productscene.sync.service.*;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sync/tasks")
public class SyncTaskController {

    private final TemuSyncLogService syncLogService;
    private final SyncTaskService syncTaskService;
    private final SyncDataCleanupService syncDataCleanupService;

    public SyncTaskController(TemuSyncLogService syncLogService,
                              SyncTaskService syncTaskService,
                              SyncDataCleanupService syncDataCleanupService) {
        this.syncLogService = syncLogService;
        this.syncTaskService = syncTaskService;
        this.syncDataCleanupService = syncDataCleanupService;
    }

    // ==================== 新任务管理接口 ====================

    @PostMapping
    public ResponseEntity<ApiResponse<List<SyncTaskDTO.TaskItem>>> createTasks(
            @RequestBody SyncTaskDTO.CreateRequest request) {
        try {
            List<SyncTaskDTO.TaskItem> tasks = syncTaskService.createTasks(request);
            return ResponseEntity.ok(ApiResponse.success("同步任务已创建", tasks));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TemuSyncTask>>> listTasks(
            @RequestParam String shopId,
            @RequestParam(required = false) String syncType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.success(syncTaskService.listTasks(shopId, syncType, page, pageSize)));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<SyncTaskDTO.TaskItem>> getTaskDetail(@PathVariable Long taskId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(syncTaskService.getTaskDetail(taskId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{taskId}/progress")
    public ResponseEntity<ApiResponse<SyncTaskDTO.ProgressResponse>> getProgress(@PathVariable Long taskId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(syncTaskService.getProgress(taskId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{taskId}/retry")
    public ResponseEntity<ApiResponse<SyncTaskDTO.TaskItem>> retryTask(
            @PathVariable Long taskId,
            @RequestBody(required = false) SyncTaskDTO.RetryRequest request) {
        try {
            String mode = request != null && request.getMode() != null ? request.getMode() : "CONTINUE";
            return ResponseEntity.ok(ApiResponse.success("重试已启动", syncTaskService.retryTask(taskId, mode)));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelTask(@PathVariable Long taskId) {
        try {
            syncTaskService.cancelTask(taskId);
            return ResponseEntity.ok(ApiResponse.success("任务已取消", null));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/shop-data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearShopData(@RequestParam String shopId) {
        try {
            Map<String, Object> result = syncDataCleanupService.clearShopSyncData(shopId);
            return ResponseEntity.ok(ApiResponse.success(String.valueOf(result.get("message")), result));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== 旧日志接口（兼容） ====================

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Page<TemuSyncLog>>> getLogs(
            @RequestParam String shopId,
            @RequestParam(required = false) String syncType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.success(syncLogService.getLogs(shopId, syncType, page, pageSize)));
    }
}
