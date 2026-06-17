package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.TemuSitePublishExceptionDTO;
import com.tminos.productscene.entity.TemuSitePublishException;
import com.tminos.productscene.repository.TemuSitePublishExceptionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemuSitePublishExceptionService {

    private static final Pattern SKC_PATTERN = Pattern.compile("\\bSKC_[A-Za-z0-9_-]+\\b");
    private static final Pattern OFFER_ID_FROM_SKC_PATTERN = Pattern.compile("\\bSKC_\\d+_(\\d{6,})\\b");
    private static final Pattern SPU_PATTERN = Pattern.compile("SPU[:：]\\s*(\\d+)");
    private static final Pattern PRICE_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*[¥元]");
    private static final Pattern GOODS_NO_PATTERN = Pattern.compile("货号[:：]\\s*([^\\s]+)");
    private static final Pattern SITE_PATTERN = Pattern.compile("(美国站|欧区|英国站|德国站|法国站|意大利站|西班牙站|日本站|加拿大站|澳大利亚站|全球)");
    private static final Pattern STATUS_PATTERN = Pattern.compile("状态[:：]\\s*([^\\s]+)");
    private static final Pattern CREATE_TIME_PATTERN = Pattern.compile("创建时间[:：]\\s*([0-9]{4}-[0-9]{2}-[0-9]{2}\\s+[0-9]{2}:[0-9]{2}:[0-9]{2})");
    private static final Pattern PRICE_CONFIRM_TIME_PATTERN = Pattern.compile("价格确认时间[:：]\\s*([0-9]{4}-[0-9]{2}-[0-9]{2}\\s+[0-9]{2}:[0-9]{2}:[0-9]{2})");
    private static final Pattern JOINED_SITE_TIME_PATTERN = Pattern.compile("加入站点时间[:：]\\s*([^\\s]+)");
    private static final Pattern OPERATOR_PATTERN = Pattern.compile("-\\s*-\\s*([a-zA-Z][a-zA-Z0-9._-]*)\\s+商品对接运营");

    private final TemuSitePublishExceptionRepository repository;
    private final ObjectMapper objectMapper;

    public TemuSitePublishExceptionService(TemuSitePublishExceptionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<TemuSitePublishExceptionDTO.Item> list(
            String keyword,
            String siteName,
            String reasonType,
            Boolean active,
            int page,
            int size
    ) {
        String normalizedKeyword = trimToNull(keyword);
        String normalizedSite = trimToNull(siteName);
        String normalizedReasonType = trimToNull(reasonType);
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        Specification<TemuSitePublishException> specification = (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (StringUtils.hasText(normalizedKeyword)) {
                String likeValue = "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("skc")), likeValue),
                        cb.like(cb.lower(root.get("offerId")), likeValue),
                        cb.like(cb.lower(root.get("temuSpuId")), likeValue),
                        cb.like(cb.lower(root.get("temuGoodsId")), likeValue),
                        cb.like(cb.lower(root.get("goodsNo")), likeValue),
                        cb.like(cb.lower(root.get("productTitle")), likeValue),
                        cb.like(cb.lower(root.get("categoryText")), likeValue),
                        cb.like(cb.lower(root.get("reasonText")), likeValue),
                        cb.like(cb.lower(root.get("remark")), likeValue)
                ));
            }
            if (StringUtils.hasText(normalizedSite)) {
                predicate = cb.and(predicate, cb.equal(cb.lower(root.get("siteName")), normalizedSite.toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(normalizedReasonType)) {
                predicate = cb.and(predicate, cb.equal(cb.lower(root.get("reasonType")), normalizedReasonType.toLowerCase(Locale.ROOT)));
            }
            if (active != null) {
                predicate = cb.and(predicate, cb.equal(root.get("active"), active));
            }
            return predicate;
        };
        return repository.findAll(specification, pageable).map(this::toItem);
    }

    @Transactional
    public TemuSitePublishExceptionDTO.ImportResponse importFromPath(String filePath) throws IOException {
        String normalizedPath = trimToNull(filePath);
        if (normalizedPath == null) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        try (InputStream inputStream = Files.newInputStream(Path.of(normalizedPath))) {
            return importJson(inputStream, Path.of(normalizedPath).getFileName().toString());
        }
    }

    @Transactional
    public TemuSitePublishExceptionDTO.ImportResponse importJson(InputStream inputStream, String sourceFileName) throws IOException {
        JsonNode root = objectMapper.readTree(inputStream);
        JsonNode records = root.path("records");
        if (!records.isArray()) {
            throw new IllegalArgumentException("JSON 中没有 records 数组");
        }
        ImportContext context = new ImportContext(
                text(root, "url"),
                text(root, "title"),
                parseCollectedAt(text(root, "collectedAt")),
                trimToNull(sourceFileName)
        );

        int total = records.size();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> skippedReasons = new ArrayList<>();
        for (JsonNode record : records) {
            ParsedRecord parsed = parseRecord(record, context);
            if (parsed == null) {
                skipped++;
                if (skippedReasons.size() < 20) {
                    skippedReasons.add("跳过无 SKC 或无异常原因的记录");
                }
                continue;
            }
            TemuSitePublishException entity = repository
                    .findFirstBySkcAndSiteName(parsed.skc(), parsed.siteName())
                    .orElseGet(TemuSitePublishException::new);
            boolean isNew = entity.getId() == null;
            applyParsed(entity, parsed);
            repository.save(entity);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }
        return TemuSitePublishExceptionDTO.ImportResponse.builder()
                .totalRecords(total)
                .imported(created + updated)
                .created(created)
                .updated(updated)
                .skipped(skipped)
                .skippedReasons(skippedReasons)
                .build();
    }

    @Transactional
    public TemuSitePublishExceptionDTO.Item update(Long id, TemuSitePublishExceptionDTO.UpdateRequest request) {
        TemuSitePublishException entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("加站异常记录不存在: " + id));
        if (request != null) {
            if (request.getActive() != null) {
                entity.setActive(request.getActive());
            }
            if (request.getReasonType() != null) {
                entity.setReasonType(classifyReason(request.getReasonType()));
            }
            if (request.getRemark() != null) {
                entity.setRemark(trimToNull(request.getRemark()));
            }
        }
        return toItem(repository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public boolean isOfferBlocked(String offerId) {
        String normalized = trimToNull(offerId);
        return normalized != null && repository.existsByOfferIdAndActiveTrue(normalized);
    }

    @Transactional(readOnly = true)
    public Set<String> findBlockedOfferIds(Collection<String> offerIds) {
        if (offerIds == null || offerIds.isEmpty()) {
            return Set.of();
        }
        List<String> normalized = offerIds.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(repository.findActiveOfferIds(normalized));
    }

    private ParsedRecord parseRecord(JsonNode record, ImportContext context) {
        String rowText = firstText(text(record, "rowText"), text(record, "detailText"));
        String detailText = text(record, "detailText");
        String allText = joinText(rowText, detailText, record.path("fields").toString());
        String skc = firstMatch(SKC_PATTERN, allText, 0);
        if (!StringUtils.hasText(skc)) {
            return null;
        }
        String reasonText = extractReason(allText);
        if (!StringUtils.hasText(reasonText)) {
            return null;
        }
        String siteName = extractSite(reasonText, allText);
        String offerId = firstMatch(OFFER_ID_FROM_SKC_PATTERN, skc, 1);
        String temuSpuId = firstMatch(SPU_PATTERN, allText, 1);
        String goodsNo = extractGoodsNo(allText, skc);
        return new ParsedRecord(
                skc,
                offerId,
                parseProductCollectionIdFromSkc(skc),
                temuSpuId,
                firstLongNumberAfterSpu(allText, temuSpuId),
                goodsNo,
                siteName,
                firstMatch(STATUS_PATTERN, allText, 1),
                extractTitle(allText, skc),
                extractCategory(allText),
                extractPrice(allText),
                reasonText,
                classifyReason(reasonText),
                firstMatch(OPERATOR_PATTERN, allText, 1),
                firstMatch(CREATE_TIME_PATTERN, allText, 1),
                firstMatch(PRICE_CONFIRM_TIME_PATTERN, allText, 1),
                firstMatch(JOINED_SITE_TIME_PATTERN, allText, 1),
                context.sourceUrl(),
                context.sourceTitle(),
                context.sourceCollectedAt(),
                context.sourceFileName(),
                rowText,
                detailText,
                toJson(record)
        );
    }

    private void applyParsed(TemuSitePublishException entity, ParsedRecord parsed) {
        entity.setSkc(parsed.skc());
        entity.setOfferId(parsed.offerId());
        entity.setProductCollectionId(parsed.productCollectionId());
        entity.setTemuSpuId(parsed.temuSpuId());
        entity.setTemuGoodsId(parsed.temuGoodsId());
        entity.setGoodsNo(parsed.goodsNo());
        entity.setSiteName(firstText(parsed.siteName(), "未知站点"));
        entity.setStatusText(parsed.statusText());
        entity.setProductTitle(parsed.productTitle());
        entity.setCategoryText(parsed.categoryText());
        entity.setDeclaredPrice(parsed.declaredPrice());
        entity.setReasonText(parsed.reasonText());
        entity.setReasonType(parsed.reasonType());
        entity.setOperatorName(parsed.operatorName());
        entity.setCreatedTimeText(parsed.createdTimeText());
        entity.setPriceConfirmTimeText(parsed.priceConfirmTimeText());
        entity.setJoinedSiteTimeText(parsed.joinedSiteTimeText());
        entity.setSourceUrl(parsed.sourceUrl());
        entity.setSourceTitle(parsed.sourceTitle());
        entity.setSourceCollectedAt(parsed.sourceCollectedAt());
        entity.setSourceFileName(parsed.sourceFileName());
        entity.setRowText(parsed.rowText());
        entity.setDetailText(parsed.detailText());
        entity.setRawJson(parsed.rawJson());
        entity.setActive(true);
    }

    private TemuSitePublishExceptionDTO.Item toItem(TemuSitePublishException entity) {
        return TemuSitePublishExceptionDTO.Item.builder()
                .id(entity.getId())
                .skc(entity.getSkc())
                .offerId(entity.getOfferId())
                .productCollectionId(entity.getProductCollectionId())
                .temuSpuId(entity.getTemuSpuId())
                .temuGoodsId(entity.getTemuGoodsId())
                .goodsNo(entity.getGoodsNo())
                .siteName(entity.getSiteName())
                .statusText(entity.getStatusText())
                .productTitle(entity.getProductTitle())
                .categoryText(entity.getCategoryText())
                .declaredPrice(entity.getDeclaredPrice())
                .reasonText(entity.getReasonText())
                .reasonType(entity.getReasonType())
                .operatorName(entity.getOperatorName())
                .createdTimeText(entity.getCreatedTimeText())
                .priceConfirmTimeText(entity.getPriceConfirmTimeText())
                .joinedSiteTimeText(entity.getJoinedSiteTimeText())
                .sourceUrl(entity.getSourceUrl())
                .sourceTitle(entity.getSourceTitle())
                .sourceCollectedAt(entity.getSourceCollectedAt())
                .sourceFileName(entity.getSourceFileName())
                .rowText(entity.getRowText())
                .detailText(entity.getDetailText())
                .active(entity.getActive())
                .remark(entity.getRemark())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String extractReason(String text) {
        String marker = "加站异常站点及原因";
        int index = text == null ? -1 : text.indexOf(marker);
        if (index < 0) {
            return null;
        }
        String tail = text.substring(index + marker.length()).replaceFirst("^[:：]\\s*", "").trim();
        int end = minPositive(
                tail.indexOf("站点申报价格"),
                tail.indexOf("商品对接运营"),
                tail.indexOf("创建时间"),
                tail.indexOf("\n---")
        );
        if (end >= 0) {
            tail = tail.substring(0, end);
        }
        return truncate(tail, 2000);
    }

    private String extractSite(String reasonText, String allText) {
        String fromReason = firstMatch(SITE_PATTERN, reasonText, 1);
        if (StringUtils.hasText(fromReason)) {
            return fromReason;
        }
        return firstMatch(SITE_PATTERN, allText, 1);
    }

    private String extractGoodsNo(String text, String skc) {
        Matcher matcher = GOODS_NO_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String value = trimToNull(matcher.group(1));
            if (value != null && !value.equals(skc)) {
                return truncate(value, 256);
            }
        }
        return null;
    }

    private String extractTitle(String text, String skc) {
        String value = text == null ? "" : text;
        int siteIndex = value.indexOf("站点：");
        if (siteIndex > 0) {
            String titlePart = value.substring(0, siteIndex);
            titlePart = titlePart.replaceFirst("^\\s*[0-9]+(?:\\.[0-9]+)?¥\\s*", "").trim();
            return truncate(titlePart, 1000);
        }
        int skcIndex = value.indexOf(skc);
        if (skcIndex > 0) {
            return truncate(value.substring(0, skcIndex).trim(), 1000);
        }
        return null;
    }

    private String extractCategory(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        int siteIndex = text.indexOf("站点：");
        if (siteIndex < 0) {
            return null;
        }
        String beforeSite = text.substring(0, siteIndex);
        int ellipsisIndex = beforeSite.lastIndexOf("...");
        if (ellipsisIndex >= 0) {
            return truncate(beforeSite.substring(Math.max(0, ellipsisIndex - 20)).trim(), 500);
        }
        return null;
    }

    private BigDecimal extractPrice(String text) {
        Matcher matcher = PRICE_PATTERN.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long parseProductCollectionIdFromSkc(String skc) {
        if (!StringUtils.hasText(skc)) {
            return null;
        }
        String[] parts = skc.split("_");
        if (parts.length < 3) {
            return null;
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstLongNumberAfterSpu(String text, String spuId) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(spuId)) {
            return null;
        }
        int spuIndex = text.indexOf("SPU");
        if (spuIndex < 0) {
            return null;
        }
        String tail = text.substring(spuIndex);
        Matcher matcher = Pattern.compile("\\b\\d{8,}\\b").matcher(tail);
        while (matcher.find()) {
            String value = matcher.group();
            if (!value.equals(spuId)) {
                return value;
            }
        }
        return null;
    }

    private String classifyReason(String reasonText) {
        String text = trimToNull(reasonText);
        if (text == null) {
            return "OTHER";
        }
        if (text.contains("CPC") || text.contains("资质")) {
            return "QUALIFICATION";
        }
        if (text.contains("实拍图") || text.contains("标签")) {
            return "REAL_PHOTO_OR_LABEL";
        }
        if (text.contains("危险描述") || text.contains("敏感")) {
            return "SENSITIVE_ATTRIBUTE";
        }
        if (text.contains("违禁") || text.contains("禁售") || text.contains("不合规")) {
            return "COMPLIANCE";
        }
        return text.length() <= 128 ? text : "OTHER";
    }

    private LocalDateTime parseCollectedAt(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return trimToNull(node.get(field).asText());
    }

    private String firstMatch(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        return matcher.find() ? trimToNull(matcher.group(group)) : null;
    }

    private int minPositive(int... values) {
        int result = -1;
        for (int value : values) {
            if (value >= 0 && (result < 0 || value < result)) {
                result = value;
            }
        }
        return result;
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return node == null ? null : node.toString();
        }
    }

    private String joinText(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(value.trim());
            }
        }
        return builder.toString();
    }

    private String firstText(String... values) {
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

    private String truncate(String value, int maxLength) {
        String text = trimToNull(value);
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private record ImportContext(
            String sourceUrl,
            String sourceTitle,
            LocalDateTime sourceCollectedAt,
            String sourceFileName
    ) {
    }

    private record ParsedRecord(
            String skc,
            String offerId,
            Long productCollectionId,
            String temuSpuId,
            String temuGoodsId,
            String goodsNo,
            String siteName,
            String statusText,
            String productTitle,
            String categoryText,
            BigDecimal declaredPrice,
            String reasonText,
            String reasonType,
            String operatorName,
            String createdTimeText,
            String priceConfirmTimeText,
            String joinedSiteTimeText,
            String sourceUrl,
            String sourceTitle,
            LocalDateTime sourceCollectedAt,
            String sourceFileName,
            String rowText,
            String detailText,
            String rawJson
    ) {
    }
}
