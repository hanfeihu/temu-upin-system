package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.TemuParentSpecMappingDTO;
import com.tminos.productscene.entity.TemuParentSpecMapping;
import com.tminos.productscene.service.TemuParentSpecMappingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/spec-mappings/parent-spec-mappings")
public class TemuParentSpecMappingController {

    private final TemuParentSpecMappingService service;

    public TemuParentSpecMappingController(TemuParentSpecMappingService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TemuParentSpecMapping>>> list(
            @RequestParam(name = "enabled", required = false) Boolean enabled
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(enabled)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TemuParentSpecMapping>> create(
            @Valid @RequestBody TemuParentSpecMappingDTO.UpsertRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Created", service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TemuParentSpecMapping>> update(
            @PathVariable Long id,
            @Valid @RequestBody TemuParentSpecMappingDTO.UpsertRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Updated", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}