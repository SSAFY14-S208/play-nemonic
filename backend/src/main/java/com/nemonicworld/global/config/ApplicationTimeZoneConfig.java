package com.nemonicworld.global.config;

import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationTimeZoneConfig {

    private final String applicationTimeZone;

    public ApplicationTimeZoneConfig(@Value("${nemonic.time-zone:Asia/Seoul}") String applicationTimeZone) {
        this.applicationTimeZone = applicationTimeZone;
    }

    @PostConstruct
    void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(applicationTimeZone)));
    }
}
