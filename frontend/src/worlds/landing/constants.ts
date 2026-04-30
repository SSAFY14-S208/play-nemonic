// 측정 기준: Box3 실측 (scale=0.1일 때 max_y=0.0810, x_half=0.0694, z_half=0.0333)
// → scale 제거 후 원본 크기 복원 (×10)

// 책상 상판 표면 Y좌표 (GLB 원본 스케일 기준)
export const DESK_SURFACE_Y = 0.81;

// X축 이동 허용 반폭 (0.694m - 캡슐 반지름 여유 0.05m)
export const DESK_HALF_WIDTH = 0.64;

// Z축 이동 허용 반깊이 (0.333m - 캡슐 반지름 여유 0.05m)
export const DESK_HALF_DEPTH = 0.27;
