package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.dto.TemuOrderAftersaleDTO;
import com.tminos.productscene.service.TemuOrderAftersaleService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/temu-order-aftersales")
public class TemuOrderAftersaleController {

    private final TemuOrderAftersaleService aftersaleService;

    public TemuOrderAftersaleController(TemuOrderAftersaleService aftersaleService) {
        this.aftersaleService = aftersaleService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TemuOrderAftersaleDTO.ListItem>>> list(
            @RequestParam(value = "shopRecordId", required = false) Long shopRecordId,
            @RequestParam(value = "shopId", required = false) String shopId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "afterSalesStatusGroup", required = false) Integer afterSalesStatusGroup,
            @RequestParam(value = "createAtStartMs", required = false) Long createAtStartMs,
            @RequestParam(value = "createAtEndMs", required = false) Long createAtEndMs,
            @RequestParam(value = "updateAtStartMs", required = false) Long updateAtStartMs,
            @RequestParam(value = "updateAtEndMs", required = false) Long updateAtEndMs,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                aftersaleService.list(shopRecordId, shopId, keyword, afterSalesStatusGroup,
                        createAtStartMs, createAtEndMs, updateAtStartMs, updateAtEndMs,
                        page, pageSize)
        ));
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<List<TemuOrderAftersaleDTO.SyncResponse>>> sync(
            @RequestBody(required = false) TemuOrderAftersaleDTO.SyncRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("售后同步完成", aftersaleService.sync(request)));
    }
}
