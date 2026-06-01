import http from "k6/http";
import {check, sleep} from "k6";
import {summaryOutputs} from "../../k6-common/summary.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api/v1";
const ADMIN_TOKEN = __ENV.ADMIN_TOKEN || "";
const STATUS = __ENV.STATUS || "ACTIVE";
const PAGE = __ENV.PAGE || "0";
const SIZE = __ENV.SIZE || "20";
const VUS = Number(__ENV.VUS || 20);
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || 500);

export const options = {
    summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
    scenarios: {
        infinite_canvas_active_rooms: {
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
        "http_reqs{endpoint:infinite_canvas_active_rooms}": ["count>0"],
        "checks{endpoint:infinite_canvas_active_rooms}": ["rate>0.99"],
        "http_req_failed{endpoint:infinite_canvas_active_rooms}": ["rate<0.01"],
        "http_req_duration{endpoint:infinite_canvas_active_rooms}": [`p(95)<${P95_THRESHOLD_MS}`],
    },
};

export function setup() {
    if (!ADMIN_TOKEN) {
        throw new Error("ADMIN_TOKEN env is required for backoffice infinite canvas load test.");
    }
}

export default function () {
    const response = http.get(`${BASE_URL}/backoffice/infinite-canvas/canvases?status=${STATUS}&page=${PAGE}&size=${SIZE}`,
        {
            headers: {
                Authorization: `Bearer ${ADMIN_TOKEN}`,
                Accept: "application/json",
            },
            tags: {
                endpoint: "infinite_canvas_active_rooms",
            },
        });

    check(response, {
        "infinite canvas status is 200": (res) => res.status === 200,
        "infinite canvas success is true": (res) => res.json("success") === true,
    }, {endpoint: "infinite_canvas_active_rooms"});

    sleep(Number(__ENV.SLEEP_SECONDS || 1));
}

export function handleSummary(data) {
    return summaryOutputs("무한캔버스 활성 방 목록 k6 부하 테스트", data,
        "backend/docs/performance/04-무한캔버스-조회-payload-최적화/k6/results/04-무한캔버스-활성-방-목록-k6-결과", "infinite_canvas_active_rooms");
}
