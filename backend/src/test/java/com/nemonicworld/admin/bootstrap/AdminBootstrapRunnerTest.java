package com.nemonicworld.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private AdminBootstrapService adminBootstrapService;

    @Test
    void disabledBootstrapDoesNothing() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties();
        properties.setEnabled(false);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(properties, adminBootstrapService);

        runner.run(null);

        verifyNoInteractions(adminBootstrapService);
    }

    @Test
    void enabledBootstrapDelegatesWhenRequiredPropertiesExist() {
        AdminBootstrapProperties properties = validProperties();
        when(adminBootstrapService.bootstrap(properties)).thenReturn(AdminBootstrapResult.CREATED);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(properties, adminBootstrapService);

        runner.run(null);

        verify(adminBootstrapService).bootstrap(properties);
    }

    @Test
    void enabledBootstrapFailsWhenRequiredPropertyIsMissing() {
        AdminBootstrapProperties properties = validProperties();
        properties.setLoginId(" ");
        AdminBootstrapRunner runner = new AdminBootstrapRunner(properties, adminBootstrapService);

        assertThatThrownBy(() -> runner.run(null)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ADMIN_BOOTSTRAP_LOGIN_ID");
        verifyNoInteractions(adminBootstrapService);
    }

    private AdminBootstrapProperties validProperties() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties();
        properties.setEnabled(true);
        properties.setLoginId("superadmin");
        properties.setPassword("initial-password");
        properties.setNickname("슈퍼관리자");
        properties.setEmail("admin@example.com");

        return properties;
    }
}
