package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.ProductCollection;
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

    private final ProductCollectionTemuSkuRepository temuSkuRepo;
    private final ObjectMapper objectMapper;

    public TemuAutoPublishEligibilityService(ProductCollectionTemuSkuRepository temuSkuRepo,
                                            ObjectMapper objectMapper) {
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

        // 1) OCR status is informational only. Auto publish no longer blocks Chinese images.
        Integer ocrStatus = pc.getOcrStatus();
        debug.put("ocrStatus", ocrStatus);
        checks.add(new RuleCheck(
                "R1",
                "OCR status is not required",
                true,
                Map.of(
                        "expected", "allow any OCR status",
                        "actual", ocrStatus,
                        "display", "OCR status: " + (ocrStatus == null ? "null" : String.valueOf(ocrStatus)) + " (not required)"
                )
        ));

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

        // 6) Chinese text in images is allowed for auto publish.
        debug.put("sourcePlatform", pc.getSourcePlatform());
        debug.put("ocrChineseCheckApplied", false);
        checks.add(new RuleCheck(
                "R6",
                "Chinese images are allowed",
                true,
                Map.of(
                        "expected", "skip OCR Chinese validation",
                        "actual", Map.of(
                                "sourcePlatform", pc.getSourcePlatform(),
                                "checkApplied", false
                        ),
                        "display", "OCR chinese check skipped"
                )
        ));

                // 7) TEMU sku count > 0
        int temuSkuCount = temuSkuRepo.findBySpuIdOrderByIdAsc(pc.getId()).size();
        debug.put("temuSkuCount", temuSkuCount);
                boolean r7 = temuSkuCount > 0;
        checks.add(new RuleCheck(
                                "R7",
                "TEMU SKU count > 0",
                                r7,
                Map.of(
                        "expected", "temuSkuCount > 0",
                        "actual", temuSkuCount,
                        "display", "TEMU SKU count: " + temuSkuCount + " (need > 0)"
                )
        ));
                if (!r7) reasons.add("temuSkuCount <= 0");

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

    public record RuleCheck(String ruleId, String ruleName, boolean passed, Map<String, Object> details) {
    }

    public record EligibilityResult(boolean eligible, List<String> reasons, Map<String, Object> debug, List<RuleCheck> checks) {
    }
}
