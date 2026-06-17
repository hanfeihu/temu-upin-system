package com.tminos.productscene.sync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
public class SyncConfig {
    @Value("${thread-pool.sync-task-core-pool-size:3}")
    private int syncTaskCorePoolSize;

    @Value("${thread-pool.sync-task-max-pool-size:10}")
    private int syncTaskMaxPoolSize;

    @Value("${thread-pool.sync-task-queue-capacity:50}")
    private int syncTaskQueueCapacity;

    @Value("${thread-pool.sync-worker-core-pool-size:10}")
    private int syncWorkerCorePoolSize;

    @Value("${thread-pool.sync-worker-max-pool-size:20}")
    private int syncWorkerMaxPoolSize;

    @Value("${thread-pool.sync-worker-queue-capacity:500}")
    private int syncWorkerQueueCapacity;

    @Bean("syncTaskExecutor")
    public Executor syncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, syncTaskCorePoolSize));
        executor.setMaxPoolSize(Math.max(syncTaskCorePoolSize, syncTaskMaxPoolSize));
        executor.setQueueCapacity(Math.max(1, syncTaskQueueCapacity));
        executor.setThreadNamePrefix("sync-task-");
        executor.initialize();
        return executor;
    }

    /**
     * 同步下载 / 入库内部并发线程池（所有任务共用，通过 Semaphore 控制每个任务的并发度）
     */
    @Bean("syncWorkerPool")
    public ExecutorService syncWorkerPool() {
        return new ThreadPoolExecutor(
                Math.max(1, syncWorkerCorePoolSize),
                Math.max(syncWorkerCorePoolSize, syncWorkerMaxPoolSize),
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(Math.max(1, syncWorkerQueueCapacity)),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("sync-worker-" + t.threadId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
