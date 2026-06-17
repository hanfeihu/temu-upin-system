package com.tminos.productscene.controller;

import com.tminos.productscene.dto.ApiResponse;
import com.tminos.productscene.dto.ProductDashboardDTO;
import com.tminos.productscene.service.ProductDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/product-dashboard")
public class ProductDashboardController {

    private final ProductDashboardService productDashboardService;

    public ProductDashboardController(ProductDashboardService productDashboardService) {
        this.productDashboardService = productDashboardService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ProductDashboardDTO.Response>> dashboard(
            @RequestParam(value = "days", required = false) Integer days
    ) {
        return ResponseEntity.ok(ApiResponse.success(productDashboardService.getDashboard(days)));
    }
}
