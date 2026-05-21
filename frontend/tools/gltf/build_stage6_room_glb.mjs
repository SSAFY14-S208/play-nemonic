import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(scriptDirectory, '..', '..')
const DEFAULT_OUTPUT_GLB = path.join(
  frontendRoot,
  'public',
  'models',
  'isometric-girl-room-stage6-web.glb',
)

const POST_EXPORT_STEPS = [
  {
    args: (workingGlb) => [workingGlb, workingGlb],
    label: 'strip shader-only material extensions',
    scriptName: 'strip_room_shader_extensions.mjs',
  },
  {
    args: (workingGlb) => [workingGlb, workingGlb],
    label: 'compress internal display texture',
    scriptName: 'compress_room_display_texture.mjs',
  },
  {
    args: (workingGlb) => [workingGlb, workingGlb],
    label: 'flatten ground material textures',
    scriptName: 'patch_room_ground_material.mjs',
  },
  {
    args: (workingGlb) => [workingGlb, workingGlb],
    label: 'lighten succulent material textures',
    scriptName: 'patch_room_succulent_material.mjs',
  },
]

function parseArgs() {
  const args = process.argv.slice(2)
  const parsedArgs = {
    inputGlb: null,
    keepTemp: false,
    outputGlb: DEFAULT_OUTPUT_GLB,
    skipAudit: false,
  }

  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index]

    if (arg === '--input' || arg === '-i') {
      parsedArgs.inputGlb = path.resolve(process.cwd(), args[index + 1] ?? '')
      index += 1
      continue
    }

    if (arg === '--output' || arg === '-o') {
      parsedArgs.outputGlb = path.resolve(process.cwd(), args[index + 1] ?? '')
      index += 1
      continue
    }

    if (arg === '--keep-temp') {
      parsedArgs.keepTemp = true
      continue
    }

    if (arg === '--skip-audit') {
      parsedArgs.skipAudit = true
      continue
    }

    if (arg === '--help' || arg === '-h') {
      printUsage()
      process.exit(0)
    }

    throw new Error(`Unknown argument: ${arg}`)
  }

  if (!parsedArgs.inputGlb) {
    throw new Error('Missing required --input <glb> argument')
  }

  return parsedArgs
}

function printUsage() {
  console.log(`Usage:
  node tools/gltf/build_stage6_room_glb.mjs --input <stage6-web-prepatch.glb> [--output <final.glb>]

Options:
  -i, --input       Required. Pre-patch stage6 web GLB.
  -o, --output      Final GLB path. Defaults to public/models/isometric-girl-room-stage6-web.glb.
      --skip-audit  Skip final audit.
      --keep-temp   Keep temporary working directory.
`)
}

function formatBytes(byteLength) {
  return `${(byteLength / (1024 * 1024)).toFixed(2)}MB`
}

function parseGlbDocument(glbPath) {
  const buffer = fs.readFileSync(glbPath)
  const magic = buffer.readUInt32LE(0)
  const version = buffer.readUInt32LE(4)

  if (magic !== 0x46546c67 || version !== 2) {
    throw new Error(`${glbPath} is not a glTF 2.0 GLB file`)
  }

  let offset = 12

  while (offset < buffer.length) {
    const chunkLength = buffer.readUInt32LE(offset)
    const chunkType = buffer.readUInt32LE(offset + 4)
    const chunkStart = offset + 8
    const chunkEnd = chunkStart + chunkLength

    if (chunkType === 0x4e4f534a) {
      return JSON.parse(buffer.toString('utf8', chunkStart, chunkEnd))
    }

    offset = chunkEnd
  }

  throw new Error(`${glbPath} does not contain a JSON chunk`)
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

function createGlbShapeSummary(glbPath) {
  const document = parseGlbDocument(glbPath)
  const renderedTriangles = (document.nodes ?? []).reduce(
    (triangleCount, node) =>
      node.mesh === undefined
        ? triangleCount
        : triangleCount + getMeshTriangleCount(document, node.mesh),
    0,
  )

  return {
    animations: document.animations?.length ?? 0,
    images: document.images?.length ?? 0,
    materials: document.materials?.length ?? 0,
    meshNodes: (document.nodes ?? []).filter((node) => node.mesh !== undefined)
      .length,
    renderedTriangles,
  }
}

function warnIfInputLooksUnreduced(inputShape) {
  const warnings = []

  if (inputShape.animations > 0) {
    warnings.push(
      `input still has ${inputShape.animations} animation(s); re-export with export_animations=False`,
    )
  }

  if (inputShape.renderedTriangles > 180_000 || inputShape.meshNodes > 70) {
    warnings.push(
      `input still looks geometry-heavy (${inputShape.renderedTriangles} triangles, ${inputShape.meshNodes} mesh nodes); reduce the Blender source before post-export patches`,
    )
  }

  if (warnings.length === 0) return

  console.warn(
    `\n[stage6-room-glb] Input shape warning:\n- ${warnings.join('\n- ')}\n`,
  )
}

function runNodeScript(scriptName, args) {
  const scriptPath = path.join(scriptDirectory, scriptName)
  const result = spawnSync(process.execPath, [scriptPath, ...args], {
    cwd: frontendRoot,
    stdio: 'inherit',
  })

  if (result.error) {
    throw result.error
  }

  if (result.status !== 0) {
    throw new Error(`${scriptName} failed with exit code ${result.status}`)
  }
}

function copyInputToWorkingFile(inputGlb, workingGlb) {
  if (!fs.existsSync(inputGlb)) {
    throw new Error(`Input GLB was not found: ${inputGlb}`)
  }

  fs.copyFileSync(inputGlb, workingGlb)
}

function printStep(label) {
  console.log(`\n[stage6-room-glb] ${label}`)
}

function main() {
  const { inputGlb, keepTemp, outputGlb, skipAudit } = parseArgs()
  const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'stage6-room-glb-'))
  const workingGlb = path.join(tempDirectory, 'stage6-room-working.glb')

  try {
    const inputShape = createGlbShapeSummary(inputGlb)
    warnIfInputLooksUnreduced(inputShape)

    copyInputToWorkingFile(inputGlb, workingGlb)

    POST_EXPORT_STEPS.forEach((step) => {
      printStep(step.label)
      runNodeScript(step.scriptName, step.args(workingGlb))
    })

    if (!skipAudit) {
      printStep('audit optimized GLB')
      runNodeScript('audit_room_glb.mjs', [workingGlb])
    }

    fs.mkdirSync(path.dirname(outputGlb), { recursive: true })
    fs.copyFileSync(workingGlb, outputGlb)

    const inputSize = fs.statSync(inputGlb).size
    const outputSize = fs.statSync(outputGlb).size
    const outputShape = createGlbShapeSummary(outputGlb)

    console.log(
      JSON.stringify(
        {
          inputGlb,
          inputShape,
          inputSize: formatBytes(inputSize),
          outputGlb,
          outputShape,
          outputSize: formatBytes(outputSize),
          tempDirectory: keepTemp ? tempDirectory : undefined,
        },
        null,
        2,
      ),
    )
  } finally {
    if (!keepTemp) {
      fs.rmSync(tempDirectory, { force: true, recursive: true })
    }
  }
}

main()
