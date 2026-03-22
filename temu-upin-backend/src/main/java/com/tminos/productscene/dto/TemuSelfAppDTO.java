package com.tminos.productscene.dto;

import jakarta.validation.constraints.NotBlank;

public class TemuSelfAppDTO {

    public static class CreateRequest {
        @NotBlank(message = "应用名称不能为空")
        private String appName;

        @NotBlank(message = "App Key 不能为空")
        private String appKey;

        @NotBlank(message = "App Secret 不能为空")
        private String appSecret;

        private Boolean enabled;

        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getAppKey() { return appKey; }
        public void setAppKey(String appKey) { this.appKey = appKey; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }

    public static class UpdateRequest {
        @NotBlank(message = "应用名称不能为空")
        private String appName;

        @NotBlank(message = "App Key 不能为空")
        private String appKey;

        /** Optional; if empty, keep existing secret */
        private String appSecret;

        private Boolean enabled;

        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getAppKey() { return appKey; }
        public void setAppKey(String appKey) { this.appKey = appKey; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }

    public static class View {
        private Long id;
        private Boolean enabled;
        private String appName;
        private String appKey;
        private String appSecretMasked;
        private String createdAt;
        private String updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getAppKey() { return appKey; }
        public void setAppKey(String appKey) { this.appKey = appKey; }
        public String getAppSecretMasked() { return appSecretMasked; }
        public void setAppSecretMasked(String appSecretMasked) { this.appSecretMasked = appSecretMasked; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }
}
