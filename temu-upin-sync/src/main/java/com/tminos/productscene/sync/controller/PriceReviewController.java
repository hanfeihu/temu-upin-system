package com.tminos.productscene.sync.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.sync.dto.PriceReviewDTO;
import com.tminos.productscene.sync.service.TemuPriceReviewLowPriceRejectWorkerConfigService;
import com.tminos.productscene.sync.service.TemuPriceReviewLowPriceRejectWorkerService;
import com.tminos.productscene.sync.service.TemuPriceReviewService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sync/price-review")
public class PriceReviewController {

    private final TemuPriceReviewService priceReviewService;
    private final TemuPriceReviewLowPriceRejectWorkerConfigService lowPriceRejectWorkerConfigService;
    private final TemuPriceReviewLowPriceRejectWorkerService lowPriceRejectWorkerService;

    public PriceReviewController(TemuPriceReviewService priceReviewService,
                                 TemuPriceReviewLowPriceRejectWorkerConfigService lowPriceRejectWorkerConfigService,
                                 TemuPriceReviewLowPriceRejectWorkerService lowPriceRejectWorkerService) {
        this.priceReviewService = priceReviewService;
        this.lowPriceRejectWorkerConfigService = lowPriceRejectWorkerConfigService;
        this.lowPriceRejectWorkerService = lowPriceRejectWorkerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PriceReviewDTO.ReviewOrderItem>>> list(
            @RequestParam String shopId,
            @RequestParam(required = false) Integer orderStatus,
            @RequestParam(required = false) String reviewAction,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.success(
                priceReviewService.listOrders(shopId, orderStatus, reviewAction, page, pageSize)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PriceReviewDTO.ReviewOrderItem>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(priceReviewService.getOrderDetail(id)));
    }

    @PostMapping("/batch-review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchReview(
            @RequestBody PriceReviewDTO.BatchReviewRequest request) {
        Map<String, Object> result = priceReviewService.batchReview(request);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("批量核价完成", result));
        } else {
            Object message = result.get("message");
            if ((message == null || String.valueOf(message).isBlank()) && result.get("errors") instanceof java.util.List<?> errors && !errors.isEmpty()) {
                message = errors.get(0);
            }
            return ResponseEntity.ok(ApiResponse.error("批量核价失败: " + (message == null ? "未知错误" : message)));
        }
    }

    @PostMapping("/batch-local-complete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchLocalComplete(
            @RequestBody PriceReviewDTO.BatchLocalCompleteRequest request) {
        Map<String, Object> result = priceReviewService.batchLocalComplete(request);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        if (success) {
            Object message = result.get("message");
            return ResponseEntity.ok(ApiResponse.success(message == null ? "批量标记已完成" : String.valueOf(message), result));
        }
        Object message = result.get("message");
        return ResponseEntity.ok(ApiResponse.error("批量标记已完成失败: " + (message == null ? "未知错误" : message)));
    }

    @GetMapping("/low-price-reject-worker/config")
    public ResponseEntity<ApiResponse<PriceReviewDTO.LowPriceRejectWorkerConfigView>> lowPriceRejectWorkerConfig() {
        return ResponseEntity.ok(ApiResponse.success(lowPriceRejectWorkerConfigService.current()));
    }

    @PutMapping("/low-price-reject-worker/config")
    public ResponseEntity<ApiResponse<PriceReviewDTO.LowPriceRejectWorkerStatusView>> updateLowPriceRejectWorkerConfig(
            @RequestBody(required = false) PriceReviewDTO.UpdateLowPriceRejectWorkerConfigRequest request) {
        lowPriceRejectWorkerConfigService.save(request);
        return ResponseEntity.ok(ApiResponse.success("低价自动拒绝配置已保存", lowPriceRejectWorkerService.restartIfRunning()));
    }

    @GetMapping("/low-price-reject-worker/status")
    public ResponseEntity<ApiResponse<PriceReviewDTO.LowPriceRejectWorkerStatusView>> lowPriceRejectWorkerStatus() {
        return ResponseEntity.ok(ApiResponse.success(lowPriceRejectWorkerService.status()));
    }

    @PostMapping("/low-price-reject-worker/start")
    public ResponseEntity<ApiResponse<PriceReviewDTO.LowPriceRejectWorkerStatusView>> startLowPriceRejectWorker() {
        lowPriceRejectWorkerConfigService.updateEnabled(true);
        return ResponseEntity.ok(ApiResponse.success("低价自动拒绝 worker 已启动", lowPriceRejectWorkerService.start()));
    }

    @PostMapping("/low-price-reject-worker/stop")
    public ResponseEntity<ApiResponse<PriceReviewDTO.LowPriceRejectWorkerStatusView>> stopLowPriceRejectWorker() {
        PriceReviewDTO.LowPriceRejectWorkerStatusView status = lowPriceRejectWorkerService.stop();
        lowPriceRejectWorkerConfigService.updateEnabled(false);
        return ResponseEntity.ok(ApiResponse.success("低价自动拒绝 worker 已停止", status));
    }
}
