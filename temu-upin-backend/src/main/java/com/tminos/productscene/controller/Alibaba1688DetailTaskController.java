package com.tminos.productscene.controller;

import com.tminos.productscene.dto.Alibaba1688DetailTaskDTO;
import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.service.Alibaba1688DetailTaskService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/alibaba1688-detail-tasks")
public class Alibaba1688DetailTaskController {

    private final Alibaba1688DetailTaskService service;

    public Alibaba1688DetailTaskController(Alibaba1688DetailTaskService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Alibaba1688DetailTaskDTO.ListItem>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "credentialId", required = false) Long credentialId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(keyword, credentialId, status, page, size)));
    }

    @PostMapping("/dialog-create")
    public ResponseEntity<ApiResponse<Alibaba1688DetailTaskDTO.CreateDialogResponse>> dialogCreate(
            @RequestBody Alibaba1688DetailTaskDTO.CreateDialogRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("1688 详情采集任务已创建", service.createFromDialog(request)));
    }

    @PostMapping("/import-from-card-links")
    public ResponseEntity<ApiResponse<Alibaba1688DetailTaskDTO.ImportFromCardLinksResponse>> importFromCardLinks(
            @RequestBody Alibaba1688DetailTaskDTO.ImportFromCardLinksRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("1688 卡片链接已导入详情采集任务", service.importFromCardLinks(request)));
    }

    @PostMapping("/worker/claim")
    public ResponseEntity<ApiResponse<Alibaba1688DetailTaskDTO.WorkerClaimResponse>> workerClaim(
            @RequestBody(required = false) Alibaba1688DetailTaskDTO.WorkerClaimRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.claimNext(request)));
    }

    @PostMapping("/{id}/worker/success")
    public ResponseEntity<ApiResponse<Alibaba1688DetailTaskDTO.ListItem>> workerSuccess(
            @PathVariable Long id,
            @RequestBody Alibaba1688DetailTaskDTO.WorkerSuccessRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("1688 详情采集成功", service.reportSuccess(id, request)));
    }

    @PostMapping("/{id}/worker/failure")
    public ResponseEntity<ApiResponse<Alibaba1688DetailTaskDTO.ListItem>> workerFailure(
            @PathVariable Long id,
            @RequestBody(required = false) Alibaba1688DetailTaskDTO.WorkerFailureRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("1688 详情采集失败已记录", service.reportFailure(id, request)));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<Alibaba1688DetailTaskDTO.ListItem>> retry(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("1688 详情采集任务已重新排队", service.retryTask(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Alibaba1688DetailTaskDTO.ListItem>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("1688 详情采集任务已取消", service.cancelTask(id)));
    }
}
