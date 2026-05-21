import { Bloom, EffectComposer } from '@react-three/postprocessing'
import { ROOM_PREVIEW_RENDERING } from './constants'

export default function RoomPreviewPostProcessing() {
  const bloom = ROOM_PREVIEW_RENDERING.postProcessing

  return (
    <EffectComposer
      multisampling={0}
      renderPriority={0}
      resolutionScale={bloom.resolutionScale}
    >
      <Bloom
        intensity={bloom.bloomIntensity}
        luminanceSmoothing={bloom.luminanceSmoothing}
        luminanceThreshold={bloom.luminanceThreshold}
        mipmapBlur
        radius={bloom.bloomRadius}
      />
    </EffectComposer>
  )
}
