package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.TemuSpecMappingDTO;
import com.tminos.productscene.service.TemuSpecMappingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/spec-mappings")
public class TemuSpecMappingController {

    private final TemuSpecMappingService service;

    public TemuSpecMappingController(TemuSpecMappingService service) {
        this.service = service;
    }

    @GetMapping("/profiles")
    public ResponseEntity<ApiResponse<List<TemuSpecMappingDTO.ProfileResponse>>> listProfiles(
            @RequestParam(name = "enabled", required = false) Boolean enabled
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.listProfiles(enabled)));
    }

    @PostMapping("/profiles")
    public ResponseEntity<ApiResponse<TemuSpecMappingDTO.ProfileResponse>> createProfile(
            @Valid @RequestBody TemuSpecMappingDTO.ProfileUpsertRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Created", service.createProfile(request)));
    }

    @PutMapping("/profiles/{id}")
    public ResponseEntity<ApiResponse<TemuSpecMappingDTO.ProfileResponse>> updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody TemuSpecMappingDTO.ProfileUpsertRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Updated", service.updateProfile(id, request)));
    }

    @DeleteMapping("/profiles/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProfile(@PathVariable Long id) {
        service.deleteProfile(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    @GetMapping("/workbench/{spuId}")
    public ResponseEntity<ApiResponse<TemuSpecMappingDTO.WorkbenchResponse>> getWorkbench(@PathVariable Long spuId) {
        return ResponseEntity.ok(ApiResponse.success(service.getWorkbench(spuId)));
    }

    @PostMapping("/workbench/{spuId}/preview")
    public ResponseEntity<ApiResponse<TemuSpecMappingDTO.PreviewResponse>> preview(
            @PathVariable Long spuId,
            @RequestBody(required = false) TemuSpecMappingDTO.PreviewRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.preview(spuId, request == null ? new TemuSpecMappingDTO.PreviewRequest() : request)));
    }

    @PostMapping("/workbench/{spuId}/drafts")
    public ResponseEntity<ApiResponse<TemuSpecMappingDTO.DraftResponse>> saveDraft(
            @PathVariable Long spuId,
            @RequestBody TemuSpecMappingDTO.DraftSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Saved", service.saveDraft(spuId, request)));
    }
}