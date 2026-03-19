package com.tminos.productscene.dto;

import com.tminos.productscene.entity.TemuAttrRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TemuAttrRuleDTO {

    public static class UpsertRequest {
        @NotNull(message = "ruleType is required")
        private TemuAttrRule.RuleType ruleType;
        private String leafCatId;
        @NotBlank(message = "attrName is required")
        private String attrName;
        @NotNull(message = "fillMode is required")
        private TemuAttrRule.FillMode fillMode;
        private String fixedValue;
        private Boolean enabled;
        private Integer sortOrder;

        public TemuAttrRule.RuleType getRuleType() {
            return ruleType;
        }

        public void setRuleType(TemuAttrRule.RuleType ruleType) {
            this.ruleType = ruleType;
        }

        public String getLeafCatId() {
            return leafCatId;
        }

        public void setLeafCatId(String leafCatId) {
            this.leafCatId = leafCatId;
        }

        public String getAttrName() {
            return attrName;
        }

        public void setAttrName(String attrName) {
            this.attrName = attrName;
        }

        public TemuAttrRule.FillMode getFillMode() {
            return fillMode;
        }

        public void setFillMode(TemuAttrRule.FillMode fillMode) {
            this.fillMode = fillMode;
        }

        public String getFixedValue() {
            return fixedValue;
        }

        public void setFixedValue(String fixedValue) {
            this.fixedValue = fixedValue;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
}
