package com.tminos.productscene.sync.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.sync.dto.PriceReviewDTO;
import com.tminos.productscene.sync.service.TemuPriceReviewService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sync/price-review")
public class PriceReviewController {

    private final TemuPriceReviewService priceReviewService;

    public PriceReviewController(TemuPriceReviewService priceReviewService) {
        this.priceReviewService = priceReviewService;
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
}
