package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.TemuAttrAiFillDTO;
import com.tminos.productscene.entity.TemuAttrAiFillTask;
import com.tminos.productscene.service.TemuAttrAiFillService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/temu-attr-ai-fill")
public class TemuAttrAiFillController {

    private final TemuAttrAiFillService service;

    public TemuAttrAiFillController(TemuAttrAiFillService service) {
        this.service = service;
    }

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<Page<TemuAttrAiFillDTO.TaskRow>>> list(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        Page<TemuAttrAiFillTask> p = service.list(q, status, page, size);
        Page<TemuAttrAiFillDTO.TaskRow> out = p.map(this::toRow);
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<TemuAttrAiFillDTO.TaskDetail>> get(@PathVariable Long id) {
        TemuAttrAiFillTask t = service.get(id);
        if (t == null) return ResponseEntity.ok(ApiResponse.error("not found"));
        return ResponseEntity.ok(ApiResponse.success(toDetail(t)));
    }

    @PostMapping("/tasks")
    public ResponseEntity<ApiResponse<TemuAttrAiFillDTO.TaskRow>> create(@RequestBody TemuAttrAiFillDTO.CreateTaskRequest req) {
        Long spuId = req == null ? null : req.getSpuId();
        TemuAttrAiFillTask t = service.createTask(spuId);
        return ResponseEntity.ok(ApiResponse.success("Created", toRow(t)));
    }

    @PostMapping("/tasks/{id}/run")
    public ResponseEntity<ApiResponse<TemuAttrAiFillDTO.TaskRow>> run(@PathVariable Long id) {
        try {
            TemuAttrAiFillTask t = service.runOnce(id);
            return ResponseEntity.ok(ApiResponse.success(toRow(t)));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    private TemuAttrAiFillDTO.TaskRow toRow(TemuAttrAiFillTask t) {
        if (t == null) return null;
        return TemuAttrAiFillDTO.TaskRow.builder()
                .id(t.getId())
                .taskId(t.getTaskId())
                .spuId(t.getSpuId())
                .productName(t.getProductName())
                .productMainImage(t.getProductMainImage())
                .status(t.getStatus())
                .leafCatId(t.getLeafCatId())
                .resultSummary(t.getResultSummary())
                .errorMsg(t.getErrorMsg())
                .startedAt(t.getStartedAt())
                .finishedAt(t.getFinishedAt())
                .createdAt(t.getCreatedAt())
                .build();
    }

    private TemuAttrAiFillDTO.TaskDetail toDetail(TemuAttrAiFillTask t) {
        if (t == null) return null;
        return TemuAttrAiFillDTO.TaskDetail.builder()
                .id(t.getId())
                .taskId(t.getTaskId())
                .spuId(t.getSpuId())
                .productName(t.getProductName())
                .productMainImage(t.getProductMainImage())
                .status(t.getStatus())
                .leafCatId(t.getLeafCatId())
                .startedAt(t.getStartedAt())
                .finishedAt(t.getFinishedAt())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .templateRaw(t.getTemplateRaw())
                .promptText(t.getPromptText())
                .responseRaw(t.getResponseRaw())
                .parsedJson(t.getParsedJson())
                .resultJson(t.getResultJson())
                .ruleActions(t.getRuleActions())
                .resultSummary(t.getResultSummary())
                .errorMsg(t.getErrorMsg())
                .build();
    }
}
