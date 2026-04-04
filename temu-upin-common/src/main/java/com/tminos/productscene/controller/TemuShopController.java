package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.dto.TemuShopDTO;
import com.tminos.productscene.service.TemuShopService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/temu-shops")
public class TemuShopController {

    private final TemuShopService service;

    public TemuShopController(TemuShopService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TemuShopDTO.View>>> list(
            @RequestParam(name = "enabled", required = false) Boolean enabled
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(enabled)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TemuShopDTO.View>> create(
            @Valid @RequestBody TemuShopDTO.CreateRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.success("Created", service.create(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TemuShopDTO.View>> update(
            @PathVariable Long id,
            @Valid @RequestBody TemuShopDTO.UpdateRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.success("Updated", service.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
