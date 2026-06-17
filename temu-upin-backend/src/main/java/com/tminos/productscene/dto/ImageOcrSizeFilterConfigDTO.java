package com.tminos.productscene.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

public class ImageOcrSizeFilterConfigDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotNull
        @Min(1)
        private Integer imageWidth;

        @NotNull
        @Min(1)
        private Integer imageHeight;

        private Boolean enabled;
        private String remark;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Integer imageWidth;
        private Integer imageHeight;
        private Boolean enabled;
        private String remark;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
