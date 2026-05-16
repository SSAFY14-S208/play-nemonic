import fs from 'node:fs'
import path from 'node:path'

const DEFAULT_GLB_PATH = path.join(
  process.cwd(),
  'public',
  'models',
  'isometric-girl-room.glb',
)

const GLB_CHUNK_TYPES = {
  BIN: 0x004e4942,
  JSON: 0x4e4f534a,
}

const FALLBACK_MATERIALS = [
  {
    keywords: ['room isometric', 'turn on for world lighting'],
    material: {
      name: 'Nemonic_Web_Room_Shell_Fallback',
      doubleSided: true,
      pbrMetallicRoughness: {
        baseColorFactor: [0.92, 0.88, 1, 1],
        metallicFactor: 0,
        roughnessFactor: 0.78,
      },
    },
  },
  {
    nodeName: 'panel',
    replaceExistingMaterial: true,
    material: {
      name: 'Nemonic_Web_Main_Wall_Panel_Fallback',
      doubleSided: true,
      pbrMetallicRoughness: {
        baseColorFactor: [0.86, 0.81, 0.98, 1],
        metallicFactor: 0,
        roughnessFactor: 0.5,
      },
    },
  },
  {
    nodeName: 'cube shelf',
    replaceExistingMaterial: true,
    material: {
      name: 'Nemonic_Web_Bright_Surface_Fallback',
      doubleSided: true,
      pbrMetallicRoughness: {
        baseColorFactor: [1, 0.97, 1, 1],
        metallicFactor: 0,
        roughnessFactor: 0.38,
      },
    },
  },
  {
    nodeName: 'main tabletop',
    replaceExistingMaterial: true,
    material: {
      name: 'Nemonic_Web_Bright_Surface_Fallback',
      doubleSided: true,
      pbrMetallicRoughness: {
        baseColorFactor: [1, 0.97, 1, 1],
        metallicFactor: 0,
        roughnessFactor: 0.38,
      },
    },
  },
  {
    nodeName: 'side tabletop.001',
    replaceExistingMaterial: true,
    material: {
      name: 'Nemonic_Web_Bright_Surface_Fallback',
      doubleSided: true,
      pbrMetallicRoughness: {
        baseColorFactor: [1, 0.97, 1, 1],
        metallicFactor: 0,
        roughnessFactor: 0.38,
      },
    },
  },
  {
    nodeName: 'side tabletop.002',
    replaceExistingMaterial: true,
    material: {
      name: 'Nemonic_Web_Bright_Surface_Fallback',
      doubleSided: true,
      pbrMetallicRoughness: {
        baseColorFactor: [1, 0.97, 1, 1],
        metallicFactor: 0,
        roughnessFactor: 0.38,
      },
    },
  },
  {
    nodeName: 'carpet',
    replaceExistingMaterial: true,
    material: {
      name: 'Nemonic_Web_Carpet_Fallback',
      doubleSided: true,
      pbrMetallicRoughness: {
        baseColorFactor: [1, 0.96, 1, 1],
        metallicFactor: 0,
        roughnessFactor: 0.98,
      },
    },
  },
  {
    nodeName: 'shelf 1',
    material: {
      name: 'Nemonic_Web_Shelf_Fallback',
      doubleSided: true,
      pbrMetallicRoughness: {
        baseColorFactor: [0.98, 0.95, 1, 1],
        metallicFactor: 0,
        roughnessFactor: 0.5,
      },
    },
  },
]

function readUint32(buffer, offset) {
  return buffer.readUInt32LE(offset)
}

function writeUint32(buffer, offset, value) {
  buffer.writeUInt32LE(value, offset)
}

function getPaddedJsonBuffer(document) {
  const json = JSON.stringify(document)
  const jsonByteLength = Buffer.byteLength(json)
  const paddedByteLength = Math.ceil(jsonByteLength / 4) * 4
  const buffer = Buffer.alloc(paddedByteLength, 0x20)

  buffer.write(json, 0, 'utf8')

  return buffer
}

function parseGlb(glbPath) {
  const buffer = fs.readFileSync(glbPath)

  if (buffer.toString('utf8', 0, 4) !== 'glTF') {
    throw new Error(`${glbPath} is not a valid GLB file.`)
  }

  let offset = 12
  let jsonChunk = null
  let binChunk = null

  while (offset < buffer.length) {
    const chunkLength = readUint32(buffer, offset)
    const chunkType = readUint32(buffer, offset + 4)
    const chunkStart = offset + 8
    const chunkEnd = chunkStart + chunkLength
    const data = buffer.subarray(chunkStart, chunkEnd)

    if (chunkType === GLB_CHUNK_TYPES.JSON) {
      jsonChunk = JSON.parse(data.toString('utf8'))
    } else if (chunkType === GLB_CHUNK_TYPES.BIN) {
      binChunk = Buffer.from(data)
    }

    offset = chunkEnd
  }

  if (!jsonChunk || !binChunk) {
    throw new Error(`${glbPath} must contain JSON and BIN chunks.`)
  }

  return {
    binChunk,
    document: jsonChunk,
  }
}

function getOrCreateMaterialIndex(document, fallbackMaterial) {
  document.materials ??= []
  const nextMaterial = JSON.parse(JSON.stringify(fallbackMaterial))

  const existingIndex = document.materials.findIndex(
    (material) => material.name === fallbackMaterial.name,
  )

  if (existingIndex >= 0) {
    document.materials[existingIndex] = nextMaterial
    return existingIndex
  }

  return document.materials.push(nextMaterial) - 1
}

function nodeMatches(node, fallback) {
  const nodeName = (node.name ?? '').toLowerCase()

  if (fallback.nodeName) {
    return nodeName === fallback.nodeName
  }

  return fallback.keywords.every((keyword) => nodeName.includes(keyword))
}

function applyFallbackMaterials(document) {
  let patchedPrimitiveCount = 0

  document.nodes?.forEach((node) => {
    if (node.mesh === undefined) return

    const fallback = FALLBACK_MATERIALS.find((candidate) =>
      nodeMatches(node, candidate),
    )

    if (!fallback) return

    const mesh = document.meshes?.[node.mesh]
    if (!mesh) return

    const materialIndex = getOrCreateMaterialIndex(document, fallback.material)

    mesh.primitives?.forEach((primitive) => {
      if (
        primitive.material !== undefined &&
        !fallback.replaceExistingMaterial
      ) {
        return
      }

      primitive.material = materialIndex
      patchedPrimitiveCount += 1
    })
  })

  return patchedPrimitiveCount
}

function writeGlb(glbPath, document, binChunk) {
  const jsonBuffer = getPaddedJsonBuffer(document)
  const totalLength = 12 + 8 + jsonBuffer.length + 8 + binChunk.length
  const output = Buffer.alloc(totalLength)

  output.write('glTF', 0, 'utf8')
  writeUint32(output, 4, 2)
  writeUint32(output, 8, totalLength)
  writeUint32(output, 12, jsonBuffer.length)
  writeUint32(output, 16, GLB_CHUNK_TYPES.JSON)
  jsonBuffer.copy(output, 20)
  const binHeaderOffset = 20 + jsonBuffer.length
  writeUint32(output, binHeaderOffset, binChunk.length)
  writeUint32(output, binHeaderOffset + 4, GLB_CHUNK_TYPES.BIN)
  binChunk.copy(output, binHeaderOffset + 8)

  fs.writeFileSync(glbPath, output)
}

const glbPath = process.argv[2]
  ? path.resolve(process.argv[2])
  : DEFAULT_GLB_PATH
const { binChunk, document } = parseGlb(glbPath)
const patchedPrimitiveCount = applyFallbackMaterials(document)

if (patchedPrimitiveCount > 0) {
  writeGlb(glbPath, document, binChunk)
}

console.log(
  JSON.stringify(
    {
      glbPath,
      materialCount: document.materials?.length ?? 0,
      patchedPrimitiveCount,
    },
    null,
    2,
  ),
)
