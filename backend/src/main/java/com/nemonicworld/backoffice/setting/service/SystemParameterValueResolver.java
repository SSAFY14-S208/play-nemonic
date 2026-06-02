package com.nemonicworld.backoffice.setting.service;

import com.nemonicworld.backoffice.setting.entity.SystemParameter;
import com.nemonicworld.backoffice.setting.repository.SystemParameterRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class SystemParameterValueResolver {

    private SystemParameterValueResolver() {
    }

    public static Optional<String> findValue(SystemParameterRepository repository, String key) {
        return repository.findByKey(key).map(SystemParameter::value);
    }

    public static Map<String, String> findValues(SystemParameterRepository repository, List<String> keys) {
        return repository.findAllByKeys(keys).stream()
            .collect(Collectors.toMap(SystemParameter::key, SystemParameter::value, (first, second) -> first));
    }

    public static String valueOf(Map<String, String> valuesByKey, String key) {
        return valuesByKey.get(key);
    }
}
