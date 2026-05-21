# Stage6 Room GLB Pipeline

This folder keeps the browser-facing room GLB reproducible after the Blender
source scene has already been curated for web use.

## Current Shipping Asset

- Runtime GLB: `public/models/isometric-girl-room-stage6-web.glb`
- Runtime cache key: `?v=20260520-succulent-lite`
- Current audit result:
  - file size: `1.88MB`
  - rendered triangles: `144,227`
  - images: `20`
  - materials: `23`
  - animations: `0`

## Scope

The pipeline here handles post-export GLB cleanup:

- remove shader-only material extensions that expand Three.js shader variants
- compress the internal display texture
- replace the ground/grass texture stack with a flat black material
- remove the succulent roughness texture, remove its incorrect normal map slot,
  and compress its base color texture
- run the room GLB audit before writing the final output

It does not edit Blender geometry. Geometry cleanup such as Nemonic internal
part removal must be done in the Blender source scene before exporting the
pre-patch stage6 web GLB.

## Export From Blender

Use the current stage6 Blender source file and export a pre-patch GLB:

```bash
blender --background <stage6-room-source.blend> --python tools/blender/export_stage6_web_glb.py -- --output-glb output/isometric-girl-room-stage6-web-prepatch.glb
```

`export_stage6_web_glb.py` intentionally exports no animations.

## Build The Shipping GLB

Run the post-export optimizer from `frontend/`:

```bash
node tools/gltf/build_stage6_room_glb.mjs \
  --input output/isometric-girl-room-stage6-web-prepatch.glb \
  --output public/models/isometric-girl-room-stage6-web.glb
```

The command writes the final output only after all patch steps and the GLB audit
pass.

## Individual Steps

The build script runs these scripts in order:

1. `strip_room_shader_extensions.mjs`
2. `compress_room_display_texture.mjs`
3. `patch_room_ground_material.mjs`
4. `patch_room_succulent_material.mjs`
5. `audit_room_glb.mjs`

The input GLB should still contain the target material/image names used by these
patches. Do not use an already patched final GLB as the input.

The post-export build script is not a geometry reducer. If the input still has
about `203,976` rendered triangles, `78` mesh nodes, or exported animations, the
script will warn because the committed shipping target (`144,227` triangles,
`64` mesh nodes, `0` animations) requires the Blender source scene to be reduced
before this step.

## Validation

For the committed shipping GLB:

```bash
node tools/gltf/audit_room_glb.mjs public/models/isometric-girl-room-stage6-web.glb
```
