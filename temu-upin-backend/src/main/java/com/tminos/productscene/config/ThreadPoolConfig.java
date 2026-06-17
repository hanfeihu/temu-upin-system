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

    /**
     * Async sync-task executor core threads.
     */
    private Integer syncTaskCorePoolSize = 3;

    /**
     * Async sync-task executor max threads.
     */
    private Integer syncTaskMaxPoolSize = 10;

    /**
     * Async sync-task executor queue size.
     */
    private Integer syncTaskQueueCapacity = 50;

    /**
     * Shared sync worker pool core threads.
     */
    private Integer syncWorkerCorePoolSize = 10;

    /**
     * Shared sync worker pool max threads.
     */
    private Integer syncWorkerMaxPoolSize = 20;

    /**
     * Shared sync worker pool queue size.
     */
    private Integer syncWorkerQueueCapacity = 500;

    public Integer getWorkers() {
        return workers;
    }

    public Integer getImageTranslateWorkers() {
        return imageTranslateWorkers;
    }

    public Integer getSyncTaskCorePoolSize() {
        return syncTaskCorePoolSize;
    }

    public Integer getSyncTaskMaxPoolSize() {
        return syncTaskMaxPoolSize;
    }

    public Integer getSyncTaskQueueCapacity() {
        return syncTaskQueueCapacity;
    }

    public Integer getSyncWorkerCorePoolSize() {
        return syncWorkerCorePoolSize;
    }

    public Integer getSyncWorkerMaxPoolSize() {
        return syncWorkerMaxPoolSize;
    }

    public Integer getSyncWorkerQueueCapacity() {
        return syncWorkerQueueCapacity;
    }
}
