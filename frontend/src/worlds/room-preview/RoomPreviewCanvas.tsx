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
  ROOM_PREVIEW_RENDERING_PROFILES,
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
        set({ frameloop: "demand" });
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
  const renderingProfile = ROOM_PREVIEW_RENDERING_PROFILES[variant];
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
        dpr={renderingProfile.devicePixelRatio}
        frameloop="demand"
        gl={{
          alpha: false,
          antialias: renderingProfile.antialias,
          powerPreference: "high-performance",
        }}
        shadows={renderingProfile.shadows}
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
          gl.shadowMap.enabled = renderingProfile.shadows;
          gl.shadowMap.type = THREE.PCFSoftShadowMap;
          onCanvasReady?.();
        }}
      >
        <CanvasPauseControl />
        <RoomPreviewScene
          enablePostProcessing={renderingProfile.postProcessing}
          variant={variant}
        />
      </Canvas>
      <RoomPreviewLightDebugPanel />
    </>
  );
}
