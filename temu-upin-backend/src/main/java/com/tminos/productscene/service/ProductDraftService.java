package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.ProductDraftDTO;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.ProductDraft;
import com.tminos.productscene.repository.ImportTitleFilterWordRepository;
import com.tminos.productscene.repository.ProductDraftRepository;
import com.tminos.productscene.service.pull.parser.Alibaba1688HtmlParser;
import com.tminos.productscene.service.pull.parser.TemuHtmlParser;
import jakarta.persistence.EntityNotFoundException;
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
import java.util.regex.Pattern;

@Service
public class ProductDraftService {

    private final ProductDraftRepository repo;
    private final ProductCollectionService productCollectionService;
    private final ObjectMapper objectMapper;
    private final ImportTitleFilterWordRepository titleFilterWordRepo;

    public ProductDraftService(ProductDraftRepository repo,
                               ProductCollectionService productCollectionService,
                               ObjectMapper objectMapper,
                               ImportTitleFilterWordRepository titleFilterWordRepo) {
        this.repo = repo;
        this.productCollectionService = productCollectionService;
        this.objectMapper = objectMapper;
        this.titleFilterWordRepo = titleFilterWordRepo;
    }

    @Transactional(readOnly = true)
    public Page<ProductDraftDTO.ListItem> list(String q, String sourcePlatform, Boolean showDeleted, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "updatedAt"));
        boolean includeDeleted = Boolean.TRUE.equals(showDeleted);
        Specification<ProductDraft> spec = (root, query, cb) -> {
            var p = cb.conjunction();
            if (!includeDeleted) {
                p = cb.and(p, cb.isFalse(root.get("deleted")));
            }
            if (StringUtils.hasText(sourcePlatform)) {
                p = cb.and(p, cb.equal(root.get("sourcePlatform"), sourcePlatform.trim().toUpperCase()));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim() + "%";
                p = cb.and(p, cb.or(
                        cb.like(cb.lower(root.get("productName")), like.toLowerCase()),
                        cb.like(cb.lower(root.get("productId")), like.toLowerCase())
                ));
            }
            return p;
        };
        return repo.findAll(spec, pageable).map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public ProductDraftDTO.Detail get(Long id) {
        return toDetail(require(id));
    }

    @Transactional
    public ProductDraftDTO.Detail importDraft(ProductDraftDTO.ImportRequest request) {
        if (request == null || !StringUtils.hasText(request.getHtml())) {
            throw new IllegalArgumentException("HTML content is required");
        }
        ParsedSnapshot snapshot = parseSnapshot(request.getHtml(), request.getExtractedJson());
        ProductDraft draft = ProductDraft.builder()
                .sourcePlatform(snapshot.sourcePlatform)
                .productId(defaultText(snapshot.parsed.getProductId(), "UNKNOWN-" + System.currentTimeMillis()))
                .productName(defaultText(cleanImportedTitle(snapshot.parsed.getProductName()), "未命名草稿"))
                .productCategory(trim(snapshot.parsed.getProductCategory()))
                .originalCategory(trim(snapshot.parsed.getOriginalCategory()))
                .productMainImage(trim(snapshot.parsed.getProductMainImage()))
                .productUrl(trim(snapshot.parsed.getProductUrl()))
                .monthlySales(trim(snapshot.parsed.getMonthlySales()))
                .reviewCount(snapshot.parsed.getReviewCount())
                .companyName(trim(snapshot.parsed.getCompanyName()))
                .originalHtml(request.getHtml())
                .extractedJson(trimToNull(request.getExtractedJson()))
                .parserSnapshotJson(snapshot.snapshotJson)
                .deleted(false)
                .version(0)
                .pushedToCollection(false)
                .build();
        return toDetail(repo.save(draft));
    }

    @Transactional
    public ProductDraftDTO.Detail update(Long id, ProductDraftDTO.UpdateRequest request) {
        ProductDraft draft = require(id);
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.getProductName() != null) draft.setProductName(defaultText(trim(request.getProductName()), draft.getProductName()));
        if (request.getProductCategory() != null) draft.setProductCategory(trimToNull(request.getProductCategory()));
        if (request.getOriginalCategory() != null) draft.setOriginalCategory(trimToNull(request.getOriginalCategory()));
        if (request.getProductMainImage() != null) draft.setProductMainImage(trimToNull(request.getProductMainImage()));
        if (request.getProductUrl() != null) draft.setProductUrl(trimToNull(request.getProductUrl()));
        if (request.getMonthlySales() != null) draft.setMonthlySales(trimToNull(request.getMonthlySales()));
        if (request.getReviewCount() != null) draft.setReviewCount(request.getReviewCount());
        if (request.getCompanyName() != null) draft.setCompanyName(trimToNull(request.getCompanyName()));
        return toDetail(repo.save(draft));
    }

    @Transactional
    public void delete(Long id) {
        ProductDraft draft = require(id);
        draft.setDeleted(true);
        repo.save(draft);
    }

    @Transactional
    public ProductDraftDTO.PushResponse pushToCollection(Long id) throws Exception {
        ProductDraft draft = require(id);
        if (!StringUtils.hasText(draft.getOriginalHtml())) {
            throw new IllegalArgumentException("draft html is required");
        }
        ProductCollection saved = productCollectionService.importFromHtml(draft.getOriginalHtml(), draft.getExtractedJson());
        draft.setPushedToCollection(true);
        draft.setPushedCollectionId(saved.getId());
        draft.setPushedAt(LocalDateTime.now());
        draft.setPushMessage("Imported to product collection");
        repo.save(draft);
        return ProductDraftDTO.PushResponse.builder()
                .draftId(draft.getId())
                .collectionId(saved.getId())
                .productId(saved.getProductId())
                .message("Imported")
                .build();
    }

    private ProductDraft require(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("draft not found: " + id));
    }

    private ProductDraftDTO.ListItem toListItem(ProductDraft e) {
        return ProductDraftDTO.ListItem.builder()
                .id(e.getId())
                .sourcePlatform(e.getSourcePlatform())
                .productId(e.getProductId())
                .productName(e.getProductName())
                .productCategory(e.getProductCategory())
                .originalCategory(e.getOriginalCategory())
                .productMainImage(e.getProductMainImage())
                .productUrl(e.getProductUrl())
                .monthlySales(e.getMonthlySales())
                .reviewCount(e.getReviewCount())
                .companyName(e.getCompanyName())
                .pushedToCollection(e.getPushedToCollection())
                .pushedCollectionId(e.getPushedCollectionId())
                .pushedAt(e.getPushedAt())
                .pushMessage(e.getPushMessage())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private ProductDraftDTO.Detail toDetail(ProductDraft e) {
        return ProductDraftDTO.Detail.builder()
                .id(e.getId())
                .sourcePlatform(e.getSourcePlatform())
                .productId(e.getProductId())
                .productName(e.getProductName())
                .productCategory(e.getProductCategory())
                .originalCategory(e.getOriginalCategory())
                .productMainImage(e.getProductMainImage())
                .productUrl(e.getProductUrl())
                .monthlySales(e.getMonthlySales())
                .reviewCount(e.getReviewCount())
                .companyName(e.getCompanyName())
                .originalHtml(e.getOriginalHtml())
                .extractedJson(e.getExtractedJson())
                .parserSnapshotJson(e.getParserSnapshotJson())
                .pushedToCollection(e.getPushedToCollection())
                .pushedCollectionId(e.getPushedCollectionId())
                .pushedAt(e.getPushedAt())
                .pushMessage(e.getPushMessage())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private ParsedSnapshot parseSnapshot(String html, String extractedJson) {
        try {
            Alibaba1688HtmlParser.ParsedProduct parsed;
            String sourcePlatform;
            if (TemuHtmlParser.looksLikeTemuHtml(html)) {
                parsed = new TemuHtmlParser(objectMapper).parse(html);
                sourcePlatform = "TEMU";
            } else {
                parsed = new Alibaba1688HtmlParser(objectMapper).parse(html);
                sourcePlatform = "1688";
            }
            String snapshotJson = objectMapper.writeValueAsString(parsed);
            return new ParsedSnapshot(parsed, sourcePlatform, snapshotJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("draft import parse failed: " + e.getMessage(), e);
        }
    }

    private String cleanImportedTitle(String title) {
        if (title == null) return null;
        String t = title.trim();
        if (t.isBlank()) return t;

        List<String> kws = null;
        try {
            if (titleFilterWordRepo != null) {
                kws = titleFilterWordRepo.findAllOrdered().stream()
                        .map(w -> w == null ? null : w.getWord())
                        .filter(s -> s != null && !s.trim().isEmpty())
                        .map(String::trim)
                        .toList();
            }
        } catch (Exception ignored) {
        }
        if (kws == null || kws.isEmpty()) return t;

        String out = t;
        for (String kw : kws) {
            if (kw == null) continue;
            String k = kw.trim();
            if (k.isBlank()) continue;
            out = out.replaceAll("(?i)" + Pattern.quote(k), " ");
        }
        out = out.replaceAll("\\s+", " ").trim();
        return out.isBlank() ? t : out;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private record ParsedSnapshot(Alibaba1688HtmlParser.ParsedProduct parsed, String sourcePlatform, String snapshotJson) {
    }
}
