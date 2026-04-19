package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_self_apps",
        indexes = {
                @Index(name = "idx_temu_self_apps_enabled", columnList = "enabled"),
                @Index(name = "idx_temu_self_apps_app_key", columnList = "app_key")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_temu_self_apps_app_key", columnNames = {"app_key"}),
                @UniqueConstraint(name = "uk_temu_self_apps_app_name", columnNames = {"app_name"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuSelfApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_name", nullable = false, length = 128)
    private String appName;

    @Column(name = "app_key", nullable = false, length = 256)
    private String appKey;

    @Column(name = "app_secret", nullable = false, length = 512)
    private String appSecret;

    @Enumerated(EnumType.STRING)
    @Column(name = "app_type", length = 32)
    @Builder.Default
    private TemuAppType appType = TemuAppType.PRODUCT;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getAppName() { return appName; }
    public String getAppKey() { return appKey; }
    public String getAppSecret() { return appSecret; }
    public TemuAppType getAppType() { return appType == null ? TemuAppType.PRODUCT : appType; }
    public Boolean getEnabled() { return enabled; }
}
