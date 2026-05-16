'use client'

import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/shared/libs'

/* ─────────────────────────────────────────────────────────────────────────────
   Button variants
   3축: size × variant(구조) × color
   - size    : 물리적 크기 (height, padding, typography)
   - variant : 구조 (filled / outlined)
   - color   : 의미적 역할 (primary / blue / purple / pink / green / dark / neutral)
───────────────────────────────────────────────────────────────────────────── */
const buttonVariants = cva(
  // base: 모든 버튼 공통
  'inline-flex items-center justify-center gap-2 rounded-full cursor-pointer transition-colors disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      // 1. size: 물리적 크기 (타이포그래피 포함)
      size: {
        sm: 'h-9  px-5 caption-b',
        md: 'h-11 px-6 body-b',
        lg: 'h-14 px-8 body-l-b',
      },

      // 2. variant: 채움 vs 테두리 구조
      variant: {
        filled:   '',
        outlined: 'border-2 bg-transparent',
      },

      // 3. color: 의미적 역할 (compoundVariants에서 실제 색상 적용)
      color: {
        primary: '',
        blue:    '',
        purple:  '',
        pink:    '',
        green:   '',
        dark:    '',
        neutral: '',
      },
    },

    // variant × color 조합별 실제 색상
    compoundVariants: [
      // ── filled ──────────────────────────────────────────────────────────
      {
        variant: 'filled',
        color: 'primary',
        className: 'bg-relay-accent text-relay-ink hover:bg-relay-accent-strong',
      },
      {
        variant: 'filled',
        color: 'blue',
        className: 'bg-relay-blue text-relay-ink hover:opacity-90',
      },
      {
        variant: 'filled',
        color: 'purple',
        className: 'bg-relay-segment-body text-relay-ink hover:opacity-90',
      },
      {
        variant: 'filled',
        color: 'pink',
        className: 'bg-relay-coral text-fg-inverse hover:opacity-90',
      },
      {
        variant: 'filled',
        color: 'green',
        className: 'bg-relay-green text-fg-inverse hover:opacity-90',
      },
      {
        variant: 'filled',
        color: 'dark',
        className: 'bg-neutral-900 text-fg-inverse hover:bg-neutral-800',
      },
      {
        variant: 'filled',
        color: 'neutral',
        className: 'bg-surface-default text-fg-primary border border-border-default hover:bg-surface-subtle',
      },

      // ── outlined ─────────────────────────────────────────────────────────
      {
        variant: 'outlined',
        color: 'primary',
        className: 'border-relay-accent text-relay-muted hover:bg-relay-accent/10',
      },
      {
        variant: 'outlined',
        color: 'blue',
        className: 'border-relay-blue text-relay-blue hover:bg-relay-blue/10',
      },
      {
        variant: 'outlined',
        color: 'purple',
        className: 'border-relay-segment-body text-relay-ink hover:bg-relay-segment-body/30',
      },
      {
        variant: 'outlined',
        color: 'pink',
        className: 'border-relay-coral text-relay-coral hover:bg-relay-coral/10',
      },
      {
        variant: 'outlined',
        color: 'green',
        className: 'border-relay-green text-relay-green hover:bg-relay-green/10',
      },
      {
        variant: 'outlined',
        color: 'dark',
        className: 'border-fg-primary text-fg-primary hover:bg-surface-subtle',
      },
      {
        variant: 'outlined',
        color: 'neutral',
        className: 'border-border-default text-fg-secondary hover:bg-surface-subtle',
      },
    ],

    defaultVariants: {
      size:    'md',
      variant: 'filled',
      color:   'primary',
    },
  },
)

// ─────────────────────────────────────────────────────────────────────────────

interface ButtonProps
  extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'color'>,
    VariantProps<typeof buttonVariants> {
  ref?: React.Ref<HTMLButtonElement>
}

export function Button({
  className,
  size,
  variant,
  color,
  ref,
  ...props
}: ButtonProps) {
  return (
    <button
      ref={ref}
      className={cn(buttonVariants({ size, variant, color }), className)}
      {...props}
    />
  )
}
