package com.nemonicworld.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;

/**
 * 데이터를 새로 만들지 않고 JdbcTemplate으로 직접 fixture를 insert 하는 통합 테스트의 공통 베이스 클래스다.
 *
 * <p>
 * {@code ddl-auto=none} 그룹은 Flyway 또는 JdbcTemplate으로 스키마/시드를 준비하기 때문에
 * Hibernate가 스키마를 다시 만들지 않아야 한다. 이 그룹의 단순 통합 테스트들이 상속하면 Spring TestContext 캐시
 * 키가 정렬되어 컨텍스트를 재사용할 수 있다.
 * </p>
 */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
public abstract class AbstractReadOnlyIntegrationTest {
}
