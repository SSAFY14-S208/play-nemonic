package com.nemonicworld.support;

import java.util.UUID;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 통합 테스트 ApplicationContext마다 독립된 H2 in-memory 데이터베이스를 할당한다.
 *
 * <p>
 * 모든 통합 테스트가 동일한 {@code jdbc:h2:mem:nemonic-test} URL을 공유하면, Spring TestContext
 * 캐시가 LRU eviction으로 오래된 컨텍스트를 close 할 때 {@code ddl-auto=create-drop}이 schema를
 * drop 하여 같은 H2 DB를 쓰는 다른 살아있는 컨텍스트들의 테이블이 사라진다.
 * </p>
 *
 * <p>
 * 각 컨텍스트가 자기만의 UUID 기반 H2 DB 이름을 갖도록 URL을 동적으로 등록하면 schema 공유 문제가 사라진다. 같은 캐시
 * 키를 가진 컨텍스트는 한 번만 초기화되므로 URL도 한 번만 결정되어 컨텍스트 내부에서는 일관된다.
 * </p>
 */
public class IsolatedH2DatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String uniqueDatabaseName = "nemonic-test-" + UUID.randomUUID();
        String url = "jdbc:h2:mem:" + uniqueDatabaseName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        TestPropertyValues
            .of("spring.datasource.url=" + url, "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.defer-datasource-initialization=true",
                "spring.sql.init.mode=always", "management.health.redis.enabled=false",
                "management.health.mail.enabled=false", "nemonic.auth.admin-bootstrap.enabled=false",
                "nemonic.relay.reconciliation.enabled=false", "nemonic.relay.disconnect.enabled=false",
                "nemonic.relay.timeout.enabled=false", "nemonic.relay.finalization.enabled=false",
                "nemonic.relay.close.enabled=false", "nemonic.relay.abandoned-close.enabled=false",
                "nemonic.relay.orphan-cleanup.enabled=false", "nemonic.relay.cleanup.enabled=false",
                "nemonic.flipbook.abandoned-close.enabled=false", "nemonic.flipbook.disconnect.enabled=false",
                "nemonic.flipbook.timeout.enabled=false", "nemonic.flipbook.finalization.enabled=false",
                "nemonic.flipbook.close.enabled=false", "nemonic.infinite-canvas.abandoned-close.enabled=false")
            .applyTo(applicationContext);
    }
}
