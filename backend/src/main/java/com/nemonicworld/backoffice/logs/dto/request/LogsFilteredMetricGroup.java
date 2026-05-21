package com.nemonicworld.backoffice.logs.dto.request;

public record LogsFilteredMetricGroup(String name, String query, LogsMetricSpec metric) {
}
