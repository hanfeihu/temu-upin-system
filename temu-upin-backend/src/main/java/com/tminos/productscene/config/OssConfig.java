package com.tminos.productscene.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "aliyun.oss")
@Getter
@Setter
public class OssConfig {
    private boolean enabled = false;
    // Bucket所在地域，例如 cn-hangzhou
    private String region;
    private String endpoint;
    private String bucket;

    // Prefer loading from a local-only secrets file (see oss-secrets.yml.example).
    // Do NOT commit real credentials.
    private String accessKeyId;
    private String accessKeySecret;

    // Optional. If provided, use this as public base domain, e.g. https://oss.tminos.com
    // Otherwise fall back to https://<bucket>.<endpoint>
    private String publicDomain;

    // Explicit getters/setters (keep Lombok annotations too).
    // Some IDE setups in this repo don't resolve Lombok-generated methods reliably.
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getPublicDomain() {
        return publicDomain;
    }

    public void setPublicDomain(String publicDomain) {
        this.publicDomain = publicDomain;
    }
}
