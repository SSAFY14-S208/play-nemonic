import Image from 'next/image'

const RESULT_ACTION_BUTTONS_IMAGE_SRC = '/images/flipbook-result/result-action-buttons.png'
const RESULT_ACTION_BUTTONS_IMAGE_WIDTH = 733
const RESULT_ACTION_BUTTONS_IMAGE_HEIGHT = 70
const RESULT_ACTION_BUTTONS_ASPECT_RATIO = `${RESULT_ACTION_BUTTONS_IMAGE_WIDTH} / ${RESULT_ACTION_BUTTONS_IMAGE_HEIGHT}`

export interface ResultActionButton {
  id: string
  label: string
  left: string
  width: string
  disabled: boolean
  onClick: () => void
}

interface ResultActionButtonsProps {
  actionButtons: ResultActionButton[]
}

export function ResultActionButtons({
  actionButtons,
}: ResultActionButtonsProps) {
  return (
    <div className="absolute left-1/2 top-[calc(4.75rem+env(safe-area-inset-top))] z-[120] w-[min(733px,calc(100vw-2rem))] -translate-x-1/2 sm:left-auto sm:right-6 sm:top-6 sm:translate-x-0">
      <div
        className="relative w-full"
        style={{ aspectRatio: RESULT_ACTION_BUTTONS_ASPECT_RATIO }}
      >
        <Image
          src={RESULT_ACTION_BUTTONS_IMAGE_SRC}
          alt=""
          fill
          priority
          draggable={false}
          unoptimized
          sizes={`(max-width: 640px) calc(100vw - 2rem), ${RESULT_ACTION_BUTTONS_IMAGE_WIDTH}px`}
          className="select-none object-contain"
          aria-hidden
        />
        {actionButtons.map((actionButton) => (
          <button
            key={actionButton.id}
            type="button"
            aria-label={actionButton.label}
            title={actionButton.label}
            onClick={actionButton.onClick}
            disabled={actionButton.disabled}
            className="absolute top-0 h-full rounded-full text-transparent transition hover:bg-white/10 active:bg-black/5 disabled:cursor-not-allowed disabled:bg-white/45 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white focus-visible:ring-offset-2 focus-visible:ring-offset-[#ff3f7e]"
            style={{ left: actionButton.left, width: actionButton.width }}
          >
            <span className="sr-only">{actionButton.label}</span>
          </button>
        ))}
      </div>
    </div>
  )
}
