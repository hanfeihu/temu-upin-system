package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_channel_business_config",
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_channel_business_code", columnNames = "business_code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIChannelBusinessConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "business_code", nullable = false)
    private String businessCode;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Builder.Default
    private Boolean enabled = true;

    private String description;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
