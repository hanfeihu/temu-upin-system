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
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.success(
                shopSkuService.list(shopId, productSkcId, productSkuId, skuExtCode, page, pageSize)));
    }

    @PutMapping("/{productSkuId}/purchase-price")
    public ResponseEntity<ApiResponse<ShopSkuDTO.ShopSkuItem>> updatePurchasePrice(
            @PathVariable Long productSkuId,
            @RequestBody ShopSkuDTO.PurchasePriceUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "采购价已保存",
                shopSkuService.updatePurchasePrice(productSkuId, request)));
    }
}
