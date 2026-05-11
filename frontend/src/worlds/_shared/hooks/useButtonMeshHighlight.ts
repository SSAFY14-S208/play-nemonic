import { useRef } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

const GLOW_COLOR = new THREE.Color(0x88ccff);
const IDLE_MIN_INTENSITY = 0.05;
const IDLE_MAX_INTENSITY = 0.2;
const HOVER_INTENSITY = 0.5;
const PULSE_SPEED = 2.5;

export function useButtonMeshHighlight(
  scene: THREE.Group,
  buttonNames: Set<string>,
) {
  const hoveredMeshRef = useRef<THREE.Mesh | null>(null);
  const materialsRef = useRef(
    new Map<THREE.Mesh, THREE.MeshStandardMaterial>(),
  );
  const activeSceneRef = useRef<THREE.Group | null>(null);

  useFrame(({ clock }) => {
    // Lazy init: clone button materials on first frame or when scene changes.
    // All initialization lives inside useFrame to avoid the React Compiler
    // immutability rule (useEffect ref assignment → useFrame mutation).
    if (activeSceneRef.current !== scene) {
      materialsRef.current.clear();

      scene.traverse((child) => {
        if (!(child instanceof THREE.Mesh) || !buttonNames.has(child.name)) {
          return;
        }
        // Clone the material so emissive changes don't leak to
        // other meshes that share the same GLB material instance.
        const current = child.material as THREE.MeshStandardMaterial;
        const cloned = current.clone();
        child.material = cloned;
        materialsRef.current.set(child, cloned);
      });

      activeSceneRef.current = scene;
    }

    const elapsed = clock.elapsedTime;
    const idlePulse =
      IDLE_MIN_INTENSITY +
      (IDLE_MAX_INTENSITY - IDLE_MIN_INTENSITY) *
        (0.5 + 0.5 * Math.sin(elapsed * PULSE_SPEED));

    for (const [mesh, material] of materialsRef.current) {
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
