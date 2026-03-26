package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.ProductCollectionTemuSku;
import com.tminos.productscene.repository.ProductCollectionRepository;
import com.tminos.productscene.repository.ProductCollectionTemuSkuRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
public class BatchImageTranslateService {

    private static final Logger log = LoggerFactory.getLogger(BatchImageTranslateService.class);

    private final ProductCollectionService productCollectionService;
    private final ProductCollectionRepository productCollectionRepository;
    private final ProductCollectionTemuSkuRepository temuSkuRepository;
    private final AliyunImageTranslateService aliyunImageTranslateService;
    private final TemuImageTranslateService temuImageTranslateService;
    private final ImageTranslateRecordService imageTranslateRecordService;
    private final ObjectMapper objectMapper;
    private final Executor batchImageTranslateExecutor;

    public BatchImageTranslateService(ProductCollectionService productCollectionService,
                                      ProductCollectionRepository productCollectionRepository,
                                      ProductCollectionTemuSkuRepository temuSkuRepository,
                                      AliyunImageTranslateService aliyunImageTranslateService,
                                      TemuImageTranslateService temuImageTranslateService,
                                      ImageTranslateRecordService imageTranslateRecordService,
                                      ObjectMapper objectMapper,
                                      @Qualifier("batchImageTranslateExecutor") Executor batchImageTranslateExecutor) {
        this.productCollectionService = productCollectionService;
        this.productCollectionRepository = productCollectionRepository;
        this.temuSkuRepository = temuSkuRepository;
        this.aliyunImageTranslateService = aliyunImageTranslateService;
        this.temuImageTranslateService = temuImageTranslateService;
        this.imageTranslateRecordService = imageTranslateRecordService;
        this.objectMapper = objectMapper;
        this.batchImageTranslateExecutor = batchImageTranslateExecutor;
    }

    @SuppressWarnings("null")
    public Map<String, Object> translateAllImages(Long spuId, String provider) throws Exception {
        if (spuId == null) {
            throw new IllegalArgumentException("spuId is required");
        }
        String resolvedProvider = normalizeProvider(provider);
        ProductCollection pc = productCollectionService.get(spuId);
        List<String> carousel = parseJsonStringList(pc.getCarouselImages());
        List<String> detail = parseJsonStringList(pc.getDetailImages());
        List<ProductCollectionTemuSku> temuSkus = temuSkuRepository.findBySpuIdOrderByIdAsc(spuId);

        Map<String, String> fieldToUrl = new LinkedHashMap<>();
        if (StringUtils.hasText(pc.getProductMainImage())) {
            fieldToUrl.put("productMainImage", pc.getProductMainImage().trim());
        }
        for (int i = 0; i < carousel.size(); i++) {
            String url = trimToNull(carousel.get(i));
            if (url != null) {
                fieldToUrl.put("carouselImages[" + i + "]", url);
            }
        }
        for (int i = 0; i < detail.size(); i++) {
            String url = trimToNull(detail.get(i));
            if (url != null) {
                fieldToUrl.put("detailImages[" + i + "]", url);
            }
        }
        for (ProductCollectionTemuSku sku : temuSkus) {
            String url = trimToNull(sku.getImage());
            if (url != null) {
                fieldToUrl.put("temuSku[" + sku.getId() + "].image", url);
            }
        }

        if (fieldToUrl.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("spuId", spuId);
            empty.put("provider", resolvedProvider);
            empty.put("changed", false);
            empty.put("totalFields", 0);
            empty.put("uniqueImages", 0);
            empty.put("successCount", 0);
            empty.put("failedCount", 0);
            empty.put("changes", Collections.emptyList());
            return empty;
        }

        Map<String, List<String>> fieldsByUrl = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fieldToUrl.entrySet()) {
            fieldsByUrl.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(entry.getKey());
        }

        Map<String, CompletableFuture<TranslationOutcome>> futureByUrl = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : fieldsByUrl.entrySet()) {
            String sourceUrl = entry.getKey();
            List<String> refs = List.copyOf(entry.getValue());
            futureByUrl.put(sourceUrl, CompletableFuture.supplyAsync(() -> translateOne(spuId, resolvedProvider, sourceUrl, refs), batchImageTranslateExecutor));
        }

        CompletableFuture.allOf(futureByUrl.values().toArray(new CompletableFuture[0])).get(30, TimeUnit.MINUTES);

        Map<String, TranslationOutcome> outcomeByUrl = new LinkedHashMap<>();
        int successCount = 0;
        int failedCount = 0;
        List<Map<String, Object>> changes = new ArrayList<>();
        for (Map.Entry<String, CompletableFuture<TranslationOutcome>> entry : futureByUrl.entrySet()) {
            TranslationOutcome outcome = entry.getValue().getNow(TranslationOutcome.failed(
                    entry.getKey(),
                    entry.getKey(),
                    resolvedProvider,
                    fieldsByUrl.get(entry.getKey()),
                    "translation timeout"
            ));
            outcomeByUrl.put(entry.getKey(), outcome);
            if (outcome.success()) {
                successCount++;
            } else {
                failedCount++;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceUrl", outcome.sourceUrl());
            item.put("translatedUrl", outcome.finalUrl());
            item.put("provider", outcome.provider());
            item.put("status", outcome.success() ? "success" : "failed");
            item.put("fieldRefs", outcome.fieldRefs());
            item.put("errorMsg", outcome.errorMsg());
            changes.add(item);
        }

        String oldMain = pc.getProductMainImage();
        String newMain = applyOutcome(oldMain, outcomeByUrl);
        List<String> newCarousel = new ArrayList<>();
        for (String image : carousel) {
            newCarousel.add(applyOutcome(image, outcomeByUrl));
        }
        List<String> newDetail = new ArrayList<>();
        for (String image : detail) {
            newDetail.add(applyOutcome(image, outcomeByUrl));
        }

        boolean changed = !Objects.equals(oldMain, newMain)
                || !Objects.equals(carousel, newCarousel)
                || !Objects.equals(detail, newDetail);

        if (!Objects.equals(oldMain, newMain)) {
            pc.setProductMainImage(newMain);
        }
        if (!Objects.equals(carousel, newCarousel)) {
            pc.setCarouselImages(writeJson(newCarousel));
        }
        if (!Objects.equals(detail, newDetail)) {
            pc.setDetailImages(writeJson(newDetail));
        }
        if (changed) {
            productCollectionRepository.save(pc);
        }

        int skuChanged = 0;
        for (ProductCollectionTemuSku sku : temuSkus) {
            String original = sku.getImage();
            String translated = applyOutcome(original, outcomeByUrl);
            if (!Objects.equals(original, translated)) {
                sku.setImage(translated);
                skuChanged++;
            }
        }
        if (skuChanged > 0) {
            temuSkuRepository.saveAll(temuSkus);
            changed = true;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("spuId", spuId);
        result.put("provider", resolvedProvider);
        result.put("changed", changed);
        result.put("totalFields", fieldToUrl.size());
        result.put("uniqueImages", fieldsByUrl.size());
        result.put("successCount", successCount);
        result.put("failedCount", failedCount);
        result.put("skuChanged", skuChanged);
        result.put("changes", changes);
        return result;
    }

    private TranslationOutcome translateOne(Long spuId, String provider, String sourceUrl, List<String> fieldRefs) {
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            String finalUrl;
            if ("temu".equals(provider)) {
                TemuImageTranslateService.Result result = temuImageTranslateService.translateGlobalImage(
                        sourceUrl,
                        "zh",
                        "en",
                        true,
                        null,
                        true
                );
                finalUrl = firstNonBlank(result.storedUrl(), result.translatedUrl(), result.uploadedUrl(), sourceUrl);
            } else {
                AliyunImageTranslateService.Result result = aliyunImageTranslateService.translateImageByUrl(
                        sourceUrl,
                        "zh",
                        "en",
                        false,
                        true
                );
                finalUrl = firstNonBlank(result.storedUrl(), result.translatedUrl(), result.originalUrl(), sourceUrl);
            }
            LocalDateTime endedAt = LocalDateTime.now();
            imageTranslateRecordService.create(spuId, provider, "success", fieldRefs, sourceUrl, finalUrl, null, startedAt, endedAt);
            return new TranslationOutcome(sourceUrl, finalUrl, provider, fieldRefs, true, null);
        } catch (Exception e) {
            LocalDateTime endedAt = LocalDateTime.now();
            String message = e.getMessage();
            imageTranslateRecordService.create(spuId, provider, "failed", fieldRefs, sourceUrl, null, message, startedAt, endedAt);
            log.warn("batch translate failed spuId={} provider={} sourceUrl={} refs={} err={}", spuId, provider, sourceUrl, fieldRefs, message);
            return TranslationOutcome.failed(sourceUrl, sourceUrl, provider, fieldRefs, message);
        }
    }

    private String applyOutcome(String original, Map<String, TranslationOutcome> outcomeByUrl) {
        String trimmed = trimToNull(original);
        if (trimmed == null) {
            return original;
        }
        TranslationOutcome outcome = outcomeByUrl.get(trimmed);
        if (outcome == null || !outcome.success() || !StringUtils.hasText(outcome.finalUrl())) {
            return original;
        }
        return outcome.finalUrl();
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return "aliyun";
        }
        String value = provider.trim().toLowerCase();
        if ("ali".equals(value) || "aliyun".equals(value)) {
            return "aliyun";
        }
        if ("temu".equals(value)) {
            return "temu";
        }
        return "aliyun";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private List<String> parseJsonStringList(String json) {
        if (!StringUtils.hasText(json)) return new ArrayList<>();
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) return new ArrayList<>();
            List<String> result = new ArrayList<>();
            for (JsonNode item : node) {
                if (item != null && item.isTextual() && StringUtils.hasText(item.asText())) {
                    result.add(item.asText().trim());
                }
            }
            return result;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private String writeJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? Collections.emptyList() : list);
        } catch (Exception e) {
            throw new IllegalStateException("write json failed", e);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private record TranslationOutcome(String sourceUrl,
                                      String finalUrl,
                                      String provider,
                                      List<String> fieldRefs,
                                      boolean success,
                                      String errorMsg) {
        private static TranslationOutcome failed(String sourceUrl,
                                                 String finalUrl,
                                                 String provider,
                                                 List<String> fieldRefs,
                                                 String errorMsg) {
            return new TranslationOutcome(sourceUrl, finalUrl, provider, fieldRefs, false, errorMsg);
        }
    }
}