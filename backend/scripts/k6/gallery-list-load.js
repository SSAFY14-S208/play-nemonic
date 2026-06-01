import http from "k6/http";
import {check, sleep} from "k6";
import {summaryOutputs} from "./lib/summary.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api/v1";
const USER_UUID = __ENV.USER_UUID || "00000000-0000-0000-0000-000000000001";
const PAGE = __ENV.PAGE || "0";
const SIZE = __ENV.SIZE || "20";
const VUS = Number(__ENV.VUS || 20);
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || 500);

export const options = {
    summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
    scenarios: {
        gallery_list: {
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
        "http_reqs{endpoint:gallery_list}": ["count>0"],
        "checks{endpoint:gallery_list}": ["rate>0.99"],
        "http_req_failed{endpoint:gallery_list}": ["rate<0.01"],
        "http_req_duration{endpoint:gallery_list}": [`p(95)<${P95_THRESHOLD_MS}`],
    },
};

export default function () {
    const response = http.get(`${BASE_URL}/gallery?page=${PAGE}&size=${SIZE}`, {
        headers: {
            "Anonymous-User-UUID": USER_UUID,
            Accept: "application/json",
        },
        tags: {
            endpoint: "gallery_list",
        },
    });

    check(response, {
        "gallery status is 200": (res) => res.status === 200,
        "gallery success is true": (res) => res.json("success") === true,
    }, {endpoint: "gallery_list"});

    sleep(Number(__ENV.SLEEP_SECONDS || 1));
}

export function handleSummary(data) {
    return summaryOutputs("갤러리 목록 조회 k6 부하 테스트", data,
        "backend/docs/performance/k6-results/gallery-list-load", "gallery_list");
}
