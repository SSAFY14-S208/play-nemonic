import { useRef, useEffect } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

const GLOW_COLOR = new THREE.Color(0x88ccff);
const IDLE_MIN_INTENSITY = 0.05;
const IDLE_MAX_INTENSITY = 0.2;
const HOVER_INTENSITY = 0.5;
const PULSE_SPEED = 2.5;

interface OriginalMaterial {
  mesh: THREE.Mesh;
  sharedMaterial: THREE.Material;
}

export function useButtonMeshHighlight(
  scene: THREE.Group,
  buttonNames: Set<string>,
) {
  const buttonMeshesRef = useRef<THREE.Mesh[]>([]);
  const hoveredMeshRef = useRef<THREE.Mesh | null>(null);

  useEffect(() => {
    const meshes: THREE.Mesh[] = [];
    const originals: OriginalMaterial[] = [];

    scene.traverse((child) => {
      if (!(child instanceof THREE.Mesh) || !buttonNames.has(child.name)) {
        return;
      }

      meshes.push(child);

      // Clone the material so emissive changes don't leak to
      // other meshes that share the same GLB material instance.
      const sharedMaterial = child.material as THREE.MeshStandardMaterial;
      originals.push({ mesh: child, sharedMaterial });
      child.material = sharedMaterial.clone();
    });

    buttonMeshesRef.current = meshes;

    return () => {
      for (const { mesh, sharedMaterial } of originals) {
        mesh.material = sharedMaterial;
      }
    };
  }, [scene, buttonNames]);

  useFrame(({ clock }) => {
    const elapsed = clock.elapsedTime;
    const idlePulse =
      IDLE_MIN_INTENSITY +
      (IDLE_MAX_INTENSITY - IDLE_MIN_INTENSITY) *
        (0.5 + 0.5 * Math.sin(elapsed * PULSE_SPEED));

    for (const mesh of buttonMeshesRef.current) {
      const material = mesh.material as THREE.MeshStandardMaterial;

      if (mesh === hoveredMeshRef.current) {
        material.emissive.copy(GLOW_COLOR);
        material.emissiveIntensity = HOVER_INTENSITY;
      } else {
        material.emissive.copy(GLOW_COLOR);
        material.emissiveIntensity = idlePulse;
      }
    }
  });

  return { hoveredMeshRef };
}
