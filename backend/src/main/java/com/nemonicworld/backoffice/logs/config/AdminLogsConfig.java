package com.nemonicworld.backoffice.logs.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdminLogsProperties.class)
public class AdminLogsConfig {
}
