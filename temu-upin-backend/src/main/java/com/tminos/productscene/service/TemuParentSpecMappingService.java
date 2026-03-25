package com.tminos.productscene.service;

import com.tminos.productscene.dto.TemuParentSpecMappingDTO;
import com.tminos.productscene.entity.TemuParentSpecMapping;
import com.tminos.productscene.repository.TemuParentSpecMappingRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class TemuParentSpecMappingService {

    private final TemuParentSpecMappingRepository repository;

    public TemuParentSpecMappingService(TemuParentSpecMappingRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    @Transactional
    public void ensureDefaultMappings() {
        if (repository.count() > 0) {
            return;
        }
        TemuParentSpecMapping defaultMapping = TemuParentSpecMapping.builder()
                .sourceFieldName("尺寸")
                .normalizedSourceField(normalizeSourceField("尺寸"))
                .targetParentSpecName("型号")
                .enabled(true)
                .notes("系统初始化默认映射")
                .build();
        repository.save(Objects.requireNonNull(defaultMapping, "defaultMapping"));
    }

    @Transactional(readOnly = true)
    public List<TemuParentSpecMapping> list(Boolean enabled) {
        if (enabled == null) {
            return repository.findAllByOrderByIdDesc();
        }
        return repository.findByEnabledOrderByIdDesc(enabled);
    }

    @Transactional(readOnly = true)
    public String findTargetParentSpecName(String sourceFieldName) {
        String normalized = normalizeSourceField(sourceFieldName);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return repository.findByNormalizedSourceField(normalized)
                .filter(TemuParentSpecMapping::getEnabled)
                .map(TemuParentSpecMapping::getTargetParentSpecName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .orElse(null);
    }

    @Transactional
    public TemuParentSpecMapping create(TemuParentSpecMappingDTO.UpsertRequest request) {
        TemuParentSpecMapping entity = new TemuParentSpecMapping();
        apply(entity, request);
        return repository.save(Objects.requireNonNull(entity, "entity"));
    }

    @Transactional
    public TemuParentSpecMapping update(Long id, TemuParentSpecMappingDTO.UpsertRequest request) {
        TemuParentSpecMapping entity = repository.findById(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new EntityNotFoundException("mapping not found"));
        apply(entity, request);
        return repository.save(Objects.requireNonNull(entity, "entity"));
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) {
            return;
        }
        repository.deleteById(id);
    }

    public String normalizeSourceField(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replaceAll("\\s+", "").trim().toLowerCase();
    }

    private void apply(TemuParentSpecMapping entity, TemuParentSpecMappingDTO.UpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        String sourceFieldName = request.getSourceFieldName() == null ? null : request.getSourceFieldName().trim();
        String targetParentSpecName = request.getTargetParentSpecName() == null ? null : request.getTargetParentSpecName().trim();
        if (!StringUtils.hasText(sourceFieldName)) {
            throw new IllegalArgumentException("sourceFieldName is required");
        }
        if (!StringUtils.hasText(targetParentSpecName)) {
            throw new IllegalArgumentException("targetParentSpecName is required");
        }
        String normalizedSourceField = normalizeSourceField(sourceFieldName);
        boolean duplicated = entity.getId() == null
                ? repository.existsByNormalizedSourceField(normalizedSourceField)
                : repository.existsByNormalizedSourceFieldAndIdNot(normalizedSourceField, entity.getId());
        if (duplicated) {
            throw new IllegalArgumentException("sourceFieldName duplicated");
        }
        entity.setSourceFieldName(sourceFieldName);
        entity.setNormalizedSourceField(normalizedSourceField);
        entity.setTargetParentSpecName(targetParentSpecName);
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        } else if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        String notes = request.getNotes() == null ? null : request.getNotes().trim();
        entity.setNotes(StringUtils.hasText(notes) ? notes : null);
    }
}