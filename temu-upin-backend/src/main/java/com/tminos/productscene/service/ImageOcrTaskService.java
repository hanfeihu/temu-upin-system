package com.tminos.productscene.service;

import com.tminos.productscene.dto.ImageOcrDTO;
import com.tminos.productscene.entity.ImageOcrFilterWord;
import com.tminos.productscene.entity.ImageOcrTask;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.repository.ImageOcrFilterWordRepository;
import com.tminos.productscene.repository.ImageOcrTaskRepository;
import com.tminos.productscene.repository.ProductCollectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.*;

@Service
public class ImageOcrTaskService {

    private final ImageOcrTaskRepository repo;
    private final ImageOcrTaskClaimService claimService;
    private final ImageOcrFilterWordRepository filterWordRepo;
    private final ProductCollectionRepository productRepo;
    private final ObjectMapper objectMapper;
    private final TemuImageNormalizeService temuImageNormalizeService;

    public ImageOcrTaskService(ImageOcrTaskRepository repo,
                              ImageOcrTaskClaimService claimService,
                              ImageOcrFilterWordRepository filterWordRepo,
                              ProductCollectionRepository productRepo,
                              ObjectMapper objectMapper,
                              TemuImageNormalizeService temuImageNormalizeService) {
        this.repo = repo;
        this.claimService = claimService;
        this.filterWordRepo = filterWordRepo;
        this.productRepo = productRepo;
        this.objectMapper = objectMapper;
        this.temuImageNormalizeService = temuImageNormalizeService;
    }

    @Transactional(readOnly = true)
    public Page<ImageOcrTask> list(Long spuId,
                                  String productId,
                                  Integer imageType,
                                  Integer execStatus,
                                  Boolean filtered,
                                  Boolean containsChinese,
                                  String translateStatus,
                                  Integer imageWidthMin,
                                  Integer imageWidthMax,
                                  Integer imageHeightMin,
                                  Integer imageHeightMax,
                                  int page,
                                  int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "updatedAt"));

        Specification<ImageOcrTask> spec = (root, query, cb) -> {
            var p = cb.conjunction();
            if (spuId != null) {
                p = cb.and(p, cb.equal(root.get("spuId"), spuId));
            }
            if (StringUtils.hasText(productId)) {
                p = cb.and(p, cb.like(root.get("productId"), "%" + productId.trim() + "%"));
            }
            if (imageType != null) {
                p = cb.and(p, cb.equal(root.get("imageType"), imageType));
            }
            if (execStatus != null) {
                p = cb.and(p, cb.equal(root.get("execStatus"), execStatus));
            }
            if (filtered != null) {
                p = cb.and(p, cb.equal(root.get("filtered"), filtered));
            }
            if (containsChinese != null) {
                p = cb.and(p, cb.equal(root.get("containsChinese"), containsChinese));
            }
            p = applyTranslateStatusFilter(p, root, cb, translateStatus);
            if (imageWidthMin != null) {
                p = cb.and(p, cb.greaterThanOrEqualTo(root.get("imageWidth"), imageWidthMin));
            }
            if (imageWidthMax != null) {
                p = cb.and(p, cb.lessThanOrEqualTo(root.get("imageWidth"), imageWidthMax));
            }
            if (imageHeightMin != null) {
                p = cb.and(p, cb.greaterThanOrEqualTo(root.get("imageHeight"), imageHeightMin));
            }
            if (imageHeightMax != null) {
                p = cb.and(p, cb.lessThanOrEqualTo(root.get("imageHeight"), imageHeightMax));
            }
            return p;
        };

        return repo.findAll(spec, pageable);
    }

    /**
     * Statistics under current filters.
     * Note: we intentionally ignore execStatus filter so the UI can show distribution.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> stats(Long spuId,
                                            String productId,
                                            Integer imageType,
                                            Boolean filtered,
                                            Boolean containsChinese,
                                            String translateStatus,
                                            Integer imageWidthMin,
                                            Integer imageWidthMax,
                                            Integer imageHeightMin,
                                            Integer imageHeightMax) {
        Specification<ImageOcrTask> base = (root, query, cb) -> {
            var p = cb.conjunction();
            if (spuId != null) {
                p = cb.and(p, cb.equal(root.get("spuId"), spuId));
            }
            if (StringUtils.hasText(productId)) {
                p = cb.and(p, cb.like(root.get("productId"), "%" + productId.trim() + "%"));
            }
            if (imageType != null) {
                p = cb.and(p, cb.equal(root.get("imageType"), imageType));
            }
            if (filtered != null) {
                p = cb.and(p, cb.equal(root.get("filtered"), filtered));
            }
            if (containsChinese != null) {
                p = cb.and(p, cb.equal(root.get("containsChinese"), containsChinese));
            }
            p = applyTranslateStatusFilter(p, root, cb, translateStatus);
            if (imageWidthMin != null) {
                p = cb.and(p, cb.greaterThanOrEqualTo(root.get("imageWidth"), imageWidthMin));
            }
            if (imageWidthMax != null) {
                p = cb.and(p, cb.lessThanOrEqualTo(root.get("imageWidth"), imageWidthMax));
            }
            if (imageHeightMin != null) {
                p = cb.and(p, cb.greaterThanOrEqualTo(root.get("imageHeight"), imageHeightMin));
            }
            if (imageHeightMax != null) {
                p = cb.and(p, cb.lessThanOrEqualTo(root.get("imageHeight"), imageHeightMax));
            }
            return p;
        };

        long total = repo.count(base);
        long pending = repo.count(base.and((root, query, cb) -> cb.equal(root.get("execStatus"), ImageOcrTask.STATUS_PENDING)));
        long running = repo.count(base.and((root, query, cb) -> cb.equal(root.get("execStatus"), ImageOcrTask.STATUS_RUNNING)));
        long success = repo.count(base.and((root, query, cb) -> cb.equal(root.get("execStatus"), ImageOcrTask.STATUS_SUCCESS)));
        long failed = repo.count(base.and((root, query, cb) -> cb.equal(root.get("execStatus"), ImageOcrTask.STATUS_FAILED)));

        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        out.put("total", total);
        out.put("pending", pending);
        out.put("running", running);
        out.put("success", success);
        out.put("failed", failed);
        return out;
    }

    private jakarta.persistence.criteria.Predicate applyTranslateStatusFilter(
            jakarta.persistence.criteria.Predicate p,
            jakarta.persistence.criteria.Root<ImageOcrTask> root,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            String translateStatus
    ) {
        if (!StringUtils.hasText(translateStatus)) {
            return p;
        }
        String status = translateStatus.trim().toUpperCase(Locale.ROOT);
        return switch (status) {
            case "SUCCESS" -> cb.and(p, cb.equal(root.get("translateStatus"), "SUCCESS"));
            case "POSITION_NOT_FOUND" -> cb.and(p, cb.equal(root.get("translateStatus"), "POSITION_NOT_FOUND"));
            case "NOT_REQUIRED" -> cb.and(p, cb.or(
                    cb.isTrue(root.get("filtered")),
                    cb.isFalse(root.get("containsChinese"))
            ));
            case "PENDING" -> cb.and(p,
                    cb.isFalse(root.get("filtered")),
                    cb.isTrue(root.get("containsChinese")),
                    cb.or(
                            cb.isNull(root.get("translateStatus")),
                            cb.not(root.get("translateStatus").in("SUCCESS", "POSITION_NOT_FOUND"))
                    )
            );
            case "UNRECOGNIZED" -> cb.and(p, cb.isNull(root.get("containsChinese")));
            default -> p;
        };
    }

    @Transactional
    public ImageOcrTask create(ImageOcrDTO.UpsertTaskRequest req) {
        if (req == null) throw new IllegalArgumentException("req is required");
        ImageOcrTask t = new ImageOcrTask();
        t.setSpuId(req.getSpuId());
        t.setProductId(req.getProductId());
        t.setImageType(req.getImageType());
        t.setImageUrl(req.getImageUrl());
        t.setImageWidth(req.getImageWidth());
        t.setImageHeight(req.getImageHeight());
        t.setImageMd5(normalizeMd5(req.getImageMd5()));
        t.setTranslateStatus(normalizeTranslateStatus(req.getTranslateStatus()));
        t.setTranslatedImageUrl(trimToNull(req.getTranslatedImageUrl()));
        t.setExecStatus(req.getExecStatus() == null ? ImageOcrTask.STATUS_PENDING : req.getExecStatus());
        t.setExecResult(req.getExecResult());
        t.setFailReason(req.getFailReason());
        t.setExecutorPublicIp(req.getExecutorPublicIp());
        t.setFiltered(req.getFiltered() != null && req.getFiltered());
        t.setContainsChinese(req.getContainsChinese());
        return repo.save(t);
    }

    @Transactional
    public ImageOcrDTO.BackfillImageSizeResult backfillMissingImageSizes(int limit) {
        int batchSize = Math.min(Math.max(limit, 1), 500);
        List<ImageOcrTask> tasks = repo.findMissingImageMetadata(PageRequest.of(0, batchSize));
        int updated = 0;
        int failed = 0;
        for (ImageOcrTask task : tasks) {
            if (task == null || !StringUtils.hasText(task.getImageUrl())) {
                continue;
            }
            try {
                TemuImageNormalizeService.ImageMetadata metadata = temuImageNormalizeService.probeImageMetadata(task.getImageUrl());
                if (metadata != null && metadata.width() > 0 && metadata.height() > 0 && StringUtils.hasText(metadata.md5())) {
                    task.setImageWidth(metadata.width());
                    task.setImageHeight(metadata.height());
                    task.setImageMd5(normalizeMd5(metadata.md5()));
                    task.setUpdatedAt(LocalDateTime.now());
                    repo.save(task);
                    updated++;
                } else {
                    task.setImageWidth(0);
                    task.setImageHeight(0);
                    task.setUpdatedAt(LocalDateTime.now());
                    repo.save(task);
                    failed++;
                }
            } catch (Exception ignored) {
                task.setImageWidth(0);
                task.setImageHeight(0);
                task.setUpdatedAt(LocalDateTime.now());
                repo.save(task);
                failed++;
            }
        }
        long remaining = repo.countMissingImageMetadata();
        return ImageOcrDTO.BackfillImageSizeResult.builder()
                .scanned(tasks.size())
                .updated(updated)
                .failed(failed)
                .remaining(remaining)
                .build();
    }

    @Transactional
    public ImageOcrDTO.DeleteSizeFilteredImagesResult deleteEnabledSizeFilteredImages() {
        List<ImageOcrTask> tasks = repo.findEnabledSizeFilteredTasks();
        if (tasks == null || tasks.isEmpty()) {
            return ImageOcrDTO.DeleteSizeFilteredImagesResult.builder()
                    .matchedTaskCount(0)
                    .deletedTaskCount(0)
                    .removedProductImageCount(0)
                    .affectedProductCount(0)
                    .build();
        }

        int removedProductImageCount = 0;
        int deletedTaskCount = 0;
        Set<Long> affectedSpuIds = new LinkedHashSet<>();
        for (ImageOcrTask task : tasks) {
            if (task == null || task.getId() == null) continue;
            Long spuId = task.getSpuId();
            if (removeTaskImageFromProduct(task)) {
                removedProductImageCount++;
            }
            repo.delete(task);
            deletedTaskCount++;
            if (spuId != null) {
                affectedSpuIds.add(spuId);
            }
        }

        for (Long spuId : affectedSpuIds) {
            updateProductOcrStatusAfterTaskFinish(spuId);
        }

        return ImageOcrDTO.DeleteSizeFilteredImagesResult.builder()
                .matchedTaskCount(tasks.size())
                .deletedTaskCount(deletedTaskCount)
                .removedProductImageCount(removedProductImageCount)
                .affectedProductCount(affectedSpuIds.size())
                .build();
    }

    @Transactional
    public ImageOcrTask update(Long id, ImageOcrDTO.UpsertTaskRequest req) {
        ImageOcrTask t = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("ocr task not found: " + id));
        if (req == null) throw new IllegalArgumentException("req is required");
        t.setSpuId(req.getSpuId());
        t.setProductId(req.getProductId());
        t.setImageType(req.getImageType());
        t.setImageUrl(req.getImageUrl());
        t.setImageWidth(req.getImageWidth());
        t.setImageHeight(req.getImageHeight());
        t.setImageMd5(normalizeMd5(req.getImageMd5()));
        t.setTranslateStatus(normalizeTranslateStatus(req.getTranslateStatus()));
        t.setTranslatedImageUrl(trimToNull(req.getTranslatedImageUrl()));
        if (req.getExecStatus() != null) t.setExecStatus(req.getExecStatus());
        t.setExecResult(req.getExecResult());
        t.setFailReason(req.getFailReason());
        t.setExecutorPublicIp(req.getExecutorPublicIp());
        if (req.getFiltered() != null) t.setFiltered(req.getFiltered());
        t.setContainsChinese(req.getContainsChinese());
        return repo.save(t);
    }

    @Transactional
    public void delete(Long id) {
        ImageOcrTask task = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("ocr task not found: " + id));
        Long spuId = task.getSpuId();
        removeTaskImageFromProduct(task);
        repo.delete(task);
        updateProductOcrStatusAfterTaskFinish(spuId);
    }

    @Transactional
    public ImageOcrTask claimOne(String publicIp) {
        Long id = claimService == null ? null : claimService.claimNextIdPreferPendingThenFailed();
        if (id == null) return null;

        ImageOcrTask t = repo.findById(id).orElse(null);
        if (t == null) return null;
        int st0 = t.getExecStatus() == null ? ImageOcrTask.STATUS_PENDING : t.getExecStatus();
        // Allow re-claiming:
        // - pending (0)
        // - failed (3)
        // - running but timed out (1 and startedAt < now-1h)
        if (st0 == ImageOcrTask.STATUS_RUNNING) {
            LocalDateTime startedAt = t.getTaskStartedAt();
            if (startedAt == null || startedAt.isAfter(LocalDateTime.now().minusHours(1))) {
                return null;
            }
        } else if (st0 != ImageOcrTask.STATUS_PENDING && st0 != ImageOcrTask.STATUS_FAILED) {
            return null;
        }

        t.setExecStatus(ImageOcrTask.STATUS_RUNNING);
        // Reset previous failure / previous result when re-claiming
        t.setFailReason(null);
        t.setExecResult(null);
        t.setContainsChinese(null);
        t.setExecutorPublicIp(StringUtils.hasText(publicIp) ? publicIp.trim() : t.getExecutorPublicIp());
        t.setTaskStartedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        repo.save(t);

        // Update product OCR status to running
        if (t.getSpuId() != null) {
            productRepo.findById(t.getSpuId()).ifPresent(pc -> {
                Integer st = pc.getOcrStatus();
                int s = st == null ? 0 : st;
                if (s != 1) {
                    pc.setOcrStatus(1);
                    pc.setUpdatedAt(LocalDateTime.now());
                    productRepo.save(pc);
                }
            });
        }

        return t;
    }

    @Transactional
    public ImageOcrTask complete(ImageOcrDTO.CompleteTaskRequest req) {
        if (req == null || req.getTaskId() == null) {
            throw new IllegalArgumentException("taskId is required");
        }

        ImageOcrTask t = repo.findById(req.getTaskId()).orElseThrow(() -> new EntityNotFoundException("ocr task not found: " + req.getTaskId()));

        String ocrText = req.getOcrText();
        String fail = req.getFailReason();
        boolean success = StringUtils.hasText(ocrText);
        if (!success && !StringUtils.hasText(fail)) {
            throw new IllegalArgumentException("either ocrText (success) or failReason (failed) is required");
        }

        if (success) {
            t.setExecStatus(ImageOcrTask.STATUS_SUCCESS);
            t.setExecResult(ocrText);
            t.setFailReason(null);
            t.setContainsChinese(containsChinese(ocrText));
            // Filter word hit -> filtered=true
            if (containsAnyFilterWord(ocrText)) {
                t.setFiltered(true);
            }
        } else {
            t.setExecStatus(ImageOcrTask.STATUS_FAILED);
            t.setFailReason(fail);
            t.setContainsChinese(null);
        }
        t.setTaskFinishedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        repo.save(t);

        // Update product ocrStatus based on all tasks under this spu
        updateProductOcrStatusAfterTaskFinish(t.getSpuId());

        return t;
    }

    public ImageOcrDTO.OcrTaskResponse toExternalResponse(ImageOcrTask t) {
        if (t == null) return null;
        return ImageOcrDTO.OcrTaskResponse.builder()
                .id(t.getId())
                .spuId(t.getSpuId())
                .productId(t.getProductId())
                .imageType(t.getImageType())
                .imageUrl(t.getImageUrl())
                .imageWidth(t.getImageWidth())
                .imageHeight(t.getImageHeight())
                .imageMd5(t.getImageMd5())
                .translateStatus(t.getTranslateStatus())
                .translatedImageUrl(t.getTranslatedImageUrl())
                .execStatus(t.getExecStatus())
                .execResult(t.getExecResult())
                .failReason(t.getFailReason())
                .executorPublicIp(t.getExecutorPublicIp())
                .taskStartedAt(t.getTaskStartedAt())
                .taskFinishedAt(t.getTaskFinishedAt())
                .filtered(t.getFiltered())
                .containsChinese(t.getContainsChinese())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    private boolean containsAnyFilterWord(String ocrText) {
        if (!StringUtils.hasText(ocrText)) return false;
        List<ImageOcrFilterWord> words = filterWordRepo == null ? null : filterWordRepo.findAllOrdered();
        if (words == null || words.isEmpty()) return false;
        String text = ocrText;
        String lower = ocrText.toLowerCase();
        for (ImageOcrFilterWord w : words) {
            if (w == null) continue;
            String kw = w.getWord();
            if (!StringUtils.hasText(kw)) continue;
            String k = kw.trim();
            if (k.isEmpty()) continue;
            if (text.contains(k)) return true;
            // best effort: ASCII/case-insensitive
            if (lower.contains(k.toLowerCase())) return true;
        }
        return false;
    }

    private String normalizeMd5(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String md5 = value.trim().toLowerCase(Locale.ROOT);
        return md5.length() == 32 ? md5 : null;
    }

    private String normalizeTranslateStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String status = value.trim().toUpperCase(Locale.ROOT);
        return status.length() > 64 ? status.substring(0, 64) : status;
    }

    private boolean containsChinese(String text) {
        if (!StringUtils.hasText(text)) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u4E00' && c <= '\u9FFF') return true;
        }
        return false;
    }

    private void updateProductOcrStatusAfterTaskFinish(Long spuId) {
        if (spuId == null) return;
        Optional<ProductCollection> opt = productRepo.findById(spuId);
        if (opt.isEmpty()) return;

        List<ImageOcrTask> tasks = repo.findBySpuId(spuId);
        if (tasks == null || tasks.isEmpty()) {
            // no tasks -> treat as completed
            ProductCollection pc = opt.get();
            pc.setOcrStatus(2);
            pc.setUpdatedAt(LocalDateTime.now());
            productRepo.save(pc);
            return;
        }

        boolean anyPendingOrRunning = false;
        boolean anyFailed = false;
        boolean anyFiltered = false;
        for (ImageOcrTask t : tasks) {
            if (t == null) continue;
            int st = t.getExecStatus() == null ? 0 : t.getExecStatus();
            if (st == ImageOcrTask.STATUS_PENDING || st == ImageOcrTask.STATUS_RUNNING) {
                anyPendingOrRunning = true;
            } else if (st == ImageOcrTask.STATUS_FAILED) {
                anyFailed = true;
            }
            if (Boolean.TRUE.equals(t.getFiltered())) {
                anyFiltered = true;
            }
        }

        ProductCollection pc = opt.get();
        if (anyPendingOrRunning) {
            pc.setOcrStatus(1);
        } else {
            // All tasks are terminal; if any task is marked filtered, remove those image URLs from SPU images.
            if (anyFiltered) {
                try {
                    removeFilteredImagesFromProduct(pc, tasks);
                } catch (Exception ignored) {
                }
            }
            pc.setOcrStatus(anyFailed ? 3 : 2);
        }
        pc.setUpdatedAt(LocalDateTime.now());
        productRepo.save(pc);
    }

    private void removeFilteredImagesFromProduct(ProductCollection pc, List<ImageOcrTask> tasks) {
        if (pc == null || pc.getId() == null || tasks == null || tasks.isEmpty()) return;

        List<ImageOcrTask> carouselExactRemove = new ArrayList<>();
        List<ImageOcrTask> detailExactRemove = new ArrayList<>();
        Set<String> carouselRemove = new LinkedHashSet<>();
        Set<String> detailRemove = new LinkedHashSet<>();
        for (ImageOcrTask t : tasks) {
            if (t == null) continue;
            if (!Boolean.TRUE.equals(t.getFiltered())) continue;
            String url = t.getImageUrl();
            if (!StringUtils.hasText(url)) continue;
            String u = url.trim();
            if (u.isEmpty()) continue;
            String sourceField = t.getSourceField();
            Integer sourceIndex = t.getSourceIndex();
            if (ImageOcrTask.SOURCE_FIELD_CAROUSEL_IMAGES.equals(sourceField) && sourceIndex != null && sourceIndex >= 0) {
                carouselExactRemove.add(t);
            } else if (ImageOcrTask.SOURCE_FIELD_DETAIL_IMAGES.equals(sourceField) && sourceIndex != null && sourceIndex >= 0) {
                detailExactRemove.add(t);
            } else if (ImageOcrTask.IMAGE_TYPE_CAROUSEL == (t.getImageType() == null ? 0 : t.getImageType())) {
                carouselRemove.add(u);
            } else if (ImageOcrTask.IMAGE_TYPE_DETAIL == (t.getImageType() == null ? 0 : t.getImageType())) {
                detailRemove.add(u);
            }
        }

        boolean changed = false;
        if (!carouselExactRemove.isEmpty()) {
            String updated = removeExactSourcesFromJsonArray(pc.getCarouselImages(), carouselExactRemove);
            if (updated != null) {
                pc.setCarouselImages(updated);
                changed = true;
            }
        }
        if (!detailExactRemove.isEmpty()) {
            String updated = removeExactSourcesFromJsonArray(pc.getDetailImages(), detailExactRemove);
            if (updated != null) {
                pc.setDetailImages(updated);
                changed = true;
            }
        }
        if (!carouselRemove.isEmpty()) {
            String updated = removeUrlsFromJsonArray(pc.getCarouselImages(), carouselRemove);
            if (updated != null) {
                pc.setCarouselImages(updated);
                changed = true;
            }
        }
        if (!detailRemove.isEmpty()) {
            String updated = removeUrlsFromJsonArray(pc.getDetailImages(), detailRemove);
            if (updated != null) {
                pc.setDetailImages(updated);
                changed = true;
            }
        }

        if (changed) {
            pc.setUpdatedAt(LocalDateTime.now());
        }
    }

    private boolean removeTaskImageFromProduct(ImageOcrTask task) {
        if (task == null || task.getSpuId() == null || !StringUtils.hasText(task.getImageUrl())) return false;
        Optional<ProductCollection> opt = productRepo.findById(task.getSpuId());
        if (opt.isEmpty()) return false;

        ProductCollection pc = opt.get();
        boolean changed = false;
        String sourceField = task.getSourceField();
        Integer sourceIndex = task.getSourceIndex();
        String imageUrl = task.getImageUrl().trim();

        if (ImageOcrTask.SOURCE_FIELD_CAROUSEL_IMAGES.equals(sourceField) && sourceIndex != null && sourceIndex >= 0) {
            String updated = removeExactSourcesFromJsonArray(pc.getCarouselImages(), List.of(task));
            if (updated != null) {
                pc.setCarouselImages(updated);
                changed = true;
            }
        } else if (ImageOcrTask.SOURCE_FIELD_DETAIL_IMAGES.equals(sourceField) && sourceIndex != null && sourceIndex >= 0) {
            String updated = removeExactSourcesFromJsonArray(pc.getDetailImages(), List.of(task));
            if (updated != null) {
                pc.setDetailImages(updated);
                changed = true;
            }
        }

        if (!changed) {
            Set<String> remove = new LinkedHashSet<>();
            remove.add(imageUrl);
            if (ImageOcrTask.IMAGE_TYPE_CAROUSEL == (task.getImageType() == null ? 0 : task.getImageType())) {
                String updated = removeUrlsFromJsonArray(pc.getCarouselImages(), remove);
                if (updated != null) {
                    pc.setCarouselImages(updated);
                    changed = true;
                }
            } else if (ImageOcrTask.IMAGE_TYPE_DETAIL == (task.getImageType() == null ? 0 : task.getImageType())) {
                String updated = removeUrlsFromJsonArray(pc.getDetailImages(), remove);
                if (updated != null) {
                    pc.setDetailImages(updated);
                    changed = true;
                }
            } else {
                String updatedCarousel = removeUrlsFromJsonArray(pc.getCarouselImages(), remove);
                if (updatedCarousel != null) {
                    pc.setCarouselImages(updatedCarousel);
                    changed = true;
                }
                String updatedDetail = removeUrlsFromJsonArray(pc.getDetailImages(), remove);
                if (updatedDetail != null) {
                    pc.setDetailImages(updatedDetail);
                    changed = true;
                }
            }
        }

        if (Objects.equals(trimToNull(pc.getProductMainImage()), imageUrl)) {
            List<String> carousel = parseJsonStringArray(pc.getCarouselImages());
            pc.setProductMainImage(carousel.isEmpty() ? null : carousel.get(0));
            changed = true;
        }

        if (changed) {
            pc.setUpdatedAt(LocalDateTime.now());
            productRepo.save(pc);
            reindexOcrTasksForProductImages(pc);
        }
        return changed;
    }

    private void reindexOcrTasksForProductImages(ProductCollection pc) {
        if (pc == null || pc.getId() == null) return;
        List<ImageOcrTask> tasks = repo.findBySpuId(pc.getId());
        if (tasks == null || tasks.isEmpty()) return;

        Map<String, List<Integer>> carouselIndices = buildIndicesByUrl(parseJsonStringArray(pc.getCarouselImages()));
        Map<String, List<Integer>> detailIndices = buildIndicesByUrl(parseJsonStringArray(pc.getDetailImages()));
        List<ImageOcrTask> changedTasks = new ArrayList<>();

        for (ImageOcrTask task : tasks) {
            if (task == null || task.getId() == null || !StringUtils.hasText(task.getImageUrl())) continue;
            String sourceField = task.getSourceField();
            Map<String, List<Integer>> indicesByUrl;
            if (ImageOcrTask.SOURCE_FIELD_CAROUSEL_IMAGES.equals(sourceField)) {
                indicesByUrl = carouselIndices;
            } else if (ImageOcrTask.SOURCE_FIELD_DETAIL_IMAGES.equals(sourceField)) {
                indicesByUrl = detailIndices;
            } else if (ImageOcrTask.IMAGE_TYPE_CAROUSEL == (task.getImageType() == null ? 0 : task.getImageType())) {
                indicesByUrl = carouselIndices;
                sourceField = ImageOcrTask.SOURCE_FIELD_CAROUSEL_IMAGES;
            } else if (ImageOcrTask.IMAGE_TYPE_DETAIL == (task.getImageType() == null ? 0 : task.getImageType())) {
                indicesByUrl = detailIndices;
                sourceField = ImageOcrTask.SOURCE_FIELD_DETAIL_IMAGES;
            } else {
                continue;
            }

            List<Integer> indices = indicesByUrl.get(task.getImageUrl().trim());
            if (indices == null || indices.isEmpty()) continue;
            Integer nextIndex = indices.remove(0);
            boolean changed = false;
            if (!Objects.equals(task.getSourceField(), sourceField)) {
                task.setSourceField(sourceField);
                changed = true;
            }
            if (!Objects.equals(task.getSourceIndex(), nextIndex)) {
                task.setSourceIndex(nextIndex);
                changed = true;
            }
            if (changed) {
                changedTasks.add(task);
            }
        }

        if (!changedTasks.isEmpty()) {
            repo.saveAll(changedTasks);
        }
    }

    private Map<String, List<Integer>> buildIndicesByUrl(List<String> images) {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        for (int i = 0; i < images.size(); i++) {
            String url = trimToNull(images.get(i));
            if (!StringUtils.hasText(url)) continue;
            out.computeIfAbsent(url, key -> new ArrayList<>()).add(i);
        }
        return out;
    }

    private List<String> parseJsonStringArray(String json) {
        if (!StringUtils.hasText(json)) return Collections.emptyList();
        String s = json.trim();
        if (!s.startsWith("[")) return Collections.emptyList();
        try {
            List<?> raw = objectMapper.readValue(s, List.class);
            if (raw == null || raw.isEmpty()) return Collections.emptyList();
            List<String> out = new ArrayList<>();
            for (Object item : raw) {
                String value = trimToNull(item == null ? null : String.valueOf(item));
                if (StringUtils.hasText(value)) out.add(value);
            }
            return out;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.trim();
    }

    /**
     * Removes by stored array index only when the URL at that index still matches.
     * Mismatches are intentionally left untouched to avoid deleting a different image after array edits.
     */
    private String removeExactSourcesFromJsonArray(String json, List<ImageOcrTask> tasks) {
        if (tasks == null || tasks.isEmpty()) return null;
        if (!StringUtils.hasText(json)) return null;
        String s = json.trim();
        if (s.isEmpty() || !s.startsWith("[")) return null;
        try {
            List<?> raw = objectMapper.readValue(s, List.class);
            if (raw == null || raw.isEmpty()) return null;
            List<String> arr = new ArrayList<>();
            for (Object o : raw) {
                arr.add(o == null ? "" : String.valueOf(o));
            }

            List<ImageOcrTask> ordered = new ArrayList<>(tasks);
            ordered.sort(Comparator.comparing(
                    ImageOcrTask::getSourceIndex,
                    Comparator.nullsLast(Comparator.reverseOrder())
            ));

            boolean changed = false;
            for (ImageOcrTask task : ordered) {
                Integer index = task == null ? null : task.getSourceIndex();
                String expected = task == null ? null : task.getImageUrl();
                if (index == null || index < 0 || index >= arr.size() || !StringUtils.hasText(expected)) {
                    continue;
                }
                String current = arr.get(index) == null ? "" : arr.get(index).trim();
                if (current.equals(expected.trim())) {
                    arr.remove((int) index);
                    changed = true;
                }
            }

            if (!changed) return null;
            List<String> out = new ArrayList<>();
            for (String v : arr) {
                if (StringUtils.hasText(v)) {
                    out.add(v.trim());
                }
            }
            return objectMapper.writeValueAsString(out);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * @return updated json when modified; null when cannot parse or no changes.
     */
    private String removeUrlsFromJsonArray(String json, Set<String> remove) {
        if (remove == null || remove.isEmpty()) return null;
        if (!StringUtils.hasText(json)) return null;
        String s = json.trim();
        if (s.isEmpty()) return null;
        if (!s.startsWith("[")) return null;
        try {
            List<?> arr = objectMapper.readValue(s, List.class);
            if (arr == null || arr.isEmpty()) return null;
            List<String> out = new ArrayList<>();
            boolean changed = false;
            for (Object o : arr) {
                if (o == null) continue;
                String v = String.valueOf(o).trim();
                if (v.isEmpty()) continue;
                if (remove.contains(v)) {
                    changed = true;
                    continue;
                }
                out.add(v);
            }
            if (!changed) return null;
            return objectMapper.writeValueAsString(out);
        } catch (Exception ignored) {
            return null;
        }
    }
}
