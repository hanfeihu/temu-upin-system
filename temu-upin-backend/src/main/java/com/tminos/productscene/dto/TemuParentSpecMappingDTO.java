package com.tminos.productscene.dto;

import jakarta.validation.constraints.NotBlank;

public class TemuParentSpecMappingDTO {

    public static class UpsertRequest {
        @NotBlank(message = "sourceFieldName is required")
        private String sourceFieldName;

        @NotBlank(message = "targetParentSpecName is required")
        private String targetParentSpecName;

        private Boolean enabled;
        private String notes;

        public String getSourceFieldName() {
            return sourceFieldName;
        }

        public void setSourceFieldName(String sourceFieldName) {
            this.sourceFieldName = sourceFieldName;
        }

        public String getTargetParentSpecName() {
            return targetParentSpecName;
        }

        public void setTargetParentSpecName(String targetParentSpecName) {
            this.targetParentSpecName = targetParentSpecName;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}