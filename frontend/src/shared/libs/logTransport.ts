// 로그 트랜스포트 — 이벤트 버퍼링, batch flush, sendBeacon 분할 전송
// apiUnwrap을 사용하지 않고 api.post를 직접 호출한다 (실패 시 silent discard).
// 로그 전송 실패를 재귀 로깅하지 않는다 (무한 루프 방지).

import type { BeaconPriority, LogEvent, LogEventName } from "@/shared/types";

import { runtime } from "@/shared/config";

import { api } from "./apiClient";

const FLUSH_INTERVAL_MS = 5_000;
const BUFFER_THRESHOLD = 50;
const BEACON_MAX_BYTES = 60_000; // 64KB 한도에서 여유 확보

// ── 버퍼 ──

let eventBuffer: LogEvent[] = [];
let flushTimer: ReturnType<typeof setInterval> | null = null;
let isFlushSuppressed = false; // 재귀 방지 플래그

// ── 우선순위 매핑 ──

const ERROR_EVENTS: Set<LogEventName> = new Set([
  "js_error",
  "unhandled_rejection",
  "client_network_failed",
]);

const FUNNEL_CHURN_EVENTS: Set<LogEventName> = new Set([
  "funnel_started",
  "funnel_step_viewed",
  "funnel_step_completed",
  "funnel_goal_reached",
  "funnel_abandoned",
  "page_exit_intent_detected",
  "room_lobby_abandoned",
  "creation_abandoned",
  "result_share_abandoned",
  "session_start",
  "session_end",
  "page_view",
  "page_leave",
  "landing_source_detected",
  "campaign_attributed",
  "share_link_opened",
]);

const HEARTBEAT_EVENTS: Set<LogEventName> = new Set(["client_alive"]);

function getBeaconPriority(event: LogEvent): BeaconPriority {
  if (ERROR_EVENTS.has(event.event_name)) return "error";
  if (FUNNEL_CHURN_EVENTS.has(event.event_name)) return "funnel";
  if (HEARTBEAT_EVENTS.has(event.event_name)) return "heartbeat";
  return "ui";
}

const PRIORITY_ORDER: BeaconPriority[] = ["error", "funnel", "ui", "heartbeat"];

// ── 공개 API ──

export function enqueueEvent(event: LogEvent): void {
  eventBuffer.push(event);

  // 오류 이벤트는 즉시 flush
  if (ERROR_EVENTS.has(event.event_name)) {
    flushBuffer();
    return;
  }

  if (eventBuffer.length >= BUFFER_THRESHOLD) {
    flushBuffer();
  }
}

export function startPeriodicFlush(): void {
  if (flushTimer !== null) return;
  flushTimer = setInterval(flushBuffer, FLUSH_INTERVAL_MS);
}

export function stopPeriodicFlush(): void {
  if (flushTimer !== null) {
    clearInterval(flushTimer);
    flushTimer = null;
  }
}

export function flushBuffer(): void {
  if (isFlushSuppressed || eventBuffer.length === 0) return;

  const events = eventBuffer;
  eventBuffer = [];

  isFlushSuppressed = true;
  api
    .post(`logs/client`, { events })
    .catch(() => {
      // silent discard — 로그 전송 실패를 재귀 로깅하지 않는다
    })
    .finally(() => {
      isFlushSuppressed = false;
    });
}

export function flushWithBeacon(): void {
  if (eventBuffer.length === 0) return;

  const events = eventBuffer;
  eventBuffer = [];

  // 우선순위별 분류
  const buckets = new Map<BeaconPriority, LogEvent[]>();
  for (const event of events) {
    const priority = getBeaconPriority(event);
    const bucket = buckets.get(priority);
    if (bucket) {
      bucket.push(event);
    } else {
      buckets.set(priority, [event]);
    }
  }

  // 우선순위 순서로 64KB 이내에서 전송
  const url = `${runtime.apiUrl}/api/v1/logs/client`;
  const batch: LogEvent[] = [];

  for (const priority of PRIORITY_ORDER) {
    const bucket = buckets.get(priority);
    if (!bucket) continue;

    for (const event of bucket) {
      batch.push(event);
      const payload = JSON.stringify({ events: batch });
      if (new Blob([payload]).size > BEACON_MAX_BYTES) {
        // 이 이벤트를 넣으면 초과 → 빼고 현재까지 전송
        batch.pop();
        if (batch.length > 0) {
          navigator.sendBeacon(
            url,
            new Blob([JSON.stringify({ events: batch })], {
              type: "application/json",
            }),
          );
          batch.length = 0;
        }
        // 빼놓은 이벤트를 다음 batch로
        batch.push(event);
      }
    }
  }

  // 남은 이벤트 전송
  if (batch.length > 0) {
    navigator.sendBeacon(
      url,
      new Blob([JSON.stringify({ events: batch })], {
        type: "application/json",
      }),
    );
  }
}

export function getBufferSize(): number {
  return eventBuffer.length;
}
