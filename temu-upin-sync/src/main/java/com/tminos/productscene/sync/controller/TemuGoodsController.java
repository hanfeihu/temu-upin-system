package com.tminos.productscene.sync.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.sync.dto.TemuGoodsDTO;
import com.tminos.productscene.sync.entity.TemuGoods;
import com.tminos.productscene.sync.service.TemuGoodsRepairJobService;
import com.tminos.productscene.sync.service.TemuGoodsService;
import com.tminos.productscene.sync.service.TemuSensitiveAttributeConfirmService;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/sync/goods")
public class TemuGoodsController {

    private final TemuGoodsService goodsService;
    private final TemuGoodsRepairJobService goodsRepairJobService;
    private final TemuSensitiveAttributeConfirmService sensitiveAttributeConfirmService;

    public TemuGoodsController(TemuGoodsService goodsService,
                               TemuGoodsRepairJobService goodsRepairJobService,
                               TemuSensitiveAttributeConfirmService sensitiveAttributeConfirmService) {
        this.goodsService = goodsService;
        this.goodsRepairJobService = goodsRepairJobService;
        this.sensitiveAttributeConfirmService = sensitiveAttributeConfirmService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TemuGoods>>> list(
            @RequestParam String shopId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long productSkuId,
            @RequestParam(required = false) Integer skcSiteStatus,
            @RequestParam(required = false) Boolean activityBlacklisted,
            @RequestParam(required = false) Boolean allSkuOutOfStock,
            @RequestParam(required = false) Integer minSupplierPrice,
            @RequestParam(required = false) Integer maxSupplierPrice,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.success(
                goodsService.listGoods(shopId, keyword, productSkuId, skcSiteStatus, activityBlacklisted, allSkuOutOfStock, minSupplierPrice, maxSupplierPrice, page, pageSize)));
    }

        @GetMapping("/export")
        public ResponseEntity<byte[]> export(
            @RequestParam String shopId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long productSkuId,
            @RequestParam(required = false) Integer skcSiteStatus,
            @RequestParam(required = false) Boolean activityBlacklisted,
            @RequestParam(required = false) Boolean allSkuOutOfStock,
            @RequestParam(required = false) Integer minSupplierPrice,
            @RequestParam(required = false) Integer maxSupplierPrice) {
        TemuGoodsService.ExportFile exportFile = goodsService.exportGoodsBySku(
            shopId, keyword, productSkuId, skcSiteStatus, activityBlacklisted, allSkuOutOfStock, minSupplierPrice, maxSupplierPrice);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(exportFile.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body(exportFile.content());
        }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<TemuGoodsDTO.GoodsDetail>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(goodsService.getGoodsDetail(id)));
    }

    @PostMapping("/{id:\\d+}/confirm-sensitive-attr")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> confirmSensitiveAttr(@PathVariable Long id) {
        java.util.Map<String, Object> result = sensitiveAttributeConfirmService.confirmGoods(id);
        boolean success = "CONFIRMED".equals(String.valueOf(result.get("status")))
                || "SKIPPED".equals(String.valueOf(result.get("status")));
        return success
                ? ResponseEntity.ok(ApiResponse.success(String.valueOf(result.get("message")), result))
                : ResponseEntity.ok(ApiResponse.error(String.valueOf(result.get("message")), result));
    }

    @PostMapping("/confirm-sensitive-attr")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> confirmSensitiveAttrBatch(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success("敏感属性自动确认已执行", sensitiveAttributeConfirmService.confirmPendingBatch(limit)));
    }

    @RequestMapping(value = "/repair-details", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> repairDetails(@RequestParam String shopId) {
        return ResponseEntity.ok(ApiResponse.success(goodsRepairJobService.startRepair(shopId)));
    }

    @GetMapping("/repair-details/status")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> repairDetailsStatus(@RequestParam String jobId) {
        return ResponseEntity.ok(ApiResponse.success(goodsRepairJobService.getStatus(jobId)));
    }

    @GetMapping("/repair-details/status/latest")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> latestRepairDetailsStatus(@RequestParam String shopId) {
        return ResponseEntity.ok(ApiResponse.success(goodsRepairJobService.getLatestStatus(shopId)));
    }
}
