package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.dto.TemuSelfAppDTO;
import com.tminos.productscene.service.TemuSelfAppService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/temu-apps")
public class TemuSelfAppController {

    private final TemuSelfAppService service;

    public TemuSelfAppController(TemuSelfAppService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TemuSelfAppDTO.View>>> list(
            @RequestParam(name = "enabled", required = false) Boolean enabled
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(enabled)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TemuSelfAppDTO.View>> create(
            @Valid @RequestBody TemuSelfAppDTO.CreateRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.success("Created", service.create(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TemuSelfAppDTO.View>> update(
            @PathVariable Long id,
            @Valid @RequestBody TemuSelfAppDTO.UpdateRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.success("Updated", service.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
