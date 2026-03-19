package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.ImageOcrTask;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.repository.ImageOcrTaskRepository;
import com.tminos.productscene.repository.ProductCollectionSkuRepository;
import com.tminos.productscene.repository.ProductCollectionTemuSkuRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TemuAutoPublishEligibilityService {

    public static final int PRODUCT_OCR_STATUS_SUCCESS = 2;
    public static final int PRODUCT_EXEC_STATUS_SUCCESS = 2;

    private final ImageOcrTaskRepository ocrTaskRepo;
    private final ProductCollectionSkuRepository originSkuRepo;
    private final ProductCollectionTemuSkuRepository temuSkuRepo;
    private final ObjectMapper objectMapper;

    public TemuAutoPublishEligibilityService(ImageOcrTaskRepository ocrTaskRepo,
                                            ProductCollectionSkuRepository originSkuRepo,
                                            ProductCollectionTemuSkuRepository temuSkuRepo,
                                            ObjectMapper objectMapper) {
        this.ocrTaskRepo = ocrTaskRepo;
        this.originSkuRepo = originSkuRepo;
        this.temuSkuRepo = temuSkuRepo;
        this.objectMapper = objectMapper;
    }

    public EligibilityResult check(ProductCollection pc) {
        return check(pc, null);
    }

    public EligibilityResult check(ProductCollection pc, Integer preClaimCollectionStatus) {
        List<String> reasons = new ArrayList<>();
        Map<String, Object> debug = new LinkedHashMap<>();
        List<RuleCheck> checks = new ArrayList<>();

        if (pc == null || pc.getId() == null) {
            reasons.add("product is null");
            checks.add(new RuleCheck("R0", "product not null", false, Map.of()));
            return new EligibilityResult(false, reasons, debug, checks);
        }

        // 0) Product publish status must be "未发布" before claim
        // collectionStatus: 0 未发布, 1 发布中, 2 发布失败
        boolean r0 = (preClaimCollectionStatus == null || preClaimCollectionStatus == 0);
        checks.add(new RuleCheck(
                "R0",
                "Product status is 未发布",
                r0,
                Map.of(
                        "expected", "preClaimCollectionStatus is null or 0",
                        "actual", preClaimCollectionStatus,
                        "display", "Publish status(pre-claim): " + (preClaimCollectionStatus == null ? "null" : String.valueOf(preClaimCollectionStatus)) + " (need 0)"
                )
        ));
        if (!r0) reasons.add("collectionStatus not 未发布 (pre-claim)");

        // 1) OCR status must be success (2)
        Integer ocrStatus = pc.getOcrStatus();
        debug.put("ocrStatus", ocrStatus);
        boolean r1 = (ocrStatus != null && ocrStatus == PRODUCT_OCR_STATUS_SUCCESS);
        checks.add(new RuleCheck(
                "R1",
                "OCR status completed",
                r1,
                Map.of(
                        "expected", "ocrStatus == 2",
                        "actual", ocrStatus,
                        "display", "OCR status: " + (ocrStatus == null ? "null" : String.valueOf(ocrStatus)) + " (need 2)"
                )
        ));
        if (!r1) reasons.add("ocrStatus not success");

        // 2) Exec status must be success (2)
        Integer execStatus = pc.getExecStatus();
        debug.put("execStatus", execStatus);
        boolean r2 = (execStatus != null && execStatus == PRODUCT_EXEC_STATUS_SUCCESS);
        checks.add(new RuleCheck(
                "R2",
                "Execution status completed",
                r2,
                Map.of(
                        "expected", "execStatus == 2",
                        "actual", execStatus,
                        "display", "Exec status: " + (execStatus == null ? "null" : String.valueOf(execStatus)) + " (need 2)"
                )
        ));
        if (!r2) reasons.add("execStatus not success");

        // 3) Carousel image count <= 10
        int carouselCount = countJsonArray(pc.getCarouselImages());
        debug.put("carouselImageCount", carouselCount);
        boolean r3 = carouselCount <= 10;
        checks.add(new RuleCheck(
                "R3",
                "Carousel images <= 10",
                r3,
                Map.of(
                        "expected", "carouselImageCount <= 10",
                        "actual", carouselCount,
                        "display", "Carousel images: " + carouselCount + "/10"
                )
        ));
        if (!r3) reasons.add("carouselImageCount > 10");

        // 4) Temu category id not empty
        debug.put("temuCatid", pc.getTemuCatid());
        boolean r4 = StringUtils.hasText(pc.getTemuCatid());
        checks.add(new RuleCheck(
                "R4",
                "Temu category id not empty",
                r4,
                Map.of(
                        "expected", "temuCatid not blank",
                        "actual", pc.getTemuCatid(),
                        "display", "Temu catid: " + (r4 ? "OK" : "EMPTY")
                )
        ));
        if (!r4) reasons.add("temuCatid empty");

        // 5) Temu attributes not empty
        debug.put("hasTemuAttributes", StringUtils.hasText(pc.getTemuAttributes()));
        boolean r5 = StringUtils.hasText(pc.getTemuAttributes());
        checks.add(new RuleCheck(
                "R5",
                "Temu attributes not empty",
                r5,
                Map.of(
                        "expected", "temuAttributes not blank",
                        "actual", r5,
                        "display", "Temu attributes: " + (r5 ? "OK" : "EMPTY")
                )
        ));
        if (!r5) reasons.add("temuAttributes empty");

        // 6) For tasks with filtered=false: OCR result must NOT contain ANY Chinese characters
        List<ImageOcrTask> tasks = ocrTaskRepo.findBySpuId(pc.getId());
        int checked = 0;
        int chineseHitTasks = 0;
        List<Long> chineseHitTaskIds = new ArrayList<>();
        if (tasks != null) {
            for (ImageOcrTask t : tasks) {
                if (t == null) continue;
                if (Boolean.TRUE.equals(t.getFiltered())) {
                    continue;
                }
                // Only consider OCR-succeeded tasks; otherwise treat as "not ready" => not eligible
                if (t.getExecStatus() == null || t.getExecStatus() != ImageOcrTask.STATUS_SUCCESS) {
                    continue;
                }
                String text = t.getExecResult();
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                checked++;
                if (containsChinese(text)) {
                    chineseHitTasks++;
                    if (t.getId() != null) chineseHitTaskIds.add(t.getId());
                }
            }
        }
        debug.put("ocrCheckedNotFilteredCount", checked);
        debug.put("ocrChineseHitTaskCount", chineseHitTasks);
        boolean r6 = chineseHitTasks == 0;
        checks.add(new RuleCheck(
                "R6",
                "OCR text must not contain any Chinese characters for filtered=false images",
                r6,
                Map.of(
                        "expected", "chineseHitTasks == 0",
                        "actual", Map.of("checked", checked, "chineseHitTasks", chineseHitTasks, "taskIds", chineseHitTaskIds),
                        "display", "OCR(chinese hit): " + chineseHitTasks + " tasks / checked " + checked
                )
        ));
        if (!r6) reasons.add("ocrText contains Chinese characters (not-filtered images)");

        // 7) Origin SKU count < 10
        int originSkuCount = originSkuRepo.findBySpuId(pc.getId()).size();
        debug.put("originSkuCount", originSkuCount);
        boolean r7 = originSkuCount < 10;
        checks.add(new RuleCheck(
                "R7",
                "Origin SKU count < 10",
                r7,
                Map.of(
                        "expected", "originSkuCount < 10",
                        "actual", originSkuCount,
                        "display", "Origin SKU count: " + originSkuCount + " (need < 10)"
                )
        ));
        if (!r7) reasons.add("originSkuCount >= 10");

        // 8) TEMU sku count > 0
        int temuSkuCount = temuSkuRepo.findBySpuIdOrderByIdAsc(pc.getId()).size();
        debug.put("temuSkuCount", temuSkuCount);
        boolean r8 = temuSkuCount > 0;
        checks.add(new RuleCheck(
                "R8",
                "TEMU SKU count > 0",
                r8,
                Map.of(
                        "expected", "temuSkuCount > 0",
                        "actual", temuSkuCount,
                        "display", "TEMU SKU count: " + temuSkuCount + " (need > 0)"
                )
        ));
        if (!r8) reasons.add("temuSkuCount <= 0");

        boolean ok = reasons.isEmpty();
        return new EligibilityResult(ok, reasons, debug, checks);
    }

    private int countJsonArray(String json) {
        if (!StringUtils.hasText(json)) return 0;
        String s = json.trim();
        if (s.isEmpty()) return 0;
        if (!s.startsWith("[")) return 0;
        try {
            List<?> arr = objectMapper.readValue(s, List.class);
            return arr == null ? 0 : arr.size();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean containsChinese(String text) {
        if (!StringUtils.hasText(text)) return false;
        // Basic CJK Unified Ideographs block.
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u4E00' && c <= '\u9FFF') return true;
        }
        return false;
    }

    public record RuleCheck(String ruleId, String ruleName, boolean passed, Map<String, Object> details) {
    }

    public record EligibilityResult(boolean eligible, List<String> reasons, Map<String, Object> debug, List<RuleCheck> checks) {
    }
}
