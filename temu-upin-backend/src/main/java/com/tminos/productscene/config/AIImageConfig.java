package com.tminos.productscene.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "ai.image")
@Getter
@Setter
public class AIImageConfig {
    
    private List<ProviderConfig> providers = new ArrayList<>();
    private DefaultSettings defaultSettings;
    private PromptOptimizerConfig promptOptimizer;
    
    @Getter
    @Setter
    public static class ProviderConfig {
        private String name;
        private Boolean enabled;
        private String baseUrl;
        private String apiKey;
        private String apiSecret;
        private String model;
    }
    
    @Getter
    @Setter
    public static class DefaultSettings {
        private ImageSettings thumbnail;
        private ImageSettings carousel;
        private ImageSettings detail;
    }
    
    @Getter
    @Setter
    public static class ImageSettings {
        private Integer width;
        private Integer height;
        private Integer count;
    }
    
    @Getter
    @Setter
    public static class PromptOptimizerConfig {
        private Boolean enabled;
        private String provider;
        private String baseUrl;
        private String apiKey;
        private String model;
    }
}
