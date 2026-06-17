package com.tminos.productscene.controller;

import com.tminos.productscene.dto.OcrImageTranslateWorkerDTO;
import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.service.OcrImageTranslateWorkerConfigService;
import com.tminos.productscene.service.OcrImageTranslateWorkerLogService;
import com.tminos.productscene.service.OcrImageTranslateWorkerService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/ocr-tasks/image-translate-worker")
public class OcrImageTranslateWorkerController {

    private final OcrImageTranslateWorkerConfigService configService;
    private final OcrImageTranslateWorkerService workerService;
    private final OcrImageTranslateWorkerLogService logService;

    public OcrImageTranslateWorkerController(
            OcrImageTranslateWorkerConfigService configService,
            OcrImageTranslateWorkerService workerService,
            OcrImageTranslateWorkerLogService logService
    ) {
        this.configService = configService;
        this.workerService = workerService;
        this.logService = logService;
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<OcrImageTranslateWorkerDTO.ConfigView>> config() {
        return ResponseEntity.ok(ApiResponse.success(configService.current()));
    }

    @PutMapping("/config")
    public ResponseEntity<ApiResponse<OcrImageTranslateWorkerDTO.StatusView>> updateConfig(
            @RequestBody(required = false) OcrImageTranslateWorkerDTO.UpdateConfigRequest request
    ) {
        configService.save(request);
        return ResponseEntity.ok(ApiResponse.success("OCR 图片翻译配置已保存", workerService.restartIfRunning()));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<OcrImageTranslateWorkerDTO.StatusView>> status() {
        return ResponseEntity.ok(ApiResponse.success(workerService.status()));
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<OcrImageTranslateWorkerDTO.StatusView>> start() {
        configService.updateEnabled(true);
        return ResponseEntity.ok(ApiResponse.success("OCR 图片翻译任务已启动", workerService.start()));
    }

    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<OcrImageTranslateWorkerDTO.StatusView>> stop() {
        OcrImageTranslateWorkerDTO.StatusView status = workerService.stop();
        configService.updateEnabled(false);
        return ResponseEntity.ok(ApiResponse.success("OCR 图片翻译任务已停止", status));
    }

    @PostMapping("/retry/{ocrTaskId}")
    public ResponseEntity<ApiResponse<OcrImageTranslateWorkerDTO.StatusView>> retry(@PathVariable Long ocrTaskId) {
        return ResponseEntity.ok(ApiResponse.success("OCR 图片翻译重试已提交", workerService.submitRetryTask(ocrTaskId)));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Page<OcrImageTranslateWorkerDTO.LogView>>> logs(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(logService.list(page, size)));
    }
}
