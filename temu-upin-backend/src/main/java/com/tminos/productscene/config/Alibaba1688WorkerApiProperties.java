package com.tminos.productscene.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "alibaba1688.worker-api")
public class Alibaba1688WorkerApiProperties {
    private Boolean enabled = true;
    private String key = "Han123...";
}
