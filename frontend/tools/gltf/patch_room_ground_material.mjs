import fs from 'node:fs'

const GLB_HEADER_BYTES = 12
const GLB_CHUNK_HEADER_BYTES = 8
const GLB_MAGIC = 0x46546c67
const GLB_VERSION = 2
const JSON_CHUNK_TYPE = 0x4e4f534a
const BIN_CHUNK_TYPE = 0x004e4942
const TARGET_MATERIAL_NAME = 'Mud and grass'
const FLAT_GROUND_COLOR = [0.015, 0.012, 0.014, 1]

function align4(byteLength) {
  return (byteLength + 3) & ~3
}

function readGlb(glbPath) {
  const sourceBuffer = fs.readFileSync(glbPath)

  if (
    sourceBuffer.readUInt32LE(0) !== GLB_MAGIC ||
    sourceBuffer.readUInt32LE(4) !== GLB_VERSION
  ) {
    throw new Error(`${glbPath} is not a glTF 2.0 GLB file`)
  }

  let offset = GLB_HEADER_BYTES
  let json = null
  let binaryChunk = null

  while (offset < sourceBuffer.length) {
    const chunkLength = sourceBuffer.readUInt32LE(offset)
    const chunkType = sourceBuffer.readUInt32LE(offset + 4)
    const chunkStart = offset + GLB_CHUNK_HEADER_BYTES
    const chunkEnd = chunkStart + chunkLength

    if (chunkType === JSON_CHUNK_TYPE) {
      json = JSON.parse(sourceBuffer.toString('utf8', chunkStart, chunkEnd))
    }

    if (chunkType === BIN_CHUNK_TYPE) {
      binaryChunk = sourceBuffer.subarray(chunkStart, chunkEnd)
    }

    offset = chunkEnd
  }

  if (!json || !binaryChunk) {
    throw new Error(`${glbPath} is missing a JSON or BIN chunk`)
  }

  return { binaryChunk, json }
}

function writeGlb(glbPath, json, binaryChunk) {
  const jsonBytes = Buffer.from(JSON.stringify(json))
  const jsonPaddedLength = align4(jsonBytes.length)
  const binaryPaddedLength = align4(binaryChunk.length)
  const totalLength =
    GLB_HEADER_BYTES +
    GLB_CHUNK_HEADER_BYTES +
    jsonPaddedLength +
    GLB_CHUNK_HEADER_BYTES +
    binaryPaddedLength
  const outputBuffer = Buffer.alloc(totalLength)
  let offset = 0

  outputBuffer.writeUInt32LE(GLB_MAGIC, offset)
  outputBuffer.writeUInt32LE(GLB_VERSION, offset + 4)
  outputBuffer.writeUInt32LE(totalLength, offset + 8)
  offset += GLB_HEADER_BYTES

  outputBuffer.writeUInt32LE(jsonPaddedLength, offset)
  outputBuffer.writeUInt32LE(JSON_CHUNK_TYPE, offset + 4)
  offset += GLB_CHUNK_HEADER_BYTES
  jsonBytes.copy(outputBuffer, offset)
  outputBuffer.fill(0x20, offset + jsonBytes.length, offset + jsonPaddedLength)
  offset += jsonPaddedLength

  outputBuffer.writeUInt32LE(binaryPaddedLength, offset)
  outputBuffer.writeUInt32LE(BIN_CHUNK_TYPE, offset + 4)
  offset += GLB_CHUNK_HEADER_BYTES
  binaryChunk.copy(outputBuffer, offset)

  fs.writeFileSync(glbPath, outputBuffer)
}

function getTextureSource(texture) {
  return texture?.source ?? texture?.extensions?.EXT_texture_webp?.source
}

function setTextureSource(texture, imageIndex) {
  if (texture.extensions?.EXT_texture_webp) {
    texture.extensions.EXT_texture_webp.source = imageIndex
    return
  }

  texture.source = imageIndex
}

function getMaterialTextureImageRows(json, material) {
  const textureRows = []
  const pbr = material.pbrMetallicRoughness ?? {}

  ;[
    ['baseColorTexture', pbr.baseColorTexture?.index],
    ['metallicRoughnessTexture', pbr.metallicRoughnessTexture?.index],
    ['normalTexture', material.normalTexture?.index],
  ].forEach(([slot, textureIndex]) => {
    if (!Number.isInteger(textureIndex)) return

    const texture = json.textures[textureIndex]
    const imageIndex = getTextureSource(texture)
    const image = json.images[imageIndex]
    const bufferView = json.bufferViews[image.bufferView]

    textureRows.push({
      imageBytes: bufferView.byteLength,
      imageIndex,
      imageName: image.name,
      slot,
      textureIndex,
    })
  })

  return textureRows
}

function updateTextureInfoIndex(textureInfo, textureIndexMap) {
  if (!textureInfo || !Number.isInteger(textureInfo.index)) return

  textureInfo.index = textureIndexMap.get(textureInfo.index)
}

function updateMaterialTextureIndices(json, textureIndexMap) {
  json.materials.forEach((material) => {
    const pbr = material.pbrMetallicRoughness ?? {}

    updateTextureInfoIndex(pbr.baseColorTexture, textureIndexMap)
    updateTextureInfoIndex(pbr.metallicRoughnessTexture, textureIndexMap)
    updateTextureInfoIndex(material.normalTexture, textureIndexMap)
    updateTextureInfoIndex(material.occlusionTexture, textureIndexMap)
    updateTextureInfoIndex(material.emissiveTexture, textureIndexMap)
  })
}

function updateTextureImageIndices(json, imageIndexMap) {
  json.textures.forEach((texture) => {
    const oldImageIndex = getTextureSource(texture)

    if (!Number.isInteger(oldImageIndex)) return

    setTextureSource(texture, imageIndexMap.get(oldImageIndex))
  })
}

function updateBufferViewIndices(json, bufferViewIndexMap) {
  json.accessors?.forEach((accessor) => {
    if (Number.isInteger(accessor.bufferView)) {
      accessor.bufferView = bufferViewIndexMap.get(accessor.bufferView)
    }
  })

  json.images?.forEach((image) => {
    if (Number.isInteger(image.bufferView)) {
      image.bufferView = bufferViewIndexMap.get(image.bufferView)
    }
  })
}

function createIndexMap(items, removedIndexSet) {
  const indexMap = new Map()
  let nextIndex = 0

  items.forEach((_, index) => {
    if (removedIndexSet.has(index)) return

    indexMap.set(index, nextIndex)
    nextIndex += 1
  })

  return indexMap
}

function removeBufferRanges(binaryChunk, ranges) {
  const sortedRanges = [...ranges].sort((firstRange, secondRange) => {
    return firstRange.start - secondRange.start
  })
  const binaryPieces = []
  let readOffset = 0

  sortedRanges.forEach((range) => {
    binaryPieces.push(binaryChunk.subarray(readOffset, range.start))
    readOffset = range.end
  })

  binaryPieces.push(binaryChunk.subarray(readOffset))

  return Buffer.concat(binaryPieces)
}

function shiftByteOffset(byteOffset, removedRanges) {
  if (!Number.isFinite(byteOffset)) return byteOffset

  return removedRanges.reduce((shiftedByteOffset, range) => {
    if (byteOffset >= range.end) {
      return shiftedByteOffset - (range.end - range.start)
    }

    return shiftedByteOffset
  }, byteOffset)
}

function removeUnusedTextureImages({ binaryChunk, json, removedTextures }) {
  const removedTextureIndexSet = new Set(
    removedTextures.map((textureRow) => textureRow.textureIndex),
  )
  const removedImageIndexSet = new Set(
    removedTextures.map((textureRow) => textureRow.imageIndex),
  )
  const removedBufferViewIndexSet = new Set(
    [...removedImageIndexSet].map((imageIndex) => json.images[imageIndex].bufferView),
  )
  const removedRanges = [...removedBufferViewIndexSet].map((bufferViewIndex) => {
    const bufferView = json.bufferViews[bufferViewIndex]
    const start = bufferView.byteOffset ?? 0

    return {
      end: start + align4(bufferView.byteLength),
      start,
    }
  })
  const textureIndexMap = createIndexMap(json.textures, removedTextureIndexSet)
  const imageIndexMap = createIndexMap(json.images, removedImageIndexSet)
  const bufferViewIndexMap = createIndexMap(
    json.bufferViews,
    removedBufferViewIndexSet,
  )
  const rebuiltBinaryChunk = removeBufferRanges(binaryChunk, removedRanges)

  updateMaterialTextureIndices(json, textureIndexMap)
  json.textures = json.textures.filter((_, index) => {
    return !removedTextureIndexSet.has(index)
  })

  updateTextureImageIndices(json, imageIndexMap)
  json.images = json.images.filter((_, index) => {
    return !removedImageIndexSet.has(index)
  })

  updateBufferViewIndices(json, bufferViewIndexMap)
  json.bufferViews = json.bufferViews.filter((_, index) => {
    return !removedBufferViewIndexSet.has(index)
  })

  json.bufferViews.forEach((bufferView) => {
    bufferView.byteOffset = shiftByteOffset(bufferView.byteOffset ?? 0, removedRanges)

    const meshoptExtension = bufferView.extensions?.EXT_meshopt_compression

    if (meshoptExtension) {
      meshoptExtension.byteOffset = shiftByteOffset(
        meshoptExtension.byteOffset ?? 0,
        removedRanges,
      )
    }
  })

  json.buffers[0].byteLength = rebuiltBinaryChunk.length

  return rebuiltBinaryChunk
}

function patchGroundMaterial(json) {
  const material = json.materials.find(
    (candidateMaterial) => candidateMaterial.name === TARGET_MATERIAL_NAME,
  )

  if (!material) {
    throw new Error(`Material "${TARGET_MATERIAL_NAME}" was not found`)
  }

  const removedTextures = getMaterialTextureImageRows(json, material)
  const pbr = material.pbrMetallicRoughness ?? {}

  delete pbr.baseColorTexture
  delete pbr.metallicRoughnessTexture
  pbr.baseColorFactor = FLAT_GROUND_COLOR
  pbr.metallicFactor = 0
  pbr.roughnessFactor = 1
  material.pbrMetallicRoughness = pbr

  delete material.normalTexture
  delete material.occlusionTexture
  delete material.emissiveTexture
  delete material.emissiveFactor
  material.name = `${TARGET_MATERIAL_NAME}_flat_black`

  return removedTextures
}

function main() {
  const [, , inputPath, outputPath = inputPath] = process.argv

  if (!inputPath) {
    throw new Error(
      'Usage: node tools/gltf/patch_room_ground_material.mjs <input.glb> [output.glb]',
    )
  }

  const { binaryChunk, json } = readGlb(inputPath)
  const removedTextures = patchGroundMaterial(json)
  const patchedBinaryChunk = removeUnusedTextureImages({
    binaryChunk,
    json,
    removedTextures,
  })

  writeGlb(outputPath, json, patchedBinaryChunk)

  console.log(
    JSON.stringify(
      {
        flatColor: FLAT_GROUND_COLOR,
        outputPath,
        removedTextures,
      },
      null,
      2,
    ),
  )
}

main()
