package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.ImageTranslateRecord;
import com.tminos.productscene.repository.ImageTranslateRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ImageTranslateRecordService {

    private final ImageTranslateRecordRepository repository;
    private final ObjectMapper objectMapper;

    public ImageTranslateRecordService(ImageTranslateRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<ImageTranslateRecord> list(Long spuId, String provider, String sourceUrl, String status, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "id")
        );
        Specification<ImageTranslateRecord> spec = (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (spuId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("spuId"), spuId));
            }
            if (StringUtils.hasText(provider)) {
                predicate = cb.and(predicate, cb.equal(cb.lower(root.get("provider")), provider.trim().toLowerCase()));
            }
            if (StringUtils.hasText(status)) {
                predicate = cb.and(predicate, cb.equal(cb.lower(root.get("status")), status.trim().toLowerCase()));
            }
            if (StringUtils.hasText(sourceUrl)) {
                predicate = cb.and(predicate, cb.like(root.get("sourceUrl"), "%" + sourceUrl.trim() + "%"));
            }
            return predicate;
        };
        return repository.findAll(spec, pageable);
    }

    @Transactional
    @SuppressWarnings("null")
    public ImageTranslateRecord create(Long spuId,
                                       String provider,
                                       String status,
                                       List<String> fieldRefs,
                                       String sourceUrl,
                                       String translatedUrl,
                                       String errorMsg,
                                       LocalDateTime startedAt,
                                       LocalDateTime endedAt) {
        ImageTranslateRecord record = ImageTranslateRecord.builder()
                .spuId(spuId)
                .provider(StringUtils.hasText(provider) ? provider.trim().toLowerCase() : "aliyun")
                .status(StringUtils.hasText(status) ? status.trim().toLowerCase() : "success")
                .fieldRefs(writeFieldRefs(fieldRefs))
                .sourceUrl(sourceUrl)
                .translatedUrl(translatedUrl)
                .errorMsg(errorMsg)
                .startedAt(startedAt == null ? LocalDateTime.now() : startedAt)
                .endedAt(endedAt == null ? LocalDateTime.now() : endedAt)
                .build();
        return repository.save(record);
    }

    private String writeFieldRefs(List<String> fieldRefs) {
        if (fieldRefs == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(fieldRefs);
        } catch (Exception e) {
            return String.join(", ", fieldRefs);
        }
    }
}