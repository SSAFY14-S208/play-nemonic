// Nemonic 단독 전시 기준 높이
export const NEMONIC_DISPLAY_Y = 0;

// 네모닉 프린터 위치
export const NEMONIC_PRINTER_POSITION: [number, number, number] = [
  0,
  NEMONIC_DISPLAY_Y,
  0,
];

// ── 카메라 궤도 ──
export const ORBIT_TARGET_Y = NEMONIC_DISPLAY_Y + 0.083; // 프린터 시각 중심
export const ORBIT_DISTANCE = 1.8;
export const ORBIT_POLAR_ANGLE = (55 * Math.PI) / 180; // 수평면에서 ~35도 위
export const ORBIT_MIN_POLAR = 0.4;
export const ORBIT_MAX_POLAR = 1.3;
export const ORBIT_MIN_DISTANCE = 0.1;
export const ORBIT_MAX_DISTANCE = 1.5;
export const ORBIT_AUTO_ROTATE_SPEED = 1.5; // ~40초에 1회전
export const CAMERA_FOV = 30;
