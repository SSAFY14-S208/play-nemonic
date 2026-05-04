import { useEffect, useRef } from "react";
import { useFrame, useThree } from "@react-three/fiber";
import { PerspectiveCamera } from "@react-three/drei";
import * as THREE from "three";
import { DESK_SURFACE_Y } from "./constants";

// 캐릭터 기준 카메라 오프셋 — 27cm 표시 캐릭터 + 0.767m 책상 기준
const BASE_OFFSET = new THREE.Vector3(0, 0.5, 0.6);
const MIN_ZOOM = 0.5;
const MAX_ZOOM = 2.0;
const CAMERA_LERP_SPEED = 0.5;
const LOOK_AT_LERP_SPEED = 0.55;
const ZOOM_WHEEL_SPEED = 0.001;

interface LandingCameraProps {
  characterPositionRef: React.RefObject<THREE.Vector3>;
}

export default function LandingCamera({
  characterPositionRef,
}: LandingCameraProps) {
  const { gl } = useThree();
  const zoomRef = useRef(1.0);
  const targetCameraPositionRef = useRef(
    new THREE.Vector3(0, DESK_SURFACE_Y + 0.4, 0.5),
  );
  const lookAtTargetRef = useRef(new THREE.Vector3());
  const isFirstFrameRef = useRef(true);

  // 줌 이벤트 (마우스 휠 + 핀치)
  useEffect(() => {
    const canvas = gl.domElement;

    const handleWheel = (event: WheelEvent) => {
      event.preventDefault();
      zoomRef.current = Math.max(
        MIN_ZOOM,
        Math.min(MAX_ZOOM, zoomRef.current + event.deltaY * ZOOM_WHEEL_SPEED),
      );
    };

    let prevPinchDistance = 0;

    const handleTouchMove = (event: TouchEvent) => {
      if (event.touches.length !== 2) return;
      const deltaX = event.touches[0].clientX - event.touches[1].clientX;
      const deltaY = event.touches[0].clientY - event.touches[1].clientY;
      const currentDistance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
      if (prevPinchDistance > 0) {
        const scaleRatio = prevPinchDistance / currentDistance;
        zoomRef.current = Math.max(
          MIN_ZOOM,
          Math.min(MAX_ZOOM, zoomRef.current * scaleRatio),
        );
      }
      prevPinchDistance = currentDistance;
    };

    const handleTouchEnd = () => {
      prevPinchDistance = 0;
    };

    canvas.addEventListener("wheel", handleWheel, { passive: false });
    canvas.addEventListener("touchmove", handleTouchMove);
    canvas.addEventListener("touchend", handleTouchEnd);

    return () => {
      canvas.removeEventListener("wheel", handleWheel);
      canvas.removeEventListener("touchmove", handleTouchMove);
      canvas.removeEventListener("touchend", handleTouchEnd);
    };
  }, [gl]);

  // 매 프레임 캐릭터 위치 추적
  useFrame((state) => {
    const characterPosition = characterPositionRef.current;
    const zoom = zoomRef.current;

    targetCameraPositionRef.current.set(
      characterPosition.x + BASE_OFFSET.x * zoom,
      characterPosition.y + BASE_OFFSET.y * zoom,
      characterPosition.z + BASE_OFFSET.z * zoom,
    );
    // 첫 프레임은 즉시 스냅, 이후 부드러운 추적
    if (isFirstFrameRef.current) {
      state.camera.position.copy(targetCameraPositionRef.current);
      lookAtTargetRef.current.copy(characterPosition);
      isFirstFrameRef.current = false;
    } else {
      state.camera.position.lerp(
        targetCameraPositionRef.current,
        CAMERA_LERP_SPEED,
      );
      // lookAt도 lerp — Rapier 위치 미세 노이즈가 카메라 회전 떨림으로 증폭되는 것을 완충
      lookAtTargetRef.current.lerp(characterPosition, LOOK_AT_LERP_SPEED);
    }

    state.camera.lookAt(lookAtTargetRef.current);
  });

  return <PerspectiveCamera makeDefault fov={30} near={0.1} far={500} />;
}
