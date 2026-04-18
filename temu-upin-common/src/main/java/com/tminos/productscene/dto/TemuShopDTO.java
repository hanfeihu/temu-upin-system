package com.tminos.productscene.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TemuShopDTO {

    public static class CreateRequest {
        @NotBlank(message = "店铺名称不能为空")
        private String shopName;

        @NotBlank(message = "店铺ID不能为空")
        private String shopId;

        @NotBlank(message = "TOKEN 不能为空")
        private String token;

        @NotNull(message = "应用ID不能为空")
        private Long appId;

        private Boolean enabled;
        private Integer siteId;
        private String warehouseId;
        private Integer defaultStock;
        private Integer maxStock;
        private String originRegion1ShortName;
        private Long originRegion2Id;
        private String freightTemplateId;
        private Integer shipmentLimitSecond;

        public String getShopName() { return shopName; }
        public void setShopName(String shopName) { this.shopName = shopName; }
        public String getShopId() { return shopId; }
        public void setShopId(String shopId) { this.shopId = shopId; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public Long getAppId() { return appId; }
        public void setAppId(Long appId) { this.appId = appId; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Integer getSiteId() { return siteId; }
        public void setSiteId(Integer siteId) { this.siteId = siteId; }
        public String getWarehouseId() { return warehouseId; }
        public void setWarehouseId(String warehouseId) { this.warehouseId = warehouseId; }
        public Integer getDefaultStock() { return defaultStock; }
        public void setDefaultStock(Integer defaultStock) { this.defaultStock = defaultStock; }
        public Integer getMaxStock() { return maxStock; }
        public void setMaxStock(Integer maxStock) { this.maxStock = maxStock; }
        public String getOriginRegion1ShortName() { return originRegion1ShortName; }
        public void setOriginRegion1ShortName(String originRegion1ShortName) { this.originRegion1ShortName = originRegion1ShortName; }
        public Long getOriginRegion2Id() { return originRegion2Id; }
        public void setOriginRegion2Id(Long originRegion2Id) { this.originRegion2Id = originRegion2Id; }
        public String getFreightTemplateId() { return freightTemplateId; }
        public void setFreightTemplateId(String freightTemplateId) { this.freightTemplateId = freightTemplateId; }
        public Integer getShipmentLimitSecond() { return shipmentLimitSecond; }
        public void setShipmentLimitSecond(Integer shipmentLimitSecond) { this.shipmentLimitSecond = shipmentLimitSecond; }
    }

    public static class UpdateRequest {
        @NotBlank(message = "店铺名称不能为空")
        private String shopName;

        @NotBlank(message = "店铺ID不能为空")
        private String shopId;

        /** Optional; if empty, keep existing token */
        private String token;

        @NotNull(message = "应用ID不能为空")
        private Long appId;

        private Boolean enabled;
        private Integer siteId;
        private String warehouseId;
        private Integer defaultStock;
        private Integer maxStock;
        private String originRegion1ShortName;
        private Long originRegion2Id;
        private String freightTemplateId;
        private Integer shipmentLimitSecond;

        public String getShopName() { return shopName; }
        public void setShopName(String shopName) { this.shopName = shopName; }
        public String getShopId() { return shopId; }
        public void setShopId(String shopId) { this.shopId = shopId; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public Long getAppId() { return appId; }
        public void setAppId(Long appId) { this.appId = appId; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Integer getSiteId() { return siteId; }
        public void setSiteId(Integer siteId) { this.siteId = siteId; }
        public String getWarehouseId() { return warehouseId; }
        public void setWarehouseId(String warehouseId) { this.warehouseId = warehouseId; }
        public Integer getDefaultStock() { return defaultStock; }
        public void setDefaultStock(Integer defaultStock) { this.defaultStock = defaultStock; }
        public Integer getMaxStock() { return maxStock; }
        public void setMaxStock(Integer maxStock) { this.maxStock = maxStock; }
        public String getOriginRegion1ShortName() { return originRegion1ShortName; }
        public void setOriginRegion1ShortName(String originRegion1ShortName) { this.originRegion1ShortName = originRegion1ShortName; }
        public Long getOriginRegion2Id() { return originRegion2Id; }
        public void setOriginRegion2Id(Long originRegion2Id) { this.originRegion2Id = originRegion2Id; }
        public String getFreightTemplateId() { return freightTemplateId; }
        public void setFreightTemplateId(String freightTemplateId) { this.freightTemplateId = freightTemplateId; }
        public Integer getShipmentLimitSecond() { return shipmentLimitSecond; }
        public void setShipmentLimitSecond(Integer shipmentLimitSecond) { this.shipmentLimitSecond = shipmentLimitSecond; }
    }

    public static class View {
        private Long id;
        private Boolean enabled;
        private String shopName;
        private String shopId;
        private String tokenMasked;
        private Long appId;
        private String appName;
        private Integer siteId;
        private String warehouseId;
        private Integer defaultStock;
        private Integer maxStock;
        private String originRegion1ShortName;
        private Long originRegion2Id;
        private String freightTemplateId;
        private Integer shipmentLimitSecond;
        private String createdAt;
        private String updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getShopName() { return shopName; }
        public void setShopName(String shopName) { this.shopName = shopName; }
        public String getShopId() { return shopId; }
        public void setShopId(String shopId) { this.shopId = shopId; }
        public String getTokenMasked() { return tokenMasked; }
        public void setTokenMasked(String tokenMasked) { this.tokenMasked = tokenMasked; }
        public Long getAppId() { return appId; }
        public void setAppId(Long appId) { this.appId = appId; }
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public Integer getSiteId() { return siteId; }
        public void setSiteId(Integer siteId) { this.siteId = siteId; }
        public String getWarehouseId() { return warehouseId; }
        public void setWarehouseId(String warehouseId) { this.warehouseId = warehouseId; }
        public Integer getDefaultStock() { return defaultStock; }
        public void setDefaultStock(Integer defaultStock) { this.defaultStock = defaultStock; }
        public Integer getMaxStock() { return maxStock; }
        public void setMaxStock(Integer maxStock) { this.maxStock = maxStock; }
        public String getOriginRegion1ShortName() { return originRegion1ShortName; }
        public void setOriginRegion1ShortName(String originRegion1ShortName) { this.originRegion1ShortName = originRegion1ShortName; }
        public Long getOriginRegion2Id() { return originRegion2Id; }
        public void setOriginRegion2Id(Long originRegion2Id) { this.originRegion2Id = originRegion2Id; }
        public String getFreightTemplateId() { return freightTemplateId; }
        public void setFreightTemplateId(String freightTemplateId) { this.freightTemplateId = freightTemplateId; }
        public Integer getShipmentLimitSecond() { return shipmentLimitSecond; }
        public void setShipmentLimitSecond(Integer shipmentLimitSecond) { this.shipmentLimitSecond = shipmentLimitSecond; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }
}
