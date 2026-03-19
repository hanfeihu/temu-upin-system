package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_channels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIChannel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String platform;
    
    @Column(nullable = false)
    private String model;
    
    private String apiKey;
    
    private String apiSecret;
    
    private String baseUrl;
    
    @Builder.Default
    private Boolean enabled = true;
    
    private String description;
    
    @Builder.Default
    private Integer sortOrder = 0;
    
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt;
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
