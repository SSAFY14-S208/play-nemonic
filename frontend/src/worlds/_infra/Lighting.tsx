export default function Lighting() {
  return (
    <>
      {/* 창문을 통해 들어오는 따뜻한 주광 */}
      {/* 광원을 거의 머리 위로 올려 그림자가 발 아래로 정확히 떨어지도록 */}
      <directionalLight
        color="#fff0d0"
        position={[1.5, 6, 1]}
        intensity={1.8}
        castShadow
        shadow-mapSize-width={2048}
        shadow-mapSize-height={2048}
        shadow-camera-near={0.1}
        shadow-camera-far={20}
        shadow-camera-left={-1}
        shadow-camera-right={1}
        shadow-camera-top={1}
        shadow-camera-bottom={-1}
        shadow-bias={-0.00005}
        shadow-normalBias={0.005}
      />
      {/* 전체 환경광 — 따뜻한 아이보리 */}
      <ambientLight color="#fff5e4" intensity={0.7} />
      {/* 하늘/바닥 반사 보조광 */}
      <hemisphereLight args={["#ffecd2", "#c8a87a", 0.3]} />
    </>
  );
}
