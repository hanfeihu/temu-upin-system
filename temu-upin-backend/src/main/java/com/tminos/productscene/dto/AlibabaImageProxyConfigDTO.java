package com.tminos.productscene.dto;

public class AlibabaImageProxyConfigDTO {

    public static class UpsertRequest {
        private String configName;
        private Boolean enabled;
        private String proxyBaseUrl;
        private String imageProxyPath;
        private String allowedHostsText;
        private String remark;

        public String getConfigName() { return configName; }
        public void setConfigName(String configName) { this.configName = configName; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getProxyBaseUrl() { return proxyBaseUrl; }
        public void setProxyBaseUrl(String proxyBaseUrl) { this.proxyBaseUrl = proxyBaseUrl; }
        public String getImageProxyPath() { return imageProxyPath; }
        public void setImageProxyPath(String imageProxyPath) { this.imageProxyPath = imageProxyPath; }
        public String getAllowedHostsText() { return allowedHostsText; }
        public void setAllowedHostsText(String allowedHostsText) { this.allowedHostsText = allowedHostsText; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class View {
        private Long id;
        private String configName;
        private Boolean enabled;
        private String proxyBaseUrl;
        private String imageProxyPath;
        private String allowedHostsText;
        private String remark;
        private String createdAt;
        private String updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getConfigName() { return configName; }
        public void setConfigName(String configName) { this.configName = configName; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getProxyBaseUrl() { return proxyBaseUrl; }
        public void setProxyBaseUrl(String proxyBaseUrl) { this.proxyBaseUrl = proxyBaseUrl; }
        public String getImageProxyPath() { return imageProxyPath; }
        public void setImageProxyPath(String imageProxyPath) { this.imageProxyPath = imageProxyPath; }
        public String getAllowedHostsText() { return allowedHostsText; }
        public void setAllowedHostsText(String allowedHostsText) { this.allowedHostsText = allowedHostsText; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }
}
