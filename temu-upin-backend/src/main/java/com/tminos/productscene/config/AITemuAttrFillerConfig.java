package com.tminos.productscene.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "ai.temu-attr-filler")
@Getter
@Setter
public class AITemuAttrFillerConfig {

    private Boolean enabled = true;
    private String baseUrl;
    private String apiKey;
    private String model;

    // Network / model settings
    private Integer timeoutMs = 240_000;
    private Integer maxTokens = 2500;

    /**
     * Some required input-type attributes must not be 0 (e.g. Battery capacity).
     * Configure by pid.
     */
    private List<Integer> requiredInputNonZeroPids = new ArrayList<>();

    /** Default fallback value when an override is needed. */
    private String requiredInputNonZeroDefault = "1";

    /**
     * Some attributes should be left EMPTY (not filled by AI) even if required in template.
     * This is useful when a field is conditionally required but not applicable for the current product.
     */
    private List<Integer> forceEmptyPids = new ArrayList<>();

    public Boolean getEnabled() {
        return enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public List<Integer> getRequiredInputNonZeroPids() {
        return requiredInputNonZeroPids;
    }

    public String getRequiredInputNonZeroDefault() {
        return requiredInputNonZeroDefault;
    }

    public List<Integer> getForceEmptyPids() {
        return forceEmptyPids;
    }

}
