package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ImportHtmlRequest;
import com.tminos.productscene.dto.ProductCollectionDTO.ProductCollectionResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.ProductCollectionDetailResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.SplitProductRequest;
import com.tminos.productscene.dto.ProductCollectionDTO.SplitProductResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.TemuTitleOptimizationResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.TemuPublishPayloadResponse;
import com.tminos.productscene.dto.ProductCollectionDTO.UpdateProductCollectionRequest;
import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.StabilityFusionDTO;
import com.tminos.productscene.dto.TemuImageDTO;
import com.tminos.productscene.dto.TemuCategoryDTO;
import com.tminos.productscene.dto.TemuSkuDTO;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.service.BatchImageTranslateService;
import com.tminos.productscene.service.ProductCollectionService;
import com.tminos.productscene.service.PostImportAutomationService;
import com.tminos.productscene.service.AliyunImageTranslateService;
import com.tminos.productscene.service.ImageGenerationService;
import com.tminos.productscene.service.TemuImageTranslateService;
import com.tminos.productscene.service.TemuPublishService;
import com.tminos.productscene.service.TemuSkuService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/product-collections")
public class ProductCollectionController {

    private final ProductCollectionService service;
    private final PostImportAutomationService postImportAutomationService;
    private final TemuImageTranslateService temuImageTranslateService;
    private final AliyunImageTranslateService aliyunImageTranslateService;
    private final BatchImageTranslateService batchImageTranslateService;
    private final ImageGenerationService imageGenerationService;
    private final TemuSkuService temuSkuService;
    private final TemuPublishService temuPublishService;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(ProductCollectionController.class);

    public ProductCollectionController(ProductCollectionService service,
                                       PostImportAutomationService postImportAutomationService,
                                       TemuImageTranslateService temuImageTranslateService,
                                       AliyunImageTranslateService aliyunImageTranslateService,
                                       BatchImageTranslateService batchImageTranslateService,
                                       ImageGenerationService imageGenerationService,
                                       TemuSkuService temuSkuService,
                                       TemuPublishService temuPublishService,
                                       ObjectMapper objectMapper) {
        this.service = service;
        this.postImportAutomationService = postImportAutomationService;
        this.temuImageTranslateService = temuImageTranslateService;
        this.aliyunImageTranslateService = aliyunImageTranslateService;
        this.batchImageTranslateService = batchImageTranslateService;
        this.imageGenerationService = imageGenerationService;
        this.temuSkuService = temuSkuService;
        this.temuPublishService = temuPublishService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{id}/images/fuse")
    public ResponseEntity<ApiResponse<StabilityFusionDTO.FuseResponse>> fuseImages(
            @PathVariable Long id,
            @RequestBody StabilityFusionDTO.FuseRequest req
    ) {
        service.get(id);
        try {
            StabilityFusionDTO.FuseResponse out = imageGenerationService.fuseImagesWithStability(req);
            return ResponseEntity.ok(ApiResponse.success("OK", out));
        } catch (Exception e) {
            String em = e.getMessage();
            if (em == null || em.isBlank()) {
                em = e.getClass().getSimpleName();
            }
            return ResponseEntity.ok(ApiResponse.error(em));
        }
    }


    // HTML import endpoint (browser plugin)
    @PostMapping(value = "/import", consumes = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ApiResponse<String>> importHtml(
            @RequestBody ImportHtmlRequest request
    ) {
        String htmlContent = request == null ? null : request.getHtml();
        String extractedJson = request == null ? null : request.getExtractedJson();

        try {
            if (htmlContent == null || htmlContent.trim().isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error("HTML content is required"));
            }

            ProductCollection saved = service.importFromHtml(htmlContent, extractedJson);

            // Do not trigger post-import automation here.
            // A global polling worker will pick up execStatus=0 items and process them one-by-one.
            return ResponseEntity.ok(ApiResponse.success(
                    "Imported",
                    "id=" + saved.getId() + ", productId=" + saved.getProductId()
            ));
        } catch (Exception e) {
            log.error("HTML import failed", e);
            return ResponseEntity.ok(ApiResponse.error("Failed: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductCollectionResponse>>> list(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "sourcePlatform", required = false) String sourcePlatform,
            @RequestParam(value = "collectionStatus", required = false) Integer collectionStatus,
            @RequestParam(value = "showDeleted", required = false) Boolean showDeleted,
            @RequestParam(value = "temuCatid", required = false) String temuCatid,
            @RequestParam(value = "moqMin", required = false) Integer moqMin,
            @RequestParam(value = "moqMax", required = false) Integer moqMax,
            @RequestParam(value = "carouselImageCountMin", required = false) Integer carouselImageCountMin,
            @RequestParam(value = "carouselImageCountMax", required = false) Integer carouselImageCountMax,
            @RequestParam(value = "detailImageCountMin", required = false) Integer detailImageCountMin,
            @RequestParam(value = "detailImageCountMax", required = false) Integer detailImageCountMax,
            @RequestParam(value = "skuCountMin", required = false) Integer skuCountMin,
            @RequestParam(value = "skuCountMax", required = false) Integer skuCountMax,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(
                q,
                sourcePlatform,
                collectionStatus,
                showDeleted,
                temuCatid,
                moqMin,
                moqMax,
                carouselImageCountMin,
                carouselImageCountMax,
                detailImageCountMin,
                detailImageCountMax,
                skuCountMin,
                skuCountMax,
                page,
                size
        )));
    }

    @GetMapping("/temu-categories")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> listTemuCategories(
            @RequestParam(value = "showDeleted", required = false) Boolean showDeleted
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.listTemuCategories(showDeleted)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductCollectionDetailResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.getDetail(id)));
    }

    @GetMapping("/{id}/temu-publish-payload")
    public ResponseEntity<ApiResponse<TemuPublishPayloadResponse>> getTemuPublishPayload(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.getTemuPublishPayload(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductCollection>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductCollectionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Updated", service.update(id, request)));
    }

    @PostMapping("/{id}/split")
    public ResponseEntity<ApiResponse<SplitProductResponse>> split(
            @PathVariable Long id,
            @Valid @RequestBody SplitProductRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Split success", service.splitProduct(id, request)));
    }

    @PostMapping("/{id}/post-import/requeue")
    public ResponseEntity<ApiResponse<Void>> requeuePostImportTask(
            @PathVariable Long id,
            @RequestParam(name = "force", defaultValue = "false") boolean force
    ) {
        service.requeuePostImportTask(id, force);
        return ResponseEntity.ok(ApiResponse.success("Re-queued", null));
    }

    @PostMapping("/{id}/temu-category/match")
    public ResponseEntity<ApiResponse<TemuCategoryDTO.MatchCategoryResponse>> matchTemuCategory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.matchTemuCategory(id)));
    }

    @PostMapping("/{id}/temu-title-optimizer/generate")
    public ResponseEntity<ApiResponse<TemuTitleOptimizationResponse>> generateTemuTitleOptimization(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.generateTemuTitleOptimization(id)));
    }

    @PostMapping("/{id}/temu-category/attributes")
    public ResponseEntity<ApiResponse<String>> getTemuCategoryAttributes(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.getTemuCategoryAttributesRaw(id)));
    }

    @PostMapping("/{id}/temu-category/save")
    public ResponseEntity<ApiResponse<Void>> saveTemuCategory(
            @PathVariable Long id,
            @RequestBody TemuCategoryDTO.SaveTemuCategoryRequest req
    ) {
        String cid = req == null ? null : req.getTemuCatid();
        String cname = req == null ? null : req.getTemuCatname();
        service.saveTemuCategory(id, cid, cname);
        return ResponseEntity.ok(ApiResponse.success("Saved", null));
    }

    @PostMapping("/{id}/temu-attributes/save")
    public ResponseEntity<ApiResponse<Void>> saveTemuAttributes(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> payload
    ) {
        String json = null;
        try {
            if (payload != null) {
                Object v = payload.get("temuAttributes");
                if (v instanceof String s) {
                    json = s;
                } else if (v != null) {
                    json = objectMapper.writeValueAsString(v);
                }
            }
        } catch (Exception ignored) {
        }
        service.saveTemuAttributes(id, json);
        return ResponseEntity.ok(ApiResponse.success("Saved", null));
    }

    @PostMapping("/{id}/temu-attributes/ai-fill")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> aiFillTemuAttributes(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.aiFillTemuAttributes(id)));
    }

    @GetMapping("/{id}/temu/skus")
    public ResponseEntity<ApiResponse<java.util.List<TemuSkuDTO.TemuSkuRow>>> listTemuSkus(@PathVariable Long id) {
        service.get(id);
        return ResponseEntity.ok(ApiResponse.success(temuSkuService.list(id)));
    }

    @PostMapping("/{id}/temu/skus/init")
    public ResponseEntity<ApiResponse<java.util.List<TemuSkuDTO.TemuSkuRow>>> initTemuSkus(
            @PathVariable Long id,
            @RequestBody(required = false) TemuSkuDTO.InitTemuSkusRequest req
    ) {
        service.get(id);
        boolean force = req != null && Boolean.TRUE.equals(req.getForce());
        return ResponseEntity.ok(ApiResponse.success(temuSkuService.initFromOrigin(id, force)));
    }

    @PostMapping("/{id}/temu/skus/save")
    public ResponseEntity<ApiResponse<java.util.List<TemuSkuDTO.TemuSkuRow>>> saveTemuSkus(
            @PathVariable Long id,
            @RequestBody TemuSkuDTO.SaveTemuSkusRequest req
    ) {
        service.get(id);
        java.util.List<TemuSkuDTO.TemuSkuRow> rows = req == null ? null : req.getSkus();
        return ResponseEntity.ok(ApiResponse.success(temuSkuService.saveAll(id, rows)));
    }

    @PostMapping("/{id}/temu/skus/image/upload")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> uploadTemuSkuImage(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> payload
    ) {
        service.get(id);
        String imageUrl = payload == null ? null : (payload.get("imageUrl") == null ? null : String.valueOf(payload.get("imageUrl")));
        try {
            TemuImageTranslateService.UploadResult r = temuImageTranslateService.uploadGlobalImageByUrl(imageUrl);
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("originalUrl", r.originalUrl());
            out.put("imageUrl", r.uploadedUrl());
            return ResponseEntity.ok(ApiResponse.success(out));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/temu/images/kwcdn-replace")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> replaceImagesToKwcdn(@PathVariable Long id) {
        service.get(id);
        try {
            java.util.Map<String, Object> out = temuImageTranslateService.replaceProductImagesToKwcdn(id);
            return ResponseEntity.ok(ApiResponse.success(out));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Replace failed: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/temu/images/normalize-800")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> normalizeAllImagesTo800(@PathVariable Long id) {
        service.get(id);
        try {
            java.util.Map<String, Object> out = temuImageTranslateService.normalizeAllImagesToTemu800(id);
            return ResponseEntity.ok(ApiResponse.success(out));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Normalize failed: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/images/translate-all")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> translateAllImages(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> payload
    ) {
        service.get(id);
        try {
            String provider = payload == null || payload.get("provider") == null
                    ? null
                    : String.valueOf(payload.get("provider"));
            java.util.Map<String, Object> out = batchImageTranslateService.translateAllImages(id, provider);
            return ResponseEntity.ok(ApiResponse.success(out));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Batch translate failed: " + e.getMessage()));
        }
    }

    @PostMapping({"/{id}/image/translate", "/{id}/temu/image/translate"})
    public ResponseEntity<ApiResponse<TemuImageDTO.TranslateImageResponse>> translateImage(
            @PathVariable Long id,
            @RequestBody TemuImageDTO.TranslateImageRequest req
    ) {
        // Ensure the product collection exists (useful for debugging and consistent UX)
        service.get(id);
        try {
            String provider = req == null ? null : req.getProvider();
            if (provider == null || provider.isBlank()) {
                provider = "temu";
            } else {
                provider = provider.trim().toLowerCase();
            }

            String imageUrl = req == null ? null : req.getImageUrl();
            String sourceLang = req == null ? null : req.getSourceLanguage();
            String targetLang = req == null ? null : req.getTargetLang();
            Boolean containDetail = req == null ? null : req.getContainDetail();
            Boolean uploadToOss = req == null ? null : req.getUploadToOss();

            TemuImageTranslateService.Result r;
            if ("aliyun".equals(provider) || "ali".equals(provider)) {
                if (aliyunImageTranslateService == null) {
                    throw new IllegalStateException("Aliyun translator not configured");
                }
                boolean doUpload = uploadToOss != null && uploadToOss;
                boolean withoutText = containDetail != null && !containDetail;

                AliyunImageTranslateService.Result ar = aliyunImageTranslateService.translateImageByUrl(
                        imageUrl,
                        sourceLang,
                        targetLang,
                        withoutText,
                        doUpload
                );
                r = new TemuImageTranslateService.Result(
                        ar.originalUrl(),
                        ar.originalUrl(),
                        null,
                        ar.translatedUrl(),
                        ar.storedUrl()
                );
            } else if ("temu".equals(provider)) {
                r = temuImageTranslateService.translateGlobalImage(
                        imageUrl,
                        sourceLang,
                        targetLang,
                        containDetail,
                        req == null ? null : req.getScene(),
                        uploadToOss
                );
            } else {
                throw new IllegalArgumentException("Unknown provider: " + provider);
            }

            TemuImageDTO.TranslateImageResponse out = new TemuImageDTO.TranslateImageResponse(
                    r.originalUrl(),
                    r.uploadedUrl(),
                    r.taskId(),
                    r.translatedUrl(),
                    r.storedUrl(),
                    "OK"
            );

            return ResponseEntity.ok(ApiResponse.success("OK", out));
        } catch (Exception e) {
            String em = e.getMessage();
            if (em == null || em.isBlank()) {
                em = e.getClass().getSimpleName();
            }
            return ResponseEntity.ok(ApiResponse.error(em));
        }
    }

    @PostMapping("/{id}/temu/publish")
    public ResponseEntity<ApiResponse<com.tminos.productscene.dto.TemuPublishDTO.PublishResponse>> publishToTemu(@PathVariable Long id) {
        try {
            com.tminos.productscene.dto.TemuPublishDTO.PublishResponse r = temuPublishService.publish(id);
            if (r != null && Boolean.TRUE.equals(r.getSuccess())) {
                return ResponseEntity.ok(ApiResponse.success("OK", r));
            }
            String msg = r == null ? "Publish failed" : (r.getMessage() == null ? "Publish failed" : r.getMessage());
            if (r != null && Boolean.TRUE.equals(r.getBlockedByMainSaleSpec()) && r.getMainSaleSpecTaskId() != null) {
                String taskToken = "taskId=" + r.getMainSaleSpecTaskId();
                if (!msg.contains(taskToken)) {
                    msg = msg + " (" + taskToken + ")";
                }
            }
            if (r != null && r.getRunId() != null) {
                msg = msg + " (runId=" + r.getRunId() + ")";
            }
            return ResponseEntity.ok(ApiResponse.error(msg));
        } catch (Exception e) {
            log.error("TEMU publish failed id={}", id, e);
            String em = e.getMessage();
            if (em == null || em.isBlank()) {
                em = e.getClass().getSimpleName();
            }
            return ResponseEntity.ok(ApiResponse.error("Publish failed: " + em));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.deleteHard(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
