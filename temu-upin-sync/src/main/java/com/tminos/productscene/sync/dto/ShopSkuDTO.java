package com.tminos.productscene.sync.dto;

import lombok.Data;

public class ShopSkuDTO {

    @Data
    public static class ShopSkuItem {
        private Long id;
        private String shopId;
        private Long productId;
        private String productName;
        private Long productSkcId;
        private Long productSkuId;
        private String skuExtCode;
        private String skuSpecName;
        private String mainImageUrl;
        private Integer virtualStock;
        private Integer purchasePrice;
        private Integer referenceSupplierPrice;
        private Integer usSiteSupplierPrice;
    }

    @Data
    public static class PurchasePriceUpdateRequest {
        private String shopId;
        private Integer purchasePrice;
    }
}
