package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "alibaba_1688_card_link_records",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_alibaba_1688_card_link_offer_id", columnNames = "offer_id")
        },
        indexes = {
                @Index(name = "idx_alibaba_1688_card_link_status", columnList = "status"),
                @Index(name = "idx_alibaba_1688_card_link_type", columnList = "type"),
                @Index(name = "idx_alibaba_1688_card_link_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alibaba1688CardLinkRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type", length = 32)
    private String type;

    @Column(name = "offer_id", nullable = false, length = 64)
    private String offerId;

    @Column(name = "detail_url", columnDefinition = "TEXT")
    private String detailUrl;

    @Column(name = "card_href", columnDefinition = "TEXT")
    private String cardHref;

    @Column(name = "render_key", columnDefinition = "TEXT")
    private String renderKey;

    @Column(name = "card_index", length = 64)
    private String cardIndex;

    @Column(name = "offer_id_source", length = 64)
    private String offerIdSource;

    @Column(name = "card_class", columnDefinition = "TEXT")
    private String cardClass;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

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
        if (status == null) {
            status = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
