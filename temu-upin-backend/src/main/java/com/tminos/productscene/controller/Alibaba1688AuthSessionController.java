package com.tminos.productscene.controller;

import com.tminos.productscene.dto.Alibaba1688AuthSessionDTO;
import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.service.Alibaba1688AuthSessionService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/alibaba1688-auth-sessions")
public class Alibaba1688AuthSessionController {

    private final Alibaba1688AuthSessionService service;

    public Alibaba1688AuthSessionController(Alibaba1688AuthSessionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Alibaba1688AuthSessionDTO.ListItem>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(keyword, enabled, status, page, size)));
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<List<Alibaba1688AuthSessionDTO.OptionItem>>> listOptions(
            @RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.listOptions(enabledOnly)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Alibaba1688AuthSessionDTO.ListItem>> create(
            @RequestBody Alibaba1688AuthSessionDTO.SaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("1688 凭证已创建", service.save(null, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Alibaba1688AuthSessionDTO.ListItem>> update(
            @PathVariable Long id,
            @RequestBody Alibaba1688AuthSessionDTO.SaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("1688 凭证已更新", service.save(id, request)));
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<Alibaba1688AuthSessionDTO.ListItem>> toggleEnabled(
            @PathVariable Long id,
            @RequestBody Alibaba1688AuthSessionDTO.SaveRequest request
    ) {
        boolean enabled = request != null && Boolean.TRUE.equals(request.getEnabled());
        return ResponseEntity.ok(ApiResponse.success("1688 凭证状态已更新", service.toggleEnabled(id, enabled)));
    }

    @PostMapping("/worker/save-storage")
    public ResponseEntity<ApiResponse<Alibaba1688AuthSessionDTO.WorkerSaveResponse>> workerSaveStorage(
            @RequestBody Alibaba1688AuthSessionDTO.WorkerSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("1688 登录态已保存", service.workerSaveStorage(request)));
    }

    @GetMapping("/{id}/worker-storage-state")
    public ResponseEntity<ApiResponse<Alibaba1688AuthSessionDTO.StorageStateResponse>> workerStorageState(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.getWorkerStorageState(id)));
    }

    @PostMapping("/{id}/worker-status")
    public ResponseEntity<ApiResponse<Alibaba1688AuthSessionDTO.ListItem>> workerUpdateStatus(
            @PathVariable Long id,
            @RequestBody(required = false) Alibaba1688AuthSessionDTO.WorkerStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("1688 凭证状态已更新", service.workerUpdateStatus(id, request)));
    }
}
