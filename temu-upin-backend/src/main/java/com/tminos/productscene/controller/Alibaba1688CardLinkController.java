package com.tminos.productscene.controller;

import com.tminos.productscene.dto.Alibaba1688CardLinkDTO;
import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.service.Alibaba1688CardLinkService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/alibaba1688-card-links")
public class Alibaba1688CardLinkController {

    private final Alibaba1688CardLinkService service;

    public Alibaba1688CardLinkController(Alibaba1688CardLinkService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Alibaba1688CardLinkDTO.ListItem>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(keyword, type, status, page, size)));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<Alibaba1688CardLinkDTO.ImportResult>> importItems(
            @RequestBody(required = false) Alibaba1688CardLinkDTO.ImportRequest request
    ) {
        Alibaba1688CardLinkDTO.ImportResult result = service.importItems(request == null ? null : request.getItems());
        return ResponseEntity.ok(ApiResponse.success("1688 卡片链接导入完成", result));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Alibaba1688CardLinkDTO.ListItem>> updateStatus(
            @PathVariable Long id,
            @RequestBody Alibaba1688CardLinkDTO.UpdateStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("状态已更新", service.updateStatus(id, request == null ? null : request.getStatus())));
    }
}
