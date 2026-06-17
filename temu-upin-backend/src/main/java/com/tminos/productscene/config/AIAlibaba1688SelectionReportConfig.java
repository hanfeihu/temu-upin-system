package com.tminos.productscene.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai.alibaba1688-selection-report")
@Getter
@Setter
public class AIAlibaba1688SelectionReportConfig {

    private Boolean enabled = true;
    private String baseUrl;
    private String apiKey;
    private String model;
    private Integer timeoutMs = 240_000;
    private Integer maxTokens = 3200;
}
