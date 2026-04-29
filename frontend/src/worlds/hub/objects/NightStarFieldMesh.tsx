import { useNightStarField } from './hooks'

export default function NightStarFieldMesh() {
  const stars = useNightStarField()

  return <points geometry={stars.geometry} material={stars.material} renderOrder={-10} />
}
