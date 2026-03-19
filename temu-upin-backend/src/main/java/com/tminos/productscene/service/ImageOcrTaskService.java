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

    public ImageOcrTaskService(ImageOcrTaskRepository repo,
                              ImageOcrTaskClaimService claimService,
                              ImageOcrFilterWordRepository filterWordRepo,
                              ProductCollectionRepository productRepo,
                              ObjectMapper objectMapper) {
        this.repo = repo;
        this.claimService = claimService;
        this.filterWordRepo = filterWordRepo;
        this.productRepo = productRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<ImageOcrTask> list(Long spuId,
                                  String productId,
                                  Integer imageType,
                                  Integer execStatus,
                                  Boolean filtered,
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
                                            Boolean filtered) {
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

    @Transactional
    public ImageOcrTask create(ImageOcrDTO.UpsertTaskRequest req) {
        if (req == null) throw new IllegalArgumentException("req is required");
        ImageOcrTask t = new ImageOcrTask();
        t.setSpuId(req.getSpuId());
        t.setProductId(req.getProductId());
        t.setImageType(req.getImageType());
        t.setImageUrl(req.getImageUrl());
        t.setExecStatus(req.getExecStatus() == null ? ImageOcrTask.STATUS_PENDING : req.getExecStatus());
        t.setExecResult(req.getExecResult());
        t.setFailReason(req.getFailReason());
        t.setExecutorPublicIp(req.getExecutorPublicIp());
        t.setFiltered(req.getFiltered() != null && req.getFiltered());
        return repo.save(t);
    }

    @Transactional
    public ImageOcrTask update(Long id, ImageOcrDTO.UpsertTaskRequest req) {
        ImageOcrTask t = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("ocr task not found: " + id));
        if (req == null) throw new IllegalArgumentException("req is required");
        t.setSpuId(req.getSpuId());
        t.setProductId(req.getProductId());
        t.setImageType(req.getImageType());
        t.setImageUrl(req.getImageUrl());
        if (req.getExecStatus() != null) t.setExecStatus(req.getExecStatus());
        t.setExecResult(req.getExecResult());
        t.setFailReason(req.getFailReason());
        t.setExecutorPublicIp(req.getExecutorPublicIp());
        if (req.getFiltered() != null) t.setFiltered(req.getFiltered());
        return repo.save(t);
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
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
            // Filter word hit -> filtered=true
            if (containsAnyFilterWord(ocrText)) {
                t.setFiltered(true);
            }
        } else {
            t.setExecStatus(ImageOcrTask.STATUS_FAILED);
            t.setFailReason(fail);
        }
        t.setTaskFinishedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        repo.save(t);

        // Update product ocrStatus based on all tasks under this spu
        updateProductOcrStatusAfterTaskFinish(t.getSpuId());

        return t;
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

        Set<String> carouselRemove = new LinkedHashSet<>();
        Set<String> detailRemove = new LinkedHashSet<>();
        for (ImageOcrTask t : tasks) {
            if (t == null) continue;
            if (!Boolean.TRUE.equals(t.getFiltered())) continue;
            String url = t.getImageUrl();
            if (!StringUtils.hasText(url)) continue;
            String u = url.trim();
            if (u.isEmpty()) continue;
            int type = t.getImageType() == null ? 0 : t.getImageType();
            if (type == ImageOcrTask.IMAGE_TYPE_CAROUSEL) {
                carouselRemove.add(u);
            } else if (type == ImageOcrTask.IMAGE_TYPE_DETAIL) {
                detailRemove.add(u);
            }
        }

        boolean changed = false;
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
