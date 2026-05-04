import { useEffect } from "react";
import { useGLTF } from "@react-three/drei";
import { useThree } from "@react-three/fiber";
import * as THREE from "three";

const MODEL_PATH = "/models/modern_desk.glb";

export default function DeskMesh() {
  const { scene } = useGLTF(MODEL_PATH);
  const { gl } = useThree();

  useEffect(() => {
    const maxAnisotropy = gl.capabilities.getMaxAnisotropy();
    scene.traverse((child) => {
      if (child instanceof THREE.Mesh) {
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
            if (texture) {
              texture.anisotropy = maxAnisotropy;
              texture.needsUpdate = true; // GPU에 변경사항 재업로드 트리거
            }
          });
        });
      }
    });
  }, [scene, gl]);

  // GLB 원본 스케일이 이미 실물 크기 (표면 Y ≈ 0.81m) — scale 보정 불필요
  return <primitive object={scene} />;
}

useGLTF.preload(MODEL_PATH);
