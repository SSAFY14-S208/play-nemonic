import { useMemo, useRef } from "react";
import { useFrame, type ThreeEvent } from "@react-three/fiber";
import * as THREE from "three";

const BLACK_GUIDE_COLOR = "#151515";
const RING_SEGMENTS = 96;
const ORBIT_ARROW_RADIUS = 0.128;
const ORBIT_ARROW_ARC_RADIANS = Math.PI * 0.7;
const ORBIT_ARROW_SPEED = 0.45;
const ORBIT_ARROW_START_ANGLE = 0.1;
const DISABLED_RAYCAST: THREE.Mesh["raycast"] = () => undefined;

type Direction = "down" | "right";

function ThinArrow({
  direction,
  phase,
  position,
}: {
  direction: Direction;
  phase: number;
  position: [number, number, number];
}) {
  const groupRef = useRef<THREE.Group>(null);
  const shaftMaterialRef = useRef<THREE.MeshBasicMaterial>(null);
  const headMaterialRef = useRef<THREE.MeshBasicMaterial>(null);
  const shaftRotation: [number, number, number] =
    direction === "down" ? [0, 0, 0] : [0, 0, Math.PI / 2];
  const headRotation: [number, number, number] =
    direction === "down" ? [0, 0, Math.PI] : [0, 0, -Math.PI / 2];
  const headPosition: [number, number, number] =
    direction === "down" ? [0, -0.024, 0] : [0.024, 0, 0];

  useFrame(({ clock }) => {
    const group = groupRef.current;
    const shaftMaterial = shaftMaterialRef.current;
    const headMaterial = headMaterialRef.current;

    if (!group || !shaftMaterial || !headMaterial) return;

    const wave = Math.sin(clock.elapsedTime * 2.4 + phase);
    const bobDistance = 0.0035 * wave;
    const opacity = 0.44 + ((wave + 1) / 2) * 0.18;

    group.position.set(...position);

    if (direction === "right") {
      group.position.x -= bobDistance;
    } else {
      group.position.y += bobDistance;
    }

    shaftMaterial.opacity = opacity;
    headMaterial.opacity = opacity;
  });

  return (
    <group ref={groupRef} position={position}>
      <mesh raycast={DISABLED_RAYCAST} renderOrder={20} rotation={shaftRotation}>
        <cylinderGeometry args={[0.00075, 0.00075, 0.034, 12]} />
        <meshBasicMaterial
          ref={shaftMaterialRef}
          color={BLACK_GUIDE_COLOR}
          depthTest={false}
          depthWrite={false}
          toneMapped={false}
          transparent
        />
      </mesh>
      <mesh
        raycast={DISABLED_RAYCAST}
        renderOrder={20}
        position={headPosition}
        rotation={headRotation}
      >
        <coneGeometry args={[0.0042, 0.012, 18]} />
        <meshBasicMaterial
          ref={headMaterialRef}
          color={BLACK_GUIDE_COLOR}
          depthTest={false}
          depthWrite={false}
          toneMapped={false}
          transparent
        />
      </mesh>
    </group>
  );
}

function TargetRing({
  hitRadius = 0.033,
  onClick,
  phase,
  position,
  radius = 0.017,
  rotation,
}: {
  hitRadius?: number;
  onClick: () => void;
  phase: number;
  position: [number, number, number];
  radius?: number;
  rotation?: [number, number, number];
}) {
  const groupRef = useRef<THREE.Group>(null);
  const ringMaterialRef = useRef<THREE.MeshBasicMaterial>(null);

  useFrame(({ clock }) => {
    const group = groupRef.current;
    const ringMaterial = ringMaterialRef.current;

    if (!group || !ringMaterial) return;

    const wave = (Math.sin(clock.elapsedTime * 2.2 + phase) + 1) / 2;

    group.scale.setScalar(0.96 + wave * 0.1);
    ringMaterial.opacity = 0.34 + wave * 0.2;
  });

  const setCursor = (cursor: string) => {
    if (typeof document === "undefined") return;

    document.body.style.cursor = cursor;
  };

  const handleClick = (event: ThreeEvent<MouseEvent>) => {
    event.stopPropagation();
    onClick();
  };

  const handlePointerOver = (event: ThreeEvent<PointerEvent>) => {
    event.stopPropagation();
    setCursor("pointer");
  };

  const handlePointerOut = (event: ThreeEvent<PointerEvent>) => {
    event.stopPropagation();
    setCursor("");
  };

  return (
    <group ref={groupRef} position={position} rotation={rotation}>
      <mesh raycast={DISABLED_RAYCAST} renderOrder={19}>
        <ringGeometry args={[radius, radius + 0.0017, RING_SEGMENTS]} />
        <meshBasicMaterial
          ref={ringMaterialRef}
          color={BLACK_GUIDE_COLOR}
          depthTest={false}
          depthWrite={false}
          side={THREE.DoubleSide}
          toneMapped={false}
          transparent
        />
      </mesh>
      <mesh
        renderOrder={21}
        onClick={handleClick}
        onPointerOver={handlePointerOver}
        onPointerOut={handlePointerOut}
      >
        <circleGeometry args={[hitRadius, RING_SEGMENTS]} />
        <meshBasicMaterial
          color="#ffffff"
          colorWrite={false}
          depthTest={false}
          depthWrite={false}
          opacity={0}
          transparent
        />
      </mesh>
    </group>
  );
}

function OrbitArrowSegment({ angleOffset }: { angleOffset: number }) {
  const arrowHeadAngle = ORBIT_ARROW_ARC_RADIANS;
  const arrowHeadPosition = useMemo<[number, number, number]>(
    () => [
      ORBIT_ARROW_RADIUS * Math.cos(arrowHeadAngle),
      0,
      ORBIT_ARROW_RADIUS * Math.sin(arrowHeadAngle),
    ],
    [arrowHeadAngle],
  );
  const arrowHeadQuaternion = useMemo(() => {
    const arrowDirection = new THREE.Vector3(
      -Math.sin(arrowHeadAngle),
      0,
      Math.cos(arrowHeadAngle),
    ).normalize();

    return new THREE.Quaternion().setFromUnitVectors(
      new THREE.Vector3(0, 1, 0),
      arrowDirection,
    );
  }, [arrowHeadAngle]);

  return (
    <group rotation={[0, angleOffset + ORBIT_ARROW_START_ANGLE, 0]}>
      <mesh
        raycast={DISABLED_RAYCAST}
        renderOrder={14}
        rotation={[Math.PI / 2, 0, 0]}
      >
        <torusGeometry
          args={[
            ORBIT_ARROW_RADIUS,
            0.00072,
            8,
            48,
            ORBIT_ARROW_ARC_RADIANS,
          ]}
        />
        <meshBasicMaterial
          color={BLACK_GUIDE_COLOR}
          depthWrite={false}
          opacity={0.24}
          toneMapped={false}
          transparent
        />
      </mesh>
      <mesh
        quaternion={arrowHeadQuaternion}
        raycast={DISABLED_RAYCAST}
        renderOrder={15}
        position={arrowHeadPosition}
      >
        <coneGeometry args={[0.0042, 0.012, 18]} />
        <meshBasicMaterial
          color={BLACK_GUIDE_COLOR}
          depthWrite={false}
          opacity={0.42}
          toneMapped={false}
          transparent
        />
      </mesh>
    </group>
  );
}

function OrbitRotationHint() {
  const orbitGroupRef = useRef<THREE.Group>(null);

  useFrame(({ clock }) => {
    const orbitGroup = orbitGroupRef.current;

    if (!orbitGroup) return;

    orbitGroup.rotation.y = -clock.elapsedTime * ORBIT_ARROW_SPEED;
  });

  return (
    <group ref={orbitGroupRef} position={[0, 0.032, 0]}>
      <OrbitArrowSegment angleOffset={0} />
      <OrbitArrowSegment angleOffset={Math.PI} />
    </group>
  );
}

export default function LandingInteractionHints({
  onOpenHintClick,
  onPrintHintClick,
  visible,
}: {
  onOpenHintClick: () => void;
  onPrintHintClick: () => void;
  visible: boolean;
}) {
  if (!visible) return null;

  return (
    <group>
      <OrbitRotationHint />
      <ThinArrow direction="down" phase={0} position={[0.046, 0.132, 0.048]} />
      <TargetRing
        onClick={onPrintHintClick}
        phase={0.4}
        position={[0.046, 0.088, 0.048]}
        rotation={[Math.PI / 2, 0, 0]}
      />
      <ThinArrow
        direction="right"
        phase={1.1}
        position={[-0.087, 0.055, 0.003]}
      />
      <TargetRing
        hitRadius={0.043}
        onClick={onOpenHintClick}
        phase={1.5}
        position={[-0.051, 0.055, 0.003]}
        radius={0.029}
        rotation={[0, Math.PI / 2, 0]}
      />
    </group>
  );
}
