package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.TemuMainSaleSpecInferenceDTO;
import com.tminos.productscene.entity.TemuMainSaleSpecInferenceTask;
import com.tminos.productscene.service.TemuMainSaleSpecInferenceService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/temu-main-sale-spec-inference")
public class TemuMainSaleSpecInferenceController {

    private final TemuMainSaleSpecInferenceService service;

    public TemuMainSaleSpecInferenceController(TemuMainSaleSpecInferenceService service) {
        this.service = service;
    }

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<Page<TemuMainSaleSpecInferenceDTO.TaskRow>>> list(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        Page<TemuMainSaleSpecInferenceTask> p = service.list(q, status, page, size);
        Page<TemuMainSaleSpecInferenceDTO.TaskRow> out = p.map(this::toRow);
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<TemuMainSaleSpecInferenceDTO.TaskDetail>> get(@PathVariable Long id) {
        TemuMainSaleSpecInferenceTask t = service.get(id);
        if (t == null) return ResponseEntity.ok(ApiResponse.error("not found"));
        return ResponseEntity.ok(ApiResponse.success(toDetail(t)));
    }

    @PostMapping("/tasks")
    public ResponseEntity<ApiResponse<TemuMainSaleSpecInferenceDTO.TaskRow>> create(
            @RequestBody TemuMainSaleSpecInferenceDTO.CreateTaskRequest req
    ) {
        Long spuId = req == null ? null : req.getSpuId();
        TemuMainSaleSpecInferenceTask existed = service.getBySpuId(spuId);
        TemuMainSaleSpecInferenceTask t = service.createTask(spuId);
        String message = existed == null ? "Created" : "Already exists";
        return ResponseEntity.ok(ApiResponse.success(message, toRow(t)));
    }

    @PostMapping("/tasks/{id}/run")
    public ResponseEntity<ApiResponse<TemuMainSaleSpecInferenceDTO.TaskRow>> run(@PathVariable Long id) {
        try {
            TemuMainSaleSpecInferenceTask t = service.runOnce(id);
            return ResponseEntity.ok(ApiResponse.success(toRow(t)));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        try {
            service.deleteTask(id);
            return ResponseEntity.ok(ApiResponse.success("Deleted", null));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    private TemuMainSaleSpecInferenceDTO.TaskRow toRow(TemuMainSaleSpecInferenceTask t) {
        if (t == null) return null;
        return TemuMainSaleSpecInferenceDTO.TaskRow.builder()
                .id(t.getId())
                .inferenceId(t.getInferenceId())
                .spuId(t.getSpuId())
                .productName(t.getProductName())
                .productMainImage(t.getProductMainImage())
                .status(t.getStatus())
                .resultSummary(t.getResultSummary())
                .startedAt(t.getStartedAt())
                .finishedAt(t.getFinishedAt())
                .createdAt(t.getCreatedAt())
                .build();
    }

    private TemuMainSaleSpecInferenceDTO.TaskDetail toDetail(TemuMainSaleSpecInferenceTask t) {
        if (t == null) return null;
        return TemuMainSaleSpecInferenceDTO.TaskDetail.builder()
                .id(t.getId())
                .inferenceId(t.getInferenceId())
                .spuId(t.getSpuId())
                .productName(t.getProductName())
                .productMainImage(t.getProductMainImage())
                .status(t.getStatus())
                .leafCatId(t.getLeafCatId())
                .resultSummary(t.getResultSummary())
                .startedAt(t.getStartedAt())
                .finishedAt(t.getFinishedAt())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .promptText(t.getPromptText())
                .responseRaw(t.getResponseRaw())
                .responseContent(t.getResponseContent())
                .parsedJson(t.getParsedJson())
                .resultJson(t.getResultJson())
                .mainProductSkuSpecReqs(t.getMainProductSkuSpecReqs())
                .productSpecPropertyReqs(t.getProductSpecPropertyReqs())
                .productSkuReqs(t.getProductSkuReqs())
                .errorMsg(t.getErrorMsg())
                .build();
    }
}
