package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "generated_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedImage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id")
    private ProductSku sku;

    @Column(name = "channel_id")
    private Long channelId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageType imageType;
    
    @Column(nullable = false)
    private String imageUrl;

    // Nullable for smooth schema migration on existing DB rows.
    @Column
    @Builder.Default
    private Boolean success = true;

    @Column(length = 2000)
    private String errorMessage;
    
    private Integer width;
    
    private Integer height;
    
    private String prompt;
    
    private String aiProvider;
    
    private String aiModel;
    
    @Builder.Default
    private Integer sortOrder = 0;
    
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    public enum ImageType {
        THUMBNAIL,
        CAROUSEL,
        DETAIL
    }
}
