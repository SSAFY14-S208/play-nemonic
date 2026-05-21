# 0003 Fortune Closed Visual Mock

Date: 2026-05-08

## Status

Accepted

## Context

The fortune feature renders a 2D theatrical stage (curtains, wizard popo,
tarot table, magic cube box, hanging chain ornaments, moon, sparkles), a
saju input form styled as an in-world magic booth (birth orbs, segment
indicators, plaques, sparks), a typewriter dialogue panel, a thermal-printer
status panel, a result card with cardstock textures, and a 3D-printer
generated video sequence. Each surface is intentionally art-directed to a
single fortune-themed look rather than reusable product UI.

The shared design system still remains the default for all regular frontend
features. Forcing semantic application tokens onto the fortune surfaces would
fight the source illustration set (deep purple/navy + gold accents, custom
serif lockup, drop-shadow lighting, blur + mix-blend-mode glows) and would
scatter one-off raw values across components instead of keeping them in a
single feature-local stylesheet.

## Decision

`features/fortune` may use a sealed fortune visual system, including
feature-local raw colors, gradients, shadows, blurs, mix-blend-mode glows,
keyframe animations, and the bundled fortune fonts (GraceSerif, LeeSeoyun)
inside the following surfaces:

- `features/fortune/fortune.css`
- `features/fortune/FortuneVisual.tsx`
- `features/fortune/FortunePage.tsx`
- `features/fortune/components/*`
- `public/images/fortune/*`, `public/sounds/fortune/*`,
  `public/fonts/fortune/*`, `public/videos/fortune/*` route-local assets

The exception is limited to `features/fortune`. Other features must not
import fortune classes, colors, fonts, or assets as a shortcut around the
shared design system.

## Consequences

- Fortune raw colors, gradients, shadows, and animation keyframes stay
  centralized in `features/fortune/fortune.css`.
- Fortune fonts stay centralized in `public/fonts/fortune/` and are loaded
  through fortune-scoped CSS only.
- Components outside `features/fortune` continue to use semantic tokens and
  the bundled typography utilities.
- If any fortune visual becomes reusable product UI later (e.g. a generic
  result card variant, a generic curtain transition), this ADR must be
  revisited and those values should move into the normal design token system
  before being shared.
