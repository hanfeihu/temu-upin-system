package com.tminos.productscene.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.SupplierProductSubmissionDTO;
import com.tminos.productscene.entity.SupplierProductSubmission;
import com.tminos.productscene.repository.SupplierProductSubmissionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierProductSubmissionService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final SupplierProductSubmissionRepository repository;
    private final ObjectMapper objectMapper;
    private final SupplierProductPackageService packageService;

    @Transactional(readOnly = true)
    public Page<SupplierProductSubmissionDTO.Item> list(String keyword, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Specification<SupplierProductSubmission> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("supplierName")), like),
                        cb.like(cb.lower(root.get("supplierPhone")), like),
                        cb.like(cb.lower(root.get("supplierAddress")), like),
                        cb.like(cb.lower(root.get("productName")), like),
                        cb.like(cb.lower(root.get("remark")), like)
                ));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(
                spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(this::toItem);
    }

    @Transactional(readOnly = true)
    public SupplierProductSubmissionDTO.Item detail(Long id) {
        return toItem(findById(id));
    }

    @Transactional
    public SupplierProductSubmissionDTO.Item create(SupplierProductSubmissionDTO.SaveRequest request) {
        SupplierProductSubmission entity = new SupplierProductSubmission();
        apply(entity, request);
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("PENDING");
        }
        return toItem(repository.save(entity));
    }

    @Transactional
    public SupplierProductSubmissionDTO.Item update(Long id, SupplierProductSubmissionDTO.SaveRequest request) {
        SupplierProductSubmission entity = findById(id);
        apply(entity, request);
        return toItem(repository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        packageService.deleteBySubmissionId(id);
        repository.delete(findById(id));
    }

    @Transactional
    public SupplierProductSubmissionDTO.Item approve(Long id) {
        SupplierProductSubmission entity = findById(id);
        entity.setStatus("APPROVED");
        SupplierProductSubmission saved = repository.save(entity);
        packageService.createForSubmission(saved);
        return toItem(saved);
    }

    @Transactional
    public SupplierProductSubmissionDTO.Item reject(Long id, SupplierProductSubmissionDTO.SaveRequest request) {
        SupplierProductSubmission entity = findById(id);
        entity.setStatus("REJECTED");
        if (request != null && StringUtils.hasText(request.getRemark())) {
            entity.setRemark(trim(request.getRemark()));
        }
        return toItem(repository.save(entity));
    }

    private SupplierProductSubmission findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("供应商提品不存在: " + id));
    }

    private void apply(SupplierProductSubmission entity, SupplierProductSubmissionDTO.SaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("提交内容不能为空");
        }
        String supplierName = trim(request.getSupplierName());
        String productName = trim(request.getProductName());
        if (!StringUtils.hasText(supplierName)) {
            throw new IllegalArgumentException("供货商名称不能为空");
        }
        if (!StringUtils.hasText(productName)) {
            throw new IllegalArgumentException("产品名称不能为空");
        }

        entity.setSupplierName(supplierName);
        entity.setSupplierPhone(trim(request.getSupplierPhone()));
        entity.setSupplierAddress(trim(request.getSupplierAddress()));
        entity.setProductName(productName);
        entity.setSupplyPrice(request.getSupplyPrice());
        entity.setWeightG(request.getWeightG());
        entity.setLengthCm(request.getLengthCm());
        entity.setWidthCm(request.getWidthCm());
        entity.setHeightCm(request.getHeightCm());
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setRemark(trim(request.getRemark()));
        entity.setImageUrlsJson(writeImageUrls(request.getImageUrls()));
    }

    private SupplierProductSubmissionDTO.Item toItem(SupplierProductSubmission entity) {
        return SupplierProductSubmissionDTO.Item.builder()
                .id(entity.getId())
                .supplierName(entity.getSupplierName())
                .supplierPhone(entity.getSupplierPhone())
                .supplierAddress(entity.getSupplierAddress())
                .productName(entity.getProductName())
                .supplyPrice(entity.getSupplyPrice())
                .weightG(entity.getWeightG())
                .lengthCm(entity.getLengthCm())
                .widthCm(entity.getWidthCm())
                .heightCm(entity.getHeightCm())
                .imageUrls(readImageUrls(entity.getImageUrlsJson()))
                .status(entity.getStatus())
                .remark(entity.getRemark())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private List<String> readImageUrls(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeImageUrls(List<String> imageUrls) {
        List<String> cleaned = imageUrls == null ? List.of() : imageUrls.stream()
                .map(this::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (Exception e) {
            throw new IllegalArgumentException("图片数据保存失败: " + e.getMessage(), e);
        }
    }

    private String resolveStatus(String status) {
        String value = trim(status);
        return StringUtils.hasText(value) ? value : "PENDING";
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
