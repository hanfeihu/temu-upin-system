package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.TemuForbiddenWordRuleDTO;
import com.tminos.productscene.service.TemuForbiddenWordRuleService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/temu-forbidden-word-rules")
public class TemuForbiddenWordRuleController {

    private final TemuForbiddenWordRuleService service;

    public TemuForbiddenWordRuleController(TemuForbiddenWordRuleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TemuForbiddenWordRuleDTO.Item>>> list(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.page(q, enabled, page, size)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TemuForbiddenWordRuleDTO.Item>> create(
            @RequestBody TemuForbiddenWordRuleDTO.SaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("已新增 TEMU 违禁词", service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TemuForbiddenWordRuleDTO.Item>> update(
            @PathVariable Long id,
            @RequestBody TemuForbiddenWordRuleDTO.SaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("已更新 TEMU 违禁词", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("已删除 TEMU 违禁词", null));
    }
}
