package com.nemonicworld.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 모든 통합 테스트에서 {@link InMemoryAdminTokenStore} 를 {@code @Primary} 빈으로 주입한다.
 *
 * <p>
 * {@code IntegrationTest} 메타 어노테이션이 본 설정을 자동으로 import 하므로 개별 테스트가 별도로 선언할 필요가 없다.
 * </p>
 */
@TestConfiguration
public class TestAdminTokenStoreConfiguration {

    @Bean
    @Primary
    public InMemoryAdminTokenStore inMemoryAdminTokenStore() {
        return new InMemoryAdminTokenStore();
    }
}
