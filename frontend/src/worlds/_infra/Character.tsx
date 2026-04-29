import { useRef } from "react";
import { useGLTF, useAnimations } from "@react-three/drei";
import {
  RigidBody,
  CapsuleCollider,
  type RapierRigidBody,
} from "@react-three/rapier";
import * as THREE from "three";
import { useCharacterMovement, useCharacterAnimation } from "./hooks";

const MODEL_PATH = "/models/nong_dam_gom.glb";
const CAPSULE_HALF_HEIGHT = 0.4;
const CAPSULE_RADIUS = 0.4;
// GLB 실제 크기를 확인한 뒤 1.7m(170cm)에 맞게 조정
const MODEL_SCALE = 1;

interface CharacterProps {
  targetPositionRef?: React.RefObject<THREE.Vector3>;
  characterPositionRef?: React.RefObject<THREE.Vector3>;
  isPointerDownRef?: React.RefObject<boolean>;
}

export default function Character({
  targetPositionRef: externalTargetRef,
  characterPositionRef: externalCharacterRef,
  isPointerDownRef: externalPointerRef,
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

  const isMovingRef = useCharacterMovement(
    rigidBodyRef,
    targetPositionRef,
    isPointerDownRef,
    characterPositionRef,
  );

  useCharacterAnimation(actions, isMovingRef);

  return (
    <RigidBody
      ref={rigidBodyRef}
      type="kinematicPosition"
      colliders={false}
      position={[0, CAPSULE_HALF_HEIGHT + CAPSULE_RADIUS, 0]}
    >
      <CapsuleCollider args={[CAPSULE_HALF_HEIGHT, CAPSULE_RADIUS]} />
      <group ref={groupRef} scale={MODEL_SCALE}>
        <primitive object={scene} />
      </group>
    </RigidBody>
  );
}

useGLTF.preload(MODEL_PATH);
