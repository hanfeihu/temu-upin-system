package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_image_meta",
        indexes = {
                @Index(name = "uk_temu_image_meta_url", columnList = "url", unique = true),
                @Index(name = "idx_temu_image_meta_checked_at", columnList = "checked_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TemuImageMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String url;

    private Integer width;

    private Integer height;

    @Column(length = 255)
    private String host;

    @Column(nullable = false)
    private Boolean kwcdn;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (checkedAt == null) checkedAt = now;
        if (kwcdn == null) kwcdn = Boolean.FALSE;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Explicit getters/setters (keep Lombok too; avoids toolchain/lombok issues)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Boolean getKwcdn() { return kwcdn; }
    public void setKwcdn(Boolean kwcdn) { this.kwcdn = kwcdn; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
