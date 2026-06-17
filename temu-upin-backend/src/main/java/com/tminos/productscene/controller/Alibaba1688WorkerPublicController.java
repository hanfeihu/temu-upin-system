package com.tminos.productscene.controller;

import com.tminos.productscene.dto.Alibaba1688AuthSessionDTO;
import com.tminos.productscene.dto.Alibaba1688DetailTaskDTO;
import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.service.Alibaba1688AuthSessionService;
import com.tminos.productscene.service.Alibaba1688DetailTaskService;
import com.tminos.productscene.service.Alibaba1688WorkerApiAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/alibaba1688-worker")
public class Alibaba1688WorkerPublicController {

    private final Alibaba1688WorkerApiAuthService authService;
    private final Alibaba1688AuthSessionService authSessionService;
    private final Alibaba1688DetailTaskService detailTaskService;

    public Alibaba1688WorkerPublicController(
            Alibaba1688WorkerApiAuthService authService,
            Alibaba1688AuthSessionService authSessionService,
            Alibaba1688DetailTaskService detailTaskService
    ) {
        this.authService = authService;
        this.authSessionService = authSessionService;
        this.detailTaskService = detailTaskService;
    }

    @GetMapping("/credentials")
    public ResponseEntity<ApiResponse<List<Alibaba1688AuthSessionDTO.OptionItem>>> credentials(HttpServletRequest servletRequest) {
        authService.requireAllowed(servletRequest);
        return ResponseEntity.ok(ApiResponse.success(authSessionService.listOptions(true)));
    }

    @PostMapping("/credentials/save-storage")
    public ResponseEntity<ApiResponse<Alibaba1688AuthSessionDTO.WorkerSaveResponse>> saveStorage(
            HttpServletRequest servletRequest,
            @RequestBody Alibaba1688AuthSessionDTO.WorkerSaveRequest request
    ) {
        authService.requireAllowed(servletRequest);
        return ResponseEntity.ok(ApiResponse.success("1688 登录态已保存", authSessionService.workerSaveStorage(request)));
    }

    @GetMapping("/credentials/{id}/storage-state")
    public ResponseEntity<ApiResponse<Alibaba1688AuthSessionDTO.StorageStateResponse>> storageState(
            HttpServletRequest servletRequest,
            @PathVariable Long id
    ) {
        authService.requireAllowed(servletRequest);
        return ResponseEntity.ok(ApiResponse.success(authSessionService.getWorkerStorageState(id)));
    }

    @PostMapping("/credentials/{id}/status")
    public ResponseEntity<ApiResponse<Alibaba1688AuthSessionDTO.ListItem>> updateStatus(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @RequestBody(required = false) Alibaba1688AuthSessionDTO.WorkerStatusRequest request
    ) {
        authService.requireAllowed(servletRequest);
        return ResponseEntity.ok(ApiResponse.success("1688 凭证状态已更新", authSessionService.workerUpdateStatus(id, request)));
    }

    @PostMapping("/tasks/claim")
    public ResponseEntity<ApiResponse<Alibaba1688DetailTaskDTO.WorkerClaimResponse>> claim(
            HttpServletRequest servletRequest,
            @RequestBody(required = false) Alibaba1688DetailTaskDTO.WorkerClaimRequest request
    ) {
        authService.requireAllowed(servletRequest);
        return ResponseEntity.ok(ApiResponse.success(detailTaskService.claimNext(request)));
    }

    @PostMapping("/tasks/{id}/success")
    public ResponseEntity<ApiResponse<Alibaba1688DetailTaskDTO.ListItem>> success(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @RequestBody Alibaba1688DetailTaskDTO.WorkerSuccessRequest request
    ) {
        authService.requireAllowed(servletRequest);
        return ResponseEntity.ok(ApiResponse.success("1688 详情采集成功", detailTaskService.reportSuccess(id, request)));
    }

    @PostMapping("/tasks/{id}/failure")
    public ResponseEntity<ApiResponse<Alibaba1688DetailTaskDTO.ListItem>> failure(
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @RequestBody(required = false) Alibaba1688DetailTaskDTO.WorkerFailureRequest request
    ) {
        authService.requireAllowed(servletRequest);
        return ResponseEntity.ok(ApiResponse.success("1688 详情采集失败已记录", detailTaskService.reportFailure(id, request)));
    }
}
