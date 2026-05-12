package com.nemonicworld.relay.service.finalization;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class RelayRoomFinalizationAsyncConfig {

    public static final String RELAY_FINALIZATION_TASK_EXECUTOR = "relayFinalizationTaskExecutor";

    @Bean(RELAY_FINALIZATION_TASK_EXECUTOR)
    public TaskExecutor relayFinalizationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("relay-finalization-");
        executor.initialize();
        return executor;
    }
}
