import http from "k6/http";
import {check, sleep} from "k6";
import {summaryOutputs} from "../../k6-common/summary.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api/v1";
const ADMIN_TOKEN = __ENV.ADMIN_TOKEN || "";
const HIDDEN = __ENV.HIDDEN || "";
const MODERATION_STATUS = __ENV.MODERATION_STATUS || "";
const SOURCE_TYPE = __ENV.SOURCE_TYPE || "";
const REPORTED = __ENV.REPORTED || "true";
const KEYWORD = __ENV.KEYWORD || "";
const PAGE = __ENV.PAGE || "0";
const SIZE = __ENV.SIZE || "20";
const VUS = Number(__ENV.VUS || 10);
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || 500);
const ENDPOINT = "admin_community_memo_list";

export const options = {
    summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
    scenarios: {
        admin_community_memo_list: {
            executor: "ramping-vus",
            stages: [
                {duration: __ENV.RAMP_UP || "20s", target: VUS},
                {duration: __ENV.DURATION || "1m", target: VUS},
                {duration: __ENV.RAMP_DOWN || "10s", target: 0},
            ],
            gracefulRampDown: "5s",
        },
    },
    thresholds: {
        "http_reqs{endpoint:admin_community_memo_list}": ["count>0"],
        "checks{endpoint:admin_community_memo_list}": ["rate>0.99"],
        "http_req_failed{endpoint:admin_community_memo_list}": ["rate<0.01"],
        "http_req_duration{endpoint:admin_community_memo_list}": [`p(95)<${P95_THRESHOLD_MS}`],
    },
};

export function setup() {
    if (!ADMIN_TOKEN) {
        throw new Error("ADMIN_TOKEN env is required for admin community memo list load test.");
    }
}

function queryString() {
    const params = [
        ["hidden", HIDDEN],
        ["moderationStatus", MODERATION_STATUS],
        ["sourceType", SOURCE_TYPE],
        ["reported", REPORTED],
        ["keyword", KEYWORD],
        ["page", PAGE],
        ["size", SIZE],
    ];
    return params
        .filter(([, value]) => value !== "")
        .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
        .join("&");
}

export default function () {
    const response = http.get(`${BASE_URL}/admin/community/memos?${queryString()}`, {
        headers: {
            Authorization: `Bearer ${ADMIN_TOKEN}`,
            Accept: "application/json",
        },
        tags: {
            endpoint: ENDPOINT,
        },
    });

    check(response, {
        "admin community memo list status is 200": (res) => res.status === 200,
        "admin community memo list success is true": (res) => res.json("success") === true,
        "admin community memo list items is array": (res) => Array.isArray(res.json("data.items")),
    }, {endpoint: ENDPOINT});

    sleep(Number(__ENV.SLEEP_SECONDS || 1));
}

export function handleSummary(data) {
    return summaryOutputs("관리자 커뮤니티 메모 목록 k6 부하 테스트", data,
        "backend/docs/performance/07-커뮤니티-메모-조회-인덱스-최적화/k6/results/07-2-관리자-커뮤니티-메모-목록-k6-결과",
        ENDPOINT);
}
