package com.nemonicworld.backoffice.logs.dto.request;

public record LogsFilter(String field, Object value, Boolean negate) {

    public boolean negated() {
        return Boolean.TRUE.equals(negate);
    }
}
