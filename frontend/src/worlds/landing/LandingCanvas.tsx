"use client";
import { Canvas } from "@react-three/fiber";
import { Physics } from "@react-three/rapier";
import LandingScene from "./LandingScene";

export default function LandingCanvas() {
  return (
    <Canvas shadows style={{ width: "100%", height: "100vh", touchAction: "none" }}>
      <Physics gravity={[0, -9.81, 0]}>
        <LandingScene />
      </Physics>
    </Canvas>
  );
}
