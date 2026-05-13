package com.nemonicworld.flipbook.service.finalization;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class FlipbookFinalizationAsyncConfig {

    @Bean(name = "flipbookFinalizationExecutor")
    public Executor flipbookFinalizationExecutor(
        @Value("${nemonic.flipbook.finalization.async.core-pool-size:2}") int corePoolSize,
        @Value("${nemonic.flipbook.finalization.async.max-pool-size:4}") int maxPoolSize,
        @Value("${nemonic.flipbook.finalization.async.queue-capacity:100}") int queueCapacity,
        @Value("${nemonic.flipbook.finalization.async.await-termination-seconds:30}") int awaitTerminationSeconds) {
        int resolvedCorePoolSize = Math.max(1, corePoolSize);
        int resolvedMaxPoolSize = Math.max(resolvedCorePoolSize, maxPoolSize);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(resolvedCorePoolSize);
        executor.setMaxPoolSize(resolvedMaxPoolSize);
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setThreadNamePrefix("flipbook-finalization-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(Math.max(0, awaitTerminationSeconds));
        executor.initialize();
        return executor;
    }
}
