"use client";

import { Canvas, useThree } from "@react-three/fiber";
import { useEffect } from "react";
import * as THREE from "three";
import { cn } from "@/shared/libs";
import { useCanvasPauseStore } from "@/shared/stores";
import {
  ROOM_PREVIEW_CAMERA,
  ROOM_PREVIEW_HUB_CAMERA_PRESETS,
  ROOM_PREVIEW_RENDERING,
  type RoomPreviewVariant,
} from "./constants";
import { RoomPreviewLightDebugPanel } from "./light-debug";
import RoomPreviewScene from "./RoomPreviewScene";

function CanvasPauseControl() {
  const isPaused = useCanvasPauseStore((state) => state.isPaused);
  const set = useThree((state) => state.set);
  const invalidate = useThree((state) => state.invalidate);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      await Promise.resolve();
      if (cancelled) return;

      if (isPaused) {
        set({ frameloop: "never" });
      } else {
        set({ frameloop: "always" });
        invalidate();
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [isPaused, set, invalidate]);

  return null;
}

export default function RoomPreviewCanvas({
  className,
  onCanvasReady,
  variant = "preview",
}: {
  className?: string;
  onCanvasReady?: () => void;
  variant?: RoomPreviewVariant;
}) {
  const initialCamera =
    variant === "hub"
      ? ROOM_PREVIEW_HUB_CAMERA_PRESETS.overview
      : ROOM_PREVIEW_CAMERA;

  return (
    <>
      <Canvas
        className={cn("absolute inset-0 h-full w-full", className)}
        camera={{
          fov: ROOM_PREVIEW_CAMERA.fov,
          near: ROOM_PREVIEW_CAMERA.near,
          far: ROOM_PREVIEW_CAMERA.far,
          position: initialCamera.position,
        }}
        dpr={ROOM_PREVIEW_RENDERING.devicePixelRatio}
        gl={{
          alpha: false,
          antialias: true,
          powerPreference: "high-performance",
        }}
        shadows
        onCreated={({
          camera,
          gl,
        }: {
          camera: THREE.Camera;
          gl: THREE.WebGLRenderer;
        }) => {
          camera.lookAt(...initialCamera.target);
          gl.outputColorSpace = THREE.SRGBColorSpace;
          gl.toneMapping = THREE.AgXToneMapping;
          gl.toneMappingExposure = ROOM_PREVIEW_RENDERING.toneMappingExposure;
          gl.shadowMap.enabled = true;
          gl.shadowMap.type = THREE.PCFSoftShadowMap;
          onCanvasReady?.();
        }}
      >
        <CanvasPauseControl />
        <RoomPreviewScene variant={variant} />
      </Canvas>
      <RoomPreviewLightDebugPanel />
    </>
  );
}
