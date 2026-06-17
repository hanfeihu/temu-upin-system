package com.tminos.productscene.controller;

import com.tminos.productscene.dto.Alibaba1688SelectionAiReportWorkerDTO;
import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.service.Alibaba1688SelectionAiReportTaskClaimService;
import com.tminos.productscene.service.Alibaba1688SelectionAiReportWorkerConfigService;
import com.tminos.productscene.service.Alibaba1688SelectionAiReportWorkerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/alibaba1688-selection-pools/ai-report-worker")
public class Alibaba1688SelectionAiReportWorkerController {

    private final Alibaba1688SelectionAiReportWorkerConfigService configService;
    private final Alibaba1688SelectionAiReportWorkerService workerService;
    private final Alibaba1688SelectionAiReportTaskClaimService claimService;

    public Alibaba1688SelectionAiReportWorkerController(
            Alibaba1688SelectionAiReportWorkerConfigService configService,
            Alibaba1688SelectionAiReportWorkerService workerService,
            Alibaba1688SelectionAiReportTaskClaimService claimService
    ) {
        this.configService = configService;
        this.workerService = workerService;
        this.claimService = claimService;
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionAiReportWorkerDTO.ConfigView>> config() {
        return ResponseEntity.ok(ApiResponse.success(configService.current()));
    }

    @PutMapping("/config")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionAiReportWorkerDTO.StatusView>> updateConfig(
            @RequestBody(required = false) Alibaba1688SelectionAiReportWorkerDTO.UpdateConfigRequest request
    ) {
        configService.save(request);
        return ResponseEntity.ok(ApiResponse.success("AI 报告任务配置已保存", workerService.restartIfRunning()));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionAiReportWorkerDTO.StatusView>> status() {
        return ResponseEntity.ok(ApiResponse.success(workerService.status()));
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionAiReportWorkerDTO.StatusView>> start() {
        Alibaba1688SelectionAiReportWorkerDTO.StatusView before = workerService.status();
        Alibaba1688SelectionAiReportWorkerDTO.StatusView status = workerService.start();
        configService.updateEnabled(true);
        String message = before != null && (before.getPendingCount() == null || before.getPendingCount() <= 0)
                ? "当前无待执行任务，AI 报告任务监控已启动，将每 5 分钟自动检查"
                : "AI 报告任务已启动";
        return ResponseEntity.ok(ApiResponse.success(message, status));
    }

    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionAiReportWorkerDTO.StatusView>> stop() {
        Alibaba1688SelectionAiReportWorkerDTO.StatusView status = workerService.stop();
        configService.updateEnabled(false);
        return ResponseEntity.ok(ApiResponse.success("AI 报告任务已停止", status));
    }

    @PostMapping("/retry-failed")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionAiReportWorkerDTO.StatusView>> retryFailed() {
        int retried = claimService.retryFailed();
        Alibaba1688SelectionAiReportWorkerDTO.StatusView status = workerService.restartIfRunning();
        String message = retried <= 0
                ? "当前没有失败任务需要重试"
                : "已将失败任务重新加入待执行队列：" + retried + " 条";
        return ResponseEntity.ok(ApiResponse.success(message, status));
    }
}
