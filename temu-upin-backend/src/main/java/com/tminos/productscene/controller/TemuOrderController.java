package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.dto.TemuOrderDTO;
import com.tminos.productscene.service.TemuOrderService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/temu-orders")
public class TemuOrderController {

    private final TemuOrderService orderService;

    public TemuOrderController(TemuOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TemuOrderDTO.ListItem>>> list(
            @RequestParam(value = "shopRecordId", required = false) Long shopRecordId,
            @RequestParam(value = "shopId", required = false) String shopId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "matchedTemuSkuIdLike", required = false) String matchedTemuSkuIdLike,
            @RequestParam(value = "cancelState", required = false) String cancelState,
            @RequestParam(value = "aftersaleState", required = false) String aftersaleState,
            @RequestParam(value = "orderStatus", required = false) Integer orderStatus,
            @RequestParam(value = "matchStatus", required = false) String matchStatus,
            @RequestParam(value = "orderTimeStartMs", required = false) Long orderTimeStartMs,
            @RequestParam(value = "orderTimeEndMs", required = false) Long orderTimeEndMs,
            @RequestParam(value = "updateTimeStartMs", required = false) Long updateTimeStartMs,
            @RequestParam(value = "updateTimeEndMs", required = false) Long updateTimeEndMs,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.list(shopRecordId, shopId, keyword, matchedTemuSkuIdLike, cancelState, aftersaleState, orderStatus, matchStatus,
                        orderTimeStartMs, orderTimeEndMs, updateTimeStartMs, updateTimeEndMs,
                        page, pageSize)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TemuOrderDTO.Detail>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getDetail(id)));
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<List<TemuOrderDTO.SyncResponse>>> sync(
            @RequestBody(required = false) TemuOrderDTO.SyncRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("订单同步完成", orderService.sync(request)));
    }

    @PostMapping("/{id}/logistics/refresh")
    public ResponseEntity<ApiResponse<TemuOrderDTO.LogisticsSnapshot>> refreshLogistics(
            @PathVariable Long id,
            @RequestBody(required = false) TemuOrderDTO.RefreshLogisticsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("物流信息已刷新", orderService.refreshLogistics(id, request)));
    }
}
