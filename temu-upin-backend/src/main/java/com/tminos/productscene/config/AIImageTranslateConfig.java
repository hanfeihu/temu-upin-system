package com.tminos.productscene.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai.image-translate")
@Getter
@Setter
public class AIImageTranslateConfig {

    private Boolean enabled = true;
    private String baseUrl;
    private String apiKey;
    private String model = "gpt-image-1.5";
    private String size = "auto";
    private String quality = "medium";
    private String outputFormat = "png";
    private Integer timeoutMs = 300_000;
    private Boolean uploadToOssDefault = false;
    private String marketplace = "Temu";
    private String sourceLanguage = "Chinese";
    private String targetLanguage = "English";
}
