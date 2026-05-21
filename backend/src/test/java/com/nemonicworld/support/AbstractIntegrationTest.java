package com.nemonicworld.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;

/**
 * 통합 테스트의 공통 컨텍스트 설정을 공유하기 위한 베이스 클래스다.
 *
 * <p>
 * {@code ddl-auto=create-drop} 그룹에서 추가 property 없이 사용되는 단순 통합 테스트들이 이 클래스를
 * 상속하면, Spring TestContext 캐시 키를 통일할 수 있어 적중률이 올라간다. 추가 {@code @MockitoBean}이나
 * 다른 {@code @TestPropertySource}가 필요한 테스트는 본 클래스를 상속한 채 자신만의 어노테이션을 덧붙이면 된다.
 * </p>
 *
 * <p>
 * {@code @MockitoBean} 선언은 base 쪽에 두지 않는다. 각 테스트가 자신의 mock 조합으로 캐시 키를 만들도록 책임을
 * 위임한다.
 * </p>
 */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
public abstract class AbstractIntegrationTest {
}
