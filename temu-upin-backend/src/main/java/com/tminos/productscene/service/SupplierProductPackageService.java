package com.tminos.productscene.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.SupplierProductPackageDTO;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.SupplierProductPackage;
import com.tminos.productscene.entity.SupplierProductSubmission;
import com.tminos.productscene.repository.ProductCollectionRepository;
import com.tminos.productscene.repository.SupplierProductPackageRepository;
import com.tminos.productscene.repository.SupplierProductSubmissionRepository;
import com.tminos.productscene.util.TextAiUrlHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SupplierProductPackageService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final SupplierProductPackageRepository packageRepository;
    private final SupplierProductSubmissionRepository submissionRepository;
    private final ProductCollectionRepository productCollectionRepository;
    private final ImageGenerationService imageGenerationService;
    private final TextAiChannelResolver textAiChannelResolver;
    private final TargetShopBindingService targetShopBindingService;
    @Qualifier("aiLongRestTemplate")
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public SupplierProductPackage createForSubmission(SupplierProductSubmission submission) {
        if (submission == null || submission.getId() == null) {
            throw new IllegalArgumentException("供应商提品不能为空");
        }
        return packageRepository.findBySubmissionId(submission.getId()).orElseGet(() -> packageRepository.save(
                SupplierProductPackage.builder()
                        .submissionId(submission.getId())
                        .status("PENDING")
                        .build()
        ));
    }

    @Transactional
    public void deleteBySubmissionId(Long submissionId) {
        if (submissionId != null) {
            packageRepository.deleteBySubmissionId(submissionId);
        }
    }

    @Transactional(readOnly = true)
    public Page<SupplierProductPackageDTO.Item> list(String keyword, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Specification<SupplierProductPackage> spec = (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (StringUtils.hasText(status)) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status.trim()));
            }
            if (StringUtils.hasText(keyword)) {
                List<Long> submissionIds = submissionRepository.findAll().stream()
                        .filter(item -> contains(item.getSupplierName(), keyword)
                                || contains(item.getProductName(), keyword)
                                || contains(item.getRemark(), keyword))
                        .map(SupplierProductSubmission::getId)
                        .filter(Objects::nonNull)
                        .toList();
                if (submissionIds.isEmpty()) {
                    predicate = cb.and(predicate, cb.disjunction());
                } else {
                    predicate = cb.and(predicate, root.get("submissionId").in(submissionIds));
                }
            }
            return predicate;
        };
        return packageRepository.findAll(
                spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"))
        ).map(this::toItemAllowMissingSubmission);
    }

    @Transactional
    public SupplierProductPackageDTO.Item generate(Long id) {
        SupplierProductPackage pack = loadPackage(id);
        SupplierProductSubmission submission = loadSubmission(pack.getSubmissionId());
        pack.setStatus("GENERATING");
        pack.setLastError(null);
        packageRepository.save(pack);

        try {
            String title = generateTitle(submission);
            List<String> sourceImages = readStringList(submission.getImageUrlsJson());
            String sourceImage = sourceImages.isEmpty() ? null : sourceImages.get(0);
            List<String> prompts = buildImagePrompts(submission, title);
            List<String> generatedUrls = new ArrayList<>();
            for (String prompt : prompts) {
                generatedUrls.add(imageGenerationService.generateSupplierProductImage(prompt, sourceImage, 800, 800));
            }
            pack.setAiTitle(title);
            pack.setAiTitleZh(submission.getProductName());
            pack.setPromptJson(writeJson(prompts));
            pack.setGeneratedImageUrlsJson(writeJson(generatedUrls));
            pack.setStatus("GENERATED");
            pack.setLastError(null);
        } catch (Exception e) {
            pack.setStatus("FAILED");
            pack.setLastError(trimToMax(e.getMessage(), 2000));
        }
        return toItem(packageRepository.save(pack));
    }

    @Transactional
    public SupplierProductPackageDTO.Item pushToProductCollection(Long id, SupplierProductPackageDTO.PushRequest request) {
        SupplierProductPackage pack = loadPackage(id);
        TargetShopBindingService.TargetShopBinding targetShopBinding = targetShopBindingService.resolve(
                request == null ? null : request.getTargetShopIds()
        );
        if (targetShopBinding.shopIds().isEmpty()) {
            throw new IllegalArgumentException("推送到采集商品库前请选择目标店铺");
        }
        if (pack.getProductCollectionId() != null) {
            Optional<ProductCollection> existing = productCollectionRepository.findById(pack.getProductCollectionId());
            if (existing.isPresent() && !Boolean.TRUE.equals(existing.get().getDeleted())) {
                ProductCollection existingProduct = existing.get();
                existingProduct.setTargetShopIds(targetShopBindingService.toJson(targetShopBinding.shopIds()));
                existingProduct.setTargetShopNames(targetShopBindingService.toJson(targetShopBinding.shopNames()));
                existingProduct.setUpdatedAt(LocalDateTime.now());
                productCollectionRepository.save(existingProduct);
                return toItem(pack);
            }
            pack.setProductCollectionId(null);
        }
        SupplierProductSubmission submission = loadSubmission(pack.getSubmissionId());
        List<String> images = readStringList(pack.getGeneratedImageUrlsJson());
        if (images.isEmpty()) {
            images = readStringList(submission.getImageUrlsJson());
        }
        if (images.isEmpty()) {
            throw new IllegalArgumentException("没有可推送的图片");
        }
        LocalDateTime now = LocalDateTime.now();
        String productId = "SUP-" + submission.getId();
        ProductCollection product = ProductCollection.builder()
                .createdAt(now)
                .updatedAt(now)
                .deleted(false)
                .version(0)
                .productId(productId)
                .alibabaProductId(productId)
                .productName(firstNonBlank(pack.getAiTitle(), submission.getProductName()))
                .productMainImage(images.get(0))
                .carouselImages(writeJson(images))
                .detailImages(writeJson(images))
                .sourcePlatform("SUPPLIER")
                .targetShopIds(targetShopBindingService.toJson(targetShopBinding.shopIds()))
                .targetShopNames(targetShopBindingService.toJson(targetShopBinding.shopNames()))
                .companyName(submission.getSupplierName())
                .companyLocation(submission.getSupplierAddress())
                .shippingLocation(submission.getSupplierAddress())
                .minPrice(submission.getSupplyPrice())
                .maxPrice(submission.getSupplyPrice())
                .netWeight(submission.getWeightG())
                .packagingWeight(submission.getWeightG())
                .packagingLength(submission.getLengthCm())
                .packagingWidth(submission.getWidthCm())
                .packagingHeight(submission.getHeightCm())
                .collectionStatus(0)
                .execStatus(0)
                .temuPublished(false)
                .originalContent(buildOriginalContent(submission, pack))
                .skuData(buildSkuData(submission))
                .skuModel(buildSkuData(submission))
                .build();
        ProductCollection saved = productCollectionRepository.save(product);
        pack.setProductCollectionId(saved.getId());
        pack.setStatus("PUSHED");
        submission.setStatus("PUSHED");
        submissionRepository.save(submission);
        return toItem(packageRepository.save(pack));
    }

    private String generateTitle(SupplierProductSubmission submission) throws Exception {
        TextAiChannelResolver.ResolvedChannel channel = textAiChannelResolver.resolve(
                TextAiBusinessCodes.SUPPLIER_PRODUCT_PACKAGING,
                null,
                null,
                "gpt-5.5"
        );
        if (!StringUtils.hasText(channel.getBaseUrl()) || !StringUtils.hasText(channel.getApiKey())) {
            return fallbackTitle(submission);
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", StringUtils.hasText(channel.getModel()) ? channel.getModel() : "gpt-5.5");
        req.put("stream", false);
        req.put("temperature", 0.4);
        req.put("messages", List.of(
                Map.of("role", "system", "content", "Return strict JSON only. Generate safe non-brand e-commerce title for the US market."),
                Map.of("role", "user", "content", "Product name: " + nullToEmpty(submission.getProductName())
                        + "\nSupplier note: " + nullToEmpty(submission.getRemark())
                        + "\nGenerate JSON: {\"title\":\"English title under 160 chars\"}. Avoid brand/IP/medical claims.")
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(channel.getApiKey().trim());
        ResponseEntity<String> response = restTemplate.exchange(
                TextAiUrlHelper.chatCompletionsUrl(channel.getBaseUrl(), channel.getBaseUrl()),
                HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(req), headers),
                String.class
        );
        String content = extractAssistantContent(response.getBody());
        if (StringUtils.hasText(content)) {
            String stripped = stripCodeFence(content);
            JsonNode node = objectMapper.readTree(stripped);
            String title = node.path("title").asText(null);
            if (StringUtils.hasText(title)) {
                return title.trim();
            }
        }
        return fallbackTitle(submission);
    }

    private List<String> buildImagePrompts(SupplierProductSubmission submission, String title) {
        String base = "Create an 800x800 photorealistic e-commerce image for the US market. "
                + "Use the reference product image to keep the product shape, color, material and proportions accurate. "
                + "No text, no watermark, no logo, no brand. Product: " + firstNonBlank(title, submission.getProductName()) + ". "
                + "Supplier note: " + nullToEmpty(submission.getRemark()) + ". ";
        return List.of(
                base + "Clean white background hero product photo, centered, premium marketplace main image.",
                base + "Lifestyle scene in a modern American home, natural daylight, realistic use context.",
                base + "Close-up detail image showing material, texture and practical quality, studio lighting.",
                base + "Clean composition that visually communicates size and everyday use, no text labels.",
                base + "Attractive carousel image with product arranged neatly in a real-life US shopping context."
        );
    }

    private SupplierProductPackageDTO.Item toItem(SupplierProductPackage pack) {
        SupplierProductSubmission submission = loadSubmission(pack.getSubmissionId());
        return toItem(pack, submission);
    }

    private SupplierProductPackageDTO.Item toItemAllowMissingSubmission(SupplierProductPackage pack) {
        Optional<SupplierProductSubmission> submission = submissionRepository.findById(pack.getSubmissionId());
        return submission.map(value -> toItem(pack, value)).orElseGet(() -> SupplierProductPackageDTO.Item.builder()
                .id(pack.getId())
                .submissionId(pack.getSubmissionId())
                .supplierName("已删除")
                .productName("供应商提品已删除: " + pack.getSubmissionId())
                .generatedImageUrls(readStringList(pack.getGeneratedImageUrlsJson()))
                .aiTitle(pack.getAiTitle())
                .aiTitleZh(pack.getAiTitleZh())
                .status("ORPHANED")
                .lastError(firstNonBlank(pack.getLastError(), "关联的供应商提品已删除"))
                .productCollectionId(pack.getProductCollectionId())
                .createdAt(pack.getCreatedAt())
                .updatedAt(pack.getUpdatedAt())
                .build());
    }

    private SupplierProductPackageDTO.Item toItem(SupplierProductPackage pack, SupplierProductSubmission submission) {
        return SupplierProductPackageDTO.Item.builder()
                .id(pack.getId())
                .submissionId(pack.getSubmissionId())
                .supplierName(submission.getSupplierName())
                .productName(submission.getProductName())
                .remark(submission.getRemark())
                .supplyPrice(submission.getSupplyPrice())
                .weightG(submission.getWeightG())
                .lengthCm(submission.getLengthCm())
                .widthCm(submission.getWidthCm())
                .heightCm(submission.getHeightCm())
                .sourceImageUrls(readStringList(submission.getImageUrlsJson()))
                .aiTitle(pack.getAiTitle())
                .aiTitleZh(pack.getAiTitleZh())
                .generatedImageUrls(readStringList(pack.getGeneratedImageUrlsJson()))
                .status(pack.getStatus())
                .lastError(pack.getLastError())
                .productCollectionId(pack.getProductCollectionId())
                .createdAt(pack.getCreatedAt())
                .updatedAt(pack.getUpdatedAt())
                .build();
    }

    private SupplierProductPackage loadPackage(Long id) {
        return packageRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("AI商品包装记录不存在: " + id));
    }

    private SupplierProductSubmission loadSubmission(Long id) {
        return submissionRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("供应商提品不存在: " + id));
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON保存失败: " + e.getMessage(), e);
        }
    }

    private String buildSkuData(SupplierProductSubmission submission) {
        Map<String, Object> sku = new LinkedHashMap<>();
        sku.put("skuId", "SUP-SKU-" + submission.getId());
        sku.put("price", submission.getSupplyPrice());
        sku.put("stock", 999);
        sku.put("weightG", submission.getWeightG());
        sku.put("lengthCm", submission.getLengthCm());
        sku.put("widthCm", submission.getWidthCm());
        sku.put("heightCm", submission.getHeightCm());
        return writeJson(List.of(sku));
    }

    private String buildOriginalContent(SupplierProductSubmission submission, SupplierProductPackage pack) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("source", "supplier_product_submission");
        data.put("submissionId", submission.getId());
        data.put("supplierName", submission.getSupplierName());
        data.put("supplierPhone", submission.getSupplierPhone());
        data.put("supplierAddress", submission.getSupplierAddress());
        data.put("productName", submission.getProductName());
        data.put("aiTitle", pack.getAiTitle());
        data.put("remark", submission.getRemark());
        data.put("supplyPrice", submission.getSupplyPrice());
        data.put("weightG", submission.getWeightG());
        data.put("lengthCm", submission.getLengthCm());
        data.put("widthCm", submission.getWidthCm());
        data.put("heightCm", submission.getHeightCm());
        data.put("sourceImages", readStringList(submission.getImageUrlsJson()));
        data.put("generatedImages", readStringList(pack.getGeneratedImageUrlsJson()));
        return writeJson(data);
    }

    private String fallbackTitle(SupplierProductSubmission submission) {
        return firstNonBlank(submission.getProductName(), "Useful Home Product") + " for Everyday Use";
    }

    private String extractAssistantContent(String raw) throws Exception {
        if (!StringUtils.hasText(raw)) return null;
        JsonNode root = objectMapper.readTree(raw);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) return null;
        return choices.get(0).path("message").path("content").asText(null);
    }

    private String stripCodeFence(String content) {
        if (!StringUtils.hasText(content)) return content;
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstLineBreak = trimmed.indexOf('\n');
        if (firstLineBreak < 0) return trimmed.replace("```", "").trim();
        String body = trimmed.substring(firstLineBreak + 1);
        int endFence = body.lastIndexOf("```");
        if (endFence >= 0) body = body.substring(0, endFence);
        return body.trim();
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value) && StringUtils.hasText(keyword)
                && value.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : (StringUtils.hasText(second) ? second.trim() : null);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimToMax(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
