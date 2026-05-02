import { useRef, useEffect } from "react";
import { useGLTF, useAnimations } from "@react-three/drei";
import {
  RigidBody,
  CapsuleCollider,
  type RapierRigidBody,
} from "@react-three/rapier";
import * as THREE from "three";
import { useCharacterMovement, useCharacterAnimation } from "./hooks";
import {
  MODEL_SCALE,
  MODEL_OFFSET_Y,
  CAPSULE_HALF_HEIGHT,
  CAPSULE_RADIUS,
} from "./constants";

const MODEL_PATH = "/models/nong_dam_gom.glb";

interface CharacterProps {
  targetPositionRef?: React.RefObject<THREE.Vector3>;
  characterPositionRef?: React.RefObject<THREE.Vector3>;
  isPointerDownRef?: React.RefObject<boolean>;
  surfaceY?: number;
  initialPosition?: [number, number, number];
}

export default function Character({
  targetPositionRef: externalTargetRef,
  characterPositionRef: externalCharacterRef,
  isPointerDownRef: externalPointerRef,
  surfaceY = 0,
  initialPosition,
}: CharacterProps) {
  const rigidBodyRef = useRef<RapierRigidBody>(null);
  const groupRef = useRef<THREE.Group>(null);

  // props가 없을 때 fallback refs (HubScene 등 독립 사용 시)
  const internalTargetRef = useRef<THREE.Vector3>(new THREE.Vector3());
  const internalCharacterRef = useRef<THREE.Vector3>(new THREE.Vector3());
  const internalPointerRef = useRef<boolean>(false);

  const targetPositionRef = externalTargetRef ?? internalTargetRef;
  const characterPositionRef = externalCharacterRef ?? internalCharacterRef;
  const isPointerDownRef = externalPointerRef ?? internalPointerRef;

  const { scene, animations } = useGLTF(MODEL_PATH);
  const { actions } = useAnimations(animations, groupRef);

  // GLB 내부 모든 Mesh에 castShadow 적용 (group에는 전파 안 됨)
  useEffect(() => {
    scene.traverse((child) => {
      if (child instanceof THREE.Mesh) {
        child.castShadow = true;
      }
    });
  }, [scene]);

  const isMovingRef = useCharacterMovement(
    rigidBodyRef,
    targetPositionRef,
    isPointerDownRef,
    characterPositionRef,
    surfaceY,
  );

  useCharacterAnimation(actions, isMovingRef);

  return (
    <RigidBody
      ref={rigidBodyRef}
      type="kinematicPosition"
      colliders={false}
      position={initialPosition ?? [0, surfaceY + CAPSULE_HALF_HEIGHT + CAPSULE_RADIUS, 0]}
    >
      <CapsuleCollider args={[CAPSULE_HALF_HEIGHT, CAPSULE_RADIUS]} />
      <group ref={groupRef} position={[0, MODEL_OFFSET_Y, 0]} scale={MODEL_SCALE}>
        <primitive object={scene} />
      </group>
    </RigidBody>
  );
}

useGLTF.preload(MODEL_PATH);
