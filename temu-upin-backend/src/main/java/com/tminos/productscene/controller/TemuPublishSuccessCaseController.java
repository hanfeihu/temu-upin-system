package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ProductDTO.ApiResponse;
import com.tminos.productscene.dto.TemuPublishSuccessCaseDTO;
import com.tminos.productscene.service.TemuPublishSuccessCaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/temu-publish/success-cases")
public class TemuPublishSuccessCaseController {

    private final TemuPublishSuccessCaseService successCaseService;

    public TemuPublishSuccessCaseController(TemuPublishSuccessCaseService successCaseService) {
        this.successCaseService = successCaseService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TemuPublishSuccessCaseDTO.Row>>> list(
            @RequestParam(value = "spuId", required = false) Long spuId,
            @RequestParam(value = "temuCatid", required = false) String temuCatid
    ) {
        return ResponseEntity.ok(ApiResponse.success(successCaseService.list(spuId, temuCatid)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TemuPublishSuccessCaseDTO.Detail>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(successCaseService.get(id)));
    }
}