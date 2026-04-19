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

        private String orderToken;
        private Long orderAppId;
        private String dianxiaomiCookie;

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
        public String getOrderToken() { return orderToken; }
        public void setOrderToken(String orderToken) { this.orderToken = orderToken; }
        public Long getOrderAppId() { return orderAppId; }
        public void setOrderAppId(Long orderAppId) { this.orderAppId = orderAppId; }
        public String getDianxiaomiCookie() { return dianxiaomiCookie; }
        public void setDianxiaomiCookie(String dianxiaomiCookie) { this.dianxiaomiCookie = dianxiaomiCookie; }
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

        private String orderToken;
        private Long orderAppId;
        private String dianxiaomiCookie;

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
        public String getOrderToken() { return orderToken; }
        public void setOrderToken(String orderToken) { this.orderToken = orderToken; }
        public Long getOrderAppId() { return orderAppId; }
        public void setOrderAppId(Long orderAppId) { this.orderAppId = orderAppId; }
        public String getDianxiaomiCookie() { return dianxiaomiCookie; }
        public void setDianxiaomiCookie(String dianxiaomiCookie) { this.dianxiaomiCookie = dianxiaomiCookie; }
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
        private String productTokenMasked;
        private String orderTokenMasked;
        private Long productAppId;
        private String productAppName;
        private Long orderAppId;
        private String orderAppName;
        private String dianxiaomiCookieMasked;
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
        public String getProductTokenMasked() { return productTokenMasked; }
        public void setProductTokenMasked(String productTokenMasked) { this.productTokenMasked = productTokenMasked; }
        public String getOrderTokenMasked() { return orderTokenMasked; }
        public void setOrderTokenMasked(String orderTokenMasked) { this.orderTokenMasked = orderTokenMasked; }
        public Long getProductAppId() { return productAppId; }
        public void setProductAppId(Long productAppId) { this.productAppId = productAppId; }
        public String getProductAppName() { return productAppName; }
        public void setProductAppName(String productAppName) { this.productAppName = productAppName; }
        public Long getOrderAppId() { return orderAppId; }
        public void setOrderAppId(Long orderAppId) { this.orderAppId = orderAppId; }
        public String getOrderAppName() { return orderAppName; }
        public void setOrderAppName(String orderAppName) { this.orderAppName = orderAppName; }
        public String getDianxiaomiCookieMasked() { return dianxiaomiCookieMasked; }
        public void setDianxiaomiCookieMasked(String dianxiaomiCookieMasked) { this.dianxiaomiCookieMasked = dianxiaomiCookieMasked; }
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
