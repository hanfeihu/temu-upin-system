package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.OcrImageTranslateWorkerDTO;
import com.tminos.productscene.entity.ImageOcrTask;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.repository.ImageOcrTaskRepository;
import com.tminos.productscene.repository.ProductCollectionRepository;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OcrImageTranslateWorkerService {

    private static final Logger log = LoggerFactory.getLogger(OcrImageTranslateWorkerService.class);
    private static final String TRANSLATE_STATUS_SUCCESS = "SUCCESS";
    private static final String TRANSLATE_STATUS_POSITION_NOT_FOUND = "POSITION_NOT_FOUND";

    private final OcrImageTranslateWorkerConfigService configService;
    private final OcrImageTranslateWorkerLogService logService;
    private final ImageOcrTaskRepository ocrTaskRepository;
    private final ProductCollectionRepository productCollectionRepository;
    private final AIImageTranslateService aiImageTranslateService;
    private final AliyunImageTranslateService aliyunImageTranslateService;
    private final TemuImageNormalizeService temuImageNormalizeService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong skippedCount = new AtomicLong(0);
    private final Set<Long> manualRetryTaskIds = ConcurrentHashMap.newKeySet();

    private volatile Thread workerThread;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime stoppedAt;
    private volatile LocalDateTime lastScanAt;
    private volatile LocalDateTime lastWorkAt;
    private volatile LocalDateTime lastErrorAt;
    private volatile String lastError;
    private volatile Long lastSpuId;
    private volatile Long lastOcrTaskId;

    public OcrImageTranslateWorkerService(
            OcrImageTranslateWorkerConfigService configService,
            OcrImageTranslateWorkerLogService logService,
            ImageOcrTaskRepository ocrTaskRepository,
            ProductCollectionRepository productCollectionRepository,
            AIImageTranslateService aiImageTranslateService,
            AliyunImageTranslateService aliyunImageTranslateService,
            TemuImageNormalizeService temuImageNormalizeService,
            ObjectMapper objectMapper
    ) {
        this.configService = configService;
        this.logService = logService;
        this.ocrTaskRepository = ocrTaskRepository;
        this.productCollectionRepository = productCollectionRepository;
        this.aiImageTranslateService = aiImageTranslateService;
        this.aliyunImageTranslateService = aliyunImageTranslateService;
        this.temuImageNormalizeService = temuImageNormalizeService;
        this.objectMapper = objectMapper;
    }

    public synchronized OcrImageTranslateWorkerDTO.StatusView start() {
        if (running.get()) {
            return status();
        }
        if (workerThread != null && workerThread.isAlive()) {
            return status();
        }
        running.set(true);
        startedAt = LocalDateTime.now();
        stoppedAt = null;
        workerThread = new Thread(this::loop, "ocr-image-translate-worker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("OcrImageTranslateWorker started");
        return status();
    }

    public synchronized OcrImageTranslateWorkerDTO.StatusView stop() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
        stoppedAt = LocalDateTime.now();
        log.info("OcrImageTranslateWorker stopping");
        return status();
    }

    public OcrImageTranslateWorkerDTO.StatusView restartIfRunning() {
        if (!running.get()) {
            return status();
        }
        stop();
        return start();
    }

    public OcrImageTranslateWorkerDTO.StatusView submitRetryTask(Long ocrTaskId) {
        if (ocrTaskId == null) {
            throw new IllegalArgumentException("ocrTaskId is required");
        }
        ImageOcrTask task = ocrTaskRepository.findById(ocrTaskId)
                .orElseThrow(() -> new IllegalArgumentException("OCR task not found: " + ocrTaskId));
        if (task.getSpuId() == null) {
            throw new IllegalArgumentException("OCR task spuId is empty: " + ocrTaskId);
        }
        if (!manualRetryTaskIds.add(ocrTaskId)) {
            return status();
        }
        Thread retryThread = new Thread(() -> {
            try {
                ImageOcrTask latest = ocrTaskRepository.findById(ocrTaskId).orElse(null);
                if (latest == null || latest.getSpuId() == null) {
                    return;
                }
                ProductCollection product = productCollectionRepository.findById(latest.getSpuId()).orElse(null);
                String targetShopId = resolveTargetShopId(product);
                processTask(latest.getSpuId(), latest, targetShopId, configService.current(), new LinkedHashMap<>());
            } catch (Throwable e) {
                onError(e);
            } finally {
                manualRetryTaskIds.remove(ocrTaskId);
            }
        }, "ocr-image-translate-retry-" + ocrTaskId);
        retryThread.setDaemon(true);
        retryThread.start();
        return status();
    }

    public OcrImageTranslateWorkerDTO.StatusView status() {
        OcrImageTranslateWorkerDTO.ConfigView config = configService.current();
        OcrImageTranslateWorkerDTO.StatusView view = new OcrImageTranslateWorkerDTO.StatusView();
        view.setRunning(running.get());
        view.setEnabled(Boolean.TRUE.equals(config.getEnabled()));
        view.setMaxChineseImageCount(config.getMaxChineseImageCount());
        view.setBatchSize(config.getBatchSize());
        view.setPollMs(config.getPollMs());
        view.setProvider(config.getProvider());
        view.setModel(config.getModel());
        view.setPendingProductCount(countPendingProducts(config.getMaxChineseImageCount()));
        view.setSuccessCount(successCount.get());
        view.setFailureCount(failureCount.get());
        view.setSkippedCount(skippedCount.get());
        view.setLastSpuId(lastSpuId);
        view.setLastOcrTaskId(lastOcrTaskId);
        view.setStartedAt(startedAt);
        view.setStoppedAt(stoppedAt);
        view.setLastScanAt(lastScanAt);
        view.setLastWorkAt(lastWorkAt);
        view.setLastErrorAt(lastErrorAt);
        view.setLastError(lastError);
        return view;
    }

    @PreDestroy
    public void destroy() {
        stop();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void autoStartIfEnabled() {
        if (configService.currentEnabled()) {
            try {
                start();
            } catch (Exception e) {
                onError(e);
            }
        }
    }

    private void loop() {
        while (running.get()) {
            try {
                runOnce();
                sleep(configService.currentPollMs());
            } catch (Throwable e) {
                onError(e);
                sleep(10_000L);
            }
        }
    }

    private void runOnce() {
        lastScanAt = LocalDateTime.now();
        OcrImageTranslateWorkerDTO.ConfigView config = configService.current();
        List<Long> spuIds = findCandidateSpuIds(config.getMaxChineseImageCount(), config.getBatchSize());
        if (spuIds.isEmpty()) {
            return;
        }
        for (Long spuId : spuIds) {
            if (!running.get()) {
                break;
            }
            processProduct(spuId, config);
        }
    }

    private void processProduct(Long spuId, OcrImageTranslateWorkerDTO.ConfigView config) {
        lastSpuId = spuId;
        ProductCollection product = productCollectionRepository.findById(spuId).orElse(null);
        if (product == null
                || !Objects.equals(product.getOcrStatus(), 2)
                || Boolean.TRUE.equals(product.getDeleted())
                || Boolean.TRUE.equals(product.getTemuPublished())) {
            skippedCount.incrementAndGet();
            return;
        }
        List<ImageOcrTask> tasks = ocrTaskRepository.findBySpuId(spuId).stream()
                .filter(this::isChineseImageTask)
                .sorted(Comparator.comparing(ImageOcrTask::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (tasks.isEmpty()) {
            skippedCount.incrementAndGet();
            return;
        }
        if (tasks.size() > config.getMaxChineseImageCount()) {
            skippedCount.incrementAndGet();
            log.info("skip OCR image translate spuId={} chineseImageCount={} max={}", spuId, tasks.size(), config.getMaxChineseImageCount());
            return;
        }

        String targetShopId = resolveTargetShopId(product);
        Map<String, String> translatedUrlCache = new LinkedHashMap<>();
        for (ImageOcrTask task : tasks) {
            if (!running.get()) {
                break;
            }
            processTask(product.getId(), task, targetShopId, config, translatedUrlCache);
        }
    }

    private void processTask(
            Long spuId,
            ImageOcrTask task,
            String targetShopId,
            OcrImageTranslateWorkerDTO.ConfigView config,
            Map<String, String> translatedUrlCache
    ) {
        LocalDateTime started = LocalDateTime.now();
        lastOcrTaskId = task == null ? null : task.getId();
        ImageOcrTask originalTask = task;
        String originalUrl = task == null ? null : trimToNull(task.getImageUrl());
        String provider = normalizeProvider(config.getProvider());
        String model = "aliyun".equals(provider) ? "aliyun" : config.getModel();
        try {
            task = reloadEligibleTask(task);
            if (task == null || !isProductStillEligible(spuId)) {
                skippedCount.incrementAndGet();
                logService.save(logService.build(
                        originalTask,
                        "SKIPPED",
                        model,
                        null,
                        null,
                        "OCR 任务已过滤/已无中文，或商品 OCR 未完成/已发布/删除，跳过翻译",
                        null,
                        started,
                        LocalDateTime.now()
                ));
                return;
            }
            originalUrl = trimToNull(task.getImageUrl());
            if (!StringUtils.hasText(originalUrl)) {
                throw new IllegalArgumentException("OCR task imageUrl is empty");
            }
            if (!isImageReplacementLocatable(spuId, task, originalUrl)) {
                markTaskPositionNotFound(task.getId());
                throw new SkippedException("未找到可替换的商品图位置，跳过翻译");
            }
            String temuUrl = translatedUrlCache.get(originalUrl);
            String translatedUrl = null;
            if (!StringUtils.hasText(temuUrl)) {
                TranslationOutcome outcome = translateImage(originalUrl, targetShopId, config, provider);
                translatedUrl = outcome.translatedUrl();
                temuUrl = outcome.temuUrl();
                translatedUrlCache.put(originalUrl, temuUrl);
            }
            if (!StringUtils.hasText(temuUrl)) {
                throw new IllegalStateException("TEMU uploaded image url is empty");
            }
            boolean changed = replaceImageAndMarkTask(spuId, task.getId(), originalUrl, temuUrl);
            LocalDateTime finished = LocalDateTime.now();
            logService.save(logService.build(
                    task,
                    "SUCCESS",
                    model,
                    translatedUrl,
                    temuUrl,
                    changed ? "已翻译并替换商品图/OCR图" : "已翻译并更新 OCR 图",
                    null,
                    started,
                    finished
            ));
            successCount.incrementAndGet();
            lastWorkAt = finished;
            lastError = null;
        } catch (SkippedException e) {
            LocalDateTime finished = LocalDateTime.now();
            logService.save(logService.build(
                    originalTask,
                    "SKIPPED",
                    model,
                    null,
                    null,
                    e.getMessage(),
                    null,
                    started,
                    finished
            ));
            skippedCount.incrementAndGet();
        } catch (Throwable e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            LocalDateTime finished = LocalDateTime.now();
            logService.save(logService.build(
                    task,
                    "FAILED",
                    model,
                    null,
                    null,
                    null,
                    message,
                    started,
                    finished
            ));
            failureCount.incrementAndGet();
            onError(e);
        }
    }

    private TranslationOutcome translateImage(
            String originalUrl,
            String targetShopId,
            OcrImageTranslateWorkerDTO.ConfigView config,
            String provider
    ) throws Exception {
        if ("aliyun".equals(provider)) {
            AliyunImageTranslateService.Result result = aliyunImageTranslateService.translateImageByUrl(
                    originalUrl,
                    "zh",
                    "en",
                    false,
                    false
            );
            TemuImageNormalizeService.Result temuResult = temuImageNormalizeService.normalizeImageUrlToTemu800(
                    result.translatedUrl(),
                    originalUrl,
                    targetShopId
            );
            return new TranslationOutcome(result.translatedUrl(), temuResult.getUploadedUrl());
        }

        AIImageTranslateService.TranslateResult aiResult = aiImageTranslateService.translate(
                null,
                originalUrl,
                new AIImageTranslateService.RequestOptions(
                        "Chinese",
                        "English",
                        "Temu",
                        "Return a square product image for final exact 800x800 upload. Remove all Chinese text and keep product content unchanged. If any company name, shop name, supplier name, contact information, or brand-like company identifier appears on the image, erase it cleanly instead of translating or preserving it.",
                        "1024x1024",
                        config.getQuality(),
                        "png",
                        config.getModel(),
                        false
                )
        );
        TemuImageNormalizeService.Result temuResult = temuImageNormalizeService.normalizeBytesToTemu800(
                aiResult.imageBytes(),
                originalUrl,
                targetShopId
        );
        return new TranslationOutcome(aiResult.ossUrl(), temuResult.getUploadedUrl());
    }

    private String normalizeProvider(String value) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            return "ai";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        return ("aliyun".equals(normalized) || "ali".equals(normalized)) ? "aliyun" : "ai";
    }

    private record TranslationOutcome(String translatedUrl, String temuUrl) {
    }

    @Transactional
    public boolean replaceImageAndMarkTask(Long spuId, Long taskId, String oldUrl, String newUrl) throws Exception {
        ProductCollection product = productCollectionRepository.findById(spuId)
                .orElseThrow(() -> new IllegalStateException("ProductCollection not found: " + spuId));
        ImageOcrTask task = ocrTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("OCR task not found: " + taskId));
        if (!isProductStillEligible(product)) {
            throw new SkippedException("商品已发布/删除或状态不再允许翻译，跳过替换");
        }
        if (!isChineseImageTask(task)) {
            throw new SkippedException("OCR 任务已过滤/已无中文，跳过替换");
        }

        boolean changed = false;
        ReplacementTarget replacementTarget = resolveReplacementTarget(task);
        String sourceField = replacementTarget.sourceField();
        Integer sourceIndex = replacementTarget.sourceIndex();
        if (Objects.equals(product.getProductMainImage(), oldUrl)) {
            product.setProductMainImage(newUrl);
            changed = true;
        }
        if (ImageOcrTask.SOURCE_FIELD_CAROUSEL_IMAGES.equals(sourceField) && sourceIndex != null) {
            ReplaceResult result = replaceAtIndex(product.getCarouselImages(), sourceIndex, oldUrl, newUrl, true);
            if (result.changed()) {
                product.setCarouselImages(result.json());
                changed = true;
            }
        }
        if (ImageOcrTask.SOURCE_FIELD_DETAIL_IMAGES.equals(sourceField) && sourceIndex != null) {
            ReplaceResult result = replaceAtIndex(product.getDetailImages(), sourceIndex, oldUrl, newUrl, true);
            if (result.changed()) {
                product.setDetailImages(result.json());
                changed = true;
            }
        }

        if (!changed) {
            ReplaceResult carouselResult = replaceAll(product.getCarouselImages(), oldUrl, newUrl);
            if (carouselResult.changed()) {
                product.setCarouselImages(carouselResult.json());
                changed = true;
            }
            ReplaceResult detailResult = replaceAll(product.getDetailImages(), oldUrl, newUrl);
            if (detailResult.changed()) {
                product.setDetailImages(detailResult.json());
                changed = true;
            }
        }
        if (!changed) {
            throw new SkippedException("未找到可替换的商品图位置，跳过更新 OCR 图");
        }
        if (changed) {
            product.setUpdatedAt(LocalDateTime.now());
            productCollectionRepository.save(product);
        }

        task.setImageUrl(newUrl);
        task.setImageWidth(800);
        task.setImageHeight(800);
        try {
            TemuImageNormalizeService.ImageMetadata metadata = temuImageNormalizeService.probeImageMetadata(newUrl);
            task.setImageMd5(metadata == null ? null : metadata.md5());
        } catch (Exception ignored) {
            task.setImageMd5(null);
        }
        task.setTranslateStatus(TRANSLATE_STATUS_SUCCESS);
        task.setTranslatedImageUrl(newUrl);
        task.setContainsChinese(false);
        task.setFailReason(null);
        task.setExecStatus(ImageOcrTask.STATUS_SUCCESS);
        task.setUpdatedAt(LocalDateTime.now());
        ocrTaskRepository.save(task);
        return changed;
    }

    private boolean isChineseImageTask(ImageOcrTask task) {
        return task != null
                && task.getId() != null
                && task.getSpuId() != null
                && task.getExecStatus() != null
                && task.getExecStatus() == ImageOcrTask.STATUS_SUCCESS
                && Boolean.TRUE.equals(task.getContainsChinese())
                && !Boolean.TRUE.equals(task.getFiltered())
                && !TRANSLATE_STATUS_SUCCESS.equals(task.getTranslateStatus())
                && !TRANSLATE_STATUS_POSITION_NOT_FOUND.equals(task.getTranslateStatus())
                && StringUtils.hasText(task.getImageUrl());
    }

    @Transactional
    public void markTaskPositionNotFound(Long taskId) {
        if (taskId == null) {
            return;
        }
        ImageOcrTask task = ocrTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        task.setTranslateStatus(TRANSLATE_STATUS_POSITION_NOT_FOUND);
        task.setUpdatedAt(LocalDateTime.now());
        ocrTaskRepository.save(task);
    }

    private ImageOcrTask reloadEligibleTask(ImageOcrTask task) {
        if (task == null || task.getId() == null) {
            return null;
        }
        ImageOcrTask latest = ocrTaskRepository.findById(task.getId()).orElse(null);
        return isChineseImageTask(latest) ? latest : null;
    }

    private boolean isProductStillEligible(Long spuId) {
        if (spuId == null) {
            return false;
        }
        ProductCollection product = productCollectionRepository.findById(spuId).orElse(null);
        return isProductStillEligible(product);
    }

    private boolean isProductStillEligible(ProductCollection product) {
        if (product == null
                || !Objects.equals(product.getOcrStatus(), 2)
                || Boolean.TRUE.equals(product.getDeleted())
                || Boolean.TRUE.equals(product.getTemuPublished())) {
            return false;
        }
        Integer status = product.getCollectionStatus();
        int normalizedStatus = status == null ? 0 : status;
        return normalizedStatus == 0 || normalizedStatus == 1 || normalizedStatus == 2;
    }

    @Transactional(readOnly = true)
    public Long countPendingProducts(Integer maxChineseImageCount) {
        Object result = entityManager.createNativeQuery(candidateCountSql())
                .setParameter("maxCount", Math.max(maxChineseImageCount == null ? 5 : maxChineseImageCount, 1))
                .getSingleResult();
        return result instanceof Number number ? number.longValue() : 0L;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Long> findCandidateSpuIds(Integer maxChineseImageCount, Integer batchSize) {
        List<Number> rows = entityManager.createNativeQuery(candidateListSql())
                .setParameter("maxCount", Math.max(maxChineseImageCount == null ? 5 : maxChineseImageCount, 1))
                .setParameter("limit", Math.max(batchSize == null ? 1 : batchSize, 1))
                .getResultList();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(Objects::nonNull)
                .map(Number::longValue)
                .toList();
    }

    private String candidateCountSql() {
        return """
                select count(*) from (
                    select pc.id
                    from product_collection pc
                    join image_ocr_task t on t.spu_id = pc.id
                    where coalesce(pc.deleted, false) = false
                      and coalesce(pc.temu_published, false) = false
                      and pc.ocr_status = 2
                      and coalesce(pc.collection_status, 0) in (0, 1, 2)
                      and t.exec_status = 2
                      and coalesce(t.filtered, false) = false
                      and coalesce(t.translate_status, '') not in ('SUCCESS', 'POSITION_NOT_FOUND')
                      and coalesce(t.contains_chinese, false) = true
                      and t.image_url is not null and btrim(t.image_url) <> ''
                      and (
                        (t.source_field in ('carouselImages', 'detailImages') and t.source_index is not null and t.source_index >= 0)
                        or pc.product_main_image = t.image_url
                        or coalesce(pc.carousel_images, '') like concat('%', t.image_url, '%')
                        or coalesce(pc.detail_images, '') like concat('%', t.image_url, '%')
                      )
                    group by pc.id
                    having count(t.id) <= :maxCount
                ) candidates
                """;
    }

    private String candidateListSql() {
        return """
                select pc.id
                from product_collection pc
                join image_ocr_task t on t.spu_id = pc.id
                where coalesce(pc.deleted, false) = false
                  and coalesce(pc.temu_published, false) = false
                  and pc.ocr_status = 2
                  and coalesce(pc.collection_status, 0) in (0, 1, 2)
                  and t.exec_status = 2
                  and coalesce(t.filtered, false) = false
                  and coalesce(t.translate_status, '') not in ('SUCCESS', 'POSITION_NOT_FOUND')
                  and coalesce(t.contains_chinese, false) = true
                  and t.image_url is not null and btrim(t.image_url) <> ''
                  and (
                    (t.source_field in ('carouselImages', 'detailImages') and t.source_index is not null and t.source_index >= 0)
                    or pc.product_main_image = t.image_url
                    or coalesce(pc.carousel_images, '') like concat('%', t.image_url, '%')
                    or coalesce(pc.detail_images, '') like concat('%', t.image_url, '%')
                  )
                group by pc.id
                having count(t.id) <= :maxCount
                order by min(t.updated_at) asc, pc.id asc
                limit :limit
                """;
    }

    private boolean isImageReplacementLocatable(Long spuId, ImageOcrTask task, String oldUrl) {
        if (spuId == null || task == null || !StringUtils.hasText(oldUrl)) {
            return false;
        }
        ProductCollection product = productCollectionRepository.findById(spuId).orElse(null);
        if (product == null) {
            return false;
        }
        if (Objects.equals(product.getProductMainImage(), oldUrl)) {
            return true;
        }

        ReplacementTarget replacementTarget = resolveReplacementTarget(task);
        String sourceField = replacementTarget.sourceField();
        Integer sourceIndex = replacementTarget.sourceIndex();
        if (ImageOcrTask.SOURCE_FIELD_CAROUSEL_IMAGES.equals(sourceField) && sourceIndex != null) {
            return indexExists(product.getCarouselImages(), sourceIndex);
        }
        if (ImageOcrTask.SOURCE_FIELD_DETAIL_IMAGES.equals(sourceField) && sourceIndex != null) {
            return indexExists(product.getDetailImages(), sourceIndex);
        }

        return containsUrl(product.getCarouselImages(), oldUrl) || containsUrl(product.getDetailImages(), oldUrl);
    }

    private ReplacementTarget resolveReplacementTarget(ImageOcrTask task) {
        if (task == null) {
            return new ReplacementTarget(null, null);
        }
        String sourceField = trimToNull(task.getSourceField());
        Integer sourceIndex = task.getSourceIndex();
        if (!StringUtils.hasText(sourceField)) {
            sourceField = sourceFieldForImageType(task.getImageType());
        }
        return new ReplacementTarget(sourceField, sourceIndex);
    }

    private String sourceFieldForImageType(Integer imageType) {
        if (imageType == null) {
            return null;
        }
        if (imageType == ImageOcrTask.IMAGE_TYPE_CAROUSEL) {
            return ImageOcrTask.SOURCE_FIELD_CAROUSEL_IMAGES;
        }
        if (imageType == ImageOcrTask.IMAGE_TYPE_DETAIL) {
            return ImageOcrTask.SOURCE_FIELD_DETAIL_IMAGES;
        }
        return null;
    }

    private boolean indexExists(String json, int index) {
        List<String> list = parseJsonList(json);
        return index >= 0 && index < list.size() && StringUtils.hasText(list.get(index));
    }

    private boolean containsUrl(String json, String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        return parseJsonList(json).stream().anyMatch(item -> Objects.equals(trimToNull(item), url));
    }

    private ReplaceResult replaceAtIndex(String json, int index, String oldUrl, String newUrl, boolean trustIndex) throws Exception {
        List<String> list = parseJsonList(json);
        if (index < 0 || index >= list.size()) {
            return new ReplaceResult(json, false);
        }
        String current = trimToNull(list.get(index));
        if (Objects.equals(current, newUrl)) {
            return new ReplaceResult(json, false);
        }
        if (!trustIndex && !Objects.equals(current, oldUrl)) {
            return new ReplaceResult(json, false);
        }
        list.set(index, newUrl);
        return new ReplaceResult(objectMapper.writeValueAsString(list), true);
    }

    private ReplaceResult replaceAll(String json, String oldUrl, String newUrl) throws Exception {
        List<String> list = parseJsonList(json);
        if (list.isEmpty()) {
            return new ReplaceResult(json, false);
        }
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(trimToNull(list.get(i)), oldUrl)) {
                list.set(i, newUrl);
                changed = true;
            }
        }
        return changed ? new ReplaceResult(objectMapper.writeValueAsString(list), true) : new ReplaceResult(json, false);
    }

    private List<String> parseJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return new ArrayList<>();
            }
            List<String> out = new ArrayList<>();
            for (JsonNode item : node) {
                if (item != null && item.isTextual() && StringUtils.hasText(item.asText())) {
                    out.add(item.asText().trim());
                }
            }
            return out;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private String resolveTargetShopId(ProductCollection product) {
        List<String> shopIds = parseJsonList(product == null ? null : product.getTargetShopIds());
        if (!shopIds.isEmpty() && StringUtils.hasText(shopIds.get(0))) {
            return shopIds.get(0).trim();
        }
        return null;
    }

    private void onError(Throwable e) {
        lastError = e == null || e.getMessage() == null ? "未知错误" : e.getMessage();
        lastErrorAt = LocalDateTime.now();
        log.warn("OcrImageTranslateWorker error: {}", lastError);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(Math.max(ms, 1L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record ReplaceResult(String json, boolean changed) {
    }

    private record ReplacementTarget(String sourceField, Integer sourceIndex) {
    }

    private static class SkippedException extends RuntimeException {
        private SkippedException(String message) {
            super(message);
        }
    }
}
