package com.tminos.productscene.dto;

import com.tminos.productscene.entity.ImageConfig;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

public class ProductDTO {
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateProductRequest {
        @NotBlank(message = "Product name is required")
        private String name;
        private String description;
        private String category;
        private String brand;
        private String material;
        private String tags;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateProductRequest {
        private String name;
        private String description;
        private String category;
        private String brand;
        private String material;
        private String tags;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateSkuRequest {
        @NotBlank(message = "SKU code is required")
        private String skuCode;
        private String skuName;
        private String color;
        private String size;
        private String material;
        private String weight;
        private String specDetails;
        @NotBlank(message = "Original image is required")
        private String originalImageUrl;
        private Integer sortOrder;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateSkuRequest {
        private String skuCode;
        private String skuName;
        private String color;
        private String size;
        private String material;
        private String weight;
        private String specDetails;
        private String originalImageUrl;
        private Integer sortOrder;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImageGenerationRequest {
        @NotNull(message = "Image type is required")
        private ImageConfig.ImageType imageType;
        
        @NotNull(message = "Width is required")
        @Min(value = 100, message = "Width must be at least 100")
        private Integer width;
        
        @NotNull(message = "Height is required")
        @Min(value = 100, message = "Height must be at least 100")
        private Integer height;
        
        @NotNull(message = "Count is required")
        @Min(value = 1, message = "Count must be at least 1")
        private Integer count;
        
        private String prompt;
        private String negativePrompt;
        private String aiProvider;
        private String model;

        // When provided, backend will use the selected channel in DB.
        @NotNull(message = "Channel ID is required")
        private Long channelId;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchGenerationRequest {
        @NotNull(message = "Thumbnail config is required")
        private ImageGenerationRequest thumbnail;
        
        @NotNull(message = "Carousel config is required")
        private ImageGenerationRequest carousel;
        
        @NotNull(message = "Detail config is required")
        private ImageGenerationRequest detail;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductResponse {
        private Long id;
        private String name;
        private String description;
        private String category;
        private String brand;
        private String material;
        private String tags;
        private String status;
        private Integer skuCount;
        private Integer thumbnailCount;
        private Integer carouselCount;
        private Integer detailCount;
        private String coverImageUrl;
        private Long coverSkuId;
        private String createdAt;
        private String updatedAt;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkuResponse {
        private Long id;
        private String skuCode;
        private String skuName;
        private String color;
        private String size;
        private String material;
        private String weight;
        private String specDetails;
        private String originalImageUrl;
        private String thumbnailUrl;
        private Integer sortOrder;
        private String createdAt;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GeneratedImageResponse {
        private Long id;
        private String imageType;
        private String imageUrl;
        private Integer width;
        private Integer height;
        private String prompt;
        private String aiProvider;
        private String aiModel;
        private Long channelId;
        private Boolean success;
        private String errorMessage;
        private Integer sortOrder;
        private String createdAt;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;
        
        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message("Success")
                    .data(data)
                    .build();
        }
        
        public static <T> ApiResponse<T> success(String message, T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message(message)
                    .data(data)
                    .build();
        }
        
        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}
