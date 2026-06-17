package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.dto.TemuOrderDashboardDTO;
import com.tminos.productscene.dto.TemuOrderDTO;
import com.tminos.productscene.service.DianxiaomiOrderSyncService;
import com.tminos.productscene.service.TemuOrderDashboardService;
import com.tminos.productscene.service.TemuOrderService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/temu-orders")
public class TemuOrderController {

    private final TemuOrderService orderService;
    private final TemuOrderDashboardService orderDashboardService;
    private final DianxiaomiOrderSyncService dianxiaomiOrderSyncService;

    public TemuOrderController(TemuOrderService orderService,
                               TemuOrderDashboardService orderDashboardService,
                               DianxiaomiOrderSyncService dianxiaomiOrderSyncService) {
        this.orderService = orderService;
        this.orderDashboardService = orderDashboardService;
        this.dianxiaomiOrderSyncService = dianxiaomiOrderSyncService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TemuOrderDTO.ListItem>>> list(
            @RequestParam(value = "shopRecordId", required = false) Long shopRecordId,
            @RequestParam(value = "shopId", required = false) String shopId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "matchedTemuSkuIdLike", required = false) String matchedTemuSkuIdLike,
            @RequestParam(value = "agingFilter", required = false) String agingFilter,
            @RequestParam(value = "cancelState", required = false) String cancelState,
            @RequestParam(value = "aftersaleState", required = false) String aftersaleState,
            @RequestParam(value = "orderStatus", required = false) Integer orderStatus,
            @RequestParam(value = "matchStatus", required = false) String matchStatus,
            @RequestParam(value = "noStockProduct", required = false) Boolean noStockProduct,
            @RequestParam(value = "orderTimeStartMs", required = false) Long orderTimeStartMs,
            @RequestParam(value = "orderTimeEndMs", required = false) Long orderTimeEndMs,
            @RequestParam(value = "updateTimeStartMs", required = false) Long updateTimeStartMs,
            @RequestParam(value = "updateTimeEndMs", required = false) Long updateTimeEndMs,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.list(shopRecordId, shopId, keyword, matchedTemuSkuIdLike, agingFilter,
                        cancelState, aftersaleState, orderStatus, matchStatus, noStockProduct,
                        orderTimeStartMs, orderTimeEndMs, updateTimeStartMs, updateTimeEndMs,
                        page, pageSize)
        ));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<TemuOrderDashboardDTO.Response>> dashboard(
            @RequestParam(value = "shopRecordId", required = false) Long shopRecordId,
            @RequestParam(value = "shopId", required = false) String shopId
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderDashboardService.getDashboard(shopRecordId, shopId)));
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

    @PostMapping("/dianxiaomi-sync")
    public ResponseEntity<ApiResponse<List<TemuOrderDTO.SyncResponse>>> syncFromDianxiaomi(
            @RequestBody(required = false) TemuOrderDTO.SyncRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("店小秘订单同步完成", dianxiaomiOrderSyncService.sync(request)));
    }

    @PostMapping("/{id}/logistics/refresh")
    public ResponseEntity<ApiResponse<TemuOrderDTO.LogisticsSnapshot>> refreshLogistics(
            @PathVariable Long id,
            @RequestBody(required = false) TemuOrderDTO.RefreshLogisticsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("物流信息已刷新", orderService.refreshLogistics(id, request)));
    }

    @PostMapping("/logistics/refresh-all")
    public ResponseEntity<ApiResponse<Integer>> refreshAllLogistics(
            @RequestBody TemuOrderDTO.SyncRequest request
    ) {
        if (request == null || request.getShopRecordId() == null) {
            throw new IllegalArgumentException("请先选择店铺");
        }
        int count = orderService.refreshAllEligibleLogistics(request.getShopRecordId());
        return ResponseEntity.ok(ApiResponse.success("全部订单物流刷新完成", count));
    }

    @PutMapping("/{id}/dianxiaomi-package-number")
    public ResponseEntity<ApiResponse<Void>> updateDianxiaomiPackageNumber(
            @PathVariable Long id,
            @RequestBody TemuOrderDTO.ManualPackageNumberRequest request
    ) {
        orderService.updateDianxiaomiPackageNumber(id, request);
        return ResponseEntity.ok(ApiResponse.success("店小秘单号已保存", null));
    }
}
