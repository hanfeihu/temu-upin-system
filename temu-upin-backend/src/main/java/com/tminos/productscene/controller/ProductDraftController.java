package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.ProductDraftDTO;
import com.tminos.productscene.service.ProductDraftService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/product-drafts")
public class ProductDraftController {

    private final ProductDraftService service;

    public ProductDraftController(ProductDraftService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductDraftDTO.ListItem>>> list(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "sourcePlatform", required = false) String sourcePlatform,
            @RequestParam(value = "targetShopId", required = false) String targetShopId,
            @RequestParam(value = "pushedToCollection", required = false) Boolean pushedToCollection,
            @RequestParam(value = "showDeleted", required = false) Boolean showDeleted,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(q, sourcePlatform, targetShopId, pushedToCollection, showDeleted, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDraftDTO.Detail>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<ProductDraftDTO.Detail>> importDraft(@Valid @RequestBody ProductDraftDTO.ImportRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Imported", service.importDraft(request)));
    }

    @PostMapping("/plugin-import")
    public ResponseEntity<ApiResponse<ProductDraftDTO.Detail>> pluginImport(@Valid @RequestBody ProductDraftDTO.ImportRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Imported", service.importDraft(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDraftDTO.Detail>> update(@PathVariable Long id, @RequestBody ProductDraftDTO.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Updated", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    @PostMapping("/{id}/push-to-collection")
    public ResponseEntity<ApiResponse<ProductDraftDTO.PushResponse>> pushToCollection(@PathVariable Long id) throws Exception {
        return ResponseEntity.ok(ApiResponse.success("Imported", service.pushToCollection(id)));
    }
}
