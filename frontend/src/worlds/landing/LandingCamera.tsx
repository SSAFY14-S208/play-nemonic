import { OrbitControls, PerspectiveCamera } from "@react-three/drei";
import {
  NEMONIC_PRINTER_POSITION,
  ORBIT_TARGET_Y,
  ORBIT_DISTANCE,
  ORBIT_POLAR_ANGLE,
  ORBIT_MIN_POLAR,
  ORBIT_MAX_POLAR,
  ORBIT_MIN_DISTANCE,
  ORBIT_MAX_DISTANCE,
  CAMERA_FOV,
} from "./constants";

export default function LandingCamera() {
  const initialCameraY =
    ORBIT_TARGET_Y + ORBIT_DISTANCE * Math.cos(ORBIT_POLAR_ANGLE);
  const initialCameraZ = ORBIT_DISTANCE * Math.sin(ORBIT_POLAR_ANGLE);

  return (
    <>
      <PerspectiveCamera
        makeDefault
        fov={CAMERA_FOV}
        near={0.1}
        far={500}
        position={[0, initialCameraY, initialCameraZ]}
      />
      <OrbitControls
        target={[
          NEMONIC_PRINTER_POSITION[0],
          ORBIT_TARGET_Y,
          NEMONIC_PRINTER_POSITION[2],
        ]}
        enablePan={false}
        minPolarAngle={ORBIT_MIN_POLAR}
        maxPolarAngle={ORBIT_MAX_POLAR}
        minDistance={ORBIT_MIN_DISTANCE}
        maxDistance={ORBIT_MAX_DISTANCE}
        enableDamping
        dampingFactor={0.05}
      />
    </>
  );
}
