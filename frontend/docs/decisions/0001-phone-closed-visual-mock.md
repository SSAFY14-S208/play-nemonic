# 0001 Phone Closed Visual Mock

Date: 2026-05-06

## Status

Accepted

## Context

The phone modal reproduces a Figma device mock. Its screen, app icons, drawing
tools, and gallery surfaces are intended to be visually closed to the phone
feature rather than reusable product UI.

The shared design system still remains the default for all regular frontend
features. For this device mock, forcing semantic application tokens would make
the implementation less faithful to the source design and would spread
one-off visual values across many components.

## Decision

`features/phone` may use a sealed phone visual system through
`features/phone/constants`, including `PHONE_COLORS` and phone-specific
typography utilities.

The exception is limited to `features/phone`. Other features must not import
phone constants, colors, or typography as a shortcut around the shared design
system.

## Consequences

- Phone mock colors stay centralized in `features/phone/constants/colors.ts`.
- Phone-specific typography stays centralized in shared utility classes with a
  `phone-` prefix.
- Components outside `features/phone` continue to use semantic tokens.
- If the phone UI becomes reusable product UI later, this ADR must be revisited
  and the visual values should move into the normal design token system.
