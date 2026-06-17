package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "alibaba_image_proxy_configs",
        indexes = {
                @Index(name = "idx_alibaba_image_proxy_configs_enabled", columnList = "enabled"),
                @Index(name = "idx_alibaba_image_proxy_configs_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlibabaImageProxyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_name", nullable = false, length = 128)
    private String configName;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Column(name = "proxy_base_url", length = 500)
    private String proxyBaseUrl;

    @Column(name = "image_proxy_path", length = 255)
    private String imageProxyPath;

    @Column(name = "allowed_hosts_text", columnDefinition = "TEXT")
    private String allowedHostsText;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

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
        if (enabled == null) {
            enabled = false;
        }
        if (configName == null || configName.isBlank()) {
            configName = "默认配置";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (enabled == null) {
            enabled = false;
        }
        if (configName == null || configName.isBlank()) {
            configName = "默认配置";
        }
    }
}
