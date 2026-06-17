package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.SupplierProductPackageDTO;
import com.tminos.productscene.service.SupplierProductPackageService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/supplier-product-packages")
@RequiredArgsConstructor
public class SupplierProductPackageController {

    private final SupplierProductPackageService service;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SupplierProductPackageDTO.Item>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(keyword, status, page, size)));
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<ApiResponse<SupplierProductPackageDTO.Item>> generate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("AI 素材已生成", service.generate(id)));
    }

    @PostMapping("/{id}/push-to-product-collection")
    public ResponseEntity<ApiResponse<SupplierProductPackageDTO.Item>> pushToProductCollection(
            @PathVariable Long id,
            @RequestBody(required = false) SupplierProductPackageDTO.PushRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("已推送到采集商品库", service.pushToProductCollection(id, request)));
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
