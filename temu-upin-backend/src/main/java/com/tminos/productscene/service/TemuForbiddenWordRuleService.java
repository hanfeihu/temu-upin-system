package com.tminos.productscene.service;

import com.tminos.productscene.dto.TemuForbiddenWordRuleDTO;
import com.tminos.productscene.entity.TemuForbiddenWordRule;
import com.tminos.productscene.repository.TemuForbiddenWordRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TemuForbiddenWordRuleService {

    private final TemuForbiddenWordRuleRepository repository;

    public TemuForbiddenWordRuleService(TemuForbiddenWordRuleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<TemuForbiddenWordRuleDTO.Item> page(String keyword, Boolean enabled, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        String normalizedKeyword = trimToNull(keyword);
        Specification<TemuForbiddenWordRule> specification = (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (StringUtils.hasText(normalizedKeyword)) {
                String likeValue = "%" + normalizedKeyword.toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("word")), likeValue),
                        cb.like(cb.lower(root.get("replacement")), likeValue),
                        cb.like(cb.lower(root.get("fieldScope")), likeValue),
                        cb.like(cb.lower(root.get("remark")), likeValue)
                ));
            }
            if (enabled != null) {
                predicate = cb.and(predicate, cb.equal(root.get("enabled"), enabled));
            }
            return predicate;
        };
        return repository.findAll(specification, pageable).map(this::toItem);
    }

    @Transactional
    public TemuForbiddenWordRuleDTO.Item create(TemuForbiddenWordRuleDTO.SaveRequest request) {
        TemuForbiddenWordRule entity = new TemuForbiddenWordRule();
        apply(entity, request);
        return toItem(repository.save(entity));
    }

    @Transactional
    public TemuForbiddenWordRuleDTO.Item update(Long id, TemuForbiddenWordRuleDTO.SaveRequest request) {
        TemuForbiddenWordRule entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("TEMU forbidden word rule not found: " + id));
        apply(entity, request);
        return toItem(repository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    private void apply(TemuForbiddenWordRule entity, TemuForbiddenWordRuleDTO.SaveRequest request) {
        if (request == null || !StringUtils.hasText(request.getWord())) {
            throw new IllegalArgumentException("违禁词不能为空");
        }
        entity.setWord(request.getWord().trim());
        entity.setReplacement(trimToNull(request.getReplacement()));
        entity.setFieldScope(trimToNull(request.getFieldScope()));
        entity.setEnabled(request.getEnabled() == null || Boolean.TRUE.equals(request.getEnabled()));
        entity.setRemark(trimToNull(request.getRemark()));
    }

    private TemuForbiddenWordRuleDTO.Item toItem(TemuForbiddenWordRule entity) {
        return TemuForbiddenWordRuleDTO.Item.builder()
                .id(entity.getId())
                .word(entity.getWord())
                .replacement(entity.getReplacement())
                .fieldScope(entity.getFieldScope())
                .enabled(entity.getEnabled())
                .remark(entity.getRemark())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
