package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ImageOcrDTO;
import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.entity.ImageOcrTask;
import com.tminos.productscene.service.ImageOcrTaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Consumer APIs for OCR workers/consumers.
 */
@RestController
@RequestMapping("/api/ocr")
public class ImageOcrConsumerController {

    private final ImageOcrTaskService service;

    public ImageOcrConsumerController(ImageOcrTaskService service) {
        this.service = service;
    }

    /**
     * Get one task and mark it running.
     */
    @PostMapping("/tasks/claim")
    public ResponseEntity<ApiResponse<ImageOcrDTO.OcrTaskResponse>> claim(@RequestBody(required = false) ImageOcrDTO.ClaimTaskRequest req) {
        String ip = req == null ? null : req.getPublicIp();
        ImageOcrTask t = service.claimOne(ip);
        return ResponseEntity.ok(ApiResponse.success(service.toExternalResponse(t)));
    }

    /**
     * Complete a task. If ocrText provided -> success; else -> failed.
     */
    @PostMapping("/tasks/complete")
    public ResponseEntity<ApiResponse<ImageOcrDTO.OcrTaskResponse>> complete(@Valid @RequestBody ImageOcrDTO.CompleteTaskRequest req) {
        return ResponseEntity.ok(ApiResponse.success(service.toExternalResponse(service.complete(req))));
    }
}
