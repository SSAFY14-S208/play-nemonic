package com.nemonicworld.admin.bootstrap;

import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBootstrapService {

    private final AdminBootstrapRepository adminBootstrapRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapService(AdminBootstrapRepository adminBootstrapRepository, PasswordEncoder passwordEncoder) {
        this.adminBootstrapRepository = adminBootstrapRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AdminBootstrapResult bootstrap(AdminBootstrapProperties properties) {
        if (adminBootstrapRepository.existsByLoginId(properties.getLoginId())) {
            return AdminBootstrapResult.SKIPPED_EXISTING;
        }

        String passwordHash = passwordEncoder.encode(properties.getPassword());
        adminBootstrapRepository.insertSuperAdmin(properties.getLoginId(), passwordHash, properties.getNickname(),
            properties.getEmail(), LocalDateTime.now());

        return AdminBootstrapResult.CREATED;
    }
}
