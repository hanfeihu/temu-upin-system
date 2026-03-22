package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.TemuMainSaleSpecRuleDTO;
import com.tminos.productscene.entity.TemuMainSaleSpecRule;
import com.tminos.productscene.service.TemuMainSaleSpecRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/temu-main-sale-spec-rules")
public class TemuMainSaleSpecRuleController {

    private final TemuMainSaleSpecRuleService service;

    public TemuMainSaleSpecRuleController(TemuMainSaleSpecRuleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TemuMainSaleSpecRule>>> list(
            @RequestParam(name = "enabled", required = false) Boolean enabled
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(enabled)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TemuMainSaleSpecRule>> create(@Valid @RequestBody TemuMainSaleSpecRuleDTO.UpsertRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Created", service.create(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TemuMainSaleSpecRule>> update(
            @PathVariable Long id,
            @Valid @RequestBody TemuMainSaleSpecRuleDTO.UpsertRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.success("Updated", service.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
