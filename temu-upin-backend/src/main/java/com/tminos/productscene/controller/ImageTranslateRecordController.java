package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.entity.ImageTranslateRecord;
import com.tminos.productscene.service.ImageTranslateRecordService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/image-translate-records")
public class ImageTranslateRecordController {

    private final ImageTranslateRecordService service;

    public ImageTranslateRecordController(ImageTranslateRecordService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ImageTranslateRecord>>> list(
            @RequestParam(value = "spuId", required = false) Long spuId,
            @RequestParam(value = "provider", required = false) String provider,
            @RequestParam(value = "sourceUrl", required = false) String sourceUrl,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(spuId, provider, sourceUrl, status, page, size)));
    }
}