package com.tminos.productscene.controller;

import com.tminos.productscene.dto.Alibaba1688SelectionPoolFilterCategoryDTO;
import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.service.Alibaba1688SelectionPoolFilterCategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/alibaba1688-selection-pool-filter-categories")
public class Alibaba1688SelectionPoolFilterCategoryController {

    private final Alibaba1688SelectionPoolFilterCategoryService service;

    public Alibaba1688SelectionPoolFilterCategoryController(Alibaba1688SelectionPoolFilterCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Alibaba1688SelectionPoolFilterCategoryDTO.Item>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(keyword, enabled)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Alibaba1688SelectionPoolFilterCategoryDTO.Item>> create(
            @RequestBody Alibaba1688SelectionPoolFilterCategoryDTO.SaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("过滤类目已创建", service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionPoolFilterCategoryDTO.Item>> update(
            @PathVariable Long id,
            @RequestBody Alibaba1688SelectionPoolFilterCategoryDTO.SaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("过滤类目已更新", service.update(id, request)));
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionPoolFilterCategoryDTO.Item>> toggleEnabled(
            @PathVariable Long id,
            @RequestBody Alibaba1688SelectionPoolFilterCategoryDTO.ToggleEnabledRequest request
    ) {
        Boolean enabled = request == null ? null : request.getEnabled();
        return ResponseEntity.ok(ApiResponse.success("过滤类目状态已更新", service.toggleEnabled(id, enabled)));
    }

    @PutMapping("/{id}/remark")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionPoolFilterCategoryDTO.Item>> updateRemark(
            @PathVariable Long id,
            @RequestBody(required = false) Alibaba1688SelectionPoolFilterCategoryDTO.UpdateRemarkRequest request
    ) {
        String remark = request == null ? null : request.getRemark();
        return ResponseEntity.ok(ApiResponse.success("过滤类目备注已更新", service.updateRemark(id, remark)));
    }

    @PostMapping("/quick-add")
    public ResponseEntity<ApiResponse<Alibaba1688SelectionPoolFilterCategoryDTO.QuickAddResponse>> quickAdd(
            @RequestBody Alibaba1688SelectionPoolFilterCategoryDTO.QuickAddRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("过滤类目已加入配置", service.quickAdd(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("过滤类目已删除", null));
    }
}
