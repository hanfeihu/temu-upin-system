package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.dto.DianxiaomiPackageFeeDTO;
import com.tminos.productscene.service.DianxiaomiPackageFeeService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/dianxiaomi-package-fees")
public class DianxiaomiPackageFeeController {

    private final DianxiaomiPackageFeeService service;

    public DianxiaomiPackageFeeController(DianxiaomiPackageFeeService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DianxiaomiPackageFeeDTO.ListItem>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(keyword, page, pageSize)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DianxiaomiPackageFeeDTO.Summary>> summary(
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.summary(keyword)));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<Long>> clear() {
        long deletedCount = service.clearAll();
        return ResponseEntity.ok(ApiResponse.success("已清空 " + deletedCount + " 条数据", deletedCount));
    }

    @DeleteMapping("/clear-success")
    public ResponseEntity<ApiResponse<Long>> clearSuccess() {
        long deletedCount = service.clearSuccessRecords();
        return ResponseEntity.ok(ApiResponse.success("已清空成功记录 " + deletedCount + " 条", deletedCount));
    }

    @PostMapping("/retry-failed")
    public ResponseEntity<ApiResponse<DianxiaomiPackageFeeDTO.ImportResult>> retryFailed() {
        return ResponseEntity.ok(ApiResponse.success("重试完成", service.retryFailedRecords()));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<DianxiaomiPackageFeeDTO.ImportResult>> retryRecord(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("重试完成", service.retryRecord(id)));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<DianxiaomiPackageFeeDTO.ImportResult>> importPackageNumbers(
            @RequestBody DianxiaomiPackageFeeDTO.ImportRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("导入完成", service.importPackageNumbers(request)));
    }
}
