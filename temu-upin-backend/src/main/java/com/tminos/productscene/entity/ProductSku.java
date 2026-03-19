package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_skus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSku {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @Column(nullable = false)
    private String skuCode;
    
    private String skuName;
    
    private String color;
    
    private String size;
    
    private String material;
    
    private String weight;
    
    @Column(columnDefinition = "TEXT")
    private String specDetails;
    
    @Column(nullable = false)
    private String originalImageUrl;
    
    private String thumbnailUrl;
    
    @Builder.Default
    private Integer sortOrder = 0;
    
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
