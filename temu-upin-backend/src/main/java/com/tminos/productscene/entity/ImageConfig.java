package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "image_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String configName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageType imageType;
    
    @Column(nullable = false)
    private Integer width;
    
    @Column(nullable = false)
    private Integer height;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer count = 1;
    
    @Column(columnDefinition = "TEXT")
    private String defaultPrompt;
    
    @Column(columnDefinition = "TEXT")
    private String negativePrompt;
    
    @Builder.Default
    private Boolean isDefault = false;
    
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public enum ImageType {
        THUMBNAIL,
        CAROUSEL,
        DETAIL
    }
}
