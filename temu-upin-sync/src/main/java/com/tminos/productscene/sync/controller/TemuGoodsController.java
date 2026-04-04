package com.tminos.productscene.sync.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.sync.dto.TemuGoodsDTO;
import com.tminos.productscene.sync.entity.TemuGoods;
import com.tminos.productscene.sync.service.TemuGoodsRepairJobService;
import com.tminos.productscene.sync.service.TemuGoodsService;
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

    public TemuGoodsController(TemuGoodsService goodsService,
                               TemuGoodsRepairJobService goodsRepairJobService) {
        this.goodsService = goodsService;
        this.goodsRepairJobService = goodsRepairJobService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TemuGoods>>> list(
            @RequestParam String shopId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer skcSiteStatus,
            @RequestParam(required = false) Integer minSupplierPrice,
            @RequestParam(required = false) Integer maxSupplierPrice,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.success(
                goodsService.listGoods(shopId, keyword, skcSiteStatus, minSupplierPrice, maxSupplierPrice, page, pageSize)));
    }

        @GetMapping("/export")
        public ResponseEntity<byte[]> export(
            @RequestParam String shopId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer skcSiteStatus,
            @RequestParam(required = false) Integer minSupplierPrice,
            @RequestParam(required = false) Integer maxSupplierPrice) {
        TemuGoodsService.ExportFile exportFile = goodsService.exportGoodsBySku(
            shopId, keyword, skcSiteStatus, minSupplierPrice, maxSupplierPrice);
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
