package com.tminos.productscene.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TemuMainSaleSpecRuleDTO {

    public static class UpsertRequest {
        @NotNull(message = "ruleType is required")
        private String ruleType; // MANUAL/LEARNED

        @NotBlank(message = "skuSignature is required")
        private String skuSignature;

        @NotBlank(message = "parentSpecName is required")
        private String parentSpecName;

        private String dimKey;

        private Boolean enabled;

        public String getRuleType() { return ruleType; }
        public void setRuleType(String ruleType) { this.ruleType = ruleType; }
        public String getSkuSignature() { return skuSignature; }
        public void setSkuSignature(String skuSignature) { this.skuSignature = skuSignature; }
        public String getParentSpecName() { return parentSpecName; }
        public void setParentSpecName(String parentSpecName) { this.parentSpecName = parentSpecName; }
        public String getDimKey() { return dimKey; }
        public void setDimKey(String dimKey) { this.dimKey = dimKey; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }
}
