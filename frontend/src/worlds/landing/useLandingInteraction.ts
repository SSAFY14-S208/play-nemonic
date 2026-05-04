import { useEffect, useRef } from "react";
import { useThree } from "@react-three/fiber";
import * as THREE from "three";
import { DESK_SURFACE_Y } from "./constants";

// THREE.Plane(normal, constant): dot(normal, point) + constant = 0
// Y=DESK_SURFACE_Y 수평면 → constant = -DESK_SURFACE_Y
const DESK_PLANE = new THREE.Plane(new THREE.Vector3(0, 1, 0), -DESK_SURFACE_Y);

export function useLandingInteraction(
  targetPositionRef: React.RefObject<THREE.Vector3>,
  isPointerDownRef: React.RefObject<boolean>,
) {
  const { camera, gl } = useThree();
  const activePointerCountRef = useRef(0);

  useEffect(() => {
    const canvas = gl.domElement;
    const raycaster = new THREE.Raycaster();
    const intersection = new THREE.Vector3();

    // 레이캐스트 후 intersection 벡터를 갱신하고 hit 여부 반환
    const computeWorldPosition = (
      clientX: number,
      clientY: number,
    ): boolean => {
      const rect = canvas.getBoundingClientRect();
      const ndcX = ((clientX - rect.left) / rect.width) * 2 - 1;
      const ndcY = -((clientY - rect.top) / rect.height) * 2 + 1;
      raycaster.setFromCamera(new THREE.Vector2(ndcX, ndcY), camera);
      const hit = raycaster.ray.intersectPlane(DESK_PLANE, intersection);
      if (!hit) return false;
      // 책상 경계 밖 클릭도 좌표 그대로 전달 — 캐릭터는 책상 가장자리의
      // 보이지 않는 경계벽(DeskBoundsMesh)에 KCC가 자동으로 막아준다.
      return true;
    };

    const handlePointerDown = (event: PointerEvent) => {
      activePointerCountRef.current += 1;
      // 멀티터치(핀치)일 때는 이동 비활성화
      if (activePointerCountRef.current > 1) {
        isPointerDownRef.current = false;
        return;
      }
      isPointerDownRef.current = true;
      // 클릭 시엔 거리 무관하게 즉시 목표 갱신
      if (computeWorldPosition(event.clientX, event.clientY)) {
        targetPositionRef.current.copy(intersection);
      }
    };

    const handlePointerMove = (event: PointerEvent) => {
      if (!isPointerDownRef.current) return;
      if (!computeWorldPosition(event.clientX, event.clientY)) return;
      targetPositionRef.current.copy(intersection);
    };

    const handlePointerUp = () => {
      activePointerCountRef.current = Math.max(
        0,
        activePointerCountRef.current - 1,
      );
      if (activePointerCountRef.current === 0) {
        isPointerDownRef.current = false;
      }
    };

    canvas.addEventListener("pointerdown", handlePointerDown);
    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);
    window.addEventListener("pointercancel", handlePointerUp);

    return () => {
      canvas.removeEventListener("pointerdown", handlePointerDown);
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
      window.removeEventListener("pointercancel", handlePointerUp);
    };
  }, [camera, gl, isPointerDownRef, targetPositionRef]);
}
