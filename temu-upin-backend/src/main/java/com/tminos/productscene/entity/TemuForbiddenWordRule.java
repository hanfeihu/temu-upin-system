package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_forbidden_word_rules",
        indexes = {
                @Index(name = "idx_temu_forbidden_word_rules_word", columnList = "word"),
                @Index(name = "idx_temu_forbidden_word_rules_enabled", columnList = "enabled"),
                @Index(name = "idx_temu_forbidden_word_rules_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuForbiddenWordRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "word", nullable = false, length = 256)
    private String word;

    @Column(name = "replacement", length = 256)
    private String replacement;

    @Column(name = "field_scope", length = 128)
    private String fieldScope;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (enabled == null) enabled = true;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (enabled == null) enabled = true;
    }
}
