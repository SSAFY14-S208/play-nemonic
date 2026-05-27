package com.nemonicworld.backoffice.setting.service.parameter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.admin.service.AdminAuthorization;
import com.nemonicworld.auth.service.AdminAuditLogger;
import com.nemonicworld.auth.service.AdminClientInfo;
import com.nemonicworld.backoffice.setting.dto.request.SystemParameterTypedUpdateRequest;
import com.nemonicworld.backoffice.setting.dto.response.SystemParameterListResponse;
import com.nemonicworld.backoffice.setting.dto.response.SystemParameterResponse;
import com.nemonicworld.backoffice.setting.entity.SystemParameter;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository.UpdateValueCommand;
import com.nemonicworld.backoffice.setting.service.SystemParameterTypedUpdateMapper;
import com.nemonicworld.backoffice.setting.service.SystemParameterTypedUpdateMapper.TypedUpdateValue;
import com.nemonicworld.backoffice.setting.service.support.SystemParameterValidationSupport;
import com.nemonicworld.backoffice.setting.service.support.SystemParameterValueSupport;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.jwt.AdminPrincipal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SystemParameterUpdateUseCase {

    private static final Logger log = LoggerFactory.getLogger(SystemParameterUpdateUseCase.class);

    private static final String EMPTY_UPDATE_MESSAGE = "수정할 시스템 파라미터를 지정해주세요.";
    private static final String NOT_FOUND_MESSAGE_FORMAT = "존재하지 않는 시스템 파라미터입니다. key=%s";

    private final SystemParameterRepository systemParameterRepository;
    private final ObjectMapper objectMapper;
    private final SystemParameterValueSupport valueSupport;
    private final SystemParameterValidationSupport validationSupport;
    private final AdminAuditLogger adminAuditLogger;

    public SystemParameterUpdateUseCase(SystemParameterRepository systemParameterRepository, ObjectMapper objectMapper,
        SystemParameterValueSupport valueSupport, SystemParameterValidationSupport validationSupport,
        AdminAuditLogger adminAuditLogger) {
        this.systemParameterRepository = systemParameterRepository;
        this.objectMapper = objectMapper;
        this.valueSupport = valueSupport;
        this.validationSupport = validationSupport;
        this.adminAuditLogger = adminAuditLogger;
    }

    @Transactional
    public SystemParameterListResponse bulkUpdate(AdminPrincipal adminPrincipal,
        SystemParameterTypedUpdateRequest request, AdminClientInfo clientInfo) {
        AdminAuthorization.requireOperator(adminPrincipal);

        List<TypedUpdateValue> updates = SystemParameterTypedUpdateMapper.extractUpdates(request, objectMapper);
        if (updates.isEmpty()) {
            throw new BadRequestException(EMPTY_UPDATE_MESSAGE);
        }

        List<String> requestedKeys = updates.stream().map(TypedUpdateValue::key).toList();
        List<SystemParameter> existing = systemParameterRepository.findAllByKeys(requestedKeys);
        Map<String, SystemParameter> existingByKey = new HashMap<>();
        for (SystemParameter parameter : existing) {
            existingByKey.put(parameter.key(), parameter);
        }

        Set<String> missingKeys = new LinkedHashSet<>(requestedKeys);
        missingKeys.removeAll(existingByKey.keySet());
        if (!missingKeys.isEmpty()) {
            throw new BadRequestException(NOT_FOUND_MESSAGE_FORMAT.formatted(missingKeys));
        }

        for (TypedUpdateValue update : updates) {
            validationSupport.validateSystemParameterValue(update.key(), update.value());
        }

        List<UpdateValueCommand> commands = updates.stream().map(update -> {
            SystemParameter previous = existingByKey.get(update.key());
            return new UpdateValueCommand(previous.id(), valueSupport.serializeValue(update.value()),
                adminPrincipal.id());
        }).toList();

        LocalDateTime now = LocalDateTime.now();
        systemParameterRepository.batchUpdateValues(commands, now);

        Map<Long, String> serializedById = new HashMap<>();
        for (UpdateValueCommand command : commands) {
            serializedById.put(command.id(), command.value());
        }
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (TypedUpdateValue update : updates) {
            SystemParameter previous = existingByKey.get(update.key());
            before.put(previous.key(), valueSupport.safeParameterValue(previous.key(), previous.value()));
            after.put(previous.key(),
                valueSupport.safeParameterValue(previous.key(), serializedById.get(previous.id())));
            log.info("system-parameter updated id={} key={} updatedBy={}", previous.id(), previous.key(),
                adminPrincipal.id());
        }
        emitAfterCommit(() -> adminAuditLogger.logParamChange(adminPrincipal, "bulk:%d".formatted(updates.size()),
            clientInfo, before, after));

        List<Long> requestedIds = updates.stream().map(update -> existingByKey.get(update.key()).id()).toList();
        List<SystemParameterResponse> updatedItems = systemParameterRepository.findAllByIds(requestedIds).stream()
            .map(parameter -> SystemParameterResponse.from(parameter, valueSupport.parseValue(parameter.value())))
            .toList();

        return new SystemParameterListResponse(updatedItems, updatedItems.size());
    }

    private void emitAfterCommit(Runnable auditLog) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            auditLog.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                auditLog.run();
            }
        });
    }
}
