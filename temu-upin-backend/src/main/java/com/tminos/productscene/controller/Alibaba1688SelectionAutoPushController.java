package com.tminos.productscene.controller;

import com.tminos.productscene.dto.Alibaba1688SelectionAutoPushDTO;
import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.service.Alibaba1688SelectionAutoPushConfigService;
import com.tminos.productscene.service.Alibaba1688SelectionAutoPushLogService;
import com.tminos.productscene.service.Alibaba1688SelectionAutoPushWorkerService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/alibaba1688-selection-pools/auto-push")
public class Alibaba1688SelectionAutoPushController {

    private final Alibaba1688SelectionAutoPushConfigService configService;
    private final Alibaba1688SelectionAutoPushWorkerService workerService;
    private final Alibaba1688SelectionAutoPushLogService logService;

    public Alibaba1688SelectionAutoPushController(
            Alibaba1688SelectionAutoPushConfigService configService,
            Alibaba1688SelectionAutoPushWorkerService workerService,
            Alibaba1688SelectionAutoPushLogService logService
    ) {
        this.configService = configService;
        this.workerService = workerService;
        this.logService = logService;
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionAutoPushDTO.ConfigView>> config() {
        return ResponseEntity.ok(ApiResponse.success(configService.current()));
    }

    @PutMapping("/config")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionAutoPushDTO.StatusView>> updateConfig(
            @RequestBody(required = false) Alibaba1688SelectionAutoPushDTO.UpdateConfigRequest request
    ) {
        configService.save(request);
        return ResponseEntity.ok(ApiResponse.success("自动推送配置已保存", workerService.restartIfRunning()));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionAutoPushDTO.StatusView>> status() {
        return ResponseEntity.ok(ApiResponse.success(workerService.status()));
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionAutoPushDTO.StatusView>> start() {
        configService.updateEnabled(true);
        return ResponseEntity.ok(ApiResponse.success("自动推送已启动", workerService.start()));
    }

    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionAutoPushDTO.StatusView>> stop() {
        Alibaba1688SelectionAutoPushDTO.StatusView status = workerService.stop();
        configService.updateEnabled(false);
        return ResponseEntity.ok(ApiResponse.success("自动推送已停止", status));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Page<Alibaba1688SelectionAutoPushDTO.LogView>>> logs(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(logService.list(page, size)));
    }
}
