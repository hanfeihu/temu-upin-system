package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "logistics_provider_configs",
        indexes = {
                @Index(name = "idx_logistics_provider_configs_enabled", columnList = "enabled")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_logistics_provider_configs_provider_code", columnNames = {"provider_code"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogisticsProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "provider_name", nullable = false, length = 128)
    private String providerName;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "app_token", length = 500)
    private String appToken;

    @Column(name = "app_key", length = 500)
    private String appKey;

    @Column(name = "connect_timeout_ms")
    private Integer connectTimeoutMs;

    @Column(name = "read_timeout_ms")
    private Integer readTimeoutMs;

    @Column(name = "extra_config_json", columnDefinition = "TEXT")
    private String extraConfigJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
