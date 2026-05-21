package com.nemonicworld.clientlog.service;

import com.nemonicworld.clientlog.config.ClientLogProperties;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ClientLogRateLimiter {

    // In-memory buckets are scoped to one application instance. Use shared storage
    // such as Redis before horizontal
    // scaling if this endpoint needs a cluster-wide limit.
    private final ClientLogProperties properties;
    private final Clock clock;
    private final Map<String, ClientLogRateLimitBucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public ClientLogRateLimiter(ClientLogProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    ClientLogRateLimiter(ClientLogProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean tryConsume(String ipAddress, int eventCount) {
        long nowMillis = clock.millis();
        return buckets.computeIfAbsent(ipAddress, ignored -> new ClientLogRateLimitBucket(nowMillis)).tryConsume(
            nowMillis, eventCount, properties.getRateLimitEventsPerSecond(), properties.getRateLimitEventsPerMinute());
    }

    private static final class ClientLogRateLimitBucket {

        private long secondWindow;
        private long minuteWindow;
        private int secondCount;
        private int minuteCount;

        private ClientLogRateLimitBucket(long nowMillis) {
            secondWindow = toSecondWindow(nowMillis);
            minuteWindow = toMinuteWindow(nowMillis);
        }

        private synchronized boolean tryConsume(long nowMillis, int eventCount, int secondLimit, int minuteLimit) {
            long currentSecondWindow = toSecondWindow(nowMillis);
            if (currentSecondWindow != secondWindow) {
                secondWindow = currentSecondWindow;
                secondCount = 0;
            }

            long currentMinuteWindow = toMinuteWindow(nowMillis);
            if (currentMinuteWindow != minuteWindow) {
                minuteWindow = currentMinuteWindow;
                minuteCount = 0;
            }

            if (secondCount + eventCount > secondLimit || minuteCount + eventCount > minuteLimit) {
                return false;
            }

            secondCount += eventCount;
            minuteCount += eventCount;
            return true;
        }

        private static long toSecondWindow(long nowMillis) {
            return nowMillis / 1_000L;
        }

        private static long toMinuteWindow(long nowMillis) {
            return nowMillis / 60_000L;
        }
    }
}
