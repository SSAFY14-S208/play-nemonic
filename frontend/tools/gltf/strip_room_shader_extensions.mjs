import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const STRIPPED_MATERIAL_EXTENSIONS = new Set([
  'KHR_materials_anisotropy',
  'KHR_materials_clearcoat',
  'KHR_materials_ior',
  'KHR_materials_specular',
])

function parseArgs() {
  const [inputGlb, outputGlb] = process.argv.slice(2)

  if (!inputGlb || !outputGlb) {
    throw new Error(
      'Usage: node tools/gltf/strip_room_shader_extensions.mjs <input.glb> <output.glb>',
    )
  }

  return { inputGlb, outputGlb }
}

function readGlb(glbPath) {
  const buffer = fs.readFileSync(glbPath)
  const magic = buffer.toString('utf8', 0, 4)
  const version = buffer.readUInt32LE(4)

  if (magic !== 'glTF' || version !== 2) {
    throw new Error(`${glbPath} is not a glTF 2.0 GLB file.`)
  }

  const jsonChunkLength = buffer.readUInt32LE(12)
  const jsonChunkType = buffer.toString('utf8', 16, 20)

  if (jsonChunkType !== 'JSON') {
    throw new Error(`${glbPath} does not start with a JSON GLB chunk.`)
  }

  const jsonChunkStart = 20
  const jsonChunkEnd = jsonChunkStart + jsonChunkLength
  const jsonText = buffer.toString('utf8', jsonChunkStart, jsonChunkEnd).trimEnd()

  return {
    buffer,
    json: JSON.parse(jsonText),
    trailingChunks: buffer.subarray(jsonChunkEnd),
  }
}

function stripMaterialExtensions(document) {
  let strippedMaterialExtensionCount = 0
  const strippedMaterials = []

  for (const material of document.materials ?? []) {
    if (!material.extensions) continue

    const removedExtensions = []

    for (const extensionName of Object.keys(material.extensions)) {
      if (!STRIPPED_MATERIAL_EXTENSIONS.has(extensionName)) continue

      delete material.extensions[extensionName]
      strippedMaterialExtensionCount += 1
      removedExtensions.push(extensionName)
    }

    if (Object.keys(material.extensions).length === 0) {
      delete material.extensions
    }

    if (removedExtensions.length > 0) {
      strippedMaterials.push({
        name: material.name ?? '(unnamed)',
        removedExtensions,
      })
    }
  }

  const remainingUsedExtensions = new Set()

  for (const material of document.materials ?? []) {
    for (const extensionName of Object.keys(material.extensions ?? {})) {
      remainingUsedExtensions.add(extensionName)
    }
  }

  document.extensionsUsed = (document.extensionsUsed ?? []).filter(
    (extensionName) =>
      !STRIPPED_MATERIAL_EXTENSIONS.has(extensionName) ||
      remainingUsedExtensions.has(extensionName),
  )

  if (document.extensionsUsed.length === 0) {
    delete document.extensionsUsed
  }

  return { strippedMaterialExtensionCount, strippedMaterials }
}

function padJsonChunk(jsonText) {
  const paddingLength = (4 - (Buffer.byteLength(jsonText) % 4)) % 4
  return Buffer.from(jsonText + ' '.repeat(paddingLength), 'utf8')
}

function writeGlb(outputGlb, document, trailingChunks) {
  const jsonChunk = padJsonChunk(JSON.stringify(document))
  const totalLength = 12 + 8 + jsonChunk.length + trailingChunks.length
  const header = Buffer.alloc(12)
  const jsonHeader = Buffer.alloc(8)

  header.write('glTF', 0, 4, 'utf8')
  header.writeUInt32LE(2, 4)
  header.writeUInt32LE(totalLength, 8)

  jsonHeader.writeUInt32LE(jsonChunk.length, 0)
  jsonHeader.write('JSON', 4, 4, 'utf8')

  fs.mkdirSync(path.dirname(outputGlb), { recursive: true })
  fs.writeFileSync(outputGlb, Buffer.concat([header, jsonHeader, jsonChunk, trailingChunks]))
}

function main() {
  const { inputGlb, outputGlb } = parseArgs()
  const { json, trailingChunks } = readGlb(inputGlb)
  const summary = stripMaterialExtensions(json)

  writeGlb(outputGlb, json, trailingChunks)

  console.log(
    JSON.stringify(
      {
        inputGlb,
        outputGlb,
        strippedExtensions: [...STRIPPED_MATERIAL_EXTENSIONS],
        ...summary,
      },
      null,
      2,
    ),
  )
}

main()
