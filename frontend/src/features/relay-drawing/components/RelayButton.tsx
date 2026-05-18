'use client'

import { cva, type VariantProps } from 'class-variance-authority'
import { forwardRef } from 'react'

import { cn } from '@/shared/libs'

// 릴레이 드로잉 페이지 전용 버튼.
// - variant: 색상/배경/테두리 조합
// - size: 높이 + 좌우 패딩 (typography는 body-b로 고정)
// - shape: 모서리 곡률 (모달 푸터: rounded, CTA 카드: roundedLg, nav pill: pill)
// 모든 버튼은 호버 시 살짝 위로 떠오르는 translateY 피드백을 갖는다.
// disabled 상태에서는 떠오름/밝기 효과가 비활성화된다.
//
// next/link 등 button이 아닌 요소에 동일 스타일을 입혀야 할 때는
// `relayButtonVariants(...)` 결과 클래스를 className으로 직접 적용한다.
export const relayButtonVariants = cva(
  'body-b inline-flex cursor-pointer items-center justify-center whitespace-nowrap transition-all hover:-translate-y-0.5 disabled:opacity-45 disabled:hover:translate-y-0 disabled:hover:brightness-100',
  {
    variants: {
      variant: {
        primary: 'bg-relay-accent text-relay-ink hover:brightness-105',
        secondary:
          'border border-relay-line bg-relay-paper text-relay-accent-strong hover:brightness-95',
        ghost:
          'border border-relay-line bg-relay-active text-relay-accent-strong hover:brightness-95',
      },
      size: {
        sm: 'min-h-11 px-4',
        md: 'min-h-12 px-5',
        lg: 'min-h-14 px-7',
        xl: 'min-h-16 px-7',
      },
      shape: {
        rounded: 'rounded-[var(--radius-md)]',
        roundedLg: 'rounded-2xl',
        pill: 'rounded-full',
      },
    },
    defaultVariants: {
      variant: 'primary',
      size: 'sm',
      shape: 'rounded',
    },
  },
)

export interface RelayButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof relayButtonVariants> {}

const RelayButton = forwardRef<HTMLButtonElement, RelayButtonProps>(
  ({ className, variant, size, shape, type = 'button', ...props }, ref) => {
    return (
      <button
        ref={ref}
        type={type}
        className={cn(relayButtonVariants({ variant, size, shape }), className)}
        {...props}
      />
    )
  },
)

RelayButton.displayName = 'RelayButton'

export default RelayButton
