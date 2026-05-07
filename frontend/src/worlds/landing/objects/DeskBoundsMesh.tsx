import { RigidBody, CuboidCollider } from "@react-three/rapier";
import { DESK_SURFACE_Y, DESK_HALF_WIDTH, DESK_HALF_DEPTH } from "../constants";

// 책상 상판 가장자리에 보이지 않는 경계벽 4개를 둔다.
// KinematicCharacterController가 이 collider들을 인식해 캐릭터를 자동으로 막아준다.
// 시각적 mesh는 없음 — RigidBody + CuboidCollider만으로 충돌 처리.
const WALL_HEIGHT = 0.2; // m — 캐릭터 키(~0.04m) 대비 충분히 높게
const WALL_THICKNESS = 0.001; // m
// Rapier는 모든 설정값을 /2 한 값을 요구하기 때문에 실제로 args에 담을 값은 /2 처리를 해서 사용함
const WALL_HALF_HEIGHT = WALL_HEIGHT / 2;
const WALL_HALF_THICKNESS = WALL_THICKNESS / 2;

// 벽 중심 Y — 캐릭터가 충분히 그 안에 들어가도록 surface 위로 올림
const WALL_CENTER_Y = DESK_SURFACE_Y + WALL_HALF_HEIGHT;

export default function DeskBoundsMesh() {
  return (
    <>
      {/* +X 벽 (오른쪽) */}
      <RigidBody type="fixed" colliders={false}>
        <CuboidCollider
          args={[WALL_HALF_THICKNESS, WALL_HALF_HEIGHT, DESK_HALF_DEPTH]}
          position={[DESK_HALF_WIDTH, WALL_CENTER_Y, 0]}
        />
      </RigidBody>
      {/* -X 벽 (왼쪽) */}
      <RigidBody type="fixed" colliders={false}>
        <CuboidCollider
          args={[WALL_HALF_THICKNESS, WALL_HALF_HEIGHT, DESK_HALF_DEPTH]}
          position={[-DESK_HALF_WIDTH, WALL_CENTER_Y, 0]}
        />
      </RigidBody>
      {/* +Z 벽 (앞쪽) */}
      <RigidBody type="fixed" colliders={false}>
        <CuboidCollider
          args={[DESK_HALF_WIDTH, WALL_HALF_HEIGHT, WALL_HALF_THICKNESS]}
          position={[0, WALL_CENTER_Y, DESK_HALF_DEPTH]}
        />
      </RigidBody>
      {/* -Z 벽 (뒤쪽) */}
      <RigidBody type="fixed" colliders={false}>
        <CuboidCollider
          args={[DESK_HALF_WIDTH, WALL_HALF_HEIGHT, WALL_HALF_THICKNESS]}
          position={[0, WALL_CENTER_Y, -DESK_HALF_DEPTH]}
        />
      </RigidBody>
    </>
  );
}
