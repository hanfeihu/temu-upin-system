package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.entity.ImportTitleFilterWord;
import com.tminos.productscene.service.ImportTitleFilterWordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/title-filter-words")
public class ImportTitleFilterWordController {

    private final ImportTitleFilterWordService service;

    public ImportTitleFilterWordController(ImportTitleFilterWordService service) {
        this.service = service;
    }

    public static class UpsertReq {
        @NotBlank
        public String word;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ImportTitleFilterWord>>> list(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.page(q, page, size)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ImportTitleFilterWord>> create(@Valid @RequestBody UpsertReq req) {
        return ResponseEntity.ok(ApiResponse.success("Created", service.create(req.word)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ImportTitleFilterWord>> update(@PathVariable Long id, @Valid @RequestBody UpsertReq req) {
        return ResponseEntity.ok(ApiResponse.success("Updated", service.update(id, req.word)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
