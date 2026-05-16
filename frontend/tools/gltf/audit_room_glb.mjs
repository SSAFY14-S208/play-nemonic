import fs from 'node:fs'
import path from 'node:path'

const DEFAULT_GLB_PATH = path.join(
  process.cwd(),
  'public',
  'models',
  'isometric-girl-room.glb',
)

const ROOM_GLB_BUDGET = {
  fileSizeBytes: 8 * 1024 * 1024,
  imageCount: 32,
  materialCount: 40,
  renderedTriangleCount: 250_000,
}

const REQUIRED_EXTENSIONS = [
  'EXT_meshopt_compression',
  'EXT_texture_webp',
]

const GLB_CHUNK_TYPES = {
  BIN: 0x004e4942,
  JSON: 0x4e4f534a,
}

function readUint32(buffer, offset) {
  return buffer.readUInt32LE(offset)
}

function parseGlb(glbPath) {
  const buffer = fs.readFileSync(glbPath)
  const magic = readUint32(buffer, 0)
  const version = readUint32(buffer, 4)
  const totalLength = readUint32(buffer, 8)

  if (magic !== 0x46546c67 || version !== 2) {
    throw new Error(`${glbPath} is not a valid glTF 2.0 GLB file.`)
  }

  let offset = 12
  let document = null
  let binaryChunkBytes = 0

  while (offset < buffer.length) {
    const chunkLength = readUint32(buffer, offset)
    const chunkType = readUint32(buffer, offset + 4)
    const chunkStart = offset + 8
    const chunkEnd = chunkStart + chunkLength

    if (chunkType === GLB_CHUNK_TYPES.JSON) {
      document = JSON.parse(buffer.slice(chunkStart, chunkEnd).toString('utf8'))
    }

    if (chunkType === GLB_CHUNK_TYPES.BIN) {
      binaryChunkBytes = chunkLength
    }

    offset = chunkEnd
  }

  if (!document) {
    throw new Error(`${glbPath} does not contain a JSON chunk.`)
  }

  return {
    binaryChunkBytes,
    document,
    fileSizeBytes: totalLength,
  }
}

function getAccessorCount(document, accessorIndex) {
  return document.accessors?.[accessorIndex]?.count ?? 0
}

function getPrimitiveTriangleCount(document, primitive) {
  if ((primitive.mode ?? 4) !== 4) return 0

  if (primitive.indices !== undefined) {
    return getAccessorCount(document, primitive.indices) / 3
  }

  const positionAccessor = primitive.attributes?.POSITION

  return positionAccessor === undefined
    ? 0
    : getAccessorCount(document, positionAccessor) / 3
}

function getMeshTriangleCount(document, meshIndex) {
  const mesh = document.meshes?.[meshIndex]

  if (!mesh) return 0

  return (mesh.primitives ?? []).reduce(
    (triangleCount, primitive) =>
      triangleCount + getPrimitiveTriangleCount(document, primitive),
    0,
  )
}

function getMeshoptBufferViewCount(document) {
  return (document.bufferViews ?? []).filter(
    (bufferView) => bufferView.extensions?.EXT_meshopt_compression,
  ).length
}

function getRenderedTriangleCount(document) {
  return (document.nodes ?? []).reduce((triangleCount, node) => {
    if (node.mesh === undefined) return triangleCount

    return triangleCount + getMeshTriangleCount(document, node.mesh)
  }, 0)
}

function getUniqueTriangleCount(document) {
  return (document.meshes ?? []).reduce(
    (triangleCount, _, meshIndex) =>
      triangleCount + getMeshTriangleCount(document, meshIndex),
    0,
  )
}

function formatBytes(bytes) {
  return `${(bytes / (1024 * 1024)).toFixed(2)}MB`
}

function createAudit(glbPath) {
  const { binaryChunkBytes, document, fileSizeBytes } = parseGlb(glbPath)
  const extensionsUsed = document.extensionsUsed ?? []
  const missingRequiredExtensions = REQUIRED_EXTENSIONS.filter(
    (extensionName) => !extensionsUsed.includes(extensionName),
  )
  const renderedTriangleCount = getRenderedTriangleCount(document)

  const audit = {
    assetGenerator: document.asset?.generator ?? '',
    binaryChunkBytes,
    budgets: ROOM_GLB_BUDGET,
    counts: {
      animations: document.animations?.length ?? 0,
      bufferViews: document.bufferViews?.length ?? 0,
      images: document.images?.length ?? 0,
      materials: document.materials?.length ?? 0,
      meshNodes: (document.nodes ?? []).filter((node) => node.mesh !== undefined)
        .length,
      meshes: document.meshes?.length ?? 0,
      nodes: document.nodes?.length ?? 0,
      renderedTriangles: renderedTriangleCount,
      uniqueTriangles: getUniqueTriangleCount(document),
    },
    extensionsRequired: document.extensionsRequired ?? [],
    extensionsUsed,
    fileSizeBytes,
    fileSizeLabel: formatBytes(fileSizeBytes),
    glbPath,
    meshoptCompressedBufferViews: getMeshoptBufferViewCount(document),
    missingRequiredExtensions,
  }

  const failures = []

  if (fileSizeBytes > ROOM_GLB_BUDGET.fileSizeBytes) {
    failures.push(
      `file size ${formatBytes(fileSizeBytes)} exceeds ${formatBytes(
        ROOM_GLB_BUDGET.fileSizeBytes,
      )}`,
    )
  }

  if (renderedTriangleCount > ROOM_GLB_BUDGET.renderedTriangleCount) {
    failures.push(
      `rendered triangle count ${renderedTriangleCount} exceeds ${ROOM_GLB_BUDGET.renderedTriangleCount}`,
    )
  }

  if (audit.counts.materials > ROOM_GLB_BUDGET.materialCount) {
    failures.push(
      `material count ${audit.counts.materials} exceeds ${ROOM_GLB_BUDGET.materialCount}`,
    )
  }

  if (audit.counts.images > ROOM_GLB_BUDGET.imageCount) {
    failures.push(
      `image count ${audit.counts.images} exceeds ${ROOM_GLB_BUDGET.imageCount}`,
    )
  }

  if (missingRequiredExtensions.length > 0) {
    failures.push(
      `missing required extension(s): ${missingRequiredExtensions.join(', ')}`,
    )
  }

  return {
    audit,
    failures,
  }
}

const glbPath = process.argv[2]
  ? path.resolve(process.argv[2])
  : DEFAULT_GLB_PATH
const { audit, failures } = createAudit(glbPath)

console.log(JSON.stringify(audit, null, 2))

if (failures.length > 0) {
  console.error(`Room GLB audit failed:\n- ${failures.join('\n- ')}`)
  process.exit(1)
}

console.log('Room GLB audit passed.')
