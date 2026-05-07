package com.nemonicworld.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 모든 REST API 컨트롤러에 공통 버전 prefix를 적용하는 설정입니다.
 */
@Configuration
public class ApiPathPrefixConfig implements WebMvcConfigurer {

    private static final String API_V1_PREFIX = "/api/v1";
    private static final String APPLICATION_BASE_PACKAGE = "com.nemonicworld";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_V1_PREFIX, HandlerTypePredicate.forBasePackage(APPLICATION_BASE_PACKAGE)
            .and(HandlerTypePredicate.forAnnotation(RestController.class)));
    }
}
