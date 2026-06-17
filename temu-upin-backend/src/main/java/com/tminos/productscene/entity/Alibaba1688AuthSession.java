package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "alibaba_1688_auth_sessions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_alibaba_1688_auth_session_name", columnNames = "session_name")
        },
        indexes = {
                @Index(name = "idx_alibaba_1688_auth_session_enabled", columnList = "enabled"),
                @Index(name = "idx_alibaba_1688_auth_session_status", columnList = "status"),
                @Index(name = "idx_alibaba_1688_auth_session_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alibaba1688AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_name", nullable = false, length = 128)
    private String sessionName;

    @Column(name = "account_nick", length = 255)
    private String accountNick;

    @Column(name = "member_id", length = 128)
    private String memberId;

    @Column(name = "home_url", length = 2000)
    private String homeUrl;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "storage_state_encrypted", columnDefinition = "TEXT")
    private String storageStateEncrypted;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "storage_state_updated_at")
    private LocalDateTime storageStateUpdatedAt;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

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
            enabled = Boolean.TRUE;
        }
        if (status == null || status.isBlank()) {
            status = "EMPTY";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
