package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ImageOcrDTO;
import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.entity.ImageOcrTask;
import com.tminos.productscene.service.ImageOcrTaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/ocr-tasks")
public class ImageOcrTaskController {

    private final ImageOcrTaskService service;

    public ImageOcrTaskController(ImageOcrTaskService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ImageOcrTask>>> list(
            @RequestParam(value = "spuId", required = false) Long spuId,
            @RequestParam(value = "productId", required = false) String productId,
            @RequestParam(value = "imageType", required = false) Integer imageType,
            @RequestParam(value = "execStatus", required = false) Integer execStatus,
            @RequestParam(value = "filtered", required = false) Boolean filtered,
            @RequestParam(value = "containsChinese", required = false) Boolean containsChinese,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(spuId, productId, imageType, execStatus, filtered, containsChinese, page, size)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> stats(
            @RequestParam(value = "spuId", required = false) Long spuId,
            @RequestParam(value = "productId", required = false) String productId,
            @RequestParam(value = "imageType", required = false) Integer imageType,
            @RequestParam(value = "filtered", required = false) Boolean filtered,
            @RequestParam(value = "containsChinese", required = false) Boolean containsChinese
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.stats(spuId, productId, imageType, filtered, containsChinese)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ImageOcrTask>> create(@Valid @RequestBody ImageOcrDTO.UpsertTaskRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Created", service.create(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ImageOcrTask>> update(@PathVariable Long id, @Valid @RequestBody ImageOcrDTO.UpsertTaskRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Updated", service.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
