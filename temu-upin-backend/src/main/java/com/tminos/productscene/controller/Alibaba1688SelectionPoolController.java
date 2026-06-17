package com.tminos.productscene.controller;

import com.tminos.productscene.dto.Alibaba1688SelectionPoolDTO;
import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.service.Alibaba1688SelectionPoolService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platform/alibaba1688-selection-pools")
public class Alibaba1688SelectionPoolController {

    private final Alibaba1688SelectionPoolService service;

    public Alibaba1688SelectionPoolController(Alibaba1688SelectionPoolService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Alibaba1688SelectionPoolDTO.ListItem>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "poolStatus", required = false) String poolStatus,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "detailRecordId", required = false) Long detailRecordId,
            @RequestParam(value = "skuPriceMin", required = false) BigDecimal skuPriceMin,
            @RequestParam(value = "skuPriceMax", required = false) BigDecimal skuPriceMax,
            @RequestParam(value = "moqMin", required = false) Integer moqMin,
            @RequestParam(value = "moqMax", required = false) Integer moqMax,
            @RequestParam(value = "startBatchQtyMin", required = false) Integer startBatchQtyMin,
            @RequestParam(value = "startBatchQtyMax", required = false) Integer startBatchQtyMax,
            @RequestParam(value = "aiMaxDimensionCmMin", required = false) BigDecimal aiMaxDimensionCmMin,
            @RequestParam(value = "aiMaxDimensionCmMax", required = false) BigDecimal aiMaxDimensionCmMax,
            @RequestParam(value = "aiMaxWeightGMin", required = false) BigDecimal aiMaxWeightGMin,
            @RequestParam(value = "aiMaxWeightGMax", required = false) BigDecimal aiMaxWeightGMax,
            @RequestParam(value = "aiSelectionDecision", required = false) String aiSelectionDecision,
            @RequestParam(value = "aiContainsLiquid", required = false) Boolean aiContainsLiquid,
            @RequestParam(value = "aiFragile", required = false) Boolean aiFragile,
            @RequestParam(value = "temuSiteExceptionBlocked", required = false) Boolean temuSiteExceptionBlocked,
            @RequestParam(value = "pushed", required = false) Boolean pushed,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        String resolvedStatus = poolStatus != null ? poolStatus : status;
        return ResponseEntity.ok(ApiResponse.success(service.list(
                keyword,
                category,
                resolvedStatus,
                detailRecordId,
                skuPriceMin,
                skuPriceMax,
                moqMin,
                moqMax,
                startBatchQtyMin,
                startBatchQtyMax,
                aiMaxDimensionCmMin,
                aiMaxDimensionCmMax,
                aiMaxWeightGMin,
                aiMaxWeightGMax,
                aiSelectionDecision,
                aiContainsLiquid,
                aiFragile,
                temuSiteExceptionBlocked,
                pushed,
                page,
                size
        )));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listCategories() {
        return ResponseEntity.ok(ApiResponse.success(service.listCategories()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionPoolDTO.DetailResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.getDetail(id)));
    }

    @PostMapping("/import-from-detail")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionPoolDTO.ImportResponse>> importFromDetail(
            @RequestBody Alibaba1688SelectionPoolDTO.ImportRequest request
    ) {
        Alibaba1688SelectionPoolDTO.ImportResponse response = service.importFromDetail(request);
        String message = response == null || response.getMessage() == null ? "1688 详情已加入选品池" : response.getMessage();
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @PostMapping("/batch-import-from-detail")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionPoolDTO.BatchImportResponse>> batchImportFromDetail(
            @RequestBody Alibaba1688SelectionPoolDTO.BatchImportRequest request
    ) {
        Alibaba1688SelectionPoolDTO.BatchImportResponse response = service.batchImportFromDetail(request);
        return ResponseEntity.ok(ApiResponse.success("详情数据批量导入完成", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionPoolDTO.DetailResponse>> update(
            @PathVariable Long id,
            @RequestBody Alibaba1688SelectionPoolDTO.UpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("选品池记录已更新", service.update(id, request)));
    }

    @PostMapping("/{id}/push-to-product-collection")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionPoolDTO.PushToProductCollectionResponse>> pushToProductCollection(
            @PathVariable Long id,
            @RequestBody(required = false) Alibaba1688SelectionPoolDTO.PushToProductCollectionRequest request
    ) throws Exception {
        Alibaba1688SelectionPoolDTO.PushToProductCollectionResponse response = service.pushToProductCollection(id, request);
        String message = Boolean.TRUE.equals(response.getExisting())
                ? "商品已存在于采集库"
                : "已推送到商品采集库";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @PutMapping("/skus/{skuId}")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionPoolDTO.SkuItem>> updateSku(
            @PathVariable Long skuId,
            @RequestBody Alibaba1688SelectionPoolDTO.UpdateSkuRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("选品池 SKU 已更新", service.updateSku(skuId, request)));
    }

    @PostMapping("/{id}/reports")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionPoolDTO.ReportItem>> saveReport(
            @PathVariable Long id,
            @RequestBody(required = false) Alibaba1688SelectionPoolDTO.SaveReportRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("选品分析报告已保存", service.saveReport(id, request)));
    }
}
