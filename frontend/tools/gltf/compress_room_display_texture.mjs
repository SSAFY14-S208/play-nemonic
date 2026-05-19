import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'

const GLB_HEADER_BYTES = 12
const GLB_CHUNK_HEADER_BYTES = 8
const GLB_MAGIC = 0x46546c67
const GLB_VERSION = 2
const JSON_CHUNK_TYPE = 0x4e4f534a
const BIN_CHUNK_TYPE = 0x004e4942
const TARGET_IMAGE_INDEX = 0
const TARGET_MAX_SIZE = 96
const TARGET_WEBP_QUALITY = 12

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
  const magic = sourceBuffer.readUInt32LE(0)
  const version = sourceBuffer.readUInt32LE(4)

  if (magic !== GLB_MAGIC || version !== GLB_VERSION) {
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

function getBufferViewBytes(binaryChunk, bufferView) {
  const byteOffset = bufferView.byteOffset ?? 0

  return binaryChunk.subarray(byteOffset, byteOffset + bufferView.byteLength)
}

function shiftOffsetAfterImage(byteOffset, oldImageEnd, byteDelta) {
  if (!Number.isFinite(byteOffset) || byteOffset < oldImageEnd) {
    return byteOffset
  }

  return byteOffset + byteDelta
}

function rebuildBinaryChunk({ binaryChunk, json, replacementImageBytes }) {
  const targetBufferView = json.bufferViews[json.images[TARGET_IMAGE_INDEX].bufferView]
  const oldImageStart = targetBufferView.byteOffset ?? 0
  const oldImageEnd = oldImageStart + targetBufferView.byteLength
  const alignedReplacementLength = align4(replacementImageBytes.length)
  const byteDelta = alignedReplacementLength - targetBufferView.byteLength
  const replacementPaddingLength =
    alignedReplacementLength - replacementImageBytes.length
  const rebuiltBinaryChunk = Buffer.concat([
    binaryChunk.subarray(0, oldImageStart),
    Buffer.from(replacementImageBytes),
    Buffer.alloc(replacementPaddingLength),
    binaryChunk.subarray(oldImageEnd),
  ])

  targetBufferView.byteLength = replacementImageBytes.length

  json.bufferViews.forEach((bufferView) => {
    if (bufferView !== targetBufferView) {
      bufferView.byteOffset = shiftOffsetAfterImage(
        bufferView.byteOffset ?? 0,
        oldImageEnd,
        byteDelta,
      )
    }

    const meshoptExtension = bufferView.extensions?.EXT_meshopt_compression

    if (meshoptExtension) {
      meshoptExtension.byteOffset = shiftOffsetAfterImage(
        meshoptExtension.byteOffset ?? 0,
        oldImageEnd,
        byteDelta,
      )
    }
  })

  json.buffers[0].byteLength = rebuiltBinaryChunk.length

  return rebuiltBinaryChunk
}

async function main() {
  const [, , inputPath, outputPath = inputPath] = process.argv

  if (!inputPath) {
    throw new Error(
      'Usage: node tools/gltf/compress_room_display_texture.mjs <input.glb> [output.glb]',
    )
  }

  const sharp = loadSharp()
  const { binaryChunk, json } = readGlb(inputPath)
  const image = json.images[TARGET_IMAGE_INDEX]
  const imageBufferView = json.bufferViews[image.bufferView]
  const sourceImageBytes = getBufferViewBytes(binaryChunk, imageBufferView)
  const sourceMetadata = await sharp(sourceImageBytes).metadata()
  const compressedImageBytes = await sharp(sourceImageBytes)
    .resize({
      fit: 'inside',
      height: TARGET_MAX_SIZE,
      width: TARGET_MAX_SIZE,
      withoutEnlargement: true,
    })
    .webp({
      effort: 6,
      quality: TARGET_WEBP_QUALITY,
    })
    .toBuffer()
  const compressedMetadata = await sharp(compressedImageBytes).metadata()
  const rebuiltBinaryChunk = rebuildBinaryChunk({
    binaryChunk,
    json,
    replacementImageBytes: compressedImageBytes,
  })

  image.mimeType = 'image/webp'
  image.name = `${image.name ?? 'display'}_tiny`

  writeGlb(outputPath, json, rebuiltBinaryChunk)

  console.log(
    JSON.stringify(
      {
        imageName: image.name,
        outputPath,
        quality: TARGET_WEBP_QUALITY,
        sourceBytes: sourceImageBytes.length,
        sourceSize: {
          height: sourceMetadata.height,
          width: sourceMetadata.width,
        },
        targetBytes: compressedImageBytes.length,
        targetSize: {
          height: compressedMetadata.height,
          width: compressedMetadata.width,
        },
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
