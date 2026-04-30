import { useRef } from "react";
import { useFrame } from "@react-three/fiber";
import { type RapierRigidBody } from "@react-three/rapier";
import * as THREE from "three";
import {
  WALK_SPEED,
  STOP_DISTANCE,
  ROTATION_SLERP_SPEED,
  CAPSULE_HALF_HEIGHT,
  CAPSULE_RADIUS,
} from "../constants";

const UP_AXIS = new THREE.Vector3(0, 1, 0);

export function useCharacterMovement(
  rigidBodyRef: React.RefObject<RapierRigidBody | null>,
  targetPositionRef: React.RefObject<THREE.Vector3>,
  isPointerDownRef: React.RefObject<boolean>,
  characterPositionRef: React.RefObject<THREE.Vector3>,
  surfaceY: number = 0,
) {
  const characterY = surfaceY + CAPSULE_HALF_HEIGHT + CAPSULE_RADIUS;
  const isMovingRef = useRef(false);
  // Rapier 좌표계 왕복 대신 Three.js 쪽에서 직접 추적 (부동소수점 오차 누적 방지)
  const currentQuaternionRef = useRef(new THREE.Quaternion());
  const targetQuaternion = new THREE.Quaternion();

  useFrame((_, delta) => {
    const rigidBody = rigidBodyRef.current;
    if (!rigidBody) return;

    const translation = rigidBody.translation();

    // 매 프레임 characterPositionRef 동기화 (카메라 추적용)
    characterPositionRef.current.set(
      translation.x,
      translation.y,
      translation.z,
    );

    // 포인터가 눌리지 않은 경우 정지
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

    // 목표 방향으로 이동
    const moveStep = Math.min(WALK_SPEED * delta, horizontalDistance);
    const normalizedX = directionX / horizontalDistance;
    const normalizedZ = directionZ / horizontalDistance;

    const nextX = translation.x + normalizedX * moveStep;
    const nextZ = translation.z + normalizedZ * moveStep;

    rigidBody.setNextKinematicTranslation({
      x: nextX,
      y: characterY,
      z: nextZ,
    });

    // 이동 방향으로 부드럽게 회전
    const targetAngle = Math.atan2(normalizedX, normalizedZ);
    targetQuaternion.setFromAxisAngle(UP_AXIS, targetAngle);

    currentQuaternionRef.current.slerp(targetQuaternion, ROTATION_SLERP_SPEED);
    rigidBody.setNextKinematicRotation(currentQuaternionRef.current);
  });

  return isMovingRef;
}
