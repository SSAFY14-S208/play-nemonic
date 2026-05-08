package com.nemonicworld.fortune.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FortuneGmsProperties.class)
public class FortuneGmsConfig {
}
