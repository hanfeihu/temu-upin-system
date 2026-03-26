package com.tminos.productscene.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai.temu-title-optimizer")
@Getter
@Setter
public class AITemuTitleOptimizerConfig {

    private Boolean enabled = true;
    private String baseUrl;
    private String apiKey;
    private String model;
    private Integer timeoutMs = 240_000;
    private Integer maxTokens = 1600;
    private Integer maxAttempts = 3;
}