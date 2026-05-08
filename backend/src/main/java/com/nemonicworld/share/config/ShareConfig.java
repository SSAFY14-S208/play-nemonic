package com.nemonicworld.share.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration // 이 클래스는 설정 클래스
@EnableConfigurationProperties(ShareProperties.class) // 이 설정 클래스가 ShareProperties를 활성화
public class ShareConfig {
}
