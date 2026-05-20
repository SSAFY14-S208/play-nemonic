package com.nemonicworld.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;

/**
 * JdbcTemplate 으로 직접 fixture 를 insert 하는 통합 테스트의 공통 베이스 클래스다.
 *
 * <p>
 * 과거 본 그룹은 {@code ddl-auto=none} 이었지만, H2 in-memory DB 가 컨텍스트별로 격리되면서 JPA entity 가
 * 만드는 테이블(app_user 등)이 비어 있는 격리된 DB 에서는 lookup 이 실패한다. 이를 막기 위해
 * {@code ddl-auto=create-drop} 으로 통일하여 JPA entity table 은 Hibernate 가 만들고, 그 외 JdbcTemplate
 * 으로 접근하는 비-JPA 테이블은 schema.sql 에서 생성한다. 데이터 시드는 종전대로 각 테스트가 책임진다.
 * </p>
 */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
public abstract class AbstractReadOnlyIntegrationTest {
}
