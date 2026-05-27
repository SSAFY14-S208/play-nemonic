package com.nemonicworld.backoffice.setting.service.parameter;

import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.backoffice.setting.dto.response.SystemParameterListResponse;
import com.nemonicworld.backoffice.setting.dto.response.SystemParameterResponse;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import com.nemonicworld.backoffice.setting.service.support.SystemParameterValueSupport;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemParameterQueryUseCase {

    private final SystemParameterRepository systemParameterRepository;
    private final SystemParameterValueSupport valueSupport;

    public SystemParameterQueryUseCase(SystemParameterRepository systemParameterRepository,
        SystemParameterValueSupport valueSupport) {
        this.systemParameterRepository = systemParameterRepository;
        this.valueSupport = valueSupport;
    }

    @Transactional(readOnly = true)
    public SystemParameterListResponse getSystemParameters(AdminPrincipal adminPrincipal, String keyword) {
        AdminAuthorization.requireAuthenticated(adminPrincipal);

        String normalizedKeyword = normalizeKeyword(keyword);
        long totalElements = systemParameterRepository.countAll(normalizedKeyword);
        List<SystemParameterResponse> items = systemParameterRepository.findAll(normalizedKeyword).stream()
            .map(parameter -> SystemParameterResponse.from(parameter, valueSupport.parseValue(parameter.value())))
            .toList();

        return new SystemParameterListResponse(items, totalElements);
    }

    private String normalizeKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }
}
