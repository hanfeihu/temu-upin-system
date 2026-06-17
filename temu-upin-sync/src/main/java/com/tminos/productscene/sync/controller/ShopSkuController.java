package com.tminos.productscene.sync.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.sync.dto.ShopSkuDTO;
import com.tminos.productscene.sync.service.ShopSkuService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync/shop-skus")
public class ShopSkuController {

    private final ShopSkuService shopSkuService;

    public ShopSkuController(ShopSkuService shopSkuService) {
        this.shopSkuService = shopSkuService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ShopSkuDTO.ShopSkuItem>>> list(
            @RequestParam String shopId,
            @RequestParam(required = false) Long productSkcId,
            @RequestParam(required = false) Long productSkuId,
            @RequestParam(required = false) String skuExtCode,
            @RequestParam(required = false) Boolean virtualStockGtZero,
            @RequestParam(required = false) Integer minSupplierPrice,
            @RequestParam(required = false) Integer maxSupplierPrice,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.success(
                shopSkuService.list(shopId, productSkcId, productSkuId, skuExtCode, virtualStockGtZero, minSupplierPrice, maxSupplierPrice, page, pageSize)));
    }

    @GetMapping("/warehouses")
    public ResponseEntity<ApiResponse<java.util.List<ShopSkuDTO.WarehouseOption>>> listWarehouses(
            @RequestParam String shopId) {
        return ResponseEntity.ok(ApiResponse.success(shopSkuService.listWarehouses(shopId)));
    }

    @PutMapping("/{productSkuId}/purchase-price")
    public ResponseEntity<ApiResponse<ShopSkuDTO.ShopSkuItem>> updatePurchasePrice(
            @PathVariable Long productSkuId,
            @RequestBody ShopSkuDTO.PurchasePriceUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "采购价已保存",
                shopSkuService.updatePurchasePrice(productSkuId, request)));
    }

    @PostMapping("/{productSkuId}/supplier-price/refresh")
    public ResponseEntity<ApiResponse<ShopSkuDTO.ShopSkuItem>> refreshSupplierPrice(
            @PathVariable Long productSkuId,
            @RequestBody ShopSkuDTO.SupplierPriceRefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "供货价已同步",
                shopSkuService.refreshSupplierPrice(productSkuId, request)));
    }

    @PostMapping("/batch-zero-virtual-stock")
    public ResponseEntity<ApiResponse<ShopSkuDTO.BatchZeroVirtualStockResult>> batchZeroVirtualStock(
            @RequestBody ShopSkuDTO.BatchZeroVirtualStockRequest request) {
        ShopSkuDTO.BatchZeroVirtualStockResult result = shopSkuService.batchZeroVirtualStock(request);
        String message = String.format(
                "批量置0完成：命中 %d 个，成功 %d 个，已是0 %d 个，失败 %d 个",
                result.getMatchedCount(),
                result.getUpdatedCount(),
                result.getAlreadyZeroCount(),
                result.getFailedCount()
        );
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }
}
