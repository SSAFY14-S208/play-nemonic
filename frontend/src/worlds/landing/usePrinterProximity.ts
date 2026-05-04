import { useState, useRef } from "react";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { NEMONIC_PRINTER_POSITION, PRINTER_PROXIMITY_RADIUS } from "./constants";

/**
 * 캐릭터와 프린터 간 수평 거리를 매 프레임 체크하여 근접 여부를 반환한다.
 * Y축을 무시하므로 센서 collider의 높이 오프셋 문제가 발생하지 않는다.
 * setState는 상태가 실제로 변경될 때(진입/이탈)만 호출된다.
 */
export function usePrinterProximity(
  characterPositionRef: React.RefObject<THREE.Vector3>,
) {
  const [isNearPrinter, setIsNearPrinter] = useState(false);
  const wasNearRef = useRef(false);

  const thresholdSq = PRINTER_PROXIMITY_RADIUS * PRINTER_PROXIMITY_RADIUS;

  useFrame(() => {
    const position = characterPositionRef.current;
    const deltaX = position.x - NEMONIC_PRINTER_POSITION[0];
    const deltaZ = position.z - NEMONIC_PRINTER_POSITION[2];
    const horizontalDistanceSq = deltaX * deltaX + deltaZ * deltaZ;
    const isNear = horizontalDistanceSq < thresholdSq;

    if (isNear !== wasNearRef.current) {
      wasNearRef.current = isNear;
      setIsNearPrinter(isNear);
    }
  });

  return { isNearPrinter };
}
