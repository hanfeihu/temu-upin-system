package com.tminos.productscene.service;

import com.tminos.productscene.dto.Alibaba1688SelectionPoolFilterCategoryDTO;
import com.tminos.productscene.entity.Alibaba1688SelectionPoolFilterCategory;
import com.tminos.productscene.repository.Alibaba1688SelectionPoolFilterCategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class Alibaba1688SelectionPoolFilterCategoryService {

    private final Alibaba1688SelectionPoolFilterCategoryRepository repository;

    public Alibaba1688SelectionPoolFilterCategoryService(Alibaba1688SelectionPoolFilterCategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Alibaba1688SelectionPoolFilterCategoryDTO.Item> list(String keyword, Boolean enabled) {
        Specification<Alibaba1688SelectionPoolFilterCategory> specification = (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (enabled != null) {
                predicate = cb.and(predicate, cb.equal(root.get("enabled"), enabled));
            }
            if (StringUtils.hasText(keyword)) {
                String likeValue = "%" + keyword.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("categoryName")), likeValue),
                        cb.like(cb.lower(root.get("source")), likeValue),
                        cb.like(cb.lower(root.get("remark")), likeValue)
                ));
            }
            return predicate;
        };
        return repository.findAll(
                        specification,
                        Sort.by(
                                Sort.Order.desc("enabled"),
                                Sort.Order.desc("updatedAt"),
                                Sort.Order.asc("categoryName")
                        )
                ).stream()
                .map(this::toItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> listEnabledCategoryNames() {
        return repository.findEnabledCategoryNames().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    @Transactional
    public Alibaba1688SelectionPoolFilterCategoryDTO.Item create(Alibaba1688SelectionPoolFilterCategoryDTO.SaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        String categoryName = normalizeCategoryName(request.getCategoryName());
        if (repository.existsByCategoryName(categoryName)) {
            throw new IllegalArgumentException("过滤类目已存在: " + categoryName);
        }
        Alibaba1688SelectionPoolFilterCategory entity = new Alibaba1688SelectionPoolFilterCategory();
        apply(entity, request);
        entity.setCategoryName(categoryName);
        return toItem(repository.save(entity));
    }

    @Transactional
    public Alibaba1688SelectionPoolFilterCategoryDTO.Item update(Long id, Alibaba1688SelectionPoolFilterCategoryDTO.SaveRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("配置 ID 不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        Alibaba1688SelectionPoolFilterCategory entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("过滤类目配置不存在: " + id));
        String categoryName = normalizeCategoryName(request.getCategoryName());
        repository.findByCategoryName(categoryName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("过滤类目已存在: " + categoryName);
                });
        apply(entity, request);
        entity.setCategoryName(categoryName);
        return toItem(repository.save(entity));
    }

    @Transactional
    public Alibaba1688SelectionPoolFilterCategoryDTO.Item toggleEnabled(Long id, Boolean enabled) {
        if (id == null) {
            throw new IllegalArgumentException("配置 ID 不能为空");
        }
        if (enabled == null) {
            throw new IllegalArgumentException("enabled 不能为空");
        }
        Alibaba1688SelectionPoolFilterCategory entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("过滤类目配置不存在: " + id));
        entity.setEnabled(enabled);
        return toItem(repository.save(entity));
    }

    @Transactional
    public Alibaba1688SelectionPoolFilterCategoryDTO.Item updateRemark(Long id, String remark) {
        if (id == null) {
            throw new IllegalArgumentException("配置 ID 不能为空");
        }
        Alibaba1688SelectionPoolFilterCategory entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("过滤类目配置不存在: " + id));
        entity.setRemark(trimToNull(remark));
        return toItem(repository.save(entity));
    }

    @Transactional
    public Alibaba1688SelectionPoolFilterCategoryDTO.QuickAddResponse quickAdd(
            Alibaba1688SelectionPoolFilterCategoryDTO.QuickAddRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        String categoryName = normalizeCategoryName(request.getCategoryName());
        Alibaba1688SelectionPoolFilterCategory existing = repository.findByCategoryName(categoryName).orElse(null);
        boolean created = false;
        boolean reEnabled = false;
        if (existing == null) {
            Alibaba1688SelectionPoolFilterCategory entity = Alibaba1688SelectionPoolFilterCategory.builder()
                    .categoryName(categoryName)
                    .enabled(request.getEnabled() != null ? request.getEnabled() : Boolean.TRUE)
                    .source(resolveSource(request.getSource(), "SELECTION_POOL"))
                    .remark(trimToNull(request.getRemark()))
                    .build();
            existing = repository.save(entity);
            created = true;
        } else {
            if (!Boolean.TRUE.equals(existing.getEnabled())) {
                existing.setEnabled(Boolean.TRUE);
                reEnabled = true;
            }
            if (StringUtils.hasText(request.getSource())) {
                existing.setSource(resolveSource(request.getSource(), "SELECTION_POOL"));
            } else if (!StringUtils.hasText(existing.getSource())) {
                existing.setSource("SELECTION_POOL");
            }
            if (request.getRemark() != null) {
                existing.setRemark(trimToNull(request.getRemark()));
            }
            existing = repository.save(existing);
        }
        return Alibaba1688SelectionPoolFilterCategoryDTO.QuickAddResponse.builder()
                .created(created)
                .reEnabled(reEnabled)
                .item(toItem(existing))
                .build();
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("配置 ID 不能为空");
        }
        Alibaba1688SelectionPoolFilterCategory entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("过滤类目配置不存在: " + id));
        repository.delete(entity);
    }

    private void apply(Alibaba1688SelectionPoolFilterCategory entity, Alibaba1688SelectionPoolFilterCategoryDTO.SaveRequest request) {
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : Boolean.TRUE);
        entity.setSource(resolveSource(request.getSource(), "MANUAL"));
        entity.setRemark(trimToNull(request.getRemark()));
    }

    private Alibaba1688SelectionPoolFilterCategoryDTO.Item toItem(Alibaba1688SelectionPoolFilterCategory entity) {
        return Alibaba1688SelectionPoolFilterCategoryDTO.Item.builder()
                .id(entity.getId())
                .categoryName(entity.getCategoryName())
                .enabled(Boolean.TRUE.equals(entity.getEnabled()))
                .source(entity.getSource())
                .remark(entity.getRemark())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String normalizeCategoryName(String categoryName) {
        String normalized = trimToNull(categoryName);
        if (normalized == null) {
            throw new IllegalArgumentException("categoryName 不能为空");
        }
        return normalized;
    }

    private String resolveSource(String source, String defaultValue) {
        String normalized = trimToNull(source);
        return normalized == null ? defaultValue : normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
