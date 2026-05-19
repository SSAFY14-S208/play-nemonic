import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'

const GLB_HEADER_BYTES = 12
const GLB_CHUNK_HEADER_BYTES = 8
const GLB_MAGIC = 0x46546c67
const GLB_VERSION = 2
const JSON_CHUNK_TYPE = 0x4e4f534a
const BIN_CHUNK_TYPE = 0x004e4942
const TARGET_MATERIAL_NAME = 'succulent material'
const TARGET_BASE_COLOR_IMAGE_NAME = 'collor'
const TARGET_BASE_COLOR_MAX_SIZE = 256
const TARGET_BASE_COLOR_WEBP_QUALITY = 44
const TARGET_ROUGHNESS_FACTOR = 0.76

const require = createRequire(import.meta.url)

function loadSharp() {
  try {
    return require('sharp')
  } catch {
    const pnpmDirectory = path.resolve('node_modules/.pnpm')
    const sharpPackageDirectory = fs
      .readdirSync(pnpmDirectory)
      .find((entry) => entry.startsWith('sharp@'))

    if (!sharpPackageDirectory) {
      throw new Error('sharp package was not found in node_modules/.pnpm')
    }

    return require(
      path.join(pnpmDirectory, sharpPackageDirectory, 'node_modules/sharp'),
    )
  }
}

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

function getMaterialTextureInfoRows(material) {
  const pbr = material.pbrMetallicRoughness ?? {}

  return [
    ['baseColorTexture', pbr.baseColorTexture],
    ['metallicRoughnessTexture', pbr.metallicRoughnessTexture],
    ['normalTexture', material.normalTexture],
    ['occlusionTexture', material.occlusionTexture],
    ['emissiveTexture', material.emissiveTexture],
  ]
}

function updateTextureInfoIndex(textureInfo, textureIndexMap) {
  if (!textureInfo || !Number.isInteger(textureInfo.index)) return

  textureInfo.index = textureIndexMap.get(textureInfo.index)
}

function updateMaterialTextureIndices(json, textureIndexMap) {
  json.materials?.forEach((material) => {
    getMaterialTextureInfoRows(material).forEach(([, textureInfo]) => {
      updateTextureInfoIndex(textureInfo, textureIndexMap)
    })
  })
}

function updateTextureImageIndices(json, imageIndexMap) {
  json.textures?.forEach((texture) => {
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

function shiftByteOffset(byteOffset, removedRanges) {
  if (!Number.isFinite(byteOffset)) return byteOffset

  return removedRanges.reduce((shiftedByteOffset, range) => {
    if (byteOffset >= range.end) {
      return shiftedByteOffset - (range.end - range.start)
    }

    return shiftedByteOffset
  }, byteOffset)
}

function shiftByteOffsetAfterReplacement(byteOffset, oldImageEnd, byteDelta) {
  if (!Number.isFinite(byteOffset) || byteOffset < oldImageEnd) {
    return byteOffset
  }

  return byteOffset + byteDelta
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

function assertTextureIsUnreferenced(json, textureIndex) {
  const references = []

  json.materials?.forEach((material, materialIndex) => {
    getMaterialTextureInfoRows(material).forEach(([slotName, textureInfo]) => {
      if (textureInfo?.index === textureIndex) {
        references.push({
          materialIndex,
          materialName: material.name,
          slotName,
        })
      }
    })
  })

  if (references.length > 0) {
    throw new Error(
      `Texture index ${textureIndex} is still referenced: ${JSON.stringify(
        references,
      )}`,
    )
  }
}

function removeTextureImageAndBufferView({ binaryChunk, json, textureIndex }) {
  assertTextureIsUnreferenced(json, textureIndex)

  const texture = json.textures[textureIndex]
  const imageIndex = getTextureSource(texture)
  const image = json.images[imageIndex]
  const bufferViewIndex = image.bufferView
  const bufferView = json.bufferViews[bufferViewIndex]
  const start = bufferView.byteOffset ?? 0
  const end = start + align4(bufferView.byteLength)
  const removedRanges = [{ end, start }]
  const textureIndexMap = createIndexMap(json.textures, new Set([textureIndex]))
  const imageIndexMap = createIndexMap(json.images, new Set([imageIndex]))
  const bufferViewIndexMap = createIndexMap(
    json.bufferViews,
    new Set([bufferViewIndex]),
  )
  const rebuiltBinaryChunk = removeBufferRanges(binaryChunk, removedRanges)

  updateMaterialTextureIndices(json, textureIndexMap)
  json.textures = json.textures.filter((_, index) => index !== textureIndex)

  updateTextureImageIndices(json, imageIndexMap)
  json.images = json.images.filter((_, index) => index !== imageIndex)

  updateBufferViewIndices(json, bufferViewIndexMap)
  json.bufferViews = json.bufferViews.filter((_, index) => {
    return index !== bufferViewIndex
  })

  json.bufferViews.forEach((candidateBufferView) => {
    candidateBufferView.byteOffset = shiftByteOffset(
      candidateBufferView.byteOffset ?? 0,
      removedRanges,
    )

    const meshoptExtension =
      candidateBufferView.extensions?.EXT_meshopt_compression

    if (meshoptExtension) {
      meshoptExtension.byteOffset = shiftByteOffset(
        meshoptExtension.byteOffset ?? 0,
        removedRanges,
      )
    }
  })

  json.buffers[0].byteLength = rebuiltBinaryChunk.length

  return {
    binaryChunk: rebuiltBinaryChunk,
    removedImage: image.name,
    removedImageBytes: bufferView.byteLength,
    removedTextureIndex: textureIndex,
  }
}

function getImageBytes(binaryChunk, json, imageIndex) {
  const image = json.images[imageIndex]
  const bufferView = json.bufferViews[image.bufferView]
  const byteOffset = bufferView.byteOffset ?? 0

  return binaryChunk.subarray(byteOffset, byteOffset + bufferView.byteLength)
}

function replaceImageBytes({ binaryChunk, imageIndex, json, replacementBytes }) {
  const image = json.images[imageIndex]
  const targetBufferView = json.bufferViews[image.bufferView]
  const oldImageStart = targetBufferView.byteOffset ?? 0
  const oldImageEnd = oldImageStart + targetBufferView.byteLength
  const alignedReplacementLength = align4(replacementBytes.length)
  const byteDelta = alignedReplacementLength - targetBufferView.byteLength
  const replacementPaddingLength =
    alignedReplacementLength - replacementBytes.length
  const rebuiltBinaryChunk = Buffer.concat([
    binaryChunk.subarray(0, oldImageStart),
    Buffer.from(replacementBytes),
    Buffer.alloc(replacementPaddingLength),
    binaryChunk.subarray(oldImageEnd),
  ])

  targetBufferView.byteLength = replacementBytes.length

  json.bufferViews.forEach((bufferView) => {
    if (bufferView !== targetBufferView) {
      bufferView.byteOffset = shiftByteOffsetAfterReplacement(
        bufferView.byteOffset ?? 0,
        oldImageEnd,
        byteDelta,
      )
    }

    const meshoptExtension = bufferView.extensions?.EXT_meshopt_compression

    if (meshoptExtension) {
      meshoptExtension.byteOffset = shiftByteOffsetAfterReplacement(
        meshoptExtension.byteOffset ?? 0,
        oldImageEnd,
        byteDelta,
      )
    }
  })

  json.buffers[0].byteLength = rebuiltBinaryChunk.length

  return rebuiltBinaryChunk
}

function getTargetMaterial(json) {
  const material = json.materials.find((candidateMaterial) => {
    return candidateMaterial.name === TARGET_MATERIAL_NAME
  })

  if (!material) {
    throw new Error(`Material "${TARGET_MATERIAL_NAME}" was not found`)
  }

  return material
}

function patchSucculentMaterialSlots(json) {
  const material = getTargetMaterial(json)
  const pbr = material.pbrMetallicRoughness ?? {}
  const baseColorTextureIndex = pbr.baseColorTexture?.index
  const roughnessTextureIndex = pbr.metallicRoughnessTexture?.index
  const normalTextureIndex = material.normalTexture?.index

  if (!Number.isInteger(baseColorTextureIndex)) {
    throw new Error(`${TARGET_MATERIAL_NAME} is missing baseColorTexture`)
  }

  if (!Number.isInteger(roughnessTextureIndex)) {
    throw new Error(`${TARGET_MATERIAL_NAME} is missing roughness texture`)
  }

  if (normalTextureIndex === baseColorTextureIndex) {
    delete material.normalTexture
  }

  delete pbr.metallicRoughnessTexture
  pbr.metallicFactor = 0
  pbr.roughnessFactor = TARGET_ROUGHNESS_FACTOR
  material.pbrMetallicRoughness = pbr
  material.doubleSided = true

  return {
    baseColorTextureIndex,
    removedNormalTexture:
      normalTextureIndex === baseColorTextureIndex ? TARGET_BASE_COLOR_IMAGE_NAME : null,
    roughnessTextureIndex,
  }
}

function findImageIndexByName(json, imageName) {
  const imageIndex = json.images.findIndex((image) => image.name === imageName)

  if (imageIndex < 0) {
    throw new Error(`Image "${imageName}" was not found`)
  }

  return imageIndex
}

async function compressBaseColorImage({ binaryChunk, json, sharp }) {
  const imageIndex = findImageIndexByName(json, TARGET_BASE_COLOR_IMAGE_NAME)
  const sourceBytes = getImageBytes(binaryChunk, json, imageIndex)
  const sourceMetadata = await sharp(sourceBytes).metadata()
  const compressedBytes = await sharp(sourceBytes)
    .resize({
      fit: 'inside',
      height: TARGET_BASE_COLOR_MAX_SIZE,
      width: TARGET_BASE_COLOR_MAX_SIZE,
      withoutEnlargement: true,
    })
    .webp({
      alphaQuality: 64,
      effort: 6,
      quality: TARGET_BASE_COLOR_WEBP_QUALITY,
    })
    .toBuffer()
  const compressedMetadata = await sharp(compressedBytes).metadata()

  json.images[imageIndex].mimeType = 'image/webp'

  return {
    binaryChunk: replaceImageBytes({
      binaryChunk,
      imageIndex,
      json,
      replacementBytes: compressedBytes,
    }),
    compressedBytes: compressedBytes.length,
    compressedSize: {
      height: compressedMetadata.height,
      width: compressedMetadata.width,
    },
    sourceBytes: sourceBytes.length,
    sourceSize: {
      height: sourceMetadata.height,
      width: sourceMetadata.width,
    },
  }
}

async function main() {
  const [, , inputPath, outputPath = inputPath] = process.argv

  if (!inputPath) {
    throw new Error(
      'Usage: node tools/gltf/patch_room_succulent_material.mjs <input.glb> [output.glb]',
    )
  }

  const sharp = loadSharp()
  const { binaryChunk: sourceBinaryChunk, json } = readGlb(inputPath)
  const materialPatch = patchSucculentMaterialSlots(json)
  const roughnessRemoval = removeTextureImageAndBufferView({
    binaryChunk: sourceBinaryChunk,
    json,
    textureIndex: materialPatch.roughnessTextureIndex,
  })
  const baseColorCompression = await compressBaseColorImage({
    binaryChunk: roughnessRemoval.binaryChunk,
    json,
    sharp,
  })
  const {
    binaryChunk: outputBinaryChunk,
    ...baseColorCompressionSummary
  } = baseColorCompression

  writeGlb(outputPath, json, outputBinaryChunk)

  console.log(
    JSON.stringify(
      {
        baseColorCompression: baseColorCompressionSummary,
        materialName: TARGET_MATERIAL_NAME,
        materialPatch,
        outputPath,
        roughnessFactor: TARGET_ROUGHNESS_FACTOR,
        roughnessRemoval,
      },
      null,
      2,
    ),
  )
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
