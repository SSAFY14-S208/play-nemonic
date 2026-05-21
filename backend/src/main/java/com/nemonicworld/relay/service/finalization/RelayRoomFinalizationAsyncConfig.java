package com.nemonicworld.relay.service.finalization;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

@Configuration
public class RelayRoomFinalizationAsyncConfig {

    public static final String RELAY_FINALIZATION_TASK_EXECUTOR = "relayFinalizationTaskExecutor";
    private static final String DEFAULT_THREAD_NAME_PREFIX = "relay-finalization-";

    @Bean(RELAY_FINALIZATION_TASK_EXECUTOR)
    public TaskExecutor relayFinalizationTaskExecutor(Environment environment) {
        int corePoolSize = intProperty(environment, "core-pool-size", 2);
        int maxPoolSize = intProperty(environment, "max-pool-size", 4);
        int queueCapacity = intProperty(environment, "queue-capacity", 100);
        String threadNamePrefix = environment.getProperty(propertyName("thread-name-prefix"),
            DEFAULT_THREAD_NAME_PREFIX);
        boolean waitForTasksToCompleteOnShutdown = environment
            .getProperty(propertyName("wait-for-tasks-to-complete-on-shutdown"), Boolean.class, true);
        int awaitTerminationSeconds = intProperty(environment, "await-termination-seconds", 30);
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

    private static int intProperty(Environment environment, String name, int defaultValue) {
        return environment.getProperty(propertyName(name), Integer.class, defaultValue);
    }

    private static String propertyName(String name) {
        return "nemonic.relay.finalization.async." + name;
    }
}
