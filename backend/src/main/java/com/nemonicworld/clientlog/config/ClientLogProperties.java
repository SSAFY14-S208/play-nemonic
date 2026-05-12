package com.nemonicworld.clientlog.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "nemonic.client-log")
public class ClientLogProperties {

    private List<String> allowedOrigins = new ArrayList<>(
        List.of("http://localhost:3000", "http://127.0.0.1:3000", "http://localhost:8080", "http://127.0.0.1:8080"));
    private int maxEventsPerRequest = 200;
    private long maxPayloadBytes = 1_048_576L;
    private int rateLimitEventsPerSecond = 100;
    private int rateLimitEventsPerMinute = 1_000;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public int getMaxEventsPerRequest() {
        return maxEventsPerRequest;
    }

    public void setMaxEventsPerRequest(int maxEventsPerRequest) {
        this.maxEventsPerRequest = maxEventsPerRequest;
    }

    public long getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public void setMaxPayloadBytes(long maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public int getRateLimitEventsPerSecond() {
        return rateLimitEventsPerSecond;
    }

    public void setRateLimitEventsPerSecond(int rateLimitEventsPerSecond) {
        this.rateLimitEventsPerSecond = rateLimitEventsPerSecond;
    }

    public int getRateLimitEventsPerMinute() {
        return rateLimitEventsPerMinute;
    }

    public void setRateLimitEventsPerMinute(int rateLimitEventsPerMinute) {
        this.rateLimitEventsPerMinute = rateLimitEventsPerMinute;
    }
}
