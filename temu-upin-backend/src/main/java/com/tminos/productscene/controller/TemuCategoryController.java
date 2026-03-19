package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.TemuCategoryDTO;
import com.tminos.productscene.service.TemuCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
