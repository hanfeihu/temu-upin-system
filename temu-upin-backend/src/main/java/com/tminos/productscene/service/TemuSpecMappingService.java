package com.tminos.productscene.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.ProductCollectionDTO;
import com.tminos.productscene.dto.TemuSkuDTO;
import com.tminos.productscene.dto.TemuSpecMappingDTO;
import com.tminos.productscene.entity.TemuSpecMappingDraft;
import com.tminos.productscene.entity.TemuSpecMappingProfile;
import com.tminos.productscene.repository.TemuSpecMappingDraftRepository;
import com.tminos.productscene.repository.TemuSpecMappingProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TemuSpecMappingService {

    private static final TypeReference<List<TemuSpecMappingDTO.FieldMapping>> FIELD_MAPPING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<TemuSpecMappingDTO.ValueRule>> VALUE_RULE_LIST = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final TemuSpecMappingProfileRepository profileRepository;
    private final TemuSpecMappingDraftRepository draftRepository;
    private final ProductCollectionService productCollectionService;
    private final ObjectMapper objectMapper;

    public TemuSpecMappingService(TemuSpecMappingProfileRepository profileRepository,
                                  TemuSpecMappingDraftRepository draftRepository,
                                  ProductCollectionService productCollectionService,
                                  ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.draftRepository = draftRepository;
        this.productCollectionService = productCollectionService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<TemuSpecMappingDTO.ProfileResponse> listProfiles(Boolean enabled) {
        List<TemuSpecMappingProfile> profiles = enabled == null
                ? profileRepository.findAllByOrderByIdDesc()
                : profileRepository.findByEnabledOrderByIdDesc(enabled);
        return profiles.stream().map(this::toProfileResponse).collect(Collectors.toList());
    }

    @Transactional
    public TemuSpecMappingDTO.ProfileResponse createProfile(TemuSpecMappingDTO.ProfileUpsertRequest request) {
        TemuSpecMappingProfile profile = new TemuSpecMappingProfile();
        applyProfile(profile, request);
        return toProfileResponse(profileRepository.save(profile));
    }

    @Transactional
    @SuppressWarnings("null")
    public TemuSpecMappingDTO.ProfileResponse updateProfile(Long id, TemuSpecMappingDTO.ProfileUpsertRequest request) {
        TemuSpecMappingProfile profile = profileRepository.findById(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new EntityNotFoundException("spec mapping profile not found"));
        applyProfile(profile, request);
        return toProfileResponse(Objects.requireNonNull(profileRepository.save(profile), "profile save result"));
    }

    @Transactional
    public void deleteProfile(Long id) {
        if (id == null) {
            return;
        }
        profileRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public TemuSpecMappingDTO.WorkbenchResponse getWorkbench(Long spuId) {
        ProductCollectionDTO.ProductCollectionDetailResponse detail = productCollectionService.getDetail(spuId);
        TemuSpecMappingDTO.WorkbenchResponse response = new TemuSpecMappingDTO.WorkbenchResponse();
        response.setSpuId(spuId);
        response.setProductName(detail.getProductName());
        response.setSourceCategoryPath(detail.getOriginalCategory());
        response.setTargetCategoryId(detail.getTemuCatid());
        response.setTargetCategoryName(detail.getTemuCatname());

        List<TemuSpecMappingDTO.SourceFieldSummary> sourceFields = buildSourceFieldSummaries(detail);
        List<TemuSpecMappingDTO.SourceRow> sourceRows = buildSourceRows(detail);
        response.setSourceFields(sourceFields);
        response.setSourceRows(sourceRows);
        response.setSourceSignature(buildSourceSignature(sourceFields));
        response.setProfiles(listProfiles(null));
        response.setLatestDraft(draftRepository.findFirstBySpuIdOrderByIdDesc(spuId).map(this::toDraftResponse).orElse(null));
        return response;
    }

    @Transactional(readOnly = true)
    public TemuSpecMappingDTO.PreviewResponse preview(Long spuId, TemuSpecMappingDTO.PreviewRequest request) {
        ProductCollectionDTO.ProductCollectionDetailResponse detail = productCollectionService.getDetail(spuId);
        return buildPreview(detail, request);
    }

    @Transactional
    @SuppressWarnings("null")
    public TemuSpecMappingDTO.DraftResponse saveDraft(Long spuId, TemuSpecMappingDTO.DraftSaveRequest request) {
        ProductCollectionDTO.ProductCollectionDetailResponse detail = productCollectionService.getDetail(spuId);
        TemuSpecMappingDTO.PreviewResponse preview = buildPreview(detail, request);

        boolean activate = Boolean.TRUE.equals(request.getActive());
        if (activate) {
            List<TemuSpecMappingDraft> existingDrafts = draftRepository.findBySpuIdOrderByIdDesc(spuId);
            for (TemuSpecMappingDraft existing : existingDrafts) {
                if (Boolean.TRUE.equals(existing.getActive())) {
                    existing.setActive(false);
                }
            }
            draftRepository.saveAll(Objects.requireNonNull(existingDrafts, "existing drafts"));
        }

        TemuSpecMappingDraft draft = TemuSpecMappingDraft.builder()
                .spuId(spuId)
                .profileId(request.getProfileId())
                .draftName(firstNonBlank(request.getDraftName(), detail.getProductName() + " 规格映射草案"))
                .targetParentSpecName(firstNonBlank(request.getTargetParentSpecName(), "型号"))
                .selectedMainField(preview.getSelectedMainField())
                .selectedSkuFieldsJson(writeJson(preview.getSelectedSkuFields()))
                .fieldMappingsJson(writeJson(sortFieldMappings(request.getFieldMappings())))
                .valueRulesJson(writeJson(sortValueRules(request.getValueRules())))
                .autoIgnoreConstantFields(Boolean.TRUE.equals(request.getAutoIgnoreConstantFields()))
                .sourceSnapshotJson(writeJson(buildSourceRows(detail)))
                .previewJson(writeJson(preview))
                .active(activate)
                .build();
        return toDraftResponse(Objects.requireNonNull(draftRepository.save(draft), "draft save result"));
    }

    private void applyProfile(TemuSpecMappingProfile profile, TemuSpecMappingDTO.ProfileUpsertRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("profile name is required");
        }
        profile.setName(request.getName().trim());
        profile.setEnabled(request.getEnabled() == null || request.getEnabled());
        profile.setSourceCategoryPath(trimToNull(request.getSourceCategoryPath()));
        profile.setTargetCategoryId(trimToNull(request.getTargetCategoryId()));
        profile.setTargetCategoryName(trimToNull(request.getTargetCategoryName()));
        profile.setTargetParentSpecName(firstNonBlank(request.getTargetParentSpecName(), "型号"));
        profile.setSourceSignature(trimToNull(request.getSourceSignature()));
        profile.setFieldMappingsJson(writeJson(sortFieldMappings(request.getFieldMappings())));
        profile.setValueRulesJson(writeJson(sortValueRules(request.getValueRules())));
        profile.setMainFieldCandidatesJson(writeJson(cleanStringList(request.getMainFieldCandidates())));
        profile.setAutoIgnoreConstantFields(request.getAutoIgnoreConstantFields() == null || request.getAutoIgnoreConstantFields());
        profile.setNotes(trimToNull(request.getNotes()));
    }

    private TemuSpecMappingDTO.ProfileResponse toProfileResponse(TemuSpecMappingProfile profile) {
        TemuSpecMappingDTO.ProfileResponse response = new TemuSpecMappingDTO.ProfileResponse();
        response.setId(profile.getId());
        response.setName(profile.getName());
        response.setEnabled(profile.getEnabled());
        response.setSourceCategoryPath(profile.getSourceCategoryPath());
        response.setTargetCategoryId(profile.getTargetCategoryId());
        response.setTargetCategoryName(profile.getTargetCategoryName());
        response.setTargetParentSpecName(profile.getTargetParentSpecName());
        response.setSourceSignature(profile.getSourceSignature());
        response.setFieldMappings(readJson(profile.getFieldMappingsJson(), FIELD_MAPPING_LIST));
        response.setValueRules(readJson(profile.getValueRulesJson(), VALUE_RULE_LIST));
        response.setMainFieldCandidates(readJson(profile.getMainFieldCandidatesJson(), STRING_LIST));
        response.setAutoIgnoreConstantFields(profile.getAutoIgnoreConstantFields());
        response.setNotes(profile.getNotes());
        response.setCreatedAt(profile.getCreatedAt());
        response.setUpdatedAt(profile.getUpdatedAt());
        return response;
    }

    private TemuSpecMappingDTO.DraftResponse toDraftResponse(TemuSpecMappingDraft draft) {
        TemuSpecMappingDTO.DraftResponse response = new TemuSpecMappingDTO.DraftResponse();
        response.setId(draft.getId());
        response.setSpuId(draft.getSpuId());
        response.setProfileId(draft.getProfileId());
        response.setDraftName(draft.getDraftName());
        response.setTargetParentSpecName(draft.getTargetParentSpecName());
        response.setSelectedMainField(draft.getSelectedMainField());
        response.setSelectedSkuFields(readJson(draft.getSelectedSkuFieldsJson(), STRING_LIST));
        response.setFieldMappings(readJson(draft.getFieldMappingsJson(), FIELD_MAPPING_LIST));
        response.setValueRules(readJson(draft.getValueRulesJson(), VALUE_RULE_LIST));
        response.setAutoIgnoreConstantFields(draft.getAutoIgnoreConstantFields());
        response.setActive(draft.getActive());
        response.setPreview(readJson(draft.getPreviewJson(), TemuSpecMappingDTO.PreviewResponse.class));
        response.setCreatedAt(draft.getCreatedAt());
        response.setUpdatedAt(draft.getUpdatedAt());
        return response;
    }

    private List<TemuSpecMappingDTO.SourceFieldSummary> buildSourceFieldSummaries(ProductCollectionDTO.ProductCollectionDetailResponse detail) {
        List<TemuSpecMappingDTO.SourceFieldSummary> summaries = new ArrayList<>();
        if (detail.getSkuPropsExt() == null) {
            return summaries;
        }
        for (ProductCollectionDTO.ProductCollectionSkuPropResponse prop : detail.getSkuPropsExt()) {
            if (prop == null) {
                continue;
            }
            List<String> values = new ArrayList<>();
            if (prop.getValues() != null) {
                for (ProductCollectionDTO.ProductCollectionSkuPropValueResponse value : prop.getValues()) {
                    if (value != null && StringUtils.hasText(value.getValue())) {
                        values.add(value.getValue().trim());
                    }
                }
            }
            TemuSpecMappingDTO.SourceFieldSummary summary = new TemuSpecMappingDTO.SourceFieldSummary();
            summary.setSourceFieldName(prop.getName());
            summary.setValueCount(values.size());
            summary.setDistinctCount(new LinkedHashSet<>(values).size());
            summary.setVariable(new LinkedHashSet<>(values).size() > 1);
            summary.setSampleValues(values.stream().limit(8).collect(Collectors.toList()));
            summaries.add(summary);
        }
        return summaries;
    }

    private List<TemuSpecMappingDTO.SourceRow> buildSourceRows(ProductCollectionDTO.ProductCollectionDetailResponse detail) {
        List<TemuSpecMappingDTO.SourceRow> rows = new ArrayList<>();
        if (detail.getTemuSkus() == null) {
            return rows;
        }
        for (TemuSkuDTO.TemuSkuRow sku : detail.getTemuSkus()) {
            if (sku == null) {
                continue;
            }
            TemuSpecMappingDTO.SourceRow row = new TemuSpecMappingDTO.SourceRow();
            row.setId(sku.getId());
            row.setTemuSkuId(sku.getTemuSkuId());
            row.setOriginSkuId(sku.getOriginSkuId());
            row.setSpecKey(sku.getSpecKey());
            row.setSpecJsonMap(parseSpecJsonMap(sku.getSpecJson()));
            row.setImage(sku.getImage());
            row.setOriginPrice(sku.getOriginPrice());
            row.setSupplyPrice(sku.getSupplyPrice());
            rows.add(row);
        }
        return rows;
    }

    private String buildSourceSignature(List<TemuSpecMappingDTO.SourceFieldSummary> sourceFields) {
        if (sourceFields == null || sourceFields.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (TemuSpecMappingDTO.SourceFieldSummary field : sourceFields) {
            if (field == null || !StringUtils.hasText(field.getSourceFieldName())) {
                continue;
            }
            parts.add(field.getSourceFieldName() + "#" + firstNonNull(field.getDistinctCount(), 0));
        }
        return String.join("|", parts);
    }

    private TemuSpecMappingDTO.PreviewResponse buildPreview(ProductCollectionDTO.ProductCollectionDetailResponse detail,
                                                            TemuSpecMappingDTO.PreviewRequest request) {
        List<TemuSpecMappingDTO.SourceRow> sourceRows = buildSourceRows(detail);
        TemuSpecMappingDTO.ProfileResponse profile = request.getProfileId() == null
            ? null
            : profileRepository.findById(Objects.requireNonNull(request.getProfileId(), "profileId")).map(this::toProfileResponse).orElse(null);

        List<TemuSpecMappingDTO.FieldMapping> fieldMappings = sortFieldMappings(firstNonEmpty(
                request.getFieldMappings(),
                profile == null ? null : profile.getFieldMappings(),
                buildDefaultFieldMappings(detail)
        ));
        List<TemuSpecMappingDTO.ValueRule> valueRules = sortValueRules(firstNonEmpty(
                request.getValueRules(),
                profile == null ? null : profile.getValueRules(),
                List.of()
        ));
        List<String> mainFieldCandidates = cleanStringList(firstNonEmpty(
                request.getMainFieldCandidates(),
                profile == null ? null : profile.getMainFieldCandidates(),
                List.of()
        ));
        boolean autoIgnoreConstantFields = firstNonNull(
                request.getAutoIgnoreConstantFields(),
                profile == null ? null : profile.getAutoIgnoreConstantFields(),
                Boolean.TRUE
        );

        List<TemuSpecMappingDTO.NormalizedRow> normalizedRows = new ArrayList<>();
        LinkedHashMap<String, LinkedHashSet<String>> fieldValues = new LinkedHashMap<>();

        for (TemuSpecMappingDTO.SourceRow sourceRow : sourceRows) {
            LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
            Map<String, Object> specJsonMap = sourceRow.getSpecJsonMap() == null ? Map.of() : sourceRow.getSpecJsonMap();

            for (TemuSpecMappingDTO.FieldMapping fieldMapping : fieldMappings) {
                if (fieldMapping == null || Boolean.FALSE.equals(fieldMapping.getEnabled())) {
                    continue;
                }
                if ("ignore".equalsIgnoreCase(trimToNull(fieldMapping.getRole()))) {
                    continue;
                }
                String rawValue = extractSourceValue(fieldMapping.getSourceFieldName(), specJsonMap, sourceRow);
                if (!StringUtils.hasText(rawValue) && Boolean.TRUE.equals(fieldMapping.getIgnoreBlank())) {
                    continue;
                }
                if ("*".equals(rawValue) && Boolean.TRUE.equals(fieldMapping.getIgnoreAsterisk())) {
                    continue;
                }
                String targetFieldName = firstNonBlank(fieldMapping.getTargetFieldName(), fieldMapping.getSourceFieldName());
                if (StringUtils.hasText(targetFieldName) && StringUtils.hasText(rawValue)) {
                    normalized.put(targetFieldName.trim(), rawValue.trim());
                }
            }

            for (TemuSpecMappingDTO.ValueRule valueRule : valueRules) {
                if (valueRule == null || Boolean.FALSE.equals(valueRule.getEnabled())) {
                    continue;
                }
                String rawValue = extractSourceValue(valueRule.getSourceFieldName(), specJsonMap, sourceRow);
                if (!matches(valueRule.getMatchType(), valueRule.getMatchExpr(), rawValue)) {
                    continue;
                }
                String targetFieldName = trimToNull(valueRule.getTargetFieldName());
                String targetValue = trimToNull(valueRule.getTargetValue());
                if (targetFieldName != null && targetValue != null) {
                    normalized.put(targetFieldName, targetValue);
                }
            }

            TemuSpecMappingDTO.NormalizedRow row = new TemuSpecMappingDTO.NormalizedRow();
            row.setId(sourceRow.getId());
            row.setTemuSkuId(sourceRow.getTemuSkuId());
            row.setOriginSkuId(sourceRow.getOriginSkuId());
            row.setSpecKey(sourceRow.getSpecKey());
            row.setImage(sourceRow.getImage());
            row.setSupplyPrice(sourceRow.getSupplyPrice());
            row.setNormalizedFields(normalized);
            normalizedRows.add(row);

            for (Map.Entry<String, String> entry : normalized.entrySet()) {
                if (StringUtils.hasText(entry.getValue())) {
                    fieldValues.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>()).add(entry.getValue());
                }
            }
        }

        if (autoIgnoreConstantFields) {
            Set<String> constantFields = fieldValues.entrySet().stream()
                    .filter(entry -> entry.getValue().size() <= 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!constantFields.isEmpty()) {
                for (TemuSpecMappingDTO.NormalizedRow row : normalizedRows) {
                    if (row.getNormalizedFields() == null) {
                        continue;
                    }
                    constantFields.forEach(row.getNormalizedFields()::remove);
                }
                fieldValues.keySet().removeAll(constantFields);
            }
        }

        List<TemuSpecMappingDTO.CandidateFieldSummary> candidateFields = buildCandidateFields(fieldValues, mainFieldCandidates);
        String selectedMainField = resolveSelectedMainField(request.getSelectedMainField(), mainFieldCandidates, candidateFields);
        List<String> selectedSkuFields = resolveSelectedSkuFields(request.getSelectedSkuFields(), selectedMainField, candidateFields);

        LinkedHashMap<String, List<TemuSpecMappingDTO.NormalizedRow>> groupedRows = new LinkedHashMap<>();
        for (TemuSpecMappingDTO.NormalizedRow row : normalizedRows) {
            String mainValue = selectedMainField == null ? null : row.getNormalizedFields().get(selectedMainField);
            String groupKey = Optional.ofNullable(mainValue)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .orElse("__UNMAPPED__");
            groupedRows.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(row);
        }

        List<TemuSpecMappingDTO.PreviewGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<TemuSpecMappingDTO.NormalizedRow>> entry : groupedRows.entrySet()) {
            TemuSpecMappingDTO.PreviewGroup group = new TemuSpecMappingDTO.PreviewGroup();
            group.setGroupKey(entry.getKey());
            group.setMainFieldValue("__UNMAPPED__".equals(entry.getKey()) ? "未映射" : entry.getKey());
            group.setSkuCount(entry.getValue().size());
            group.setRows(entry.getValue());
            groups.add(group);
        }

        List<TemuSpecMappingDTO.ValidationMessage> validations = buildValidations(selectedMainField, selectedSkuFields, groups, normalizedRows.size(), candidateFields);

        TemuSpecMappingDTO.PreviewResponse response = new TemuSpecMappingDTO.PreviewResponse();
        response.setTargetParentSpecName(firstNonBlank(request.getTargetParentSpecName(), profile == null ? null : profile.getTargetParentSpecName(), "型号"));
        response.setSelectedMainField(selectedMainField);
        response.setSelectedSkuFields(selectedSkuFields);
        response.setSkcCount(groups.size());
        response.setSkuCount(normalizedRows.size());
        response.setPublishable(validations.stream().noneMatch(item -> "error".equalsIgnoreCase(item.getSeverity())));
        response.setCandidateFields(candidateFields);
        response.setNormalizedRows(normalizedRows);
        response.setGroups(groups);
        response.setValidations(validations);
        return response;
    }

    private List<TemuSpecMappingDTO.FieldMapping> buildDefaultFieldMappings(ProductCollectionDTO.ProductCollectionDetailResponse detail) {
        List<TemuSpecMappingDTO.FieldMapping> defaults = new ArrayList<>();
        if (detail.getSkuPropsExt() == null) {
            return defaults;
        }
        int index = 0;
        for (ProductCollectionDTO.ProductCollectionSkuPropResponse prop : detail.getSkuPropsExt()) {
            if (prop == null || !StringUtils.hasText(prop.getName())) {
                continue;
            }
            TemuSpecMappingDTO.FieldMapping mapping = new TemuSpecMappingDTO.FieldMapping();
            mapping.setSourceFieldName(prop.getName());
            mapping.setTargetFieldName(prop.getName());
            mapping.setRole(index == 0 ? "main_candidate" : "sku_dimension");
            mapping.setEnabled(true);
            mapping.setIgnoreBlank(true);
            mapping.setIgnoreAsterisk(true);
            mapping.setSortOrder(index++);
            defaults.add(mapping);
        }
        return defaults;
    }

    private List<TemuSpecMappingDTO.CandidateFieldSummary> buildCandidateFields(Map<String, LinkedHashSet<String>> fieldValues,
                                                                                List<String> mainFieldCandidates) {
        List<TemuSpecMappingDTO.CandidateFieldSummary> out = new ArrayList<>();
        if (fieldValues == null || fieldValues.isEmpty()) {
            return out;
        }
        Set<String> candidateSet = new LinkedHashSet<>(cleanStringList(mainFieldCandidates));
        for (Map.Entry<String, LinkedHashSet<String>> entry : fieldValues.entrySet()) {
            if (entry.getValue() == null || entry.getValue().size() <= 1) {
                continue;
            }
            TemuSpecMappingDTO.CandidateFieldSummary item = new TemuSpecMappingDTO.CandidateFieldSummary();
            item.setFieldName(entry.getKey());
            item.setDistinctCount(entry.getValue().size());
            item.setSampleValues(entry.getValue().stream().limit(8).collect(Collectors.toList()));
            item.setMainCandidate(candidateSet.isEmpty() || candidateSet.contains(entry.getKey()));
            out.add(item);
        }
        out.sort(Comparator.comparing(TemuSpecMappingDTO.CandidateFieldSummary::getDistinctCount)
                .thenComparing(TemuSpecMappingDTO.CandidateFieldSummary::getFieldName, Comparator.nullsLast(String::compareTo)));
        return out;
    }

    private String resolveSelectedMainField(String requestedMainField,
                                            List<String> mainFieldCandidates,
                                            List<TemuSpecMappingDTO.CandidateFieldSummary> candidateFields) {
        Set<String> available = candidateFields.stream().map(TemuSpecMappingDTO.CandidateFieldSummary::getFieldName).collect(Collectors.toCollection(LinkedHashSet::new));
        String requested = trimToNull(requestedMainField);
        if (requested != null && available.contains(requested)) {
            return requested;
        }
        for (String candidate : cleanStringList(mainFieldCandidates)) {
            if (available.contains(candidate)) {
                return candidate;
            }
        }
        for (TemuSpecMappingDTO.CandidateFieldSummary candidateField : candidateFields) {
            if (Boolean.TRUE.equals(candidateField.getMainCandidate()) && firstNonNull(candidateField.getDistinctCount(), 0) <= 25) {
                return candidateField.getFieldName();
            }
        }
        return candidateFields.isEmpty() ? null : candidateFields.get(0).getFieldName();
    }

    private List<String> resolveSelectedSkuFields(List<String> requestedSkuFields,
                                                  String selectedMainField,
                                                  List<TemuSpecMappingDTO.CandidateFieldSummary> candidateFields) {
        LinkedHashSet<String> available = candidateFields.stream()
                .map(TemuSpecMappingDTO.CandidateFieldSummary::getFieldName)
                .filter(field -> !Objects.equals(field, selectedMainField))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> requested = cleanStringList(requestedSkuFields);
        List<String> selected = requested.stream().filter(available::contains).collect(Collectors.toList());
        if (!selected.isEmpty()) {
            return selected;
        }
        return new ArrayList<>(available);
    }

    private List<TemuSpecMappingDTO.ValidationMessage> buildValidations(String selectedMainField,
                                                                        List<String> selectedSkuFields,
                                                                        List<TemuSpecMappingDTO.PreviewGroup> groups,
                                                                        int skuCount,
                                                                        List<TemuSpecMappingDTO.CandidateFieldSummary> candidateFields) {
        List<TemuSpecMappingDTO.ValidationMessage> validations = new ArrayList<>();
        if (!StringUtils.hasText(selectedMainField)) {
            validations.add(new TemuSpecMappingDTO.ValidationMessage("error", "main-field-missing", "未选择可用的主销售属性字段，无法形成 SKC 分组"));
            return validations;
        }
        int skcCount = groups == null ? 0 : groups.size();
        if (skcCount > 25) {
            validations.add(new TemuSpecMappingDTO.ValidationMessage("error", "skc-limit-exceeded", "当前主销售属性将产生 " + skcCount + " 个 SKC，超过 TEMU 上限 25"));
        }
        long unmappedCount = groups == null ? 0 : groups.stream().filter(group -> "未映射".equals(group.getMainFieldValue())).count();
        if (unmappedCount > 0) {
            validations.add(new TemuSpecMappingDTO.ValidationMessage("error", "main-value-unmapped", "存在 SKU 没有命中主销售属性值，请补充映射规则"));
        }
        if (selectedSkuFields == null || selectedSkuFields.isEmpty()) {
            validations.add(new TemuSpecMappingDTO.ValidationMessage("warning", "sku-dimension-empty", "当前没有组内 SKU 维度，平台将更接近一组一 SKU"));
        }
        boolean oneSkuPerGroup = groups != null && !groups.isEmpty() && groups.stream().allMatch(group -> firstNonNull(group.getSkuCount(), 0) <= 1);
        if (oneSkuPerGroup && skuCount > 1) {
            validations.add(new TemuSpecMappingDTO.ValidationMessage("warning", "one-sku-per-skc", "当前每个主组仅包含 1 个 SKU，说明主销售属性过细，更像套餐名而不是分组维度"));
        }
        if (candidateFields == null || candidateFields.isEmpty()) {
            validations.add(new TemuSpecMappingDTO.ValidationMessage("error", "no-variable-field", "未识别出任何有效变化维度，请检查字段映射或值归并规则"));
        }
        if (validations.isEmpty()) {
            validations.add(new TemuSpecMappingDTO.ValidationMessage("info", "preview-ok", "当前映射结构通过基础校验，可继续保存为草案"));
        }
        return validations;
    }

    private Map<String, Object> parseSpecJsonMap(String specJson) {
        if (!StringUtils.hasText(specJson)) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = objectMapper.readValue(specJson, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                LinkedHashMap<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    out.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return out;
            }
        } catch (Exception ignored) {
        }
        return new LinkedHashMap<>();
    }

    private String extractSourceValue(String sourceFieldName, Map<String, Object> specJsonMap, TemuSpecMappingDTO.SourceRow sourceRow) {
        String normalizedFieldName = trimToNull(sourceFieldName);
        if (normalizedFieldName == null) {
            return null;
        }
        Object direct = specJsonMap.get(normalizedFieldName);
        if (direct != null) {
            return String.valueOf(direct).trim();
        }
        if ("specKey".equalsIgnoreCase(normalizedFieldName)) {
            return trimToNull(sourceRow.getSpecKey());
        }
        if ("temuSkuId".equalsIgnoreCase(normalizedFieldName)) {
            return trimToNull(sourceRow.getTemuSkuId());
        }
        if ("originSkuId".equalsIgnoreCase(normalizedFieldName)) {
            return trimToNull(sourceRow.getOriginSkuId());
        }
        return null;
    }

    private boolean matches(String matchType, String matchExpr, String rawValue) {
        String normalizedExpr = trimToNull(matchExpr);
        String normalizedRawValue = trimToNull(rawValue);
        if (normalizedExpr == null || normalizedRawValue == null) {
            return false;
        }
        String type = firstNonBlank(matchType, "contains").toLowerCase(Locale.ROOT);
        if ("equals".equals(type)) {
            return normalizedRawValue.equalsIgnoreCase(normalizedExpr);
        }
        if ("regex".equals(type)) {
            try {
                return Pattern.compile(normalizedExpr, Pattern.CASE_INSENSITIVE).matcher(normalizedRawValue).find();
            } catch (Exception ignored) {
                return false;
            }
        }
        return normalizedRawValue.toLowerCase(Locale.ROOT).contains(normalizedExpr.toLowerCase(Locale.ROOT));
    }

    private List<TemuSpecMappingDTO.FieldMapping> sortFieldMappings(List<TemuSpecMappingDTO.FieldMapping> fieldMappings) {
        List<TemuSpecMappingDTO.FieldMapping> rows = new ArrayList<>(fieldMappings == null ? List.of() : fieldMappings);
        rows.removeIf(Objects::isNull);
        rows.sort(Comparator.comparing(TemuSpecMappingDTO.FieldMapping::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(TemuSpecMappingDTO.FieldMapping::getSourceFieldName, Comparator.nullsLast(String::compareTo)));
        return rows;
    }

    private List<TemuSpecMappingDTO.ValueRule> sortValueRules(List<TemuSpecMappingDTO.ValueRule> valueRules) {
        List<TemuSpecMappingDTO.ValueRule> rows = new ArrayList<>(valueRules == null ? List.of() : valueRules);
        rows.removeIf(Objects::isNull);
        rows.sort(Comparator.comparing(TemuSpecMappingDTO.ValueRule::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(TemuSpecMappingDTO.ValueRule::getTargetFieldName, Comparator.nullsLast(String::compareTo)));
        return rows;
    }

    private List<String> cleanStringList(List<String> values) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (values == null) {
            return new ArrayList<>();
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                out.add(trimmed);
            }
        }
        return new ArrayList<>(out);
    }

    private <T> List<T> firstNonEmpty(List<T> first, List<T> second, List<T> third) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        if (second != null && !second.isEmpty()) {
            return second;
        }
        return third == null ? List.of() : third;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("json serialize failed: " + e.getMessage(), e);
        }
    }

    private <T> T readJson(String json, Class<T> clazz) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T readJson(String json, TypeReference<T> typeReference) {
        if (!StringUtils.hasText(json)) {
            try {
                return objectMapper.readValue("[]", typeReference);
            } catch (Exception ignored) {
                return null;
            }
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception e) {
            try {
                return objectMapper.readValue("[]", typeReference);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}