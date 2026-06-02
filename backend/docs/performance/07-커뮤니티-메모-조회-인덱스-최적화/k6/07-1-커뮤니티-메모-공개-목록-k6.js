import http from "k6/http";
import {check, sleep} from "k6";
import {summaryOutputs} from "../../k6-common/summary.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api/v1";
const USER_UUID = __ENV.USER_UUID || "";
const VUS = Number(__ENV.VUS || 10);
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || 300);
const ENDPOINT = "community_memo_public_list";

export const options = {
    summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
    scenarios: {
        community_memo_public_list: {
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
        "http_reqs{endpoint:community_memo_public_list}": ["count>0"],
        "checks{endpoint:community_memo_public_list}": ["rate>0.99"],
        "http_req_failed{endpoint:community_memo_public_list}": ["rate<0.01"],
        "http_req_duration{endpoint:community_memo_public_list}": [`p(95)<${P95_THRESHOLD_MS}`],
    },
};

export default function () {
    const headers = {Accept: "application/json"};
    if (USER_UUID) {
        headers["Anonymous-User-UUID"] = USER_UUID;
    }

    const response = http.get(`${BASE_URL}/community/memos`, {
        headers,
        tags: {
            endpoint: ENDPOINT,
        },
    });

    check(response, {
        "community memo public list status is 200": (res) => res.status === 200,
        "community memo public list success is true": (res) => res.json("success") === true,
        "community memo public list items is array": (res) => Array.isArray(res.json("data.items")),
    }, {endpoint: ENDPOINT});

    sleep(Number(__ENV.SLEEP_SECONDS || 1));
}

export function handleSummary(data) {
    return summaryOutputs("커뮤니티 메모 공개 목록 k6 부하 테스트", data,
        "backend/docs/performance/07-커뮤니티-메모-조회-인덱스-최적화/k6/results/07-1-커뮤니티-메모-공개-목록-k6-결과",
        ENDPOINT);
}
