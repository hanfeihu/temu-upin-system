package com.tminos.productscene.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "temu.goods")
@Getter
@Setter
public class TemuGoodsConfig {

    // TEMU OpenAPI access token (goods module)
    private String accessToken;

    // Polling settings for image translation
    private Integer translatePollIntervalMs = 2500;
    private Integer translatePollMaxAttempts = 80;

    // Explicit getters/setters (keep Lombok annotations too).
    // Some IDE tooling in this repo doesn't resolve Lombok-generated methods reliably.
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Integer getTranslatePollIntervalMs() {
        return translatePollIntervalMs;
    }

    public void setTranslatePollIntervalMs(Integer translatePollIntervalMs) {
        this.translatePollIntervalMs = translatePollIntervalMs;
    }

    public Integer getTranslatePollMaxAttempts() {
        return translatePollMaxAttempts;
    }

    public void setTranslatePollMaxAttempts(Integer translatePollMaxAttempts) {
        this.translatePollMaxAttempts = translatePollMaxAttempts;
    }
}
