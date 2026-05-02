// ─── Bear GLB 메타데이터 (Blender source of truth, unscaled) ────────────────
// Blender export 시 1.3435m. 로컬 원점은 골반(hip)에 위치 — 발바닥/머리정수리는
// 원점에서 오프셋. GLB 재export 시 이 블록만 갱신하면 나머지가 자동 정합.
export const BEAR_TOTAL_HEIGHT = 1.3435; // m (unscaled)
export const BEAR_FOOT_OFFSET_Y = -0.04; // m (unscaled) — 발바닥의 로컬 y
export const BEAR_HEAD_OFFSET_Y = 0.9148; // m (unscaled) — 머리 정수리의 로컬 y

// ─── 씬 스케일 ───────────────────────────────────────────────────────────────
// 책상 세계가 작게 모델링되어 있어 (책상 높이 0.767m, 상판 1.44m × 0.52m)
// 1.34m 곰돌이를 그대로 두면 책상보다 훨씬 큼. 책상 위 피규어 크기로 축소.
// 0.2 → 곰돌이 표시 키 = 1.3435 × 0.2 ≈ 26.9cm
export const MODEL_SCALE = 0.03;

// 스케일 적용 후 실제 표시 치수 (파생)
export const BEAR_VISUAL_HEIGHT = BEAR_TOTAL_HEIGHT * MODEL_SCALE;
export const BEAR_VISUAL_FOOT_OFFSET_Y = BEAR_FOOT_OFFSET_Y * MODEL_SCALE;

// ─── 물리 캡슐 (Character.tsx & useCharacterMovement.ts 공유) ────────────────
// CapsuleCollider args = [halfHeight, radius]
// 총 캡슐 높이 = 2 * (halfHeight + radius) ≈ BEAR_VISUAL_HEIGHT
export const CAPSULE_RADIUS = 0.025; // m — 표시 곰돌이 몸통 단면 반지름
export const CAPSULE_HALF_HEIGHT = 0.074; // m — 원기둥부 반높이
// → 총 캡슐 = 2 * (0.074 + 0.06) = 0.268m ≈ BEAR_VISUAL_HEIGHT ✓

// 시각 모델을 RigidBody 안에서 얼마나 내려서 배치할지 (파생 상수).
// group의 position은 부모(RigidBody) 좌표계 기준이라 group 자신의 scale은 영향 없음.
// 곰돌이 발바닥(스케일 적용 후)이 캡슐 바닥에 닿으려면:
//   group_local_y + BEAR_VISUAL_FOOT_OFFSET_Y = -(CAPSULE_HALF_HEIGHT + CAPSULE_RADIUS)
//   group_local_y = -(CAPSULE_HALF_HEIGHT + CAPSULE_RADIUS) - BEAR_VISUAL_FOOT_OFFSET_Y
export const MODEL_OFFSET_Y =
  -(CAPSULE_HALF_HEIGHT + CAPSULE_RADIUS) - BEAR_VISUAL_FOOT_OFFSET_Y;

// ─── 이동 파라미터 ───────────────────────────────────────────────────────────
// 약 4cm 표시 캐릭터 기준 — 사람이 자연스럽게 걷는 속도(자기 키의 ~1배/초)에 맞춤
export const WALK_SPEED = 0.1; // m/s ≈ BEAR_VISUAL_HEIGHT × 1
export const STOP_DISTANCE = 0.003; // m — 캐릭터 크기 대비 ~7%, Rapier 미세 노이즈는 회피
export const ROTATION_SLERP_SPEED = 0.18; // 작은 캐릭터일수록 회전 늦으면 미끄러져 보임

// 캐릭터 애니메이션 (새 GLB 액션명도 동일)
export const FADE_DURATION = 0.3;

export const IDLE_ANIMATION = "idle";
export const WALK_ANIMATION = "walk";
export const DANCE_ANIMATION = "dance";

// Ctrl+1~9는 브라우저 탭 전환 단축키로 예약되어 가로챌 수 없음 → KeyD 사용
export const DANCE_KEY_CODE = "KeyD";
