package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.config.AITemuAttrFillerConfig;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.repository.ProductCollectionRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PostImportAutomationService {

    private static final Logger log = LoggerFactory.getLogger(PostImportAutomationService.class);

    private final ProductCollectionRepository repo;
    private final ProductCollectionService productCollectionService;
    private final TemuImageTranslateService temuImageTranslateService;
    private final TemuSkuService temuSkuService;
    private final AITemuAttrFillerConfig aiConfig;
    private final ObjectMapper objectMapper;
    private final Executor globalWorkerExecutor;
    private final PostImportLogService postImportLogService;

    public PostImportAutomationService(ProductCollectionRepository repo,
                                       ProductCollectionService productCollectionService,
                                       TemuImageTranslateService temuImageTranslateService,
                                       TemuSkuService temuSkuService,
                                       AITemuAttrFillerConfig aiConfig,
                                       ObjectMapper objectMapper,
                                       @org.springframework.beans.factory.annotation.Qualifier("globalWorkerExecutor") Executor globalWorkerExecutor,
                                       PostImportLogService postImportLogService) {
        this.repo = repo;
        this.productCollectionService = productCollectionService;
        this.temuImageTranslateService = temuImageTranslateService;
        this.temuSkuService = temuSkuService;
        this.aiConfig = aiConfig;
        this.objectMapper = objectMapper;
        this.globalWorkerExecutor = globalWorkerExecutor;
        this.postImportLogService = postImportLogService;
    }

    // One task per imported product; steps inside are serial.
    // Note: async triggering is intentionally disabled; use PostImportAutomationWorker polling.
    public void runForSpu(Long spuId) {
        runForSpuSync(spuId);
    }

    /**
     * Synchronous execution for global polling worker.
     * Must run one-by-one (next starts after previous fully completes).
     */
    public void runForSpuSync(Long spuId) {
        if (spuId == null) return;

        // These are filled incrementally and finally persisted into sampleJson.
        final AtomicReference<Map<String, Object>> aiRef = new AtomicReference<>();
        final AtomicReference<String> templateRawRef = new AtomicReference<>();
        final AtomicReference<String> builtJsonRef = new AtomicReference<>();
        final AtomicReference<Map<String, Object>> imgRef = new AtomicReference<>();
        final AtomicReference<Long> runIdRef = new AtomicReference<>();
        try {
            log.info("PostImportAutomationService start spuId={}", spuId);
            if (!tryStart(spuId)) {
                log.info("PostImportAutomationService skip spuId={} (not claimed)", spuId);
                return;
            }

            if (postImportLogService != null) {
                runIdRef.set(safeStartRun(spuId));
                Long runId = runIdRef.get();
                if (runId != null) {
                    postImportLogService.info(runId, "START", "post-import automation started");
                }
            }

            // 1) AI fill + save TEMU attributes (slow)
            CompletableFuture<Map<String, Object>> aiFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    Long runId = runIdRef.get();
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.info(runId, "ATTR", "aiFillTemuAttributes start");
                    }
                    Map<String, Object> ai = productCollectionService.aiFillTemuAttributes(spuId);
                    aiRef.set(ai);
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.data(runId, "ATTR", "aiFillTemuAttributes result", ai);
                    }
                    ProductCollectionService.TemuCategoryAttributesFetchResult templateFetch = productCollectionService.getTemuCategoryAttributesFetchResult(spuId);
                    String templateRaw = templateFetch.rawTemplate();
                    templateRawRef.set(templateRaw);
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.data(runId, "ATTR", "temuCategoryAttributesRaw", templateFetch.toLogMap());
                    }
                    String savedJson = buildTemuAttributesJson(spuId, templateRaw, ai);
                    builtJsonRef.set(savedJson);
                    if (postImportLogService != null && runId != null) {
                        Map<String, Object> meta = new LinkedHashMap<>();
                        meta.put("len", savedJson == null ? 0 : savedJson.length());
                        // Saved json can be large; keep it in sampleJson, not in row-level logs.
                        postImportLogService.data(runId, "ATTR", "builtTemuAttributesJson", meta);
                    }
                    productCollectionService.saveTemuAttributes(spuId, savedJson);
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.info(runId, "ATTR", "saveTemuAttributes ok");
                    }

                    // Diagnostic: read back after save to detect any immediate overwrite.
                    try {
                        ProductCollection pc = productCollectionService.get(spuId);
                        int len = pc == null || pc.getTemuAttributes() == null ? 0 : pc.getTemuAttributes().length();
                        log.info("PostImportAutomationService spuId={} runId={} after saveTemuAttributes readback len={}", spuId, runId, len);
                    } catch (Exception ex) {
                        log.warn("PostImportAutomationService spuId={} runId={} after saveTemuAttributes readback failed: {}", spuId, runId, ex.getMessage());
                    }
                    return ai;
                } catch (Exception e) {
                    Long runId = runIdRef.get();
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.error(runId, "ATTR", "ai+attributes step failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), null);
                    }
                    throw new RuntimeException(e);
                }
            }, globalWorkerExecutor);

            // 3) Normalize images to kwcdn + 800x800 (slow, already parallel inside)
            CompletableFuture<Map<String, Object>> imgFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    Long runId = runIdRef.get();
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.info(runId, "IMG", "normalizeProductImagesToTemu800 start");
                    }
                    Map<String, Object> out = temuImageTranslateService.normalizeProductImagesToTemu800(spuId);
                    imgRef.set(out);
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.data(runId, "IMG", "normalizeProductImagesToTemu800 result", out);
                    }
                    return out;
                } catch (Exception e) {
                    Long runId = runIdRef.get();
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.error(runId, "IMG", "image normalize failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), null);
                    }
                    throw new RuntimeException(e);
                }
            }, globalWorkerExecutor);

            Map<String, Object> ai = aiFuture.get(20, TimeUnit.MINUTES);
            Map<String, Object> kw = imgFuture.get(30, TimeUnit.MINUTES);

            // Diagnostic: check whether temuAttributes got overwritten by concurrent image save.
            try {
                ProductCollection pcNow = productCollectionService.get(spuId);
                int lenNow = pcNow == null || pcNow.getTemuAttributes() == null ? 0 : pcNow.getTemuAttributes().length();
                log.info("PostImportAutomationService spuId={} runId={} after imgFuture.get readback temuAttributesLen={}", spuId, runIdRef.get(), lenNow);
                Long runId = runIdRef.get();
                if (postImportLogService != null && runId != null) {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("temuAttributesLen", lenNow);
                    postImportLogService.data(runId, "ATTR", "temuAttributesLen after IMG", meta);
                }
            } catch (Exception ex) {
                log.warn("PostImportAutomationService spuId={} runId={} after imgFuture.get readback failed: {}", spuId, runIdRef.get(), ex.getMessage());
            }

            // 2) SKU convert/init (default) (depends on attributes and images persisted)
            Long runId = runIdRef.get();
            if (postImportLogService != null && runId != null) {
                postImportLogService.info(runId, "SKU", "initFromOrigin start");
            }
            temuSkuService.initFromOrigin(spuId, false);
            if (postImportLogService != null && runId != null) {
                postImportLogService.info(runId, "SKU", "initFromOrigin ok");
            }

            String result = summarize(kw, ai);
            updateExec(spuId, 2, result);
            if (postImportLogService != null && runId != null) {
                try {
                    ProductCollection pcNow = productCollectionService.get(spuId);
                    int lenNow = pcNow == null || pcNow.getTemuAttributes() == null ? 0 : pcNow.getTemuAttributes().length();
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("temuAttributesLen", lenNow);
                    postImportLogService.data(runId, "ATTR", "temuAttributesLen before finishSuccess", meta);
                } catch (Exception ignored) {
                }
                String sample = buildSampleJson(runId,
                        spuId,
                        templateRawRef.get(),
                        aiRef.get(),
                        builtJsonRef.get(),
                        imgRef.get(),
                        null);
                postImportLogService.finishSuccess(runId, result, sample);
            }
            log.info("PostImportAutomationService success spuId={}", spuId);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (!StringUtils.hasText(msg)) msg = e.getClass().getSimpleName();
            updateExec(spuId, 3, msg);
            Long runId = runIdRef.get();
            if (postImportLogService != null && runId != null) {
                try {
                    String sample = buildSampleJson(runId,
                            spuId,
                            templateRawRef.get(),
                            aiRef.get(),
                            builtJsonRef.get(),
                            imgRef.get(),
                            e);
                    postImportLogService.finishFailed(runId, msg, sample);
                } catch (Exception ignored) {
                    postImportLogService.finishFailed(runId, msg, null);
                }
            }
            log.warn("PostImportAutomationService failed spuId={} msg={}", spuId, msg);
        }
    }

    /**
     * Worker path: task is already claimed (exec_status=1) by DB-level claim service.
     * Do NOT call tryStart() here.
     */
    public void runForSpuClaimedSync(Long spuId) {
        if (spuId == null) return;

        final AtomicReference<Map<String, Object>> aiRef = new AtomicReference<>();
        final AtomicReference<String> templateRawRef = new AtomicReference<>();
        final AtomicReference<String> builtJsonRef = new AtomicReference<>();
        final AtomicReference<Map<String, Object>> imgRef = new AtomicReference<>();
        final AtomicReference<Long> runIdRef = new AtomicReference<>();

        try {
            log.info("PostImportAutomationService start(claimed) spuId={}", spuId);
            if (postImportLogService != null) {
                runIdRef.set(safeStartRun(spuId));
                Long runId = runIdRef.get();
                if (runId != null) {
                    postImportLogService.info(runId, "START", "post-import automation started (claimed)");
                }
            }

            CompletableFuture<Map<String, Object>> aiFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    Long runId = runIdRef.get();
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.info(runId, "ATTR", "aiFillTemuAttributes start");
                    }
                    Map<String, Object> ai = productCollectionService.aiFillTemuAttributes(spuId);
                    aiRef.set(ai);
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.data(runId, "ATTR", "aiFillTemuAttributes result", ai);
                    }
                    String templateRaw = productCollectionService.getTemuCategoryAttributesRaw(spuId);
                    templateRawRef.set(templateRaw);
                    if (postImportLogService != null && runId != null) {
                        Map<String, Object> meta = new LinkedHashMap<>();
                        meta.put("len", templateRaw == null ? 0 : templateRaw.length());
                        meta.put("raw", templateRaw);
                        postImportLogService.data(runId, "ATTR", "temuCategoryAttributesRaw", meta);
                    }
                    String savedJson = buildTemuAttributesJson(spuId, templateRaw, ai);
                    builtJsonRef.set(savedJson);
                    if (postImportLogService != null && runId != null) {
                        Map<String, Object> meta = new LinkedHashMap<>();
                        meta.put("len", savedJson == null ? 0 : savedJson.length());
                        meta.put("json", savedJson);
                        postImportLogService.data(runId, "ATTR", "builtTemuAttributesJson", meta);
                    }
                    productCollectionService.saveTemuAttributes(spuId, savedJson);
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.info(runId, "ATTR", "saveTemuAttributes ok");
                    }

                    // Diagnostic: read back after save to detect any immediate overwrite.
                    try {
                        ProductCollection pc = productCollectionService.get(spuId);
                        int len = pc == null || pc.getTemuAttributes() == null ? 0 : pc.getTemuAttributes().length();
                        log.info("PostImportAutomationService(claimed) spuId={} runId={} after saveTemuAttributes readback len={}", spuId, runId, len);
                    } catch (Exception ex) {
                        log.warn("PostImportAutomationService(claimed) spuId={} runId={} after saveTemuAttributes readback failed: {}", spuId, runId, ex.getMessage());
                    }
                    return ai;
                } catch (Exception e) {
                    Long runId = runIdRef.get();
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.error(runId, "ATTR", "ai+attributes step failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), null);
                    }
                    throw new RuntimeException(e);
                }
            }, globalWorkerExecutor);

            CompletableFuture<Map<String, Object>> imgFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    Long runId = runIdRef.get();
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.info(runId, "IMG", "normalizeProductImagesToTemu800 start");
                    }
                    Map<String, Object> out = temuImageTranslateService.normalizeProductImagesToTemu800(spuId);
                    imgRef.set(out);
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.data(runId, "IMG", "normalizeProductImagesToTemu800 result", out);
                    }
                    return out;
                } catch (Exception e) {
                    Long runId = runIdRef.get();
                    if (postImportLogService != null && runId != null) {
                        postImportLogService.error(runId, "IMG", "image normalize failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), null);
                    }
                    throw new RuntimeException(e);
                }
            }, globalWorkerExecutor);

            Map<String, Object> ai = aiFuture.get(20, TimeUnit.MINUTES);
            Map<String, Object> kw = imgFuture.get(30, TimeUnit.MINUTES);

            // Diagnostic: check whether temuAttributes got overwritten by concurrent image save.
            try {
                ProductCollection pcNow = productCollectionService.get(spuId);
                int lenNow = pcNow == null || pcNow.getTemuAttributes() == null ? 0 : pcNow.getTemuAttributes().length();
                log.info("PostImportAutomationService(claimed) spuId={} runId={} after imgFuture.get readback temuAttributesLen={}", spuId, runIdRef.get(), lenNow);
                Long runId = runIdRef.get();
                if (postImportLogService != null && runId != null) {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("temuAttributesLen", lenNow);
                    postImportLogService.data(runId, "ATTR", "temuAttributesLen after IMG", meta);
                }
            } catch (Exception ex) {
                log.warn("PostImportAutomationService(claimed) spuId={} runId={} after imgFuture.get readback failed: {}", spuId, runIdRef.get(), ex.getMessage());
            }

            Long runId = runIdRef.get();
            if (postImportLogService != null && runId != null) {
                postImportLogService.info(runId, "SKU", "initFromOrigin start");
            }
            temuSkuService.initFromOrigin(spuId, false);
            if (postImportLogService != null && runId != null) {
                postImportLogService.info(runId, "SKU", "initFromOrigin ok");
            }

            String result = summarize(kw, ai);
            updateExec(spuId, 2, result);
            if (postImportLogService != null && runId != null) {
                try {
                    ProductCollection pcNow = productCollectionService.get(spuId);
                    int lenNow = pcNow == null || pcNow.getTemuAttributes() == null ? 0 : pcNow.getTemuAttributes().length();
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("temuAttributesLen", lenNow);
                    postImportLogService.data(runId, "ATTR", "temuAttributesLen before finishSuccess", meta);
                } catch (Exception ignored) {
                }
                String sample = buildSampleJson(runId,
                        spuId,
                        templateRawRef.get(),
                        aiRef.get(),
                        builtJsonRef.get(),
                        imgRef.get(),
                        null);
                postImportLogService.finishSuccess(runId, result, sample);
            }
            log.info("PostImportAutomationService success spuId={}", spuId);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (!StringUtils.hasText(msg)) msg = e.getClass().getSimpleName();
            updateExec(spuId, 3, msg);
            Long runId = runIdRef.get();
            if (postImportLogService != null && runId != null) {
                try {
                    String sample = buildSampleJson(runId,
                            spuId,
                            templateRawRef.get(),
                            aiRef.get(),
                            builtJsonRef.get(),
                            imgRef.get(),
                            e);
                    postImportLogService.finishFailed(runId, msg, sample);
                } catch (Exception ignored) {
                    postImportLogService.finishFailed(runId, msg, null);
                }
            }
            log.warn("PostImportAutomationService failed spuId={} msg={}", spuId, msg);
        }
    }

    private Long safeStartRun(Long spuId) {
        try {
            return postImportLogService.startRun(spuId).getId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildSampleJson(Long runId,
                                   Long spuId,
                                   String templateRaw,
                                   Map<String, Object> ai,
                                   String builtTemuAttributesJson,
                                   Map<String, Object> imgNormalize,
                                   Throwable error) {
        try {
            ProductCollection pc = productCollectionService.get(spuId);
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("runId", runId);
            sample.put("spuId", spuId);
            sample.put("temuCatid", pc == null ? null : pc.getTemuCatid());
            sample.put("productName", pc == null ? null : pc.getProductName());
            sample.put("attributesData", pc == null ? null : pc.getAttributesData());
            sample.put("originalContent", pc == null ? null : pc.getOriginalContent());
            sample.put("skuModel", pc == null ? null : pc.getSkuModel());
            sample.put("temuAttributesSaved", pc == null ? null : pc.getTemuAttributes());
            sample.put("templateRaw", templateRaw);
            sample.put("aiResp", ai);
            sample.put("builtTemuAttributesJson", builtTemuAttributesJson);
            sample.put("imgNormalize", imgNormalize);
            if (error != null) {
                sample.put("exception", error.getClass().getName());
                sample.put("exceptionMessage", error.getMessage());
                sample.put("exceptionStack", stackTraceString(error));
            }
            return objectMapper.writeValueAsString(sample);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stackTraceString(Throwable t) {
        if (t == null) return null;
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            t.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        } catch (Exception ignored) {
            return String.valueOf(t);
        }
    }

    /**
     * Ensure each imported product runs at most once (unless manually reset).
     * - running(1) and success(2) are treated as terminal for auto enqueue.
     */
    @Transactional
    protected boolean tryStart(Long spuId) {
        if (spuId == null) return false;

        // Multi-instance safe claim: only one instance can flip 0->1.
        int claimed = 0;
        try {
            claimed = repo.claimExecTask(spuId, "running");
        } catch (Exception ignored) {
        }
        if (claimed <= 0) {
            return false;
        }
        return true;
    }

    @Transactional
    protected void updateExec(Long spuId, int status, String result) {
        Optional<ProductCollection> opt = repo.findById(spuId);
        if (opt.isEmpty()) return;
        ProductCollection pc = opt.get();
        pc.setExecStatus(status);
        pc.setExecResult(result);
        pc.setUpdatedAt(LocalDateTime.now());
        repo.save(pc);
    }

    private String summarize(Map<String, Object> kw, Map<String, Object> ai) {
        StringBuilder sb = new StringBuilder();
        if (kw != null) {
            Object changed = kw.get("changed");
            Object cc = kw.get("carouselCount");
            Object dc = kw.get("detailCount");
            sb.append("kwcdnChanged=").append(changed).append(", carousel=").append(cc).append(", detail=").append(dc);
        }
        if (ai != null) {
            Object ok = ai.get("success");
            Object miss = ai.get("missingRequiredPids");
            sb.append("; aiSuccess=").append(ok);
            if (miss != null) sb.append("; missing=").append(miss);
        }
        return sb.toString();
    }

    private String buildTemuAttributesJson(Long spuId, String templateRaw, Map<String, Object> aiResp) throws Exception {
        if (aiResp == null || !Boolean.TRUE.equals(aiResp.get("success"))) {
            String em = aiResp == null ? null : String.valueOf(aiResp.get("errorMsg"));
            if (!StringUtils.hasText(em)) em = "AI fill failed";
            throw new IllegalStateException(em);
        }
        if (!StringUtils.hasText(templateRaw)) {
            throw new IllegalStateException("Missing TEMU attribute template");
        }

        ProductCollection pc = productCollectionService.get(spuId);
        String leafCatId = extractLeafCatId(pc.getTemuCatid());

        // template pid -> meta
        JsonNode root = objectMapper.readTree(templateRaw);
        JsonNode props = root.path("result").path("properties");
        if (!props.isArray()) throw new IllegalStateException("Template properties not found");

        Map<Integer, JsonNode> templateByPid = new LinkedHashMap<>();
        for (JsonNode p : props) {
            int pid = p.path("pid").asInt(0);
            if (pid > 0) templateByPid.put(pid, p);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filled = aiResp.get("properties") instanceof List<?> l ? (List<Map<String, Object>>) aiResp.get("properties") : Collections.emptyList();
        @SuppressWarnings("unchecked")
        List<Integer> missing = aiResp.get("missingRequiredPids") instanceof List<?> l2 ? (List<Integer>) l2 : Collections.emptyList();

        filled = pruneInapplicableFilledProps(filled, templateByPid);

        // Build helper sets for parent-child applicability checks.
        // Some required attributes are only applicable when their parent vids are selected.
        // The frontend modal uses the same rule: child required should not block save
        // when no matching parent selection exists.
        Set<String> selectedVidsAll = new LinkedHashSet<>();
        Map<Integer, Set<String>> selectedVidsByRefPid = new LinkedHashMap<>();
        for (Map<String, Object> p : filled) {
            if (p == null) continue;
            JsonNode tpl = resolveTemplateNode(p, templateByPid);
            Object sv = p.get("selectedVids");
            if (!(sv instanceof List<?> list)) continue;
            Integer refPid = tpl == null ? null : toInt(tpl.path("refPid").asText(""));
            for (Object o : list) {
                if (o == null) continue;
                String s = String.valueOf(o).trim();
                if (!StringUtils.hasText(s)) continue;
                selectedVidsAll.add(s);
                if (refPid != null && refPid > 0) {
                    selectedVidsByRefPid.computeIfAbsent(refPid, ignored -> new LinkedHashSet<>()).add(s);
                }
            }
        }

        Set<Integer> forceEmpty = new HashSet<>();
        if (aiConfig != null && aiConfig.getForceEmptyPids() != null) {
            forceEmpty.addAll(aiConfig.getForceEmptyPids());
        }

        // If still missing required (excluding force-empty and non-applicable child attrs), mark as failure.
        // "Non-applicable" is determined by template parent rules.
        // Some downstream logic (and our own backfill) may fill required attributes even when
        // the model originally reported them as missing. Treat a pid as missing only if it is
        // still not present in the filled properties.
        Set<Integer> filledPids = new HashSet<>();
        for (Map<String, Object> p : filled) {
            if (p == null) continue;
            Integer pid = toInt(p.get("pid"));
            if (pid == null || pid <= 0) continue;
            if (forceEmpty.contains(pid)) continue;
            if (hasAnySelection(p)) {
                filledPids.add(pid);
            }
        }

        List<Integer> effectiveMissing = new ArrayList<>();
        for (Integer m : missing) {
            if (m == null) continue;
            if (forceEmpty.contains(m)) continue;
            if (filledPids.contains(m)) continue;

            // Conditional required: if pid is a child attribute with parent rules, and no matching
            // parent vid is selected, then this required attribute is not applicable.
            // Do not fail automation for such pids.
            JsonNode tpl = templateByPid.get(m);
            if (tpl != null && !isTemplateApplicable(tpl, selectedVidsAll, selectedVidsByRefPid)) {
                continue;
            }
            effectiveMissing.add(m);
        }
        if (!effectiveMissing.isEmpty()) {
            throw new IllegalStateException("Missing required attributes: " + effectiveMissing);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("leafCatId", leafCatId);
        out.put("savedAt", java.time.OffsetDateTime.now().toString());
        List<Map<String, Object>> outProps = new ArrayList<>();

        for (Map<String, Object> p : filled) {
            if (p == null) continue;
            Integer pid = toInt(p.get("pid"));
            if (pid == null || pid <= 0) continue;
            if (forceEmpty.contains(pid)) continue;

            JsonNode tpl = templateByPid.get(pid);
            if (tpl == null) continue;
            if (!isTemplateApplicable(tpl, selectedVidsAll, selectedVidsByRefPid)) continue;

            Map<String, Object> one = new LinkedHashMap<>();
            one.put("pid", pid);
            one.put("templatePid", tpl.path("templatePid").asInt(0));
            one.put("refPid", tpl.path("refPid").asInt(0));
            one.put("name", tpl.path("name").asText(""));
            one.put("required", tpl.path("required").asBoolean(false));

            String valueUnit = "";
            JsonNode vu = tpl.path("valueUnit");
            if (vu.isArray() && vu.size() > 0) valueUnit = vu.get(0).asText("");
            one.put("valueUnit", valueUnit);

            @SuppressWarnings("unchecked")
            List<String> selectedVids = p.get("selectedVids") instanceof List<?> sv ? sv.stream().filter(Objects::nonNull).map(x -> String.valueOf(x).trim()).filter(StringUtils::hasText).toList() : List.of();
            String freeText = p.get("freeText") == null ? null : String.valueOf(p.get("freeText")).trim();

            // Build selectedValues from template values list
            Map<String, String> vidToValue = new LinkedHashMap<>();
            JsonNode vals = tpl.path("values");
            if (vals.isArray()) {
                for (JsonNode v : vals) {
                    String vid = v.path("vid").asText("");
                    if (!StringUtils.hasText(vid)) {
                        int n = v.path("vid").asInt(0);
                        if (n > 0) vid = String.valueOf(n);
                    }
                    String vv = v.path("value").asText("");
                    if (StringUtils.hasText(vid)) vidToValue.put(vid, vv);
                }
            }

            one.put("selectedVids", selectedVids);
            List<Map<String, Object>> selectedValues = new ArrayList<>();
            for (String vid : selectedVids) {
                Map<String, Object> svv = new LinkedHashMap<>();
                svv.put("vid", toInt(vid));
                svv.put("value", vidToValue.getOrDefault(vid, vid));
                selectedValues.add(svv);
            }
            one.put("selectedValues", selectedValues);

            if (StringUtils.hasText(freeText)) {
                one.put("freeText", freeText);
                one.put("numberInputValue", freeText);
            } else {
                one.put("freeText", null);
                one.put("numberInputValue", "");
            }

            boolean isEmpty = selectedVids.isEmpty() && !StringUtils.hasText(freeText);
            if (!isEmpty) {
                outProps.add(one);
            }
        }

        out.put("properties", outProps);
        return objectMapper.writeValueAsString(out);
    }

    private String extractLeafCatId(String catid) {
        if (!StringUtils.hasText(catid)) return null;
        String[] parts = catid.trim().split(",");
        return parts.length == 0 ? null : parts[parts.length - 1].trim();
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Map<String, Object>> pruneInapplicableFilledProps(List<Map<String, Object>> filled,
                                                                   Map<Integer, JsonNode> templateByPid) {
        if (filled == null || filled.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> current = new ArrayList<>(filled);
        for (int i = 0; i < 5; i++) {
            Set<String> selectedVidsAll = new LinkedHashSet<>();
            Map<Integer, Set<String>> selectedVidsByRefPid = new LinkedHashMap<>();
            for (Map<String, Object> p : current) {
                if (p == null || !hasAnySelection(p)) continue;
                JsonNode tpl = resolveTemplateNode(p, templateByPid);
                Object sv = p.get("selectedVids");
                if (!(sv instanceof List<?> list)) continue;
                Integer refPid = tpl == null ? null : toInt(tpl.path("refPid").asText(""));
                for (Object o : list) {
                    if (o == null) continue;
                    String s = String.valueOf(o).trim();
                    if (!StringUtils.hasText(s)) continue;
                    selectedVidsAll.add(s);
                    if (refPid != null && refPid > 0) {
                        selectedVidsByRefPid.computeIfAbsent(refPid, ignored -> new LinkedHashSet<>()).add(s);
                    }
                }
            }

            List<Map<String, Object>> next = new ArrayList<>();
            boolean changed = false;
            for (Map<String, Object> p : current) {
                if (p == null) continue;
                JsonNode tpl = resolveTemplateNode(p, templateByPid);
                if (tpl != null && hasAnySelection(p) && !isTemplateApplicable(tpl, selectedVidsAll, selectedVidsByRefPid)) {
                    changed = true;
                    continue;
                }
                next.add(p);
            }
            current = next;
            if (!changed) {
                break;
            }
        }
        return current;
    }

    private JsonNode resolveTemplateNode(Map<String, Object> property,
                                         Map<Integer, JsonNode> templateByPid) {
        if (property == null) return null;
        Integer pid = toInt(property.get("pid"));
        if (pid == null || pid <= 0) return null;
        return templateByPid.get(pid);
    }

    private boolean isTemplateApplicable(JsonNode tpl,
                                         Set<String> selectedVidsAll,
                                         Map<Integer, Set<String>> selectedVidsByRefPid) {
        if (tpl == null || tpl.isNull()) {
            return true;
        }
        JsonNode parents = tpl.get("templatePropertyValueParentList");
        if (parents != null && parents.isArray() && parents.size() > 0) {
            boolean hasParentSelected = false;
            for (JsonNode rule : parents) {
                JsonNode parentVidList = rule == null ? null : rule.get("parentVidList");
                if (parentVidList == null || !parentVidList.isArray()) continue;
                for (JsonNode pv : parentVidList) {
                    if (pv == null || pv.isNull()) continue;
                    String s = pv.isTextual() ? pv.asText("") : String.valueOf(pv.asInt(0));
                    if (StringUtils.hasText(s) && selectedVidsAll.contains(s.trim())) {
                        hasParentSelected = true;
                        break;
                    }
                }
                if (hasParentSelected) break;
            }
            if (!hasParentSelected) {
                return false;
            }
        }

        JsonNode showConditions = tpl.get("showCondition");
        if (showConditions != null && showConditions.isArray() && showConditions.size() > 0) {
            for (JsonNode cond : showConditions) {
                if (cond == null || cond.isNull()) continue;
                Integer parentRefPid = toInt(cond.path("parentRefPid").asText(""));
                Set<String> selected = parentRefPid == null ? Collections.emptySet() : selectedVidsByRefPid.getOrDefault(parentRefPid, Collections.emptySet());
                JsonNode parentVids = cond.get("parentVids");
                boolean matched = false;
                if (parentVids != null && parentVids.isArray()) {
                    for (JsonNode pv : parentVids) {
                        if (pv == null || pv.isNull()) continue;
                        String s = pv.isTextual() ? pv.asText("") : String.valueOf(pv.asInt(0));
                        if (StringUtils.hasText(s) && selected.contains(s.trim())) {
                            matched = true;
                            break;
                        }
                    }
                }
                if (!matched) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasAnySelection(Map<String, Object> p) {
        if (p == null) return false;
        Object free = p.get("freeText");
        if (free != null && StringUtils.hasText(String.valueOf(free))) {
            return true;
        }
        Object sv = p.get("selectedVids");
        if (sv instanceof List<?> l) {
            for (Object o : l) {
                if (o != null && StringUtils.hasText(String.valueOf(o))) {
                    return true;
                }
            }
        }
        return false;
    }
}
