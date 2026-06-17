package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.SupplierProductSubmissionDTO;
import com.tminos.productscene.service.SupplierProductSubmissionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/supplier-product-submissions")
@RequiredArgsConstructor
public class SupplierProductSubmissionController {

    private final SupplierProductSubmissionService service;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SupplierProductSubmissionDTO.Item>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(keyword, status, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierProductSubmissionDTO.Item>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.detail(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SupplierProductSubmissionDTO.Item>> create(
            @RequestBody SupplierProductSubmissionDTO.SaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("供应商提品已保存", service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierProductSubmissionDTO.Item>> update(
            @PathVariable Long id,
            @RequestBody SupplierProductSubmissionDTO.SaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("供应商提品已更新", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("供应商提品已删除", null));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<SupplierProductSubmissionDTO.Item>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("审核已通过，已进入 AI 商品包装台", service.approve(id)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<SupplierProductSubmissionDTO.Item>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) SupplierProductSubmissionDTO.SaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("供应商提品已拒绝", service.reject(id, request)));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }
}
