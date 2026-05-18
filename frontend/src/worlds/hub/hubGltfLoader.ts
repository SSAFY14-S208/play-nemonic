import { useGLTF } from '@react-three/drei'
import { MeshoptDecoder } from 'three/examples/jsm/libs/meshopt_decoder.module.js'

// drei가 내부적으로 사용하는 GLTFLoader 타입(three-stdlib 확장판)을 직접 import
// 하려면 transitive dep에 의존해야 하므로, useGLTF 시그니처에서 ExtendLoader 콜백
// 타입을 추출해 사용한다.
type ExtendLoader = NonNullable<Parameters<typeof useGLTF>[3]>
type DreiGltfLoader = Parameters<ExtendLoader>[0]

// 룸 GLB(isometric-girl-room.glb)는 EXT_meshopt_compression 을 required 확장으로 쓴다.
// drei v10 의 useGLTF 가 기본값으로 MeshoptDecoder 를 자동 등록하지만,
// drei 메이저 업그레이드 시 기본값이 바뀔 가능성을 대비해 명시적으로 등록한다.
export const extendRoomGltfLoader: ExtendLoader = (loader: DreiGltfLoader) => {
  loader.setMeshoptDecoder(MeshoptDecoder)
}
