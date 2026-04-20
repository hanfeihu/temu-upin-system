package com.tminos.productscene.controller;

import com.tminos.productscene.dto.AiVariantPublishDTO;
import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.service.AiVariantPublishService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/ai-variant-publish")
public class AiVariantPublishController {

    private final AiVariantPublishService aiVariantPublishService;

    public AiVariantPublishController(AiVariantPublishService aiVariantPublishService) {
        this.aiVariantPublishService = aiVariantPublishService;
    }

    @PostMapping("/raw")
    public ResponseEntity<ApiResponse<AiVariantPublishDTO.RecordDetail>> publishRaw(
            @RequestBody AiVariantPublishDTO.RawPublishRequest request
    ) {
        AiVariantPublishDTO.RecordDetail detail = aiVariantPublishService.publishRaw(request);
        String message = "SUCCEEDED".equalsIgnoreCase(detail.getStatus()) ? "发布成功" : "发布已执行，状态为 " + detail.getStatus();
        return ResponseEntity.ok(ApiResponse.success(message, detail));
    }

    @PostMapping("/draft/from-product-collection")
    public ResponseEntity<ApiResponse<AiVariantPublishDTO.GenerateDraftResponse>> generateDraftFromProductCollection(
            @RequestBody AiVariantPublishDTO.GenerateDraftRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "已生成草稿",
                aiVariantPublishService.generateDraftFromProductCollection(request)
        ));
    }

    @GetMapping("/records")
    public ResponseEntity<ApiResponse<List<AiVariantPublishDTO.RecordSummary>>> listRecords(
            @RequestParam(value = "shopRecordId", required = false) Long shopRecordId,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiVariantPublishService.listRecords(shopRecordId, size)));
    }

    @GetMapping("/records/{id}")
    public ResponseEntity<ApiResponse<AiVariantPublishDTO.RecordDetail>> getRecord(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.success(aiVariantPublishService.getRecord(id)));
    }
}
