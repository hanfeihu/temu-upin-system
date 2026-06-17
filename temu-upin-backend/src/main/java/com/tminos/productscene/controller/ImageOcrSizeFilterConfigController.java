package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ImageOcrSizeFilterConfigDTO;
import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.service.ImageOcrSizeFilterConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/ocr-size-filter-configs")
public class ImageOcrSizeFilterConfigController {

    private final ImageOcrSizeFilterConfigService service;

    public ImageOcrSizeFilterConfigController(ImageOcrSizeFilterConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ImageOcrSizeFilterConfigDTO.Response>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ImageOcrSizeFilterConfigDTO.Response>> create(
            @Valid @RequestBody ImageOcrSizeFilterConfigDTO.Request req
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.create(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ImageOcrSizeFilterConfigDTO.Response>> update(
            @PathVariable Long id,
            @Valid @RequestBody ImageOcrSizeFilterConfigDTO.Request req
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
