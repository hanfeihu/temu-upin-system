package com.tminos.productscene.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    private final ThreadPoolConfig threadPoolConfig;

    public AsyncConfig(ThreadPoolConfig threadPoolConfig) {
        this.threadPoolConfig = threadPoolConfig;
    }

    @Bean(name = "postImportExecutor")
    public TaskExecutor postImportExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("post-import-");
        ex.initialize();
        return ex;
    }

    /**
     * Global worker pool for IO-heavy tasks (image normalize, etc.).
     */
    @Bean(name = "globalWorkerExecutor")
    public Executor globalWorkerExecutor() {
        int n = 2;
        try {
            if (threadPoolConfig != null && threadPoolConfig.getWorkers() != null && threadPoolConfig.getWorkers() > 0) {
                n = threadPoolConfig.getWorkers();
            }
        } catch (Exception ignored) {
        }
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(n);
        ex.setMaxPoolSize(n);
        ex.setQueueCapacity(500);
        ex.setThreadNamePrefix("global-worker-");
        ex.initialize();
        return ex;
    }

    @Bean(name = "batchImageTranslateExecutor")
    public Executor batchImageTranslateExecutor() {
        int n = 10;
        try {
            if (threadPoolConfig != null
                    && threadPoolConfig.getImageTranslateWorkers() != null
                    && threadPoolConfig.getImageTranslateWorkers() > 0) {
                n = threadPoolConfig.getImageTranslateWorkers();
            }
        } catch (Exception ignored) {
        }
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(n);
        ex.setMaxPoolSize(n);
        ex.setQueueCapacity(500);
        ex.setThreadNamePrefix("img-translate-");
        ex.initialize();
        return ex;
    }
}
