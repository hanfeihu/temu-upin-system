package com.tminos.productscene.controller;


import com.tminos.productscene.dto.ProductDTO.*;
import com.tminos.productscene.entity.GeneratedImage;
import com.tminos.productscene.entity.Product;
import com.tminos.productscene.service.ImageGenerationService;
import com.tminos.productscene.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    
    private final ProductService productService;
    private final ImageGenerationService imageGenerationService;
    
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", product));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long id) {
        ProductResponse product = productService.getProduct(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts() {
        List<ProductResponse> products = productService.getAllProducts();
        return ResponseEntity.ok(ApiResponse.success(products));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @RequestBody UpdateProductRequest request) {
        ProductResponse product = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", product));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }
    
    @PostMapping("/{id}/skus")
    public ResponseEntity<ApiResponse<SkuResponse>> addSku(
            @PathVariable Long id,
            @Valid @RequestBody CreateSkuRequest request) {
        SkuResponse sku = productService.addSku(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("SKU added successfully", sku));
    }
    
    @GetMapping("/{productId}/skus")
    public ResponseEntity<ApiResponse<List<SkuResponse>>> getSkus(@PathVariable Long productId) {
        List<SkuResponse> skus = productService.getSkusByProduct(productId);
        return ResponseEntity.ok(ApiResponse.success(skus));
    }
    
    @PutMapping("/{productId}/skus/{skuId}")
    public ResponseEntity<ApiResponse<SkuResponse>> updateSku(
            @PathVariable Long productId,
            @PathVariable Long skuId,
            @RequestBody UpdateSkuRequest request) {
        SkuResponse sku = productService.updateSku(productId, skuId, request);
        return ResponseEntity.ok(ApiResponse.success("SKU updated successfully", sku));
    }
    
    @DeleteMapping("/{productId}/skus/{skuId}")
    public ResponseEntity<ApiResponse<Void>> deleteSku(
            @PathVariable Long productId,
            @PathVariable Long skuId) {
        productService.deleteSku(productId, skuId);
        return ResponseEntity.ok(ApiResponse.success("SKU deleted successfully", null));
    }
    
    @PostMapping("/{id}/generate")
    public ResponseEntity<ApiResponse<List<GeneratedImageResponse>>> generateImages(
            @PathVariable Long id,
            @Valid @RequestBody BatchGenerationRequest request) {
        List<GeneratedImageResponse> images = imageGenerationService.generateImages(id, request);
        return ResponseEntity.ok(ApiResponse.success("Images generated successfully", images));
    }
    
    @GetMapping("/{id}/images")
    public ResponseEntity<ApiResponse<List<GeneratedImageResponse>>> getGeneratedImages(
            @PathVariable Long id) {
        List<GeneratedImageResponse> images = imageGenerationService.getGeneratedImages(id);
        return ResponseEntity.ok(ApiResponse.success(images));
    }
    
    @GetMapping("/{id}/images/{type}")
    public ResponseEntity<ApiResponse<List<GeneratedImageResponse>>> getImagesByType(
            @PathVariable Long id,
            @PathVariable String type) {
        GeneratedImage.ImageType imageType = GeneratedImage.ImageType.valueOf(type.toUpperCase());
        List<GeneratedImageResponse> images = imageGenerationService.getImagesByType(id, imageType);
        return ResponseEntity.ok(ApiResponse.success(images));
    }
    
    @DeleteMapping("/{id}/images")
    public ResponseEntity<ApiResponse<Void>> deleteGeneratedImages(@PathVariable Long id) {
        imageGenerationService.deleteGeneratedImages(id);
        return ResponseEntity.ok(ApiResponse.success("Images deleted successfully", null));
    }

    @DeleteMapping("/{productId}/images/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteGeneratedImage(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {
        imageGenerationService.deleteGeneratedImage(productId, imageId);
        return ResponseEntity.ok(ApiResponse.success("Image deleted successfully", null));
    }

    @DeleteMapping("/{productId}/images/failed")
    public ResponseEntity<ApiResponse<Integer>> deleteFailedGeneratedImages(
            @PathVariable Long productId,
            @RequestParam(value = "type", required = false) String type
    ) {
        GeneratedImage.ImageType imageType = null;
        if (type != null && !type.isBlank()) {
            imageType = GeneratedImage.ImageType.valueOf(type.toUpperCase());
        }
        int deleted = imageGenerationService.deleteFailedGeneratedImages(productId, imageType);
        return ResponseEntity.ok(ApiResponse.success("Failed images deleted successfully", deleted));
    }


}
