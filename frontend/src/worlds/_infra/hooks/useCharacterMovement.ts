import { useEffect, useRef } from "react";
import { useFrame } from "@react-three/fiber";
import {
  type RapierRigidBody,
  useRapier,
} from "@react-three/rapier";
import * as THREE from "three";

// @react-three/rapier가 KinematicCharacterController 타입을 re-export하지 않아
// world.createCharacterController의 반환 타입에서 추론한다.
type CharacterController = ReturnType<
  ReturnType<typeof useRapier>["world"]["createCharacterController"]
>;
import {
  WALK_SPEED,
  STOP_DISTANCE,
  ROTATION_SLERP_SPEED,
  CAPSULE_HALF_HEIGHT,
  CAPSULE_RADIUS,
} from "../constants";

const UP_AXIS = new THREE.Vector3(0, 1, 0);
// 캐릭터와 주변 collider 사이의 최소 여유 거리 (m). 캐릭터 키(~4cm) 대비 작게.
const CHARACTER_OFFSET = 0.001;
// Rapier QueryFilterFlags.EXCLUDE_SENSORS — 센서 collider를 충돌 대상에서 제외
const EXCLUDE_SENSORS = 8;

export function useCharacterMovement(
  rigidBodyRef: React.RefObject<RapierRigidBody | null>,
  targetPositionRef: React.RefObject<THREE.Vector3>,
  isPointerDownRef: React.RefObject<boolean>,
  characterPositionRef: React.RefObject<THREE.Vector3>,
  surfaceY: number = 0,
) {
  const { world } = useRapier();
  const characterY = surfaceY + CAPSULE_HALF_HEIGHT + CAPSULE_RADIUS;
  const isMovingRef = useRef(false);
  const controllerRef = useRef<CharacterController | null>(null);
  // Rapier 좌표계 왕복 대신 Three.js 쪽에서 직접 추적 (부동소수점 오차 누적 방지)
  const currentQuaternionRef = useRef(new THREE.Quaternion());
  const targetQuaternion = new THREE.Quaternion();

  // KinematicCharacterController 생성/해제 — 씬 단위로 한 번만
  useEffect(() => {
    const controller = world.createCharacterController(CHARACTER_OFFSET);
    controller.setSlideEnabled(true); // 벽에 비스듬히 부딪히면 미끄러짐
    controller.setUp({ x: 0, y: 1, z: 0 });
    controllerRef.current = controller;
    return () => {
      world.removeCharacterController(controller);
      controllerRef.current = null;
    };
  }, [world]);

  useFrame((_, delta) => {
    const rigidBody = rigidBodyRef.current;
    const controller = controllerRef.current;
    if (!rigidBody || !controller) return;

    const translation = rigidBody.translation();

    // 매 프레임 characterPositionRef 동기화 (카메라 추적용)
    characterPositionRef.current.set(
      translation.x,
      translation.y,
      translation.z,
    );

    if (!isPointerDownRef.current) {
      isMovingRef.current = false;
      return;
    }

    const target = targetPositionRef.current;
    const directionX = target.x - translation.x;
    const directionZ = target.z - translation.z;
    const horizontalDistance = Math.sqrt(
      directionX * directionX + directionZ * directionZ,
    );

    if (horizontalDistance < STOP_DISTANCE) {
      isMovingRef.current = false;
      return;
    }

    isMovingRef.current = true;

    // 목표 방향으로 이동량 계산 (희망 이동량)
    const moveStep = Math.min(WALK_SPEED * delta, horizontalDistance);
    const normalizedX = directionX / horizontalDistance;
    const normalizedZ = directionZ / horizontalDistance;

    const desiredMovement = {
      x: normalizedX * moveStep,
      y: 0,
      z: normalizedZ * moveStep,
    };

    // KinematicCharacterController가 충돌을 고려한 valid 이동량을 계산
    // → 벽에 부딪히면 자동으로 막히고, 비스듬히 부딪히면 미끄러짐
    const collider = rigidBody.collider(0);
    controller.computeColliderMovement(collider, desiredMovement, EXCLUDE_SENSORS);
    const correctedMovement = controller.computedMovement();

    rigidBody.setNextKinematicTranslation({
      x: translation.x + correctedMovement.x,
      y: characterY,
      z: translation.z + correctedMovement.z,
    });

    // 회전은 충돌과 무관하게 원래 의도한 이동 방향을 따라감
    // (보정된 이동량이 0이어도 캐릭터는 의도 방향을 보고 있어야 자연스러움)
    const targetAngle = Math.atan2(normalizedX, normalizedZ);
    targetQuaternion.setFromAxisAngle(UP_AXIS, targetAngle);

    currentQuaternionRef.current.slerp(targetQuaternion, ROTATION_SLERP_SPEED);
    rigidBody.setNextKinematicRotation(currentQuaternionRef.current);
  });

  return isMovingRef;
}
