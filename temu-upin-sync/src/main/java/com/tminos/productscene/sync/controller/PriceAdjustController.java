package com.tminos.productscene.sync.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.sync.dto.PriceAdjustDTO;
import com.tminos.productscene.sync.service.TemuPriceAdjustService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sync/price-adjust")
public class PriceAdjustController {

    private final TemuPriceAdjustService priceAdjustService;

    public PriceAdjustController(TemuPriceAdjustService priceAdjustService) {
        this.priceAdjustService = priceAdjustService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PriceAdjustDTO.AdjustOrderItem>>> list(
            @RequestParam String shopId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String reviewAction,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.success(
                priceAdjustService.listOrders(shopId, status, reviewAction, page, pageSize)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PriceAdjustDTO.AdjustOrderItem>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(priceAdjustService.getOrderDetail(id)));
    }

    @DeleteMapping("/local-data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearLocalData(@RequestParam String shopId) {
        Map<String, Object> result = priceAdjustService.clearLocalData(shopId);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        String message = String.valueOf(result.getOrDefault("message", success ? "本地调价单数据已清空" : "清空本地调价单数据失败"));
        return ResponseEntity.ok(success
                ? ApiResponse.success(message, result)
                : ApiResponse.error(message));
    }

    @PostMapping("/batch-review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchReview(
            @RequestBody PriceAdjustDTO.BatchReviewRequest request) {
        Map<String, Object> result = priceAdjustService.batchReview(request);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        String message = String.valueOf(result.getOrDefault("message", success ? "批量调价审核完成" : "批量调价审核失败"));
        return ResponseEntity.ok(success
                ? ApiResponse.success(message, result)
                : ApiResponse.error(message, result));
    }
}
