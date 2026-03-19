package com.tminos.productscene.dto;

import lombok.*;

public class ChannelDTO {
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateChannelRequest {
        private String name;
        private String platform;
        private String model;
        private String apiKey;
        private String apiSecret;
        private String baseUrl;
        private Boolean enabled;
        private String description;
        private Integer sortOrder;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateChannelRequest {
        private String name;
        private String platform;
        private String model;
        private String apiKey;
        private String apiSecret;
        private String baseUrl;
        private Boolean enabled;
        private String description;
        private Integer sortOrder;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChannelResponse {
        private Long id;
        private String name;
        private String platform;
        private String model;
        private String baseUrl;
        private Boolean enabled;
        private Boolean hasApiKey;
        private Boolean hasApiSecret;
        private String description;
        private Integer sortOrder;
        private String createdAt;
        private String updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InitPlatformCredentialsRequest {
        private String apiKey;
        private String apiSecret;
        private Boolean overwriteExisting;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChannelTestRequest {
        private String prompt;
        private String negativePrompt;
        private String sourceImageUrl;
        private Integer width;
        private Integer height;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChannelTestResponse {
        private Long channelId;
        private String channelName;
        private String platform;
        private String model;
        private Boolean success;
        private String imageUrl;
        private String message;
        private Long durationMs;
    }
}
