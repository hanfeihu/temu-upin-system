package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.entity.BizLogRecord;
import com.tminos.productscene.service.BizLogRecordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/biz-logs")
public class BizLogRecordController {

    private final BizLogRecordService service;

    public BizLogRecordController(BizLogRecordService service) {
        this.service = service;
    }

    public static class CreateReq {
        @NotBlank
        public String bizName;

        @NotNull
        public String content;
    }

    public static class UpdateReq {
        public String bizName;
        public String content;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<BizLogRecord>>> list(
            @RequestParam(value = "bizName", required = false) String bizName,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(bizName, page, size)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BizLogRecord>> create(@Valid @RequestBody CreateReq req) {
        return ResponseEntity.ok(ApiResponse.success("Created", service.create(req.bizName, req.content)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BizLogRecord>> update(@PathVariable Long id, @Valid @RequestBody UpdateReq req) {
        return ResponseEntity.ok(ApiResponse.success("Updated", service.update(id, req == null ? null : req.bizName, req == null ? null : req.content)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
