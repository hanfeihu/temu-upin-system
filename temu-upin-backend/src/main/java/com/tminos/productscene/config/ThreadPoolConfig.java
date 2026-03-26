package com.tminos.productscene.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "thread-pool")
@Getter
@Setter
public class ThreadPoolConfig {

    /**
     * Global worker threads for IO heavy tasks (image normalize, etc.).
     */
    private Integer workers = 2;

    /**
     * Dedicated threads for batch image translation tasks.
     */
    private Integer imageTranslateWorkers = 10;

    public Integer getWorkers() {
        return workers;
    }

    public Integer getImageTranslateWorkers() {
        return imageTranslateWorkers;
    }
}
