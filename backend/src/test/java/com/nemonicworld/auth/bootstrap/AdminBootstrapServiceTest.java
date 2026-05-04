package com.nemonicworld.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    @Mock
    private AdminBootstrapRepository adminBootstrapRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void createsSuperAdminWhenLoginIdDoesNotExist() {
        AdminBootstrapProperties properties = validProperties();
        when(adminBootstrapRepository.existsByLoginId("superadmin")).thenReturn(false);
        AdminBootstrapService service = new AdminBootstrapService(adminBootstrapRepository, passwordEncoder);
        ArgumentCaptor<String> passwordHashCaptor = ArgumentCaptor.forClass(String.class);

        AdminBootstrapResult result = service.bootstrap(properties);

        assertThat(result).isEqualTo(AdminBootstrapResult.CREATED);
        verify(adminBootstrapRepository).insertSuperAdmin(eq("superadmin"), passwordHashCaptor.capture(), eq("슈퍼관리자"),
            eq("admin@example.com"), any(LocalDateTime.class));
        assertThat(passwordEncoder.matches("initial-password", passwordHashCaptor.getValue())).isTrue();
        assertThat(passwordHashCaptor.getValue()).isNotEqualTo("initial-password");
    }

    @Test
    void skipsWhenLoginIdAlreadyExists() {
        AdminBootstrapProperties properties = validProperties();
        when(adminBootstrapRepository.existsByLoginId("superadmin")).thenReturn(true);
        AdminBootstrapService service = new AdminBootstrapService(adminBootstrapRepository, passwordEncoder);

        AdminBootstrapResult result = service.bootstrap(properties);

        assertThat(result).isEqualTo(AdminBootstrapResult.SKIPPED_EXISTING);
        verify(adminBootstrapRepository, never()).insertSuperAdmin(any(), any(), any(), any(), any());
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
