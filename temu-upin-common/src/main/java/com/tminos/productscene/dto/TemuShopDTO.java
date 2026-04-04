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
    }

    public static class View {
        private Long id;
        private Boolean enabled;
        private String shopName;
        private String shopId;
        private String tokenMasked;
        private Long appId;
        private String appName;
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
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }
}
