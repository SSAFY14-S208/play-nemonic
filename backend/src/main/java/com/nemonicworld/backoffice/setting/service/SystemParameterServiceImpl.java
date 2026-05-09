package com.nemonicworld.backoffice.setting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.backoffice.setting.dto.response.SystemParameterListResponse;
import com.nemonicworld.backoffice.setting.dto.response.SystemParameterResponse;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import com.nemonicworld.common.exception.UnauthorizedException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemParameterServiceImpl implements SystemParameterService {

    private static final String UNAUTHORIZED_MESSAGE = "관리자 인증이 필요합니다.";

    private final SystemParameterRepository systemParameterRepository;
    private final ObjectMapper objectMapper;

    public SystemParameterServiceImpl(SystemParameterRepository systemParameterRepository, ObjectMapper objectMapper) {
        this.systemParameterRepository = systemParameterRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public SystemParameterListResponse getSystemParameters(AdminPrincipal adminPrincipal, String keyword) {
        requireAdmin(adminPrincipal);

        String normalizedKeyword = normalizeKeyword(keyword);
        long totalElements = systemParameterRepository.countAll(normalizedKeyword);
        List<SystemParameterResponse> items = systemParameterRepository.findAll(normalizedKeyword).stream()
            .map(parameter -> SystemParameterResponse.from(parameter, parseValue(parameter.value()))).toList();

        return new SystemParameterListResponse(items, totalElements);
    }

    private void requireAdmin(AdminPrincipal adminPrincipal) {
        if (adminPrincipal == null) {
            throw new UnauthorizedException(UNAUTHORIZED_MESSAGE);
        }
    }

    private String normalizeKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private JsonNode parseValue(String value) {
        if (!StringUtils.hasText(value)) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }
}
