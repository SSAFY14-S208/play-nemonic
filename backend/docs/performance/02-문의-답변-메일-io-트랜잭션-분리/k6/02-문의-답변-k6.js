import http from "k6/http";
import {check, sleep} from "k6";
import {summaryOutputs} from "../../k6-common/summary.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api/v1";
const ADMIN_TOKEN = __ENV.ADMIN_TOKEN || "";
const VUS = Number(__ENV.VUS || 10);
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || 5000);

export const options = {
    summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
    scenarios: {
        admin_inquiry_reply: {
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
        "http_reqs{endpoint:admin_inquiry_reply}": ["count>0"],
        "checks{endpoint:admin_inquiry_reply}": ["rate>0.95"],
        "http_req_failed{endpoint:admin_inquiry_reply}": ["rate<0.05"],
        "http_req_duration{endpoint:admin_inquiry_reply}": [`p(95)<${P95_THRESHOLD_MS}`],
    },
};

export function setup() {
    if (!ADMIN_TOKEN) {
        throw new Error("ADMIN_TOKEN env is required for admin inquiry reply load test.");
    }
}

function createAnonymousUser() {
    const response = http.post(`${BASE_URL}/users/anonymous`, null, {
        headers: {
            "User-Agent": "k6-admin-inquiry-reply-load",
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

function createInquiry(userUuid) {
    const body = JSON.stringify({
        title: `k6 문의 ${Date.now()}`,
        type: "error",
        content: "k6 부하 테스트용 문의입니다.",
        email: "k6@example.com",
        attachments: [],
        meta: {
            source: "k6",
        },
    });

    const response = http.post(`${BASE_URL}/inquiries`, body, {
        headers: {
            "Anonymous-User-UUID": userUuid,
            "Content-Type": "application/json",
            Accept: "application/json",
        },
        tags: {
            endpoint: "inquiry_create",
        },
    });

    check(response, {
        "inquiry created": (res) => res.status === 201,
        "inquiry id exists": (res) => Boolean(res.json("data.id")),
    }, {endpoint: "inquiry_create"});

    return response.json("data.id");
}

export default function () {
    const userUuid = createAnonymousUser();
    if (!userUuid) {
        return;
    }

    const inquiryId = createInquiry(userUuid);
    if (!inquiryId) {
        return;
    }

    const response = http.post(`${BASE_URL}/admin/inquiries/${inquiryId}/reply`, JSON.stringify({
        subject: "k6 문의 답변",
        message: "k6 부하 테스트용 답변입니다.",
    }), {
        headers: {
            Authorization: `Bearer ${ADMIN_TOKEN}`,
            "Content-Type": "application/json",
            Accept: "application/json",
        },
        tags: {
            endpoint: "admin_inquiry_reply",
        },
    });

    check(response, {
        "inquiry reply status is 200": (res) => res.status === 200,
        "inquiry reply success is true": (res) => res.json("success") === true,
    }, {endpoint: "admin_inquiry_reply"});

    sleep(Number(__ENV.SLEEP_SECONDS || 1));
}

export function handleSummary(data) {
    return summaryOutputs("문의 답변 SMTP 외부 I/O 트랜잭션 분리 k6 부하 테스트", data,
        "backend/docs/performance/02-문의-답변-메일-io-트랜잭션-분리/k6/results/02-문의-답변-k6-결과", "admin_inquiry_reply");
}
