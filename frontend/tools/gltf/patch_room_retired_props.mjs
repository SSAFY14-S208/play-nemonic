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

const RETIRED_ROOM_NODE_NAMES = new Set([
  'cube top shelf',
  'cube topshelf',
  'frame.001',
  'large frame',
  'peg',
  'picture.001',
])

const RETIRED_ROOM_NODE_KEYWORDS = [
  'cube top shelf',
  'cube topshelf',
  'large frame',
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

function patchRetiredRoomProps(document) {
  const patchedNodes = []

  document.nodes?.forEach((node) => {
    const nodeName = (node.name ?? '').toLowerCase()

    const isRetiredNode =
      RETIRED_ROOM_NODE_NAMES.has(nodeName) ||
      RETIRED_ROOM_NODE_KEYWORDS.some((keyword) => nodeName.includes(keyword))

    if (!isRetiredNode || node.mesh === undefined) {
      return
    }

    patchedNodes.push({
      mesh: node.mesh,
      name: node.name,
    })
    delete node.mesh
    node.extras = {
      ...node.extras,
      nemonicRetiredFromRoom: true,
    }
  })

  return patchedNodes
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
const patchedNodes = patchRetiredRoomProps(document)

if (patchedNodes.length > 0) {
  writeGlb(glbPath, document, binChunk)
}

console.log(
  JSON.stringify(
    {
      glbPath,
      patchedNodes,
      retiredNodeKeywords: RETIRED_ROOM_NODE_KEYWORDS,
      retiredNodeNames: [...RETIRED_ROOM_NODE_NAMES],
    },
    null,
    2,
  ),
)
