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

    @Data
    public static class SupplierPriceRefreshRequest {
        private String shopId;
    }

    @Data
    public static class BatchZeroVirtualStockRequest {
        private String shopId;
        private Long productSkcId;
        private Long productSkuId;
        private String skuExtCode;
        private Boolean virtualStockGtZero;
        private Integer minSupplierPrice;
        private Integer maxSupplierPrice;
        private String warehouseId;
    }

    @Data
    public static class BatchZeroVirtualStockResult {
        private int matchedCount;
        private int updatedCount;
        private int alreadyZeroCount;
        private int failedCount;
        private java.util.List<String> messages;
    }

    @Data
    public static class WarehouseOption {
        private Integer siteId;
        private String siteName;
        private String warehouseId;
        private String warehouseName;
        private String managementType;
        private boolean defaultWarehouse;
    }
}
