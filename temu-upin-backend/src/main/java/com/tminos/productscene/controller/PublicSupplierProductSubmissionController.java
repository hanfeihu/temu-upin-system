package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.SupplierProductSubmissionDTO;
import com.tminos.productscene.service.OssService;
import com.tminos.productscene.service.SupplierProductSubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/supplier-product-submissions")
@RequiredArgsConstructor
@Slf4j
public class PublicSupplierProductSubmissionController {

    private final SupplierProductSubmissionService service;
    private final OssService ossService;

    @PostMapping
    public ResponseEntity<ApiResponse<SupplierProductSubmissionDTO.Item>> create(
            @RequestBody SupplierProductSubmissionDTO.SaveRequest request
    ) {
        SupplierProductSubmissionDTO.Item item = service.create(request);
        return ResponseEntity.ok(ApiResponse.success("提交成功", item));
    }

    @PostMapping("/upload-image")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("图片不能为空"));
        }
        try {
            String originalFilename = file.getOriginalFilename();
            String url = ossService.uploadSkuImage(file);

            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            result.put("filename", url.substring(url.lastIndexOf('/') + 1));
            result.put("originalName", originalFilename == null ? "" : originalFilename);
            result.put("size", String.valueOf(file.getSize()));
            return ResponseEntity.ok(ApiResponse.success("图片上传成功", result));
        } catch (Exception e) {
            log.error("Public supplier product image upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("图片上传失败: " + e.getMessage()));
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }
}
