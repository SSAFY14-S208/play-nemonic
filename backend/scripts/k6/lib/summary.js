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

function markdownTable(rows) {
    return [
        "| 지표 | 값 |",
        "| --- | ---: |",
        ...rows.map(([name, value]) => `| ${name} | ${value} |`),
    ].join("\n");
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
