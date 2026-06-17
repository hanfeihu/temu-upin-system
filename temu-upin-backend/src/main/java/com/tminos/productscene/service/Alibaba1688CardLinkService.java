package com.tminos.productscene.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.Alibaba1688CardLinkDTO;
import com.tminos.productscene.entity.Alibaba1688CardLinkRecord;
import com.tminos.productscene.repository.Alibaba1688CardLinkRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class Alibaba1688CardLinkService {

    private final Alibaba1688CardLinkRecordRepository repository;
    private final ObjectMapper objectMapper;

    public Alibaba1688CardLinkService(Alibaba1688CardLinkRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<Alibaba1688CardLinkDTO.ListItem> list(String keyword, String type, Integer status, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "id")
        );
        Specification<Alibaba1688CardLinkRecord> spec = (root, query, cb) -> {
            var predicate = cb.conjunction();

            if (StringUtils.hasText(type)) {
                predicate = cb.and(predicate, cb.equal(cb.lower(root.get("type")), type.trim().toLowerCase()));
            }

            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }

            if (StringUtils.hasText(keyword)) {
                String likeValue = "%" + keyword.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("offerId")), likeValue),
                        cb.like(cb.lower(root.get("detailUrl")), likeValue),
                        cb.like(cb.lower(root.get("cardHref")), likeValue),
                        cb.like(cb.lower(root.get("renderKey")), likeValue),
                        cb.like(cb.lower(root.get("cardClass")), likeValue)
                ));
            }

            return predicate;
        };
        return repository.findAll(spec, pageable).map(this::toListItem);
    }

    @Transactional
    public Alibaba1688CardLinkDTO.ImportResult importItems(List<Alibaba1688CardLinkDTO.ImportItem> items) {
        List<Alibaba1688CardLinkDTO.ImportItem> safeItems = items == null ? List.of() : items;
        LinkedHashMap<String, Alibaba1688CardLinkDTO.ImportItem> dedupedByOfferId = new LinkedHashMap<>();
        int skippedMissingOfferIdCount = 0;

        for (Alibaba1688CardLinkDTO.ImportItem item : safeItems) {
            String offerId = trimToNull(item == null ? null : item.getOfferId());
            if (!StringUtils.hasText(offerId)) {
                skippedMissingOfferIdCount++;
                continue;
            }

            dedupedByOfferId.put(offerId, Alibaba1688CardLinkDTO.ImportItem.builder()
                    .type(trimToNull(item.getType()))
                    .offerId(offerId)
                    .detailUrl(trimToNull(item.getDetailUrl()))
                    .cardHref(trimToNull(item.getCardHref()))
                    .renderKey(trimToNull(item.getRenderKey()))
                    .index(trimToNull(item.getIndex()))
                    .offerIdSource(trimToNull(item.getOfferIdSource()))
                    .cardClass(trimToNull(item.getCardClass()))
                    .build());
        }

        if (dedupedByOfferId.isEmpty()) {
            return Alibaba1688CardLinkDTO.ImportResult.builder()
                    .receivedCount(safeItems.size())
                    .validCount(0)
                    .insertedCount(0)
                    .updatedCount(0)
                    .skippedMissingOfferIdCount(skippedMissingOfferIdCount)
                    .duplicateInPayloadCount(0)
                    .build();
        }

        Map<String, Alibaba1688CardLinkRecord> existingMap = new HashMap<>();
        repository.findAllByOfferIdIn(dedupedByOfferId.keySet())
                .forEach(record -> existingMap.put(record.getOfferId(), record));

        List<Alibaba1688CardLinkRecord> toSave = new ArrayList<>();
        int insertedCount = 0;
        int updatedCount = 0;

        for (Map.Entry<String, Alibaba1688CardLinkDTO.ImportItem> entry : dedupedByOfferId.entrySet()) {
            String offerId = entry.getKey();
            Alibaba1688CardLinkDTO.ImportItem item = entry.getValue();

            Alibaba1688CardLinkRecord record = existingMap.get(offerId);
            if (record == null) {
                record = new Alibaba1688CardLinkRecord();
                record.setOfferId(offerId);
                record.setStatus(0);
                insertedCount++;
            } else {
                updatedCount++;
            }

            applyItem(record, item);
            toSave.add(record);
        }

        repository.saveAll(toSave);

        return Alibaba1688CardLinkDTO.ImportResult.builder()
                .receivedCount(safeItems.size())
                .validCount(dedupedByOfferId.size())
                .insertedCount(insertedCount)
                .updatedCount(updatedCount)
                .skippedMissingOfferIdCount(skippedMissingOfferIdCount)
                .duplicateInPayloadCount(Math.max(safeItems.size() - skippedMissingOfferIdCount - dedupedByOfferId.size(), 0))
                .build();
    }

    @Transactional
    public Alibaba1688CardLinkDTO.ListItem updateStatus(Long id, Integer status) {
        validateStatus(status);
        Alibaba1688CardLinkRecord record = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("1688 卡片链接记录不存在: " + id));
        record.setStatus(status);
        return toListItem(repository.save(record));
    }

    private void applyItem(Alibaba1688CardLinkRecord record, Alibaba1688CardLinkDTO.ImportItem item) {
        record.setType(normalizeType(item.getType()));
        record.setDetailUrl(trimToNull(item.getDetailUrl()));
        record.setCardHref(trimToNull(item.getCardHref()));
        record.setRenderKey(trimToNull(item.getRenderKey()));
        record.setCardIndex(trimToNull(item.getIndex()));
        record.setOfferIdSource(trimToNull(item.getOfferIdSource()));
        record.setCardClass(trimToNull(item.getCardClass()));
        record.setRawPayload(writeJson(item));
        if (record.getStatus() == null) {
            record.setStatus(0);
        }
    }

    private Alibaba1688CardLinkDTO.ListItem toListItem(Alibaba1688CardLinkRecord record) {
        return Alibaba1688CardLinkDTO.ListItem.builder()
                .id(record.getId())
                .type(record.getType())
                .offerId(record.getOfferId())
                .detailUrl(record.getDetailUrl())
                .cardHref(record.getCardHref())
                .renderKey(record.getRenderKey())
                .cardIndex(record.getCardIndex())
                .offerIdSource(record.getOfferIdSource())
                .cardClass(record.getCardClass())
                .status(record.getStatus())
                .rawPayload(record.getRawPayload())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("状态仅支持 0=未处理, 1=已处理");
        }
    }

    private String normalizeType(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "normal" : normalized.toLowerCase();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String writeJson(Alibaba1688CardLinkDTO.ImportItem item) {
        try {
            return objectMapper.writeValueAsString(item);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
