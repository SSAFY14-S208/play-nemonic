"use client";
import { Canvas } from "@react-three/fiber";
import LandingScene from "./LandingScene";

export default function LandingCanvas() {
  return (
    <Canvas
      gl={{ alpha: false }}
      shadows
      style={{ width: "100%", height: "100vh", touchAction: "none", background: "#ffffff" }}
    >
      <LandingScene />
    </Canvas>
  );
}
