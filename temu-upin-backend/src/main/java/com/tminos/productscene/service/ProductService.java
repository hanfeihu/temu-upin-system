package com.tminos.productscene.service;

import com.tminos.productscene.dto.ProductDTO.*;
import com.tminos.productscene.entity.*;
import com.tminos.productscene.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    private final ProductSkuRepository productSkuRepository;
    private final GeneratedImageRepository generatedImageRepository;
    private final OssService ossService;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .brand(request.getBrand())
                .material(request.getMaterial())
                .tags(request.getTags())
                .status(Product.ProductStatus.DRAFT)
                .build();
        
        product = productRepository.save(product);
        return toProductResponse(product);
    }
    
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        
        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getCategory() != null) product.setCategory(request.getCategory());
        if (request.getBrand() != null) product.setBrand(request.getBrand());
        if (request.getMaterial() != null) product.setMaterial(request.getMaterial());
        if (request.getTags() != null) product.setTags(request.getTags());
        
        product = productRepository.save(product);
        return toProductResponse(product);
    }
    
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        return toProductResponse(product);
    }
    
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::toProductResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));

        // Delete all SKUs and their linked data (generated images + OSS objects)
        List<ProductSku> skus = productSkuRepository.findByProductId(id);
        for (ProductSku sku : skus) {
            if (sku == null) {
                continue;
            }
            deleteSku(id, sku.getId());
        }

        // Defensive cleanup: delete any remaining generated images by productId
        List<GeneratedImage> images = generatedImageRepository.findByProductId(id);
        for (GeneratedImage img : images) {
            if (img == null) {
                continue;
            }
            if (StringUtils.hasText(img.getImageUrl())) {
                OssService.OssDeleteResult r = ossService.deleteByUrl(img.getImageUrl());
                if (!r.skipped() && !r.deleted()) {
                    throw new RuntimeException("Failed to delete OSS object: " + r.message());
                }
            }
        }
        if (!images.isEmpty()) {
            generatedImageRepository.deleteAll(images);
        }

        productRepository.delete(product);
    }
    
    @Transactional
    public SkuResponse addSku(Long productId, CreateSkuRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));
        
        ProductSku sku = ProductSku.builder()
                .product(product)
                .skuCode(request.getSkuCode())
                .skuName(request.getSkuName())
                .color(request.getColor())
                .size(request.getSize())
                .material(request.getMaterial())
                .weight(request.getWeight())
                .specDetails(request.getSpecDetails())
                .originalImageUrl(request.getOriginalImageUrl())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();
        
        sku = productSkuRepository.save(sku);
        return toSkuResponse(sku);
    }
    
    @Transactional
    public SkuResponse updateSku(Long productId, Long skuId, UpdateSkuRequest request) {
        ProductSku sku = productSkuRepository.findById(skuId)
                .orElseThrow(() -> new EntityNotFoundException("SKU not found: " + skuId));
        
        if (!sku.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("SKU does not belong to product");
        }
        
        if (request.getSkuCode() != null) sku.setSkuCode(request.getSkuCode());
        if (request.getSkuName() != null) sku.setSkuName(request.getSkuName());
        if (request.getColor() != null) sku.setColor(request.getColor());
        if (request.getSize() != null) sku.setSize(request.getSize());
        if (request.getMaterial() != null) sku.setMaterial(request.getMaterial());
        if (request.getWeight() != null) sku.setWeight(request.getWeight());
        if (request.getSpecDetails() != null) sku.setSpecDetails(request.getSpecDetails());
        if (request.getOriginalImageUrl() != null) sku.setOriginalImageUrl(request.getOriginalImageUrl());
        if (request.getSortOrder() != null) sku.setSortOrder(request.getSortOrder());
        
        sku = productSkuRepository.save(sku);
        return toSkuResponse(sku);
    }
    
    @Transactional
    public void deleteSku(Long productId, Long skuId) {
        ProductSku sku = productSkuRepository.findById(skuId)
                .orElseThrow(() -> new EntityNotFoundException("SKU not found: " + skuId));
        
        if (!sku.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("SKU does not belong to product");
        }

        // Delete all generated images associated with this SKU (and their OSS objects).
        List<GeneratedImage> skuImages = generatedImageRepository.findBySkuId(skuId);
        for (GeneratedImage img : skuImages) {
            if (img == null) {
                continue;
            }
            if (StringUtils.hasText(img.getImageUrl())) {
                OssService.OssDeleteResult r = ossService.deleteByUrl(img.getImageUrl());
                if (!r.skipped() && !r.deleted()) {
                    throw new RuntimeException("Failed to delete OSS object: " + r.message());
                }
            }
        }
        if (!skuImages.isEmpty()) {
            generatedImageRepository.deleteAll(skuImages);
        }

        // Delete OSS original image if applicable (ignore non-OSS URLs).
        if (StringUtils.hasText(sku.getOriginalImageUrl())) {
            OssService.OssDeleteResult result = ossService.deleteByUrl(sku.getOriginalImageUrl());
            if (!result.skipped() && !result.deleted()) {
                throw new RuntimeException("Failed to delete OSS object: " + result.message());
            }
        }

        productSkuRepository.delete(sku);
    }
    
    @Transactional(readOnly = true)
    public List<SkuResponse> getSkusByProduct(Long productId) {
        return productSkuRepository.findByProductId(productId).stream()
                .map(this::toSkuResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void updateProductStatus(Long id, Product.ProductStatus status) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        product.setStatus(status);
        productRepository.save(product);
    }
    
    private ProductResponse toProductResponse(Product product) {
        List<ProductSku> skus = productSkuRepository.findByProductId(product.getId());
        List<GeneratedImage> images = generatedImageRepository.findByProductId(product.getId());
        
        int thumbnailCount = (int) images.stream()
                .filter(img -> img.getImageType() == GeneratedImage.ImageType.THUMBNAIL).count();
        int carouselCount = (int) images.stream()
                .filter(img -> img.getImageType() == GeneratedImage.ImageType.CAROUSEL).count();
        int detailCount = (int) images.stream()
                .filter(img -> img.getImageType() == GeneratedImage.ImageType.DETAIL).count();

        String coverImageUrl = skus.stream()
                .sorted(java.util.Comparator.comparingInt(s -> s.getSortOrder() != null ? s.getSortOrder() : 0))
                .map(ProductSku::getOriginalImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);

        Long coverSkuId = skus.stream()
                .sorted(java.util.Comparator.comparingInt(s -> s.getSortOrder() != null ? s.getSortOrder() : 0))
                .filter(s -> s.getOriginalImageUrl() != null && !s.getOriginalImageUrl().isBlank())
                .map(ProductSku::getId)
                .findFirst()
                .orElse(null);
        
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .category(product.getCategory())
                .brand(product.getBrand())
                .material(product.getMaterial())
                .tags(product.getTags())
                .status(product.getStatus().name())
                .skuCount(skus.size())
                .thumbnailCount(thumbnailCount)
                .carouselCount(carouselCount)
                .detailCount(detailCount)
                .coverImageUrl(coverImageUrl)
                .coverSkuId(coverSkuId)
                .createdAt(product.getCreatedAt().format(FORMATTER))
                .updatedAt(product.getUpdatedAt().format(FORMATTER))
                .build();
    }
    
    private SkuResponse toSkuResponse(ProductSku sku) {
        return SkuResponse.builder()
                .id(sku.getId())
                .skuCode(sku.getSkuCode())
                .skuName(sku.getSkuName())
                .color(sku.getColor())
                .size(sku.getSize())
                .material(sku.getMaterial())
                .weight(sku.getWeight())
                .specDetails(sku.getSpecDetails())
                .originalImageUrl(sku.getOriginalImageUrl())
                .thumbnailUrl(sku.getThumbnailUrl())
                .sortOrder(sku.getSortOrder())
                .createdAt(sku.getCreatedAt().format(FORMATTER))
                .build();
    }
}
