package com.nemonicworld.backoffice.metrics.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdminMetricsProperties.class)
public class AdminMetricsConfig {

    private static final long MAX_CACHE_ENTRIES = 1_000L;

    @Bean
    public Cache<String, Object> adminMetricsCache(AdminMetricsProperties properties) {
        return Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(properties.resolvedCacheTtlSeconds()))
            .maximumSize(MAX_CACHE_ENTRIES).build();
    }
}
