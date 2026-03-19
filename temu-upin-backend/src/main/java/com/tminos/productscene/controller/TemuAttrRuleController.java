package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.TemuAttrRuleDTO;
import com.tminos.productscene.entity.TemuAttrRule;
import com.tminos.productscene.service.TemuAttrRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/temu-attr-rules")
public class TemuAttrRuleController {

    private final TemuAttrRuleService service;

    public TemuAttrRuleController(TemuAttrRuleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TemuAttrRule>>> list(
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            @RequestParam(name = "leafCatId", required = false) String leafCatId
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(enabled, leafCatId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TemuAttrRule>> create(@Valid @RequestBody TemuAttrRuleDTO.UpsertRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Created", service.create(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TemuAttrRule>> update(
            @PathVariable Long id,
            @Valid @RequestBody TemuAttrRuleDTO.UpsertRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.success("Updated", service.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
