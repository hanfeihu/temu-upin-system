package com.tminos.productscene.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class TemuSpecMappingDTO {

    public static class FieldMapping {
        private String sourceFieldName;
        private String targetFieldName;
        private String role;
        private Boolean enabled;
        private Boolean ignoreBlank;
        private Boolean ignoreAsterisk;
        private Integer sortOrder;

        public String getSourceFieldName() { return sourceFieldName; }
        public void setSourceFieldName(String sourceFieldName) { this.sourceFieldName = sourceFieldName; }
        public String getTargetFieldName() { return targetFieldName; }
        public void setTargetFieldName(String targetFieldName) { this.targetFieldName = targetFieldName; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Boolean getIgnoreBlank() { return ignoreBlank; }
        public void setIgnoreBlank(Boolean ignoreBlank) { this.ignoreBlank = ignoreBlank; }
        public Boolean getIgnoreAsterisk() { return ignoreAsterisk; }
        public void setIgnoreAsterisk(Boolean ignoreAsterisk) { this.ignoreAsterisk = ignoreAsterisk; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public static class ValueRule {
        private String sourceFieldName;
        private String matchType;
        private String matchExpr;
        private String targetFieldName;
        private String targetValue;
        private Boolean enabled;
        private Integer sortOrder;

        public String getSourceFieldName() { return sourceFieldName; }
        public void setSourceFieldName(String sourceFieldName) { this.sourceFieldName = sourceFieldName; }
        public String getMatchType() { return matchType; }
        public void setMatchType(String matchType) { this.matchType = matchType; }
        public String getMatchExpr() { return matchExpr; }
        public void setMatchExpr(String matchExpr) { this.matchExpr = matchExpr; }
        public String getTargetFieldName() { return targetFieldName; }
        public void setTargetFieldName(String targetFieldName) { this.targetFieldName = targetFieldName; }
        public String getTargetValue() { return targetValue; }
        public void setTargetValue(String targetValue) { this.targetValue = targetValue; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public static class ProfileUpsertRequest {
        @NotBlank(message = "name is required")
        private String name;
        private Boolean enabled;
        private String sourceCategoryPath;
        private String targetCategoryId;
        private String targetCategoryName;
        private String targetParentSpecName;
        private String sourceSignature;
        private List<String> mainFieldCandidates;
        private List<FieldMapping> fieldMappings;
        private List<ValueRule> valueRules;
        private Boolean autoIgnoreConstantFields;
        private String notes;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getSourceCategoryPath() { return sourceCategoryPath; }
        public void setSourceCategoryPath(String sourceCategoryPath) { this.sourceCategoryPath = sourceCategoryPath; }
        public String getTargetCategoryId() { return targetCategoryId; }
        public void setTargetCategoryId(String targetCategoryId) { this.targetCategoryId = targetCategoryId; }
        public String getTargetCategoryName() { return targetCategoryName; }
        public void setTargetCategoryName(String targetCategoryName) { this.targetCategoryName = targetCategoryName; }
        public String getTargetParentSpecName() { return targetParentSpecName; }
        public void setTargetParentSpecName(String targetParentSpecName) { this.targetParentSpecName = targetParentSpecName; }
        public String getSourceSignature() { return sourceSignature; }
        public void setSourceSignature(String sourceSignature) { this.sourceSignature = sourceSignature; }
        public List<String> getMainFieldCandidates() { return mainFieldCandidates; }
        public void setMainFieldCandidates(List<String> mainFieldCandidates) { this.mainFieldCandidates = mainFieldCandidates; }
        public List<FieldMapping> getFieldMappings() { return fieldMappings; }
        public void setFieldMappings(List<FieldMapping> fieldMappings) { this.fieldMappings = fieldMappings; }
        public List<ValueRule> getValueRules() { return valueRules; }
        public void setValueRules(List<ValueRule> valueRules) { this.valueRules = valueRules; }
        public Boolean getAutoIgnoreConstantFields() { return autoIgnoreConstantFields; }
        public void setAutoIgnoreConstantFields(Boolean autoIgnoreConstantFields) { this.autoIgnoreConstantFields = autoIgnoreConstantFields; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class ProfileResponse {
        private Long id;
        private String name;
        private Boolean enabled;
        private String sourceCategoryPath;
        private String targetCategoryId;
        private String targetCategoryName;
        private String targetParentSpecName;
        private String sourceSignature;
        private List<String> mainFieldCandidates;
        private List<FieldMapping> fieldMappings;
        private List<ValueRule> valueRules;
        private Boolean autoIgnoreConstantFields;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getSourceCategoryPath() { return sourceCategoryPath; }
        public void setSourceCategoryPath(String sourceCategoryPath) { this.sourceCategoryPath = sourceCategoryPath; }
        public String getTargetCategoryId() { return targetCategoryId; }
        public void setTargetCategoryId(String targetCategoryId) { this.targetCategoryId = targetCategoryId; }
        public String getTargetCategoryName() { return targetCategoryName; }
        public void setTargetCategoryName(String targetCategoryName) { this.targetCategoryName = targetCategoryName; }
        public String getTargetParentSpecName() { return targetParentSpecName; }
        public void setTargetParentSpecName(String targetParentSpecName) { this.targetParentSpecName = targetParentSpecName; }
        public String getSourceSignature() { return sourceSignature; }
        public void setSourceSignature(String sourceSignature) { this.sourceSignature = sourceSignature; }
        public List<String> getMainFieldCandidates() { return mainFieldCandidates; }
        public void setMainFieldCandidates(List<String> mainFieldCandidates) { this.mainFieldCandidates = mainFieldCandidates; }
        public List<FieldMapping> getFieldMappings() { return fieldMappings; }
        public void setFieldMappings(List<FieldMapping> fieldMappings) { this.fieldMappings = fieldMappings; }
        public List<ValueRule> getValueRules() { return valueRules; }
        public void setValueRules(List<ValueRule> valueRules) { this.valueRules = valueRules; }
        public Boolean getAutoIgnoreConstantFields() { return autoIgnoreConstantFields; }
        public void setAutoIgnoreConstantFields(Boolean autoIgnoreConstantFields) { this.autoIgnoreConstantFields = autoIgnoreConstantFields; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class SourceFieldSummary {
        private String sourceFieldName;
        private Integer valueCount;
        private Integer distinctCount;
        private Boolean variable;
        private List<String> sampleValues;

        public String getSourceFieldName() { return sourceFieldName; }
        public void setSourceFieldName(String sourceFieldName) { this.sourceFieldName = sourceFieldName; }
        public Integer getValueCount() { return valueCount; }
        public void setValueCount(Integer valueCount) { this.valueCount = valueCount; }
        public Integer getDistinctCount() { return distinctCount; }
        public void setDistinctCount(Integer distinctCount) { this.distinctCount = distinctCount; }
        public Boolean getVariable() { return variable; }
        public void setVariable(Boolean variable) { this.variable = variable; }
        public List<String> getSampleValues() { return sampleValues; }
        public void setSampleValues(List<String> sampleValues) { this.sampleValues = sampleValues; }
    }

    public static class SourceRow {
        private Long id;
        private String temuSkuId;
        private String originSkuId;
        private String specKey;
        private Map<String, Object> specJsonMap;
        private String image;
        private BigDecimal originPrice;
        private BigDecimal supplyPrice;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTemuSkuId() { return temuSkuId; }
        public void setTemuSkuId(String temuSkuId) { this.temuSkuId = temuSkuId; }
        public String getOriginSkuId() { return originSkuId; }
        public void setOriginSkuId(String originSkuId) { this.originSkuId = originSkuId; }
        public String getSpecKey() { return specKey; }
        public void setSpecKey(String specKey) { this.specKey = specKey; }
        public Map<String, Object> getSpecJsonMap() { return specJsonMap; }
        public void setSpecJsonMap(Map<String, Object> specJsonMap) { this.specJsonMap = specJsonMap; }
        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }
        public BigDecimal getOriginPrice() { return originPrice; }
        public void setOriginPrice(BigDecimal originPrice) { this.originPrice = originPrice; }
        public BigDecimal getSupplyPrice() { return supplyPrice; }
        public void setSupplyPrice(BigDecimal supplyPrice) { this.supplyPrice = supplyPrice; }
    }

    public static class CandidateFieldSummary {
        private String fieldName;
        private Integer distinctCount;
        private List<String> sampleValues;
        private Boolean mainCandidate;

        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public Integer getDistinctCount() { return distinctCount; }
        public void setDistinctCount(Integer distinctCount) { this.distinctCount = distinctCount; }
        public List<String> getSampleValues() { return sampleValues; }
        public void setSampleValues(List<String> sampleValues) { this.sampleValues = sampleValues; }
        public Boolean getMainCandidate() { return mainCandidate; }
        public void setMainCandidate(Boolean mainCandidate) { this.mainCandidate = mainCandidate; }
    }

    public static class NormalizedRow {
        private Long id;
        private String temuSkuId;
        private String originSkuId;
        private String specKey;
        private String image;
        private BigDecimal supplyPrice;
        private Map<String, String> normalizedFields;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTemuSkuId() { return temuSkuId; }
        public void setTemuSkuId(String temuSkuId) { this.temuSkuId = temuSkuId; }
        public String getOriginSkuId() { return originSkuId; }
        public void setOriginSkuId(String originSkuId) { this.originSkuId = originSkuId; }
        public String getSpecKey() { return specKey; }
        public void setSpecKey(String specKey) { this.specKey = specKey; }
        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }
        public BigDecimal getSupplyPrice() { return supplyPrice; }
        public void setSupplyPrice(BigDecimal supplyPrice) { this.supplyPrice = supplyPrice; }
        public Map<String, String> getNormalizedFields() { return normalizedFields; }
        public void setNormalizedFields(Map<String, String> normalizedFields) { this.normalizedFields = normalizedFields; }
    }

    public static class PreviewGroup {
        private String groupKey;
        private String mainFieldValue;
        private Integer skuCount;
        private List<NormalizedRow> rows;

        public String getGroupKey() { return groupKey; }
        public void setGroupKey(String groupKey) { this.groupKey = groupKey; }
        public String getMainFieldValue() { return mainFieldValue; }
        public void setMainFieldValue(String mainFieldValue) { this.mainFieldValue = mainFieldValue; }
        public Integer getSkuCount() { return skuCount; }
        public void setSkuCount(Integer skuCount) { this.skuCount = skuCount; }
        public List<NormalizedRow> getRows() { return rows; }
        public void setRows(List<NormalizedRow> rows) { this.rows = rows; }
    }

    public static class ValidationMessage {
        private String severity;
        private String code;
        private String message;

        public ValidationMessage() {}

        public ValidationMessage(String severity, String code, String message) {
            this.severity = severity;
            this.code = code;
            this.message = message;
        }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class PreviewRequest {
        private Long profileId;
        private String targetParentSpecName;
        private String selectedMainField;
        private List<String> selectedSkuFields;
        private List<FieldMapping> fieldMappings;
        private List<ValueRule> valueRules;
        private List<String> mainFieldCandidates;
        private Boolean autoIgnoreConstantFields;

        public Long getProfileId() { return profileId; }
        public void setProfileId(Long profileId) { this.profileId = profileId; }
        public String getTargetParentSpecName() { return targetParentSpecName; }
        public void setTargetParentSpecName(String targetParentSpecName) { this.targetParentSpecName = targetParentSpecName; }
        public String getSelectedMainField() { return selectedMainField; }
        public void setSelectedMainField(String selectedMainField) { this.selectedMainField = selectedMainField; }
        public List<String> getSelectedSkuFields() { return selectedSkuFields; }
        public void setSelectedSkuFields(List<String> selectedSkuFields) { this.selectedSkuFields = selectedSkuFields; }
        public List<FieldMapping> getFieldMappings() { return fieldMappings; }
        public void setFieldMappings(List<FieldMapping> fieldMappings) { this.fieldMappings = fieldMappings; }
        public List<ValueRule> getValueRules() { return valueRules; }
        public void setValueRules(List<ValueRule> valueRules) { this.valueRules = valueRules; }
        public List<String> getMainFieldCandidates() { return mainFieldCandidates; }
        public void setMainFieldCandidates(List<String> mainFieldCandidates) { this.mainFieldCandidates = mainFieldCandidates; }
        public Boolean getAutoIgnoreConstantFields() { return autoIgnoreConstantFields; }
        public void setAutoIgnoreConstantFields(Boolean autoIgnoreConstantFields) { this.autoIgnoreConstantFields = autoIgnoreConstantFields; }
    }

    public static class PreviewResponse {
        private String targetParentSpecName;
        private String selectedMainField;
        private List<String> selectedSkuFields;
        private Integer skcCount;
        private Integer skuCount;
        private Boolean publishable;
        private List<CandidateFieldSummary> candidateFields;
        private List<NormalizedRow> normalizedRows;
        private List<PreviewGroup> groups;
        private List<ValidationMessage> validations;

        public String getTargetParentSpecName() { return targetParentSpecName; }
        public void setTargetParentSpecName(String targetParentSpecName) { this.targetParentSpecName = targetParentSpecName; }
        public String getSelectedMainField() { return selectedMainField; }
        public void setSelectedMainField(String selectedMainField) { this.selectedMainField = selectedMainField; }
        public List<String> getSelectedSkuFields() { return selectedSkuFields; }
        public void setSelectedSkuFields(List<String> selectedSkuFields) { this.selectedSkuFields = selectedSkuFields; }
        public Integer getSkcCount() { return skcCount; }
        public void setSkcCount(Integer skcCount) { this.skcCount = skcCount; }
        public Integer getSkuCount() { return skuCount; }
        public void setSkuCount(Integer skuCount) { this.skuCount = skuCount; }
        public Boolean getPublishable() { return publishable; }
        public void setPublishable(Boolean publishable) { this.publishable = publishable; }
        public List<CandidateFieldSummary> getCandidateFields() { return candidateFields; }
        public void setCandidateFields(List<CandidateFieldSummary> candidateFields) { this.candidateFields = candidateFields; }
        public List<NormalizedRow> getNormalizedRows() { return normalizedRows; }
        public void setNormalizedRows(List<NormalizedRow> normalizedRows) { this.normalizedRows = normalizedRows; }
        public List<PreviewGroup> getGroups() { return groups; }
        public void setGroups(List<PreviewGroup> groups) { this.groups = groups; }
        public List<ValidationMessage> getValidations() { return validations; }
        public void setValidations(List<ValidationMessage> validations) { this.validations = validations; }
    }

    public static class DraftSaveRequest extends PreviewRequest {
        private String draftName;
        private Boolean active;

        public String getDraftName() { return draftName; }
        public void setDraftName(String draftName) { this.draftName = draftName; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }

    public static class DraftResponse {
        private Long id;
        private Long spuId;
        private Long profileId;
        private String draftName;
        private String targetParentSpecName;
        private String selectedMainField;
        private List<String> selectedSkuFields;
        private List<FieldMapping> fieldMappings;
        private List<ValueRule> valueRules;
        private Boolean autoIgnoreConstantFields;
        private Boolean active;
        private PreviewResponse preview;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getSpuId() { return spuId; }
        public void setSpuId(Long spuId) { this.spuId = spuId; }
        public Long getProfileId() { return profileId; }
        public void setProfileId(Long profileId) { this.profileId = profileId; }
        public String getDraftName() { return draftName; }
        public void setDraftName(String draftName) { this.draftName = draftName; }
        public String getTargetParentSpecName() { return targetParentSpecName; }
        public void setTargetParentSpecName(String targetParentSpecName) { this.targetParentSpecName = targetParentSpecName; }
        public String getSelectedMainField() { return selectedMainField; }
        public void setSelectedMainField(String selectedMainField) { this.selectedMainField = selectedMainField; }
        public List<String> getSelectedSkuFields() { return selectedSkuFields; }
        public void setSelectedSkuFields(List<String> selectedSkuFields) { this.selectedSkuFields = selectedSkuFields; }
        public List<FieldMapping> getFieldMappings() { return fieldMappings; }
        public void setFieldMappings(List<FieldMapping> fieldMappings) { this.fieldMappings = fieldMappings; }
        public List<ValueRule> getValueRules() { return valueRules; }
        public void setValueRules(List<ValueRule> valueRules) { this.valueRules = valueRules; }
        public Boolean getAutoIgnoreConstantFields() { return autoIgnoreConstantFields; }
        public void setAutoIgnoreConstantFields(Boolean autoIgnoreConstantFields) { this.autoIgnoreConstantFields = autoIgnoreConstantFields; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
        public PreviewResponse getPreview() { return preview; }
        public void setPreview(PreviewResponse preview) { this.preview = preview; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class WorkbenchResponse {
        private Long spuId;
        private String productName;
        private String sourceCategoryPath;
        private String targetCategoryId;
        private String targetCategoryName;
        private String sourceSignature;
        private List<TemuCategoryDTO.ParentSpecOption> targetParentSpecOptions;
        private List<SourceFieldSummary> sourceFields;
        private List<SourceRow> sourceRows;
        private List<ProfileResponse> profiles;
        private DraftResponse latestDraft;

        public Long getSpuId() { return spuId; }
        public void setSpuId(Long spuId) { this.spuId = spuId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getSourceCategoryPath() { return sourceCategoryPath; }
        public void setSourceCategoryPath(String sourceCategoryPath) { this.sourceCategoryPath = sourceCategoryPath; }
        public String getTargetCategoryId() { return targetCategoryId; }
        public void setTargetCategoryId(String targetCategoryId) { this.targetCategoryId = targetCategoryId; }
        public String getTargetCategoryName() { return targetCategoryName; }
        public void setTargetCategoryName(String targetCategoryName) { this.targetCategoryName = targetCategoryName; }
        public String getSourceSignature() { return sourceSignature; }
        public void setSourceSignature(String sourceSignature) { this.sourceSignature = sourceSignature; }
        public List<TemuCategoryDTO.ParentSpecOption> getTargetParentSpecOptions() { return targetParentSpecOptions; }
        public void setTargetParentSpecOptions(List<TemuCategoryDTO.ParentSpecOption> targetParentSpecOptions) { this.targetParentSpecOptions = targetParentSpecOptions; }
        public List<SourceFieldSummary> getSourceFields() { return sourceFields; }
        public void setSourceFields(List<SourceFieldSummary> sourceFields) { this.sourceFields = sourceFields; }
        public List<SourceRow> getSourceRows() { return sourceRows; }
        public void setSourceRows(List<SourceRow> sourceRows) { this.sourceRows = sourceRows; }
        public List<ProfileResponse> getProfiles() { return profiles; }
        public void setProfiles(List<ProfileResponse> profiles) { this.profiles = profiles; }
        public DraftResponse getLatestDraft() { return latestDraft; }
        public void setLatestDraft(DraftResponse latestDraft) { this.latestDraft = latestDraft; }
    }
}