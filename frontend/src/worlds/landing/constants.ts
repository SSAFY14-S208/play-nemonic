import { CAPSULE_HALF_HEIGHT, CAPSULE_RADIUS } from "../_infra/constants";

// 측정 기준: Box3 실측 (scale=0.1일 때 max_y=0.0810, x_half=0.0694, z_half=0.0333)
// → scale 제거 후 원본 크기 복원 (×10)

// 책상 상판 표면 Y좌표 (GLB 원본 스케일 기준)
export const DESK_SURFACE_Y = 0.767;

// X축 이동 허용 반폭 (0.694m - 캡슐 반지름 여유 0.05m)
export const DESK_HALF_WIDTH = 0.72;

// Z축 이동 허용 반깊이 (0.333m - 캡슐 반지름 여유 0.05m)
export const DESK_HALF_DEPTH = 0.26;

// 네모닉 프린터 위치 — GLB 원점이 모델 바닥 기준이면 Y = DESK_SURFACE_Y
// ⚠️ 실행 후 Box3 측정으로 X/Z 오프셋 보정 필요
export const NEMONIC_PRINTER_POSITION: [number, number, number] = [
  0,
  DESK_SURFACE_Y,
  0,
];

// 프린터 근접 감지 반경 (BallCollider)
export const PRINTER_PROXIMITY_RADIUS = 0.12;

// 프린터 HUD 표시 Y 오프셋 (프린터 위치 기준)
export const PRINTER_HUD_OFFSET_Y = 0.15;

// 랜딩 씬 캐릭터 초기 스폰 위치 — 프린터(Z=0)와 겹치지 않도록 Z 오프셋
export const CHARACTER_INITIAL_POSITION: [number, number, number] = [
  0,
  DESK_SURFACE_Y + CAPSULE_HALF_HEIGHT + CAPSULE_RADIUS,
  0.2,
];
