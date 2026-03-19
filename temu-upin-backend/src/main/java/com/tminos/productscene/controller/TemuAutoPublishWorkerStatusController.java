package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.service.TemuAutoPublishWorkerStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/platform/temu-auto-publish/worker")
public class TemuAutoPublishWorkerStatusController {

    private final TemuAutoPublishWorkerStatus status;

    public TemuAutoPublishWorkerStatusController(TemuAutoPublishWorkerStatus status) {
        this.status = status;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        return ResponseEntity.ok(ApiResponse.success(status.snapshot()));
    }
}
