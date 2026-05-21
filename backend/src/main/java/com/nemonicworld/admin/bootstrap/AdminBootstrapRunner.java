package com.nemonicworld.admin.bootstrap;

import com.nemonicworld.common.exception.InternalServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapProperties adminBootstrapProperties;
    private final AdminBootstrapService adminBootstrapService;

    public AdminBootstrapRunner(AdminBootstrapProperties adminBootstrapProperties,
        AdminBootstrapService adminBootstrapService) {
        this.adminBootstrapProperties = adminBootstrapProperties;
        this.adminBootstrapService = adminBootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!adminBootstrapProperties.isEnabled()) {
            return;
        }

        validateRequiredProperties();
        AdminBootstrapResult result = adminBootstrapService.bootstrap(adminBootstrapProperties);
        if (result == AdminBootstrapResult.CREATED) {
            log.info("super admin bootstrap created login_id={}", adminBootstrapProperties.getLoginId());
            return;
        }

        log.info("super admin bootstrap skipped existing login_id={}", adminBootstrapProperties.getLoginId());
    }

    private void validateRequiredProperties() {
        requireText(adminBootstrapProperties.getLoginId(), "ADMIN_BOOTSTRAP_LOGIN_ID");
        requireText(adminBootstrapProperties.getPassword(), "ADMIN_BOOTSTRAP_PASSWORD");
        requireText(adminBootstrapProperties.getNickname(), "ADMIN_BOOTSTRAP_NICKNAME");
        requireText(adminBootstrapProperties.getEmail(), "ADMIN_BOOTSTRAP_EMAIL");
    }

    private void requireText(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new InternalServerException("ADMIN_BOOTSTRAP_ENABLED가 true이면 %s 값이 필요합니다.".formatted(propertyName));
        }
    }
}
