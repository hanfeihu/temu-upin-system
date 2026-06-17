package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ocr_image_translate_worker_logs",
        indexes = {
                @Index(name = "idx_ocr_image_translate_worker_logs_spu", columnList = "spu_id"),
                @Index(name = "idx_ocr_image_translate_worker_logs_task", columnList = "ocr_task_id"),
                @Index(name = "idx_ocr_image_translate_worker_logs_status", columnList = "status"),
                @Index(name = "idx_ocr_image_translate_worker_logs_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcrImageTranslateWorkerLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spu_id", nullable = false)
    private Long spuId;

    @Column(name = "product_id", length = 128)
    private String productId;

    @Column(name = "ocr_task_id")
    private Long ocrTaskId;

    @Column(name = "image_type")
    private Integer imageType;

    @Column(name = "source_field", length = 64)
    private String sourceField;

    @Column(name = "source_index")
    private Integer sourceIndex;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "original_url", columnDefinition = "TEXT")
    private String originalUrl;

    @Column(name = "translated_url", columnDefinition = "TEXT")
    private String translatedUrl;

    @Column(name = "temu_url", columnDefinition = "TEXT")
    private String temuUrl;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null || status.isBlank()) status = "UNKNOWN";
    }
}
