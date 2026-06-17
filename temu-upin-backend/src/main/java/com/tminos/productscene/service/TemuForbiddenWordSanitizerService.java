package com.tminos.productscene.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.ProductCollectionTemuSku;
import com.tminos.productscene.entity.TemuForbiddenWordRule;
import com.tminos.productscene.repository.ProductCollectionRepository;
import com.tminos.productscene.repository.ProductCollectionTemuSkuRepository;
import com.tminos.productscene.repository.TemuForbiddenWordRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class TemuForbiddenWordSanitizerService {

    private final TemuForbiddenWordRuleRepository ruleRepository;
    private final ProductCollectionRepository productCollectionRepository;
    private final ProductCollectionTemuSkuRepository temuSkuRepository;
    private final ObjectMapper objectMapper;

    public TemuForbiddenWordSanitizerService(
            TemuForbiddenWordRuleRepository ruleRepository,
            ProductCollectionRepository productCollectionRepository,
            ProductCollectionTemuSkuRepository temuSkuRepository,
            ObjectMapper objectMapper
    ) {
        this.ruleRepository = ruleRepository;
        this.productCollectionRepository = productCollectionRepository;
        this.temuSkuRepository = temuSkuRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SanitizeResult sanitizeForPublish(ProductCollection product, List<ProductCollectionTemuSku> skus) {
        List<TemuForbiddenWordRule> rules = ruleRepository.findByEnabledTrueOrderByIdAsc();
        if (product == null || rules == null || rules.isEmpty()) {
            return new SanitizeResult(false, List.of(), product, skus == null ? List.of() : skus);
        }
        List<ReplacementRecord> records = new ArrayList<>();
        boolean productChanged = false;

        TextReplacement productName = replaceText(product.getProductName(), "TITLE", rules);
        if (productName.changed()) {
            product.setProductName(productName.value());
            productChanged = true;
            records.addAll(toRecords("TITLE", "productName", null, productName.matches()));
        }

        TextReplacement titleEn = replaceText(product.getTemuOptimizedTitleEn(), "TITLE", rules);
        if (titleEn.changed()) {
            product.setTemuOptimizedTitleEn(titleEn.value());
            productChanged = true;
            records.addAll(toRecords("TITLE", "temuOptimizedTitleEn", null, titleEn.matches()));
        }

        TextReplacement titleZh = replaceText(product.getTemuOptimizedTitleZh(), "TITLE", rules);
        if (titleZh.changed()) {
            product.setTemuOptimizedTitleZh(titleZh.value());
            productChanged = true;
            records.addAll(toRecords("TITLE", "temuOptimizedTitleZh", null, titleZh.matches()));
        }

        if (productChanged) {
            product.setUpdatedAt(LocalDateTime.now());
            product = productCollectionRepository.save(product);
        }

        List<ProductCollectionTemuSku> safeSkus = skus == null ? List.of() : skus;
        List<ProductCollectionTemuSku> changedSkus = new ArrayList<>();
        for (ProductCollectionTemuSku sku : safeSkus) {
            if (sku == null) {
                continue;
            }
            boolean skuChanged = false;
            TextReplacement specKey = replaceText(sku.getSpecKey(), "SKU", rules);
            if (specKey.changed()) {
                sku.setSpecKey(specKey.value());
                skuChanged = true;
                records.addAll(toRecords("SKU", "specKey", sku.getId(), specKey.matches()));
            }

            SpecJsonReplacement specJson = replaceSpecJson(sku.getSpecJson(), rules);
            if (specJson.changed()) {
                sku.setSpecJson(specJson.value());
                skuChanged = true;
                records.addAll(toRecords("SKU", "specJson", sku.getId(), specJson.matches()));
            }

            if (skuChanged) {
                changedSkus.add(sku);
            }
        }
        if (!changedSkus.isEmpty()) {
            temuSkuRepository.saveAll(changedSkus);
        }
        return new SanitizeResult(!records.isEmpty(), records, product, safeSkus);
    }

    private SpecJsonReplacement replaceSpecJson(String rawJson, List<TemuForbiddenWordRule> rules) {
        if (!StringUtils.hasText(rawJson)) {
            return new SpecJsonReplacement(false, rawJson, List.of());
        }
        try {
            LinkedHashMap<String, Object> parsed = objectMapper.readValue(rawJson, new TypeReference<>() {});
            if (parsed == null || parsed.isEmpty()) {
                return new SpecJsonReplacement(false, rawJson, List.of());
            }
            boolean changed = false;
            List<TextReplacement.Match> matches = new ArrayList<>();
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                TextReplacement replacement = replaceText(String.valueOf(entry.getValue()), "SKU", rules);
                if (replacement.changed()) {
                    entry.setValue(replacement.value());
                    matches.addAll(replacement.matches());
                    changed = true;
                }
            }
            if (!changed) {
                return new SpecJsonReplacement(false, rawJson, List.of());
            }
            return new SpecJsonReplacement(true, objectMapper.writeValueAsString(parsed), matches);
        } catch (Exception ignored) {
            TextReplacement replacement = replaceText(rawJson, "SKU", rules);
            return new SpecJsonReplacement(replacement.changed(), replacement.value(), replacement.matches());
        }
    }

    private TextReplacement replaceText(String text, String fieldScope, List<TemuForbiddenWordRule> rules) {
        if (!StringUtils.hasText(text)) {
            return new TextReplacement(false, text, List.of());
        }
        String current = text;
        List<TextReplacement.Match> matches = new ArrayList<>();
        for (TemuForbiddenWordRule rule : rules) {
            if (rule == null || !ruleApplies(rule, fieldScope) || !StringUtils.hasText(rule.getWord())) {
                continue;
            }
            String replacement = rule.getReplacement() == null ? "" : rule.getReplacement().trim();
            String next = replaceIgnoreCase(current, rule.getWord().trim(), replacement);
            if (!Objects.equals(next, current)) {
                matches.add(new TextReplacement.Match(rule.getWord().trim(), replacement, current, next));
                current = cleanupText(next);
            }
        }
        return new TextReplacement(!Objects.equals(current, text), current, matches);
    }

    private boolean ruleApplies(TemuForbiddenWordRule rule, String fieldScope) {
        String scope = rule.getFieldScope();
        if (!StringUtils.hasText(scope) || "ALL".equalsIgnoreCase(scope)) {
            return true;
        }
        return scope.equalsIgnoreCase(fieldScope);
    }

    private String replaceIgnoreCase(String text, String word, String replacement) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(word)) {
            return text;
        }
        return Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(text)
                .replaceAll(java.util.regex.Matcher.quoteReplacement(replacement == null ? "" : replacement));
    }

    private String cleanupText(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("\\s+", " ")
                .replaceAll("(?i)\\b(\\w+)\\s+\\1\\b", "$1")
                .replace("Connector Quick Connector", "Quick Connector")
                .trim();
    }

    private List<ReplacementRecord> toRecords(String scope, String fieldName, Long skuId, List<TextReplacement.Match> matches) {
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }
        List<ReplacementRecord> out = new ArrayList<>();
        for (TextReplacement.Match match : matches) {
            out.add(new ReplacementRecord(scope, fieldName, skuId, match.word(), match.replacement(), match.before(), match.after()));
        }
        return out;
    }

    public record SanitizeResult(
            boolean changed,
            List<ReplacementRecord> records,
            ProductCollection product,
            List<ProductCollectionTemuSku> skus
    ) {
    }

    public record ReplacementRecord(
            String fieldScope,
            String fieldName,
            Long skuId,
            String word,
            String replacement,
            String before,
            String after
    ) {
    }

    private record TextReplacement(boolean changed, String value, List<Match> matches) {
        private record Match(String word, String replacement, String before, String after) {
        }
    }

    private record SpecJsonReplacement(boolean changed, String value, List<TextReplacement.Match> matches) {
    }
}
