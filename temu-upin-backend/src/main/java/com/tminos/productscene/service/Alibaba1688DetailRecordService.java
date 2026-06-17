package com.tminos.productscene.service;

import com.tminos.productscene.dto.Alibaba1688DetailRecordDTO;
import com.tminos.productscene.entity.Alibaba1688AuthSession;
import com.tminos.productscene.entity.Alibaba1688DetailRecord;
import com.tminos.productscene.entity.Alibaba1688SelectionPool;
import com.tminos.productscene.repository.Alibaba1688AuthSessionRepository;
import com.tminos.productscene.repository.Alibaba1688DetailRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class Alibaba1688DetailRecordService {

    private final Alibaba1688DetailRecordRepository recordRepository;
    private final Alibaba1688AuthSessionRepository authSessionRepository;

    public Alibaba1688DetailRecordService(
            Alibaba1688DetailRecordRepository recordRepository,
            Alibaba1688AuthSessionRepository authSessionRepository
    ) {
        this.recordRepository = recordRepository;
        this.authSessionRepository = authSessionRepository;
    }

    @Transactional(readOnly = true)
    public Page<Alibaba1688DetailRecordDTO.ListItem> list(
            String keyword,
            Long credentialId,
            Long recordId,
            String status,
            Boolean excludeImported,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "lastCollectedAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        Specification<Alibaba1688DetailRecord> spec = (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (credentialId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("lastCredentialId"), credentialId));
            }
            if (recordId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("id"), recordId));
            }
            if (StringUtils.hasText(status)) {
                predicate = cb.and(predicate, cb.equal(cb.upper(root.get("status")), status.trim().toUpperCase()));
            }
            if (StringUtils.hasText(keyword)) {
                String likeValue = "%" + keyword.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("offerId")), likeValue),
                        cb.like(cb.lower(root.get("detailUrl")), likeValue),
                        cb.like(cb.lower(root.get("canonicalUrl")), likeValue),
                        cb.like(cb.lower(root.get("productName")), likeValue),
                        cb.like(cb.lower(root.get("companyName")), likeValue)
                ));
            }
            if (Boolean.TRUE.equals(excludeImported)) {
                var importedSubquery = query.subquery(Long.class);
                var poolRoot = importedSubquery.from(Alibaba1688SelectionPool.class);
                importedSubquery.select(poolRoot.get("id"));
                importedSubquery.where(cb.or(
                        cb.equal(poolRoot.get("detailRecordId"), root.get("id")),
                        cb.and(
                                cb.isNotNull(root.get("offerId")),
                                cb.equal(poolRoot.get("offerId"), root.get("offerId"))
                        ),
                        cb.and(
                                cb.isNotNull(root.get("productName")),
                                cb.equal(
                                        poolRoot.get("productTitleKey"),
                                        cb.function(
                                                "lower",
                                                String.class,
                                                cb.function(
                                                        "regexp_replace",
                                                        String.class,
                                                        cb.trim(root.get("productName")),
                                                        cb.literal("[\\s\\u00A0]+"),
                                                        cb.literal(" "),
                                                        cb.literal("g")
                                                )
                                        )
                                )
                        )
                ));
                predicate = cb.and(predicate, cb.not(cb.exists(importedSubquery)));
            }
            return predicate;
        };

        Page<Alibaba1688DetailRecord> data = recordRepository.findAll(spec, pageable);
        Map<Long, String> credentialNameMap = loadCredentialNameMap(
                data.stream().map(Alibaba1688DetailRecord::getLastCredentialId).collect(Collectors.toSet())
        );
        return data.map(item -> toListItem(item, credentialNameMap.get(item.getLastCredentialId())));
    }

    @Transactional(readOnly = true)
    public Alibaba1688DetailRecordDTO.DetailResponse getDetail(Long id) {
        Alibaba1688DetailRecord entity = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("1688 详情数据不存在: " + id));
        Map<Long, String> credentialNameMap = loadCredentialNameMap(
                entity.getLastCredentialId() == null ? Set.of() : Set.of(entity.getLastCredentialId())
        );
        return toDetailResponse(entity, credentialNameMap.get(entity.getLastCredentialId()));
    }

    private Map<Long, String> loadCredentialNameMap(Set<Long> ids) {
        Set<Long> validIds = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (Alibaba1688AuthSession session : authSessionRepository.findAllById(validIds)) {
            map.put(session.getId(), session.getSessionName());
        }
        return map;
    }

    private Alibaba1688DetailRecordDTO.ListItem toListItem(Alibaba1688DetailRecord entity, String credentialName) {
        return Alibaba1688DetailRecordDTO.ListItem.builder()
                .id(entity.getId())
                .offerId(entity.getOfferId())
                .detailUrl(entity.getDetailUrl())
                .canonicalUrl(entity.getCanonicalUrl())
                .productName(entity.getProductName())
                .companyName(entity.getCompanyName())
                .productMainImage(entity.getProductMainImage())
                .minPrice(entity.getMinPrice())
                .maxPrice(entity.getMaxPrice())
                .repeatCustomerRate(entity.getRepeatCustomerRate())
                .serviceScore(entity.getServiceScore())
                .onTimeDeliveryRate(entity.getOnTimeDeliveryRate())
                .shopPositiveRate(entity.getShopPositiveRate())
                .powerSeller(entity.getPowerSeller())
                .settledYearsText(entity.getSettledYearsText())
                .mainBusiness(entity.getMainBusiness())
                .sourcePlatform(entity.getSourcePlatform())
                .status(entity.getStatus())
                .lastError(entity.getLastError())
                .lastTaskId(entity.getLastTaskId())
                .lastCredentialId(entity.getLastCredentialId())
                .credentialName(credentialName)
                .lastCollectedAt(entity.getLastCollectedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private Alibaba1688DetailRecordDTO.DetailResponse toDetailResponse(Alibaba1688DetailRecord entity, String credentialName) {
        return Alibaba1688DetailRecordDTO.DetailResponse.builder()
                .id(entity.getId())
                .offerId(entity.getOfferId())
                .detailUrl(entity.getDetailUrl())
                .canonicalUrl(entity.getCanonicalUrl())
                .productName(entity.getProductName())
                .companyName(entity.getCompanyName())
                .productMainImage(entity.getProductMainImage())
                .minPrice(entity.getMinPrice())
                .maxPrice(entity.getMaxPrice())
                .repeatCustomerRate(entity.getRepeatCustomerRate())
                .serviceScore(entity.getServiceScore())
                .onTimeDeliveryRate(entity.getOnTimeDeliveryRate())
                .shopPositiveRate(entity.getShopPositiveRate())
                .powerSeller(entity.getPowerSeller())
                .settledYearsText(entity.getSettledYearsText())
                .mainBusiness(entity.getMainBusiness())
                .sourcePlatform(entity.getSourcePlatform())
                .status(entity.getStatus())
                .lastError(entity.getLastError())
                .lastTaskId(entity.getLastTaskId())
                .lastCredentialId(entity.getLastCredentialId())
                .credentialName(credentialName)
                .rawHtml(entity.getRawHtml())
                .extractedJson(entity.getExtractedJson())
                .parsedJson(entity.getParsedJson())
                .lastCollectedAt(entity.getLastCollectedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
