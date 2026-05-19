import { useRef } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

const GLOW_COLOR = new THREE.Color(0x88ccff);
const IDLE_MIN_INTENSITY = 0.05;
const IDLE_MAX_INTENSITY = 0.2;
const HOVER_INTENSITY = 0.5;
const PULSE_SPEED = 2.5;

type ButtonMeshHighlightOptions = {
  glowColor?: THREE.Color;
  hoverIntensity?: number;
  idleMaxIntensity?: number;
  idleMinIntensity?: number;
  pulseSpeed?: number;
  tintMaxStrength?: number;
  tintMinStrength?: number;
};

export function useButtonMeshHighlight(
  scene: THREE.Group,
  buttonNames: Set<string>,
  options: ButtonMeshHighlightOptions = {},
) {
  const glowColor = options.glowColor ?? GLOW_COLOR;
  const hoverIntensity = options.hoverIntensity ?? HOVER_INTENSITY;
  const idleMaxIntensity = options.idleMaxIntensity ?? IDLE_MAX_INTENSITY;
  const idleMinIntensity = options.idleMinIntensity ?? IDLE_MIN_INTENSITY;
  const pulseSpeed = options.pulseSpeed ?? PULSE_SPEED;
  const tintMaxStrength = options.tintMaxStrength ?? 0;
  const tintMinStrength = options.tintMinStrength ?? 0;
  const hoveredMeshRef = useRef<THREE.Mesh | null>(null);
  const materialsRef = useRef(
    new Map<
      THREE.Mesh,
      {
        idleColor: THREE.Color;
        material: THREE.MeshStandardMaterial;
      }
    >(),
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
        if (tintMaxStrength > 0) {
          cloned.toneMapped = false;
          cloned.needsUpdate = true;
        }
        child.material = cloned;
        materialsRef.current.set(child, {
          idleColor: cloned.color.clone(),
          material: cloned,
        });
      });

      activeSceneRef.current = scene;
    }

    const elapsed = clock.elapsedTime;
    const pulseRatio = 0.5 + 0.5 * Math.sin(elapsed * pulseSpeed);
    const idlePulse =
      idleMinIntensity + (idleMaxIntensity - idleMinIntensity) * pulseRatio;

    for (const [mesh, { idleColor, material }] of materialsRef.current) {
      const isHovered = mesh === hoveredMeshRef.current;
      const activeTintStrength = isHovered
        ? Math.min(tintMaxStrength + 0.24, 1)
        : tintMinStrength +
          (tintMaxStrength - tintMinStrength) * pulseRatio;

      material.color.copy(idleColor).lerp(glowColor, activeTintStrength);

      if (mesh === hoveredMeshRef.current) {
        material.emissive.copy(glowColor);
        material.emissiveIntensity = hoverIntensity;
      } else {
        material.emissive.copy(glowColor);
        material.emissiveIntensity = idlePulse;
      }
    }
  });

  return { hoveredMeshRef };
}
