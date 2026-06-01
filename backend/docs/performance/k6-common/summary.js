function metricOf(data, metricName, endpoint) {
    const taggedMetricName = endpoint ? `${metricName}{endpoint:${endpoint}}` : metricName;

    return data.metrics[taggedMetricName] || data.metrics[metricName];
}

function valueOf(data, metricName, valueName, endpoint) {
    const metric = metricOf(data, metricName, endpoint);
    if (!metric || !metric.values || metric.values[valueName] === undefined) {
        return null;
    }

    return metric.values[valueName];
}

function formatNumber(value, digits = 2) {
    if (value === null || value === undefined || Number.isNaN(value)) {
        return "-";
    }

    return Number(value).toFixed(digits);
}

function formatPercent(value) {
    if (value === null || value === undefined || Number.isNaN(value)) {
        return "-";
    }

    return `${(Number(value) * 100).toFixed(2)}%`;
}

function formatDuration(value) {
    if (value === null || value === undefined || Number.isNaN(value)) {
        return "-";
    }

    const number = Number(value);
    if (number === 0) {
        return "0s";
    }
    if (Math.abs(number) < 1) {
        return `${formatNumber(number * 1000, 2)}us`;
    }
    if (Math.abs(number) >= 1000) {
        return `${formatNumber(number / 1000, 2)}s`;
    }

    return `${formatNumber(number, 2)}ms`;
}

function formatBytes(value) {
    if (value === null || value === undefined || Number.isNaN(value)) {
        return "-";
    }

    const number = Number(value);
    if (Math.abs(number) >= 1024 * 1024) {
        return `${formatNumber(number / 1024 / 1024, 2)} MB`;
    }
    if (Math.abs(number) >= 1024) {
        return `${formatNumber(number / 1024, 2)} kB`;
    }

    return `${formatNumber(number, 0)} B`;
}

function metricLabel(name) {
    const width = 32;
    if (name.length >= width) {
        return `${name}:`;
    }

    return `${name}${".".repeat(width - name.length)}:`;
}

function appendLine(lines, name, value) {
    if (!value || value.includes("- - -")) {
        return;
    }

    lines.push(`${metricLabel(name)} ${value}`);
}

function appendBytesCounter(lines, data, name) {
    const count = valueOf(data, name, "count");
    const rate = valueOf(data, name, "rate");

    if (count === null && rate === null) {
        return;
    }

    appendLine(lines, name, `${formatBytes(count)} ${formatBytes(rate)}/s`);
}

function appendCounter(lines, data, name, endpoint) {
    const count = valueOf(data, name, "count", endpoint);
    const rate = valueOf(data, name, "rate", endpoint);

    if (count === null && rate === null) {
        return;
    }

    appendLine(lines, name, `${formatNumber(count, 0)} ${formatNumber(rate, 6)}/s`);
}

function appendGauge(lines, data, name) {
    const min = valueOf(data, name, "min");
    const max = valueOf(data, name, "max");

    if (min === null && max === null) {
        return;
    }

    appendLine(lines, name, `min=${formatNumber(min, 0)} max=${formatNumber(max, 0)}`);
}

function appendRate(lines, data, name, endpoint) {
    const rate = valueOf(data, name, "rate", endpoint);
    const passes = valueOf(data, name, "passes", endpoint);
    const fails = valueOf(data, name, "fails", endpoint);

    if (rate === null && passes === null && fails === null) {
        return;
    }

    appendLine(lines, name, `${formatPercent(rate)} ${formatNumber(passes, 0)} passed ${formatNumber(fails, 0)} failed`);
}

function appendFailureRate(lines, data, name, endpoint) {
    const rate = valueOf(data, name, "rate", endpoint);
    const failedRequests = valueOf(data, name, "passes", endpoint);
    const okRequests = valueOf(data, name, "fails", endpoint);

    if (rate === null && failedRequests === null && okRequests === null) {
        return;
    }

    appendLine(lines, name,
        `${formatPercent(rate)} ${formatNumber(failedRequests, 0)} failed ${formatNumber(okRequests, 0)} ok`);
}

function appendTrend(lines, data, name, endpoint) {
    const avg = valueOf(data, name, "avg", endpoint);
    const min = valueOf(data, name, "min", endpoint);
    const med = valueOf(data, name, "med", endpoint);
    const max = valueOf(data, name, "max", endpoint);
    const p90 = valueOf(data, name, "p(90)", endpoint);
    const p95 = valueOf(data, name, "p(95)", endpoint);
    const p99 = valueOf(data, name, "p(99)", endpoint);

    if ([avg, min, med, max, p90, p95, p99].every((value) => value === null)) {
        return;
    }

    appendLine(lines, name,
        `avg=${formatDuration(avg)} min=${formatDuration(min)} med=${formatDuration(med)} max=${formatDuration(max)} p(90)=${formatDuration(p90)} p(95)=${formatDuration(p95)} p(99)=${formatDuration(p99)}`);
}

function markdownTable(rows) {
    return [
        "| 지표 | 값 |",
        "| --- | ---: |",
        ...rows.map(([name, value]) => `| ${name} | ${value} |`),
    ].join("\n");
}

function detailedMetricLines(data, endpoint) {
    const lines = ["--- k6 상세 지표 ---"];

    appendBytesCounter(lines, data, "data_received");
    appendBytesCounter(lines, data, "data_sent");
    appendRate(lines, data, "checks", endpoint);
    appendTrend(lines, data, "http_req_blocked", endpoint);
    appendTrend(lines, data, "http_req_connecting", endpoint);
    appendTrend(lines, data, "http_req_duration", endpoint);
    appendFailureRate(lines, data, "http_req_failed", endpoint);
    appendTrend(lines, data, "http_req_receiving", endpoint);
    appendTrend(lines, data, "http_req_sending", endpoint);
    appendTrend(lines, data, "http_req_tls_handshaking", endpoint);
    appendTrend(lines, data, "http_req_waiting", endpoint);
    appendCounter(lines, data, "http_reqs", endpoint);
    appendTrend(lines, data, "iteration_duration");
    appendCounter(lines, data, "iterations");
    appendGauge(lines, data, "vus");
    appendGauge(lines, data, "vus_max");

    return lines;
}

export function buildMarkdownSummary(title, data, endpoint) {
    const rows = [
        ["측정 endpoint tag", endpoint ? `\`${endpoint}\`` : "전체"],
        ["요청 수", formatNumber(valueOf(data, "http_reqs", "count", endpoint), 0)],
        ["RPS", formatNumber(valueOf(data, "http_reqs", "rate", endpoint), 2)],
        ["실패율", formatPercent(valueOf(data, "http_req_failed", "rate", endpoint))],
        ["check 성공률", formatPercent(valueOf(data, "checks", "rate", endpoint))],
        ["평균 latency", `${formatNumber(valueOf(data, "http_req_duration", "avg", endpoint), 2)} ms`],
        ["p50 latency", `${formatNumber(valueOf(data, "http_req_duration", "med", endpoint), 2)} ms`],
        ["p95 latency", `${formatNumber(valueOf(data, "http_req_duration", "p(95)", endpoint), 2)} ms`],
        ["p99 latency", `${formatNumber(valueOf(data, "http_req_duration", "p(99)", endpoint), 2)} ms`],
    ];

    return `# ${title}

## 실행 조건

- base url: \`${__ENV.BASE_URL || "http://localhost:8080/api/v1"}\`
- vus: \`${__ENV.VUS || "20"}\`
- duration: \`${__ENV.DURATION || "1m"}\`
- ramp up: \`${__ENV.RAMP_UP || "20s"}\`
- ramp down: \`${__ENV.RAMP_DOWN || "10s"}\`

## 결과

${markdownTable(rows)}

## 상세 지표

터미널 캡처에 보이는 상세 k6 지표와 같은 값입니다.

\`\`\`text
${detailedMetricLines(data, endpoint).join("\n")}
\`\`\`

> k6 결과는 실행 환경, seed 데이터, 외부 API stub 여부에 따라 달라집니다.
`;
}

export function buildConsoleSummary(title, data, endpoint) {
    const p95 = `${formatNumber(valueOf(data, "http_req_duration", "p(95)", endpoint), 2)} ms`;
    const rps = formatNumber(valueOf(data, "http_reqs", "rate", endpoint), 2);
    const failed = formatPercent(valueOf(data, "http_req_failed", "rate", endpoint));

    return [
        "",
        `=== ${title} ===`,
        `endpoint=${endpoint || "all"} p95=${p95} rps=${rps} failed=${failed}`,
        "",
        ...detailedMetricLines(data, endpoint),
        "",
    ].join("\n");
}

export function summaryOutputs(title, data, defaultPrefix, endpoint) {
    const prefix = __ENV.RESULT_PREFIX || defaultPrefix;

    return {
        stdout: buildConsoleSummary(title, data, endpoint),
        [`${prefix}.json`]: JSON.stringify(data, null, 2),
        [`${prefix}.md`]: buildMarkdownSummary(title, data, endpoint),
    };
}
