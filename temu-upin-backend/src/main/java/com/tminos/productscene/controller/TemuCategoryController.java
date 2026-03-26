package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.TemuCategoryDTO;
import com.tminos.productscene.service.TemuCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platform/temu")
@RequiredArgsConstructor
public class TemuCategoryController {

    private final TemuCategoryService temuCategoryService;

    @PostMapping("/match-category")
    public ResponseEntity<ApiResponse<TemuCategoryDTO.MatchCategoryResponse>> matchCategory(
            @RequestBody TemuCategoryDTO.MatchCategoryRequest req
    ) {
        String title = req == null ? null : req.getTitle();
        return ResponseEntity.ok(ApiResponse.success(temuCategoryService.matchCategory(title)));
    }

    @GetMapping("/parent-specs")
    public ResponseEntity<ApiResponse<List<TemuCategoryDTO.ParentSpecOption>>> listParentSpecs() {
        return ResponseEntity.ok(ApiResponse.success(temuCategoryService.listParentSpecs()));
    }

    @GetMapping("/category-mandatory-raw")
    public ResponseEntity<Object> getCategoryMandatoryRaw(@RequestParam long leafCatId) {
        try {
            String raw = temuCategoryService.getCategoryMandatoryRaw(leafCatId);
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return ResponseEntity.ok(om.readTree(raw));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }
}
