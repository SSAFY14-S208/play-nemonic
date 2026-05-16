package com.nemonicworld.backoffice.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nemonicworld.backoffice.metrics.exception.AdminMetricsException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsTemplateRegistryTest {

    private MetricsTemplateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MetricsTemplateRegistry();
    }

    @Test
    void serviceUpStatusBuildsExpectedPromql() {
        String promql = registry.buildPromql("service_up_status", Map.of("job", "spring-app"));

        assertThat(promql).isEqualTo("up{job=\"spring-app\"}");
    }

    @Test
    void serviceUpStatusRejectsUnknownJob() {
        assertThatThrownBy(() -> registry.buildPromql("service_up_status", Map.of("job", "bogus-job")))
            .isInstanceOf(AdminMetricsException.class)
            .satisfies(error -> assertThat(((AdminMetricsException) error).code())
                .isEqualTo(AdminMetricsException.INVALID_PARAM));
    }

    @Test
    void serviceUpStatusRejectsMissingJob() {
        assertThatThrownBy(() -> registry.buildPromql("service_up_status", Map.of()))
            .isInstanceOf(AdminMetricsException.class)
            .satisfies(error -> assertThat(((AdminMetricsException) error).code())
                .isEqualTo(AdminMetricsException.INVALID_PARAM));
    }

    @Test
    void nginxRequestRateBuildsExpectedPromql() {
        String promql = registry.buildPromql("nginx_request_rate", Map.of("window", "1m"));

        assertThat(promql).isEqualTo("sum(rate(nginx_http_requests_total[1m]))");
    }

    @Test
    void nginxRequestRateRejectsUnsupportedWindow() {
        assertThatThrownBy(() -> registry.buildPromql("nginx_request_rate", Map.of("window", "3m")))
            .isInstanceOf(AdminMetricsException.class)
            .satisfies(error -> assertThat(((AdminMetricsException) error).code())
                .isEqualTo(AdminMetricsException.INVALID_PARAM));
    }

    @Test
    void nginxActiveConnectionsBuildsConstantPromql() {
        String promql = registry.buildPromql("nginx_active_connections", Map.of());

        assertThat(promql).isEqualTo("nginx_connections_active");
    }

    @Test
    void springHttpRpsByServiceBuildsExpectedPromql() {
        String promql = registry.buildPromql("spring_http_rps_by_service", Map.of("window", "5m"));

        assertThat(promql)
            .isEqualTo("sum by (service) (rate(http_server_requests_seconds_count{job=\"spring-app\"}[5m]))");
    }

    @Test
    void springHttpRpsByServiceRejectsInvalidDuration() {
        assertThatThrownBy(() -> registry.buildPromql("spring_http_rps_by_service", Map.of("window", "5x")))
            .isInstanceOf(AdminMetricsException.class)
            .satisfies(error -> assertThat(((AdminMetricsException) error).code())
                .isEqualTo(AdminMetricsException.INVALID_PARAM));
    }

    @Test
    void springHttpLatencyQuantileBuildsExpectedPromql() {
        String promql = registry.buildPromql("spring_http_latency_quantile",
            Map.of("quantile", "0.95", "window", "5m"));

        assertThat(promql).isEqualTo("histogram_quantile(0.95, sum by (le) "
            + "(rate(http_server_requests_seconds_bucket{job=\"spring-app\"}[5m])))");
    }

    @Test
    void springHttpLatencyQuantileRejectsUnknownQuantile() {
        assertThatThrownBy(
            () -> registry.buildPromql("spring_http_latency_quantile", Map.of("quantile", "0.42", "window", "5m")))
            .isInstanceOf(AdminMetricsException.class)
            .satisfies(error -> assertThat(((AdminMetricsException) error).code())
                .isEqualTo(AdminMetricsException.INVALID_PARAM));
    }

    @Test
    void springHttpErrorRatioBuildsExpectedPromql() {
        String promql = registry.buildPromql("spring_http_error_ratio", Map.of("window", "5m"));

        assertThat(promql)
            .isEqualTo("sum(rate(http_server_requests_seconds_count{job=\"spring-app\",status=~\"5..\"}[5m]))"
                + " / clamp_min(sum(rate(http_server_requests_seconds_count{job=\"spring-app\"}[5m])), 1e-9)");
    }

    @Test
    void wsActiveSessionsBuildsConstantPromql() {
        String promql = registry.buildPromql("ws_active_sessions", null);

        assertThat(promql).isEqualTo("nemonic_ws_active_sessions");
    }

    @Test
    void wsConnectRateBuildsExpectedPromql() {
        String promql = registry.buildPromql("ws_connect_rate", Map.of("window", "5m"));

        assertThat(promql).isEqualTo("sum(rate(nemonic_ws_connect_total[5m]))");
    }

    @Test
    void wsDisconnectRateBuildsExpectedPromql() {
        String promql = registry.buildPromql("ws_disconnect_rate", Map.of("window", "5m"));

        assertThat(promql).isEqualTo("sum(rate(nemonic_ws_disconnect_total[5m]))");
    }

    @Test
    void contentActiveRoomsByTypeBuildsConstantPromql() {
        String promql = registry.buildPromql("content_active_rooms_by_type", Map.of());

        assertThat(promql).isEqualTo("sum by (content_type) (nemonic_content_active_rooms)");
    }

    @Test
    void hostCpuUsagePercentBuildsExpectedPromql() {
        String promql = registry.buildPromql("host_cpu_usage_percent", Map.of("window", "1m"));

        assertThat(promql)
            .isEqualTo("100 - (avg by (instance) (irate(node_cpu_seconds_total{mode=\"idle\"}[1m])) * 100)");
    }

    @Test
    void hostMemoryUsagePercentBuildsExpectedPromql() {
        String promql = registry.buildPromql("host_memory_usage_percent", Map.of());

        assertThat(promql).isEqualTo("(1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes) * 100");
    }

    @Test
    void hostDiskUsagePercentBuildsExpectedPromql() {
        String promql = registry.buildPromql("host_disk_usage_percent", Map.of());

        assertThat(promql)
            .isEqualTo("(node_filesystem_size_bytes{mountpoint=\"/\"} - node_filesystem_avail_bytes{mountpoint=\"/\"})"
                + " / node_filesystem_size_bytes{mountpoint=\"/\"} * 100");
    }

    @Test
    void hostNetworkReceiveBytesBuildsExpectedPromql() {
        String promql = registry.buildPromql("host_network_receive_bytes", Map.of("window", "1m"));

        assertThat(promql).isEqualTo("sum by (device) (rate(node_network_receive_bytes_total[1m]))");
    }

    @Test
    void hostNetworkTransmitBytesBuildsExpectedPromql() {
        String promql = registry.buildPromql("host_network_transmit_bytes", Map.of("window", "1m"));

        assertThat(promql).isEqualTo("sum by (device) (rate(node_network_transmit_bytes_total[1m]))");
    }

    @Test
    void unknownTemplateRejected() {
        assertThatThrownBy(() -> registry.buildPromql("bogus_template", Map.of()))
            .isInstanceOf(AdminMetricsException.class)
            .satisfies(error -> assertThat(((AdminMetricsException) error).code())
                .isEqualTo(AdminMetricsException.UNKNOWN_TEMPLATE));
    }

    @Test
    void unexpectedParamKeyRejected() {
        Map<String, String> params = new HashMap<>();
        params.put("window", "5m");
        params.put("unexpected", "value");
        assertThatThrownBy(() -> registry.buildPromql("ws_connect_rate", params))
            .isInstanceOf(AdminMetricsException.class)
            .satisfies(error -> assertThat(((AdminMetricsException) error).code())
                .isEqualTo(AdminMetricsException.INVALID_PARAM));
    }

    @Test
    void stepWithinAllowedRangeAccepted() {
        assertThat(MetricsTemplateRegistry.isValidStep("30s")).isTrue();
        assertThat(MetricsTemplateRegistry.isValidStep("1m")).isTrue();
        assertThat(MetricsTemplateRegistry.isValidStep("1h")).isTrue();
    }

    @Test
    void stepOutsideAllowedRangeRejected() {
        assertThat(MetricsTemplateRegistry.isValidStep("4s")).isFalse();
        assertThat(MetricsTemplateRegistry.isValidStep("0s")).isFalse();
        assertThat(MetricsTemplateRegistry.isValidStep("2h")).isFalse();
        assertThat(MetricsTemplateRegistry.isValidStep("5d")).isFalse();
        assertThat(MetricsTemplateRegistry.isValidStep("abc")).isFalse();
        assertThat(MetricsTemplateRegistry.isValidStep(null)).isFalse();
    }

    @Test
    void templateRegistryHasAllExpectedIds() {
        assertThat(registry.templateIds()).containsExactlyInAnyOrder("service_up_status", "nginx_request_rate",
            "nginx_active_connections", "spring_http_rps_by_service", "spring_http_latency_quantile",
            "spring_http_error_ratio", "ws_active_sessions", "ws_connect_rate", "ws_disconnect_rate",
            "content_active_rooms_by_type", "host_cpu_usage_percent", "host_memory_usage_percent",
            "host_disk_usage_percent", "host_network_receive_bytes", "host_network_transmit_bytes");
    }
}
