package com.nemonicworld.clientlog.service.support;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClientLogEventValidator {

    private static final Set<String> REQUIRED_FIELDS = Set.of("@timestamp", "event_name", "service");
    private static final Pattern EVENT_NAME_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");

    public String validate(Map<String, Object> event) {
        for (String requiredField : REQUIRED_FIELDS) {
            if (!StringUtils.hasText(text(event.get(requiredField)))) {
                return "missing_" + requiredField.replace("@", "");
            }
        }

        String eventName = text(event.get("event_name"));
        if (!EVENT_NAME_PATTERN.matcher(eventName).matches()) {
            return "invalid_event_name";
        }

        return null;
    }

    public String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
