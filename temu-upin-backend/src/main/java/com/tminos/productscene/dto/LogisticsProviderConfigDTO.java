package com.tminos.productscene.dto;

public class LogisticsProviderConfigDTO {

    public static class UpsertRequest {
        private String providerCode;
        private String providerName;
        private Boolean enabled;
        private String baseUrl;
        private String appToken;
        private String appKey;
        private Integer connectTimeoutMs;
        private Integer readTimeoutMs;
        private String extraConfigJson;

        public String getProviderCode() { return providerCode; }
        public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
        public String getProviderName() { return providerName; }
        public void setProviderName(String providerName) { this.providerName = providerName; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getAppToken() { return appToken; }
        public void setAppToken(String appToken) { this.appToken = appToken; }
        public String getAppKey() { return appKey; }
        public void setAppKey(String appKey) { this.appKey = appKey; }
        public Integer getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(Integer connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public Integer getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(Integer readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
        public String getExtraConfigJson() { return extraConfigJson; }
        public void setExtraConfigJson(String extraConfigJson) { this.extraConfigJson = extraConfigJson; }
    }

    public static class View {
        private Long id;
        private String providerCode;
        private String providerName;
        private Boolean enabled;
        private String baseUrl;
        private String appTokenMasked;
        private String appKeyMasked;
        private Integer connectTimeoutMs;
        private Integer readTimeoutMs;
        private String extraConfigJson;
        private String createdAt;
        private String updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getProviderCode() { return providerCode; }
        public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
        public String getProviderName() { return providerName; }
        public void setProviderName(String providerName) { this.providerName = providerName; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getAppTokenMasked() { return appTokenMasked; }
        public void setAppTokenMasked(String appTokenMasked) { this.appTokenMasked = appTokenMasked; }
        public String getAppKeyMasked() { return appKeyMasked; }
        public void setAppKeyMasked(String appKeyMasked) { this.appKeyMasked = appKeyMasked; }
        public Integer getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(Integer connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public Integer getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(Integer readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
        public String getExtraConfigJson() { return extraConfigJson; }
        public void setExtraConfigJson(String extraConfigJson) { this.extraConfigJson = extraConfigJson; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }
}
