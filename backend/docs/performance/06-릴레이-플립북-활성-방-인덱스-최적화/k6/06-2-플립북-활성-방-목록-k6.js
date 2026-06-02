import http from "k6/http";
import {check, sleep} from "k6";
import {summaryOutputs} from "../../k6-common/summary.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api/v1";
const ADMIN_TOKEN = __ENV.ADMIN_TOKEN || "";
const STATUS = __ENV.STATUS || "";
const PAGE = __ENV.PAGE || "0";
const SIZE = __ENV.SIZE || "20";
const VUS = Number(__ENV.VUS || 10);
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || 500);
const ENDPOINT = "flipbook_active_rooms";

export const options = {
    summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
    scenarios: {
        flipbook_active_rooms: {
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
        "http_reqs{endpoint:flipbook_active_rooms}": ["count>0"],
        "checks{endpoint:flipbook_active_rooms}": ["rate>0.99"],
        "http_req_failed{endpoint:flipbook_active_rooms}": ["rate<0.01"],
        "http_req_duration{endpoint:flipbook_active_rooms}": [`p(95)<${P95_THRESHOLD_MS}`],
    },
};

export function setup() {
    if (!ADMIN_TOKEN) {
        throw new Error("ADMIN_TOKEN env is required for backoffice flipbook active room load test.");
    }
}

export default function () {
    const statusQuery = STATUS ? `status=${encodeURIComponent(STATUS)}&` : "";
    const response = http.get(`${BASE_URL}/backoffice/flipbook-rooms?${statusQuery}page=${PAGE}&size=${SIZE}`, {
        headers: {
            Authorization: `Bearer ${ADMIN_TOKEN}`,
            Accept: "application/json",
        },
        tags: {
            endpoint: ENDPOINT,
        },
    });

    check(response, {
        "flipbook active rooms status is 200": (res) => res.status === 200,
        "flipbook active rooms success is true": (res) => res.json("success") === true,
        "flipbook active rooms items is array": (res) => Array.isArray(res.json("data.items")),
    }, {endpoint: ENDPOINT});

    sleep(Number(__ENV.SLEEP_SECONDS || 1));
}

export function handleSummary(data) {
    return summaryOutputs("플립북 활성 방 목록 k6 부하 테스트", data,
        "backend/docs/performance/06-릴레이-플립북-활성-방-인덱스-최적화/k6/results/06-2-플립북-활성-방-목록-k6-결과",
        ENDPOINT);
}
