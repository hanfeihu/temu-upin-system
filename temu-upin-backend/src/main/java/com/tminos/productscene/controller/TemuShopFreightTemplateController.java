package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.dto.TemuShopFreightTemplateOptionDTO;
import com.tminos.productscene.service.TemuShopFreightTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/temu-shops")
public class TemuShopFreightTemplateController {

    private final TemuShopFreightTemplateService service;

    public TemuShopFreightTemplateController(TemuShopFreightTemplateService service) {
        this.service = service;
    }

    @GetMapping("/{id}/freight-templates")
    public ResponseEntity<ApiResponse<List<TemuShopFreightTemplateOptionDTO>>> list(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.listByShopRecordId(id)));
    }
}
