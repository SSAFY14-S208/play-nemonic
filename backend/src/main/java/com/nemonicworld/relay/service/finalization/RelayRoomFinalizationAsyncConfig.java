package com.nemonicworld.relay.service.finalization;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

@Configuration
public class RelayRoomFinalizationAsyncConfig {

    public static final String RELAY_FINALIZATION_TASK_EXECUTOR = "relayFinalizationTaskExecutor";
    private static final String DEFAULT_THREAD_NAME_PREFIX = "relay-finalization-";

    @Bean(RELAY_FINALIZATION_TASK_EXECUTOR)
    public TaskExecutor relayFinalizationTaskExecutor(
        @Value("${nemonic.relay.finalization.async.core-pool-size:2}") int corePoolSize,
        @Value("${nemonic.relay.finalization.async.max-pool-size:4}") int maxPoolSize,
        @Value("${nemonic.relay.finalization.async.queue-capacity:100}") int queueCapacity,
        @Value("${nemonic.relay.finalization.async.thread-name-prefix:relay-finalization-}") String threadNamePrefix,
        @Value("${nemonic.relay.finalization.async.wait-for-tasks-to-complete-on-shutdown:true}") boolean waitForTasksToCompleteOnShutdown,
        @Value("${nemonic.relay.finalization.async.await-termination-seconds:30}") int awaitTerminationSeconds) {
        int resolvedCorePoolSize = Math.max(1, corePoolSize);
        int resolvedMaxPoolSize = Math.max(resolvedCorePoolSize, maxPoolSize);
        String resolvedThreadNamePrefix = StringUtils.hasText(threadNamePrefix)
            ? threadNamePrefix
            : DEFAULT_THREAD_NAME_PREFIX;
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(resolvedCorePoolSize);
        executor.setMaxPoolSize(resolvedMaxPoolSize);
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setThreadNamePrefix(resolvedThreadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(waitForTasksToCompleteOnShutdown);
        executor.setAwaitTerminationSeconds(Math.max(0, awaitTerminationSeconds));
        executor.initialize();
        return executor;
    }
}
