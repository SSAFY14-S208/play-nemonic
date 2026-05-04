import { useRef, useEffect } from "react";
import { useGLTF, useAnimations } from "@react-three/drei";
import { useThree } from "@react-three/fiber";
import type { ThreeEvent } from "@react-three/fiber";
import * as THREE from "three";
import type { AnimationAction } from "three";
import { RigidBody } from "@react-three/rapier";

const MODEL_PATH = "/models/nemonic-printer.glb";
const BUTTON_MESH_NAMES = new Set([
  "NEMONIC_PRINT_BUTTON",
  "NEMONIC_OPEN_BUTTON",
]);

interface NemonicPrinterMeshProps {
  position?: [number, number, number];
  actionsRef?: React.MutableRefObject<Record<string, AnimationAction | null>>;
  onPrintButtonClick?: () => void;
  onOpenButtonClick?: () => void;
}

export default function NemonicPrinterMesh({
  position,
  actionsRef,
  onPrintButtonClick,
  onOpenButtonClick,
}: NemonicPrinterMeshProps) {
  const groupRef = useRef<THREE.Group>(null);
  const { gl } = useThree();
  const { scene, animations } = useGLTF(MODEL_PATH);
  const { actions } = useAnimations(animations, groupRef);

  // 그림자 + 텍스처 anisotropy 적용 (DeskMesh 동일 패턴)
  useEffect(() => {
    const maxAnisotropy = gl.capabilities.getMaxAnisotropy();
    scene.traverse((child) => {
      if (!(child instanceof THREE.Mesh)) return;
      child.castShadow = true;
      child.receiveShadow = true;
      const materials = Array.isArray(child.material)
        ? child.material
        : [child.material];
      materials.forEach((material) => {
        if (!(material instanceof THREE.MeshStandardMaterial)) return;
        [
          material.map,
          material.normalMap,
          material.roughnessMap,
          material.metalnessMap,
        ].forEach((texture) => {
          if (!texture) return;
          texture.anisotropy = maxAnisotropy;
          texture.needsUpdate = true;
        });
      });
    });
  }, [scene, gl]);

  // 부모 훅에 actions 노출 (애니메이션 제어용)
  useEffect(() => {
    if (actionsRef) actionsRef.current = actions;
  }, [actions, actionsRef]);

  const handleClick = (event: ThreeEvent<MouseEvent>) => {
    event.stopPropagation();
    if (event.object.name === "NEMONIC_PRINT_BUTTON") onPrintButtonClick?.();
    else if (event.object.name === "NEMONIC_OPEN_BUTTON") onOpenButtonClick?.();
  };

  const handlePointerOver = (event: ThreeEvent<PointerEvent>) => {
    if (BUTTON_MESH_NAMES.has(event.object.name)) {
      document.body.style.cursor = "pointer";
    }
  };

  const handlePointerOut = () => {
    document.body.style.cursor = "auto";
  };

  return (
    <RigidBody type="fixed" colliders="hull">
      <group
        ref={groupRef}
        position={position}
        onClick={handleClick}
        onPointerOver={handlePointerOver}
        onPointerOut={handlePointerOut}
      >
        <primitive object={scene} />
      </group>
    </RigidBody>
  );
}

useGLTF.preload(MODEL_PATH);
