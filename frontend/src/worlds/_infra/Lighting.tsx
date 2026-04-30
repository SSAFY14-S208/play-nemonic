export default function Lighting() {
  return (
    <>
      {/* 창문을 통해 들어오는 따뜻한 주광 */}
      <directionalLight
        color="#fff0d0"
        position={[8, 15, 3]}
        intensity={1.8}
        castShadow
        shadow-mapSize-width={2048}
        shadow-mapSize-height={2048}
        shadow-camera-near={0.1}
        shadow-camera-far={50}
        shadow-camera-left={-1.5}
        shadow-camera-right={1.5}
        shadow-camera-top={1.5}
        shadow-camera-bottom={-1.5}
        shadow-bias={-0.001}
      />
      {/* 전체 환경광 — 따뜻한 아이보리 */}
      <ambientLight color="#fff5e4" intensity={0.7} />
      {/* 하늘/바닥 반사 보조광 */}
      <hemisphereLight args={["#ffecd2", "#c8a87a", 0.3]} />
    </>
  );
}
