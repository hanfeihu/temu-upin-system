package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.config.TemuGoodsConfig;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.ProductCollectionTemuSku;
import com.tminos.productscene.repository.ProductCollectionRepository;
import com.tminos.productscene.repository.ProductCollectionTemuSkuRepository;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import com.tminos.temu.upin.sdk.v2.dto.TemuApiResponse;
import com.tminos.temu.upin.sdk.v2.image.TemuImageV2Client;
import com.tminos.temu.upin.sdk.v2.image.TemuImageV2Client.ImageTranslateQueryResult;
import com.tminos.temu.upin.sdk.v2.image.TemuImageV2Client.ImageTranslateRequest;
import com.tminos.temu.upin.sdk.v2.image.TemuImageV2Client.ImageTranslateSubmitResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

// same package import not required; keep explicit for clarity

@Service
public class TemuImageTranslateService {

    private static final Logger log = LoggerFactory.getLogger(TemuImageTranslateService.class);

    private final TemuGoodsConfig temuGoodsConfig;
    private final OssService ossService;
    private final ObjectMapper objectMapper;
    private final ProductCollectionService productCollectionService;
    private final ProductCollectionRepository productCollectionRepository;
    private final TemuImageNormalizeService imageNormalizeService;
    private final TemuImageMetaService temuImageMetaService;
    private final Executor globalWorkerExecutor;
    private final AliyunImageTranslateService aliyunImageTranslateService;
    private final TemuOpenApiCredentialService temuOpenApiCredentialService;
    private final ProductCollectionTemuSkuRepository temuSkuRepository;

    public TemuImageTranslateService(TemuGoodsConfig temuGoodsConfig,
                                     OssService ossService,
                                     ObjectMapper objectMapper,
                                     ProductCollectionService productCollectionService,
                                     ProductCollectionRepository productCollectionRepository,
                                     TemuImageNormalizeService imageNormalizeService,
                                     TemuImageMetaService temuImageMetaService,
                                     @org.springframework.beans.factory.annotation.Qualifier("globalWorkerExecutor") Executor globalWorkerExecutor,
                                     AliyunImageTranslateService aliyunImageTranslateService,
                                     TemuOpenApiCredentialService temuOpenApiCredentialService,
                                     ProductCollectionTemuSkuRepository temuSkuRepository) {
        this.temuGoodsConfig = temuGoodsConfig;
        this.ossService = ossService;
        this.objectMapper = objectMapper;
        this.productCollectionService = productCollectionService;
        this.productCollectionRepository = productCollectionRepository;
        this.imageNormalizeService = imageNormalizeService;
        this.temuImageMetaService = temuImageMetaService;
        this.globalWorkerExecutor = globalWorkerExecutor;
        this.aliyunImageTranslateService = aliyunImageTranslateService;
        this.temuOpenApiCredentialService = temuOpenApiCredentialService;
        this.temuSkuRepository = temuSkuRepository;
    }

    public Result translateGlobalImage(String imageUrl,
                                      String sourceLanguage,
                                      String targetLang,
                                      Boolean containDetail,
                                      String scene,
                                      Boolean uploadToOss) throws Exception {

        if (!StringUtils.hasText(imageUrl)) {
            throw new IllegalArgumentException("imageUrl is required");
        }

        String srcLang = StringUtils.hasText(sourceLanguage) ? sourceLanguage.trim() : "zh";
        String tgtLang = StringUtils.hasText(targetLang) ? targetLang.trim() : "en";
        boolean contain = containDetail == null || containDetail;
        boolean doUploadToOss = uploadToOss != null && uploadToOss;

        TemuImageV2Client client = new TemuImageV2Client(buildTemuCreds());

        // 1) upload image by url (TEMU wants its own hosted url)
        String uploadRaw = client.uploadGlobalImageByUrlRaw(imageUrl.trim(), null, null);
        String uploadedUrl = extractNestedText(uploadRaw, "result", "imageUrl");
        if (!StringUtils.hasText(uploadedUrl)) {
            uploadedUrl = extractNestedText(uploadRaw, "result", "url");
        }
        if (!StringUtils.hasText(uploadedUrl)) {
            uploadedUrl = imageUrl.trim();
        }

        // 2) submit translate task
        ImageTranslateRequest req = new ImageTranslateRequest();
        req.setTargetLang(tgtLang);
        req.setImageUrl(uploadedUrl);
        req.setCustomTaskId(System.currentTimeMillis());
        req.setIsContainDetail(contain);
        req.setLanguage(srcLang);
        if (StringUtils.hasText(scene)) {
            req.setScene(scene.trim());
        }

        TemuApiResponse<ImageTranslateSubmitResult> submit = client.translateGlobalImage(req);

        // Important: "success=true" only means the API call succeeded.
        // Business success is decided by result.resultCode (1000000 means ok).
        Integer resultCode = null;
        String resultMsg = null;
        if (submit != null && submit.getResult() != null) {
            resultCode = submit.getResult().getResultCode();
            resultMsg = submit.getResult().getResultMsg();
        }

        if (resultCode != null && resultCode != 1000000) {
            String msg = StringUtils.hasText(resultMsg) ? resultMsg : ("Translate submit failed, resultCode=" + resultCode);
            throw new IllegalStateException(msg);
        }

        String taskId = submit == null || submit.getResult() == null ? null : submit.getResult().getTaskId();
        if (!StringUtils.hasText(taskId)) {
            // try raw extraction as fallback (and read resultCode/resultMsg)
            String raw = client.translateGlobalImageRaw(req);
            String rc = extractNestedText(raw, "result", "resultCode");
            String rm = extractNestedText(raw, "result", "resultMsg");
            try {
                if (StringUtils.hasText(rc)) {
                    int c = Integer.parseInt(rc.trim());
                    if (c != 1000000) {
                        String msg = StringUtils.hasText(rm) ? rm : ("Translate submit failed, resultCode=" + c);
                        throw new IllegalStateException(msg);
                    }
                }
            } catch (NumberFormatException ignored) {
            }

            taskId = extractNestedText(raw, "result", "taskId");
            if (!StringUtils.hasText(taskId)) {
                taskId = extractNestedText(raw, "taskId");
            }
        }
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalStateException("Translate taskId not found");
        }

        // 3) poll result
        int pollIntervalMs = temuGoodsConfig.getTranslatePollIntervalMs() != null
                ? Math.max(200, temuGoodsConfig.getTranslatePollIntervalMs())
                : 2500;
        int maxAttempts = temuGoodsConfig.getTranslatePollMaxAttempts() != null
                ? Math.max(1, temuGoodsConfig.getTranslatePollMaxAttempts())
                : 80;

        String translatedUrl = null;
        for (int i = 0; i < maxAttempts; i++) {
            TemuApiResponse<ImageTranslateQueryResult> q = client.getTranslateGlobalImageResult(taskId);
            ImageTranslateQueryResult r = q == null ? null : q.getResult();
            if (r != null) {
                if (StringUtils.hasText(r.getImageResultUrl())) {
                    translatedUrl = r.getImageResultUrl().trim();
                } else if (StringUtils.hasText(r.getImageUrl())) {
                    translatedUrl = r.getImageUrl().trim();
                }
            }
            if (StringUtils.hasText(translatedUrl)) {
                break;
            }
            Thread.sleep(pollIntervalMs);
        }

        if (!StringUtils.hasText(translatedUrl)) {
            throw new IllegalStateException("Translate polling timeout");
        }

        String storedUrl = translatedUrl;
        if (doUploadToOss && isOssEnabled()) {
            try {
                storedUrl = downloadAndUpload(translatedUrl);
            } catch (Exception ignored) {
                storedUrl = translatedUrl;
            }
        }

        return new Result(imageUrl.trim(), uploadedUrl, taskId, translatedUrl, storedUrl);
    }

    public UploadResult uploadGlobalImageByUrl(String imageUrl) throws Exception {
        if (!StringUtils.hasText(imageUrl)) {
            throw new IllegalArgumentException("imageUrl is required");
        }
        TemuImageV2Client client = new TemuImageV2Client(buildTemuCreds());
        String raw = client.uploadGlobalImageByUrlRaw(imageUrl.trim(), null, null);
        String uploadedUrl = extractNestedText(raw, "result", "imageUrl");
        if (!StringUtils.hasText(uploadedUrl)) {
            uploadedUrl = extractNestedText(raw, "result", "url");
        }
        if (!StringUtils.hasText(uploadedUrl)) {
            // fall back to original
            uploadedUrl = imageUrl.trim();
        }
        return new UploadResult(imageUrl.trim(), uploadedUrl, raw);
    }

    public Map<String, Object> replaceProductImagesToKwcdn(Long spuId) throws Exception {
        if (spuId == null) throw new IllegalArgumentException("spuId is required");
        ProductCollection pc = productCollectionService.get(spuId);

        String main = pc.getProductMainImage();
        List<String> carousel = parseJsonStringList(pc.getCarouselImages());
        List<String> detail = parseJsonStringList(pc.getDetailImages());

        List<Map<String, Object>> changes = new ArrayList<>();
        boolean changed = false;

        String newMain = replaceOneIfNeeded(main, "productMainImage", changes);
        if (!Objects.equals(main, newMain)) {
            pc.setProductMainImage(newMain);
            changed = true;
        }

        List<String> newCarousel = new ArrayList<>();
        for (int i = 0; i < carousel.size(); i++) {
            String u = carousel.get(i);
            String nu = replaceOneIfNeeded(u, "carouselImages[" + i + "]", changes);
            newCarousel.add(nu);
            if (!Objects.equals(u, nu)) changed = true;
        }

        List<String> newDetail = new ArrayList<>();
        for (int i = 0; i < detail.size(); i++) {
            String u = detail.get(i);
            String nu = replaceOneIfNeeded(u, "detailImages[" + i + "]", changes);
            newDetail.add(nu);
            if (!Objects.equals(u, nu)) changed = true;
        }

        if (changed) {
            pc.setCarouselImages(writeJson(newCarousel));
            pc.setDetailImages(writeJson(newDetail));
            productCollectionRepository.save(pc);
        }

        // --- SKU images ---
        int skuChanged = 0;
        List<ProductCollectionTemuSku> temuSkus = temuSkuRepository.findBySpuIdOrderByIdAsc(spuId);
        for (ProductCollectionTemuSku sku : temuSkus) {
            String skuImg = sku.getImage();
            if (!StringUtils.hasText(skuImg)) continue;
            String newSkuImg = replaceOneIfNeeded(skuImg, "temuSku[" + sku.getId() + "].image", changes);
            if (!Objects.equals(skuImg, newSkuImg)) {
                sku.setImage(newSkuImg);
                skuChanged++;
            }
        }
        if (skuChanged > 0) {
            temuSkuRepository.saveAll(temuSkus);
            changed = true;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("spuId", spuId);
        out.put("changed", changed);
        out.put("mainImage", pc.getProductMainImage());
        out.put("carouselCount", newCarousel.size());
        out.put("detailCount", newDetail.size());
        out.put("skuImageChanged", skuChanged);
        out.put("changes", changes);
        return out;
    }

    /**
     * Normalize product images for TEMU publishing:
     * - Ensure kwcdn domain
     * - Best-effort convert to 800x800 via base64 upload (falls back to upload-by-url when decode fails)
     * Persist back to DB.
     */
    public Map<String, Object> normalizeProductImagesToTemu800(Long spuId) throws Exception {
        if (spuId == null) throw new IllegalArgumentException("spuId is required");
        ProductCollection pc = productCollectionService.get(spuId);

        int pcAttrLenAtLoad = pc.getTemuAttributes() == null ? 0 : pc.getTemuAttributes().length();
        log.info("normalizeProductImagesToTemu800 spuId={} loadedPc.temuAttributesLen={} thread={}", spuId, pcAttrLenAtLoad, Thread.currentThread().getName());

        String main = pc.getProductMainImage();
        List<String> carousel = parseJsonStringList(pc.getCarouselImages());
        List<String> detail = parseJsonStringList(pc.getDetailImages());

        List<Map<String, Object>> changes = Collections.synchronizedList(new ArrayList<>());

        // Parallelize per-image normalization. Note:
        // - Each image call may hit network + TEMU upload; keep concurrency bounded by globalWorkerExecutor.
        // - Keep field->index mapping stable by collecting futures first.
        Map<String, String> originalByField = new LinkedHashMap<>();
        if (StringUtils.hasText(main)) {
            originalByField.put("productMainImage", main);
        }
        for (int i = 0; i < carousel.size(); i++) {
            if (StringUtils.hasText(carousel.get(i))) {
                originalByField.put("carouselImages[" + i + "]", carousel.get(i));
            }
        }
        for (int i = 0; i < detail.size(); i++) {
            if (StringUtils.hasText(detail.get(i))) {
                originalByField.put("detailImages[" + i + "]", detail.get(i));
            }
        }

        Map<String, CompletableFuture<String>> futures = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : originalByField.entrySet()) {
            String field = e.getKey();
            String url = e.getValue();
            futures.put(field, CompletableFuture.supplyAsync(() -> {
                try {
                    return normalizeOne800(url, field, changes, true);
                } catch (Exception ex) {
                    // keep original on failure
                    return url;
                }
            }, globalWorkerExecutor));
        }

        // Wait for all
        for (CompletableFuture<String> f : futures.values()) {
            try {
                f.get(10, TimeUnit.MINUTES);
            } catch (Exception ignored) {
            }
        }

        String newMain = futures.containsKey("productMainImage") ? futures.get("productMainImage").getNow(main) : main;
        List<String> newCarousel = new ArrayList<>();
        for (int i = 0; i < carousel.size(); i++) {
            String field = "carouselImages[" + i + "]";
            newCarousel.add(futures.containsKey(field) ? futures.get(field).getNow(carousel.get(i)) : carousel.get(i));
        }
        List<String> newDetail = new ArrayList<>();
        for (int i = 0; i < detail.size(); i++) {
            String field = "detailImages[" + i + "]";
            newDetail.add(futures.containsKey(field) ? futures.get(field).getNow(detail.get(i)) : detail.get(i));
        }

        boolean changed = !Objects.equals(main, newMain)
                || !Objects.equals(carousel, newCarousel)
                || !Objects.equals(detail, newDetail);

        if (changed) {
            ProductCollection latest = productCollectionRepository.findById(spuId)
                    .orElseThrow(() -> new IllegalStateException("ProductCollection not found: " + spuId));
            int latestLen = latest.getTemuAttributes() == null ? 0 : latest.getTemuAttributes().length();
            int loadedLen = pc.getTemuAttributes() == null ? 0 : pc.getTemuAttributes().length();
            log.info("normalizeProductImagesToTemu800 spuId={} beforeSave latest.temuAttributesLen={} loadedPc.temuAttributesLen={} latest.execStatus={}",
                    spuId, latestLen, loadedLen, latest.getExecStatus());

            latest.setProductMainImage(newMain);
            latest.setCarouselImages(writeJson(newCarousel));
            latest.setDetailImages(writeJson(newDetail));

            productCollectionRepository.save(latest);

            try {
                ProductCollection latest2 = productCollectionRepository.findById(spuId).orElse(null);
                int latestLen2 = latest2 == null || latest2.getTemuAttributes() == null ? 0 : latest2.getTemuAttributes().length();
                Integer latestExecStatus = latest2 == null ? null : latest2.getExecStatus();
                log.info("normalizeProductImagesToTemu800 spuId={} afterSave latest.temuAttributesLen={} latest.execStatus={}", spuId, latestLen2, latestExecStatus);
            } catch (Exception ex) {
                log.warn("normalizeProductImagesToTemu800 spuId={} afterSave check failed: {}", spuId, ex.getMessage());
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("spuId", spuId);
        out.put("changed", changed);
        out.put("mainImage", newMain);
        out.put("carouselCount", newCarousel.size());
        out.put("detailCount", newDetail.size());
        out.put("changes", changes);
        return out;
    }

    /**
     * Normalize ALL product images (main, carousel, detail, SKU) to 800x800 via stretch.
     * Uses a dedicated 10-thread pool for parallel processing.
     */
    public Map<String, Object> normalizeAllImagesToTemu800(Long spuId) throws Exception {
        if (spuId == null) throw new IllegalArgumentException("spuId is required");
        ProductCollection pc = productCollectionService.get(spuId);
        log.info("normalizeAllImagesToTemu800 spuId={} thread={}", spuId, Thread.currentThread().getName());

        String main = pc.getProductMainImage();
        List<String> carousel = parseJsonStringList(pc.getCarouselImages());
        List<String> detail = parseJsonStringList(pc.getDetailImages());
        List<ProductCollectionTemuSku> temuSkus = temuSkuRepository.findBySpuIdOrderByIdAsc(spuId);

        List<Map<String, Object>> changes = Collections.synchronizedList(new ArrayList<>());

        // Collect all image tasks: field -> url
        Map<String, String> originalByField = new LinkedHashMap<>();
        if (StringUtils.hasText(main)) {
            originalByField.put("productMainImage", main);
        }
        for (int i = 0; i < carousel.size(); i++) {
            if (StringUtils.hasText(carousel.get(i))) {
                originalByField.put("carouselImages[" + i + "]", carousel.get(i));
            }
        }
        for (int i = 0; i < detail.size(); i++) {
            if (StringUtils.hasText(detail.get(i))) {
                originalByField.put("detailImages[" + i + "]", detail.get(i));
            }
        }
        // SKU images
        Map<Long, String> skuImageBySkuId = new LinkedHashMap<>();
        for (ProductCollectionTemuSku sku : temuSkus) {
            if (StringUtils.hasText(sku.getImage())) {
                String field = "temuSku[" + sku.getId() + "].image";
                originalByField.put(field, sku.getImage());
                skuImageBySkuId.put(sku.getId(), sku.getImage());
            }
        }

        // Use a dedicated 20-thread pool
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            Map<String, CompletableFuture<String>> futures = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : originalByField.entrySet()) {
                String field = e.getKey();
                String url = e.getValue();
                futures.put(field, CompletableFuture.supplyAsync(() -> {
                    try {
                        return normalizeOne800(url, field, changes, true);
                    } catch (Exception ex) {
                        log.warn("normalizeAllImagesToTemu800 field={} failed: {}", field, ex.getMessage());
                        return url;
                    }
                }, pool));
            }

            // Wait for all
            for (CompletableFuture<String> f : futures.values()) {
                try {
                    f.get(10, TimeUnit.MINUTES);
                } catch (Exception ignored) {
                }
            }

            // Rebuild results
            String newMain = futures.containsKey("productMainImage") ? futures.get("productMainImage").getNow(main) : main;
            List<String> newCarousel = new ArrayList<>();
            for (int i = 0; i < carousel.size(); i++) {
                String field = "carouselImages[" + i + "]";
                newCarousel.add(futures.containsKey(field) ? futures.get(field).getNow(carousel.get(i)) : carousel.get(i));
            }
            List<String> newDetail = new ArrayList<>();
            for (int i = 0; i < detail.size(); i++) {
                String field = "detailImages[" + i + "]";
                newDetail.add(futures.containsKey(field) ? futures.get(field).getNow(detail.get(i)) : detail.get(i));
            }

            boolean changed = !Objects.equals(main, newMain)
                    || !Objects.equals(carousel, newCarousel)
                    || !Objects.equals(detail, newDetail);

            if (!Objects.equals(main, newMain)) {
                pc.setProductMainImage(newMain);
            }
            if (changed) {
                pc.setCarouselImages(writeJson(newCarousel));
                pc.setDetailImages(writeJson(newDetail));
                productCollectionRepository.save(pc);
            }

            // Update SKU images
            int skuChanged = 0;
            for (ProductCollectionTemuSku sku : temuSkus) {
                String field = "temuSku[" + sku.getId() + "].image";
                if (futures.containsKey(field)) {
                    String original = skuImageBySkuId.get(sku.getId());
                    String newImg = futures.get(field).getNow(original);
                    if (!Objects.equals(original, newImg)) {
                        sku.setImage(newImg);
                        skuChanged++;
                    }
                }
            }
            if (skuChanged > 0) {
                temuSkuRepository.saveAll(temuSkus);
                changed = true;
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("spuId", spuId);
            out.put("changed", changed);
            out.put("mainImage", pc.getProductMainImage());
            out.put("carouselCount", newCarousel.size());
            out.put("detailCount", newDetail.size());
            out.put("skuImageChanged", skuChanged);
            out.put("totalImages", originalByField.size());
            out.put("changes", changes);
            return out;
        } finally {
            pool.shutdown();
        }
    }

    private String normalizeOne800(String url, String field, List<Map<String, Object>> changes, boolean recordMeta) throws Exception {
        String input = url == null ? null : url.trim();
        if (!StringUtils.hasText(input)) return input;

        TemuImageNormalizeService.Result r = imageNormalizeService.normalizeToTemu800(input);
        String out = r == null ? input : r.getUploadedUrl();
        if (!StringUtils.hasText(out)) out = input;

        if (recordMeta) {
            try {
                Integer w = r == null ? null : r.getWidth();
                Integer h = r == null ? null : r.getHeight();
                // record meta for the FINAL url we persist
                if (StringUtils.hasText(out)) {
                    temuImageMetaService.upsert(out, w, h);
                }
            } catch (Exception ignored) {
            }
        }

        if (!Objects.equals(input, out)) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("field", field);
            one.put("original", input);
            one.put("uploaded", out);
            one.put("width", r == null ? null : r.getWidth());
            one.put("height", r == null ? null : r.getHeight());
            one.put("reason", r == null ? null : r.getReason());
            changes.add(one);
        }

        return out;
    }

    private String replaceOneIfNeeded(String url, String field, List<Map<String, Object>> changes) throws Exception {
        String input = url == null ? null : url.trim();
        if (!StringUtils.hasText(input)) return input;
        String host;
        try {
            host = URI.create(input).getHost();
        } catch (Exception ignored) {
            host = null;
        }
        boolean isKwcdn = host != null && host.equalsIgnoreCase("img.kwcdn.com");
        if (isKwcdn) return input;

        UploadResult r = uploadGlobalImageByUrl(input);
        String out = r == null ? input : r.uploadedUrl();
        if (!StringUtils.hasText(out)) out = input;

        Map<String, Object> one = new LinkedHashMap<>();
        one.put("field", field);
        one.put("original", input);
        one.put("uploaded", out);
        changes.add(one);
        return out;
    }

    private List<String> parseJsonStringList(String json) {
        if (!StringUtils.hasText(json)) return new ArrayList<>();
        try {
            JsonNode n = objectMapper.readTree(json);
            if (!n.isArray()) return new ArrayList<>();
            List<String> out = new ArrayList<>();
            for (JsonNode x : n) {
                if (x != null && x.isTextual()) {
                    String s = x.asText();
                    if (StringUtils.hasText(s)) out.add(s.trim());
                }
            }
            return out;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private String writeJson(List<String> list) {
        if (list == null) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    private TemuOpenApiCredentials buildTemuCreds() {
        TemuOpenApiCredentials creds = temuOpenApiCredentialService.getDefaultTemuOpenApiCredentialsOrThrow();
        String token = temuGoodsConfig == null ? null : temuGoodsConfig.getAccessToken();
        if (StringUtils.hasText(token)) {
            creds.setAccessToken(token.trim());
        }
        return creds;
    }

    private boolean isOssEnabled() {
        return ossService != null && ossService.isEnabled();
    }

    private String downloadAndUpload(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Download translated image failed: HTTP " + resp.statusCode());
        }
        String contentType = resp.headers().firstValue("content-type").orElse("image/png");
        byte[] bytes = resp.body();
        return ossService.uploadBytes("temu/translated", bytes, contentType);
    }

    private String extractNestedText(String rawJson, String... path) {
        if (!StringUtils.hasText(rawJson) || path == null || path.length == 0) {
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(rawJson);
            for (String p : path) {
                if (n == null) return null;
                n = n.path(p);
            }
            if (n == null || n.isMissingNode() || n.isNull()) return null;
            String s = n.asText(null);
            return StringUtils.hasText(s) ? s.trim() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public record Result(String originalUrl,
                         String uploadedUrl,
                         String taskId,
                         String translatedUrl,
                         String storedUrl) {
    }

    public record UploadResult(String originalUrl,
                               String uploadedUrl,
                               String raw) {
    }
}
