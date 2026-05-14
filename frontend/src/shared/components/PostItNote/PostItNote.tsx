import { useId, type SVGProps } from 'react'

import { cn } from '@/shared/libs'

interface PostItNoteProps extends SVGProps<SVGSVGElement> {
  title?: string
  motion?: 'none' | 'hover' | 'active'
  selected?: boolean
}

const POST_IT_SHAPE_PATH =
  'M25.5 428.7C134.8 436.4 258.7 438.1 511.8 423.2C521.4 306.7 525.8 163.4 512.6 18.8C390.8 9.9 257.1 8.3 18.6 24.4C8.9 152.8 11.2 290.1 25.5 428.7Z'

export function PostItNote({
  className,
  title,
  motion = 'none',
  selected = false,
  ...props
}: PostItNoteProps) {
  const uniqueId = useId().replace(/:/g, '')
  const shadowId = `${uniqueId}-post-it-shadow`
  const surfaceSheenId = `${uniqueId}-post-it-surface-sheen`
  const selectionGlowId = `${uniqueId}-post-it-selection-glow`

  return (
    <svg
      viewBox="0 0 543 459"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      role={title ? 'img' : undefined}
      aria-hidden={title ? undefined : true}
      data-post-it-motion={motion === 'none' ? undefined : motion}
      className={cn('post-it-note pointer-events-none', className)}
      {...props}
    >
      {title && <title>{title}</title>}
      <g className="post-it-note-shadow" filter={`url(#${shadowId})`}>
        <path
          d={POST_IT_SHAPE_PATH}
          fill="currentColor"
          fillOpacity="0.18"
        />
      </g>
      <g className="post-it-note-paper">
        <path
          d={POST_IT_SHAPE_PATH}
          fill="currentColor"
        />
        <path
          d={POST_IT_SHAPE_PATH}
          fill={`url(#${surfaceSheenId})`}
          fillOpacity="0.18"
        />
        <path
          d="M18.6 24.4C257.1 8.3 390.8 9.9 512.6 18.8L512.7 19.5C391 10.6 257.4 9.1 18.9 25.1L18.6 24.4Z"
          fill="currentColor"
          fillOpacity="0.78"
        />
        <path
          d="M492.3 52.6C505.7 161.3 504.8 292.4 492.2 390.4"
          stroke="currentColor"
          strokeOpacity="0.12"
          strokeWidth="8"
          strokeLinecap="round"
        />
        <path
          d="M58.4 404.6C164 411.8 289.6 412.9 483.6 401.2"
          stroke="currentColor"
          strokeOpacity="0.1"
          strokeWidth="8"
          strokeLinecap="round"
        />
        <path
          d="M48.8 48.9C160 38.6 319.7 37.8 486.2 30.5"
          stroke="white"
          strokeOpacity="0.16"
          strokeWidth="11"
          strokeLinecap="round"
        />
        {selected && (
          <g className="post-it-note-selection">
            <path
              d={POST_IT_SHAPE_PATH}
              fill="none"
              stroke={`url(#${selectionGlowId})`}
              strokeWidth="24"
              strokeLinecap="round"
              strokeLinejoin="round"
              opacity="0.95"
            />
            <path
              d={POST_IT_SHAPE_PATH}
              fill="none"
              stroke="white"
              strokeWidth="7"
              strokeLinecap="round"
              strokeLinejoin="round"
              opacity="0.72"
            />
          </g>
        )}
      </g>
      <defs>
        <filter
          id={shadowId}
          x="0"
          y="0.460938"
          width="542.837"
          height="458.121"
          filterUnits="userSpaceOnUse"
          colorInterpolationFilters="sRGB"
        >
          <feFlood floodOpacity="0" result="BackgroundImageFix" />
          <feColorMatrix
            in="SourceAlpha"
            type="matrix"
            values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0"
            result="hardAlpha"
          />
          <feOffset dx="4" dy="8" />
          <feGaussianBlur stdDeviation="4" />
          <feComposite in2="hardAlpha" operator="out" />
          <feColorMatrix
            type="matrix"
            values="0 0 0 0 0.12 0 0 0 0 0.08 0 0 0 0 0.04 0 0 0 0.24 0"
          />
          <feBlend mode="normal" in2="BackgroundImageFix" result="effect1_dropShadow" />
          <feBlend mode="normal" in="SourceGraphic" in2="effect1_dropShadow" result="shape" />
        </filter>
        <linearGradient
          id={surfaceSheenId}
          x1="50.5493"
          y1="0.0809972"
          x2="334.244"
          y2="441.707"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="currentColor" stopOpacity="0.12" />
          <stop offset="0.221154" stopColor="currentColor" stopOpacity="0.42" />
          <stop offset="0.528846" stopColor="currentColor" stopOpacity="0.04" />
          <stop offset="0.903846" stopColor="currentColor" stopOpacity="0.34" />
          <stop offset="0.961538" stopColor="currentColor" stopOpacity="0.48" />
        </linearGradient>
        <linearGradient
          id={selectionGlowId}
          x1="18"
          y1="24"
          x2="512"
          y2="428"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="white" stopOpacity="0.96" />
          <stop offset="0.42" stopColor="white" stopOpacity="0.5" />
          <stop offset="0.72" stopColor="white" stopOpacity="0.78" />
          <stop offset="1" stopColor="white" stopOpacity="0.95" />
        </linearGradient>
      </defs>
    </svg>
  )
}
