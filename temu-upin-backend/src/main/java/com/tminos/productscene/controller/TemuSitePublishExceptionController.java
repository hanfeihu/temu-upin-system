package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.TemuSitePublishExceptionDTO;
import com.tminos.productscene.service.TemuSitePublishExceptionService;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/platform/temu-site-publish-exceptions")
public class TemuSitePublishExceptionController {

    private final TemuSitePublishExceptionService service;

    public TemuSitePublishExceptionController(TemuSitePublishExceptionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TemuSitePublishExceptionDTO.Item>>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "siteName", required = false) String siteName,
            @RequestParam(value = "reasonType", required = false) String reasonType,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(keyword, siteName, reasonType, active, page, size)));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TemuSitePublishExceptionDTO.ImportResponse>> importFile(
            @RequestPart("file") MultipartFile file
    ) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择 JSON 文件");
        }
        TemuSitePublishExceptionDTO.ImportResponse response = service.importJson(file.getInputStream(), file.getOriginalFilename());
        return ResponseEntity.ok(ApiResponse.success("加站异常 JSON 导入完成", response));
    }

    @PostMapping("/import-path")
    public ResponseEntity<ApiResponse<TemuSitePublishExceptionDTO.ImportResponse>> importPath(
            @RequestBody TemuSitePublishExceptionDTO.ImportPathRequest request
    ) throws Exception {
        TemuSitePublishExceptionDTO.ImportResponse response = service.importFromPath(request == null ? null : request.getFilePath());
        return ResponseEntity.ok(ApiResponse.success("加站异常 JSON 导入完成", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TemuSitePublishExceptionDTO.Item>> update(
            @PathVariable Long id,
            @RequestBody TemuSitePublishExceptionDTO.UpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("加站异常记录已更新", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("加站异常记录已删除", null));
    }
}
