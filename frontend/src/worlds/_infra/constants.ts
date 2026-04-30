// ─── 캐릭터 GLB 모델 ────────────────────────────────────────────────────────
// 원본 크기 3.35m → MODEL_SCALE 적용 후 실제 높이
export const MODEL_SCALE = 0.01;

// ─── 물리 캡슐 (Character.tsx & useCharacterMovement.ts 공유) ────────────────
// 원본 GLB feet min_y = -1.09
// 실제 feet 오프셋 = 1.09 × MODEL_SCALE = 0.0109m
// → CAPSULE_HALF_HEIGHT + CAPSULE_RADIUS = 0.011m (오차 최소)
export const CAPSULE_HALF_HEIGHT = 0.00001;
export const CAPSULE_RADIUS = 0.005;

// ─── 이동 파라미터 ───────────────────────────────────────────────────────────
export const WALK_SPEED = 0.3; // m/s — 캐릭터 키(3.35cm) 대비 적정 속도
export const STOP_DISTANCE = 0.02; // 목표 도달 판정 반경 — 마우스 micro-tremor(~10mm) 이상으로 설정해야 떨림 없음
export const ROTATION_SLERP_SPEED = 0.12;

// 캐릭터 애니메이션
export const FADE_DURATION = 0.3;

export const IDLE_ANIMATION = "idle";
export const WALK_ANIMATION = "walk";
export const DANCE_ANIMATION = "dance";

// Ctrl+1~9는 브라우저 탭 전환 단축키로 예약되어 가로챌 수 없음 → KeyD 사용
export const DANCE_KEY_CODE = "KeyD";
