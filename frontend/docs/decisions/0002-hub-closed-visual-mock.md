# 0002 Hub Closed Visual Mock

## Status

Accepted

## Context

The hub screen is a closed visual mock for the 3D world entrance. Its overlay
must match a deliberately art-directed pastel game interface, including
per-platform chip colors, translucent glass effects, and a display typeface.

The project design system normally requires semantic color tokens and bundled
typography utilities. Those rules are still the default for product UI, admin
surfaces, and shared components. The hub overlay is intentionally scoped as a
route-specific visual layer rather than a reusable design-system component.

## Decision

Hub-only overlay styles may use feature-local raw colors, gradients, shadows,
and non-utility typography inside `features/hub/HubOverlay.module.css`.

The exception is limited to:

- `features/hub/HubOverlay.tsx`
- `features/hub/HubOverlay.module.css`
- `app/(service)/hub/page.tsx` route-local backdrop fallback
- hub-specific 3D world presentation in `worlds/hub/`

Hub styles must not live in `shared/styles/`, and hub visual classes must not be
exported as shared UI primitives.

## Consequences

The hub can preserve its art-directed closed visual appearance without forcing
those values into global semantic tokens. Shared styling remains token-driven,
and future hub visual work must stay local to the hub feature/world folders.
