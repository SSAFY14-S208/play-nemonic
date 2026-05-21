package com.nemonicworld.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = IsolatedH2DatabaseInitializer.class)
@Import(TestAdminTokenStoreConfiguration.class)
public @interface IntegrationTest {
}
