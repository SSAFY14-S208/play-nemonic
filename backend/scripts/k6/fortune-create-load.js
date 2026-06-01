import http from "k6/http";
import {check, sleep} from "k6";
import {summaryOutputs} from "./lib/summary.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api/v1";
const VUS = Number(__ENV.VUS || 10);
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || 3000);

const fortuneRequestBody = JSON.stringify({
    calendarType: "solar",
    yearPillar: "임신",
    monthPillar: "경술",
    dayPillar: "계유",
    hourPillar: "을묘",
    dayMasterElement: "수",
    dayBranchElement: "금",
    dayMasterYinYang: "음",
    dayBranchYinYang: "음",
});

export const options = {
    scenarios: {
        fortune_create: {
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
        "http_reqs{endpoint:fortune_create}": ["count>0"],
        "checks{endpoint:fortune_create}": ["rate>0.95"],
        "http_req_failed{endpoint:fortune_create}": ["rate<0.05"],
        "http_req_duration{endpoint:fortune_create}": [`p(95)<${P95_THRESHOLD_MS}`],
    },
};

function createAnonymousUser() {
    const response = http.post(`${BASE_URL}/users/anonymous`, null, {
        headers: {
            "User-Agent": "k6-fortune-create-load",
            Accept: "application/json",
        },
        tags: {
            endpoint: "anonymous_user_create",
        },
    });

    check(response, {
        "anonymous user created": (res) => res.status === 201,
        "anonymous user uuid exists": (res) => Boolean(res.json("data.userUuid")),
    }, {endpoint: "anonymous_user_create"});

    return response.json("data.userUuid");
}

export default function () {
    const userUuid = createAnonymousUser();
    if (!userUuid) {
        return;
    }

    const response = http.post(`${BASE_URL}/fortune`, fortuneRequestBody, {
        headers: {
            "Anonymous-User-UUID": userUuid,
            "Content-Type": "application/json",
            Accept: "application/json",
        },
        tags: {
            endpoint: "fortune_create",
        },
    });

    check(response, {
        "fortune create status is 200": (res) => res.status === 200,
        "fortune create success is true": (res) => res.json("success") === true,
    }, {endpoint: "fortune_create"});

    sleep(Number(__ENV.SLEEP_SECONDS || 1));
}

export function handleSummary(data) {
    return summaryOutputs("운세 생성 외부 I/O 트랜잭션 분리 k6 부하 테스트", data,
        "backend/docs/performance/k6-results/fortune-create-load", "fortune_create");
}
