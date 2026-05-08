export default function HubLighting() {
  return (
    <>
      {/* Soft ambient sky/ground fill keeps pastel assets readable. */}
      <hemisphereLight args={['#fffef8', '#d7d3cd', 1.7]} />
      {/* Cross lights preserve the closed mock's bright clay highlights. */}
      <directionalLight color="#ffffff" intensity={2.2} position={[6, 8, 8]} />
      <directionalLight color="#f2f7ff" intensity={1.2} position={[-8, 3, -4]} />
    </>
  )
}
