package com.tminos.productscene.controller;

import com.tminos.productscene.dto.Alibaba1688DetailRecordDTO;
import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.service.Alibaba1688DetailRecordService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/alibaba1688-detail-records")
public class Alibaba1688DetailRecordController {

    private final Alibaba1688DetailRecordService service;

    public Alibaba1688DetailRecordController(Alibaba1688DetailRecordService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Alibaba1688DetailRecordDTO.ListItem>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "credentialId", required = false) Long credentialId,
            @RequestParam(value = "recordId", required = false) Long recordId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "excludeImported", required = false) Boolean excludeImported,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(keyword, credentialId, recordId, status, excludeImported, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Alibaba1688DetailRecordDTO.DetailResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.getDetail(id)));
    }
}
