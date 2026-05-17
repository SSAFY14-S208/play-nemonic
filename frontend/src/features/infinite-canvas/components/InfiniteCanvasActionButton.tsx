import Image from 'next/image'

interface InfiniteCanvasActionButtonProps {
  imageSrc: string
  label: string
  disabled?: boolean
  onClick: () => void
}

export default function InfiniteCanvasActionButton({
  imageSrc,
  label,
  disabled = false,
  onClick,
}: InfiniteCanvasActionButtonProps) {
  return (
    <button
      type="button"
      className="infinite-canvas-action-button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
    >
      <Image
        src={imageSrc}
        alt=""
        aria-hidden
        width={971}
        height={422}
        draggable={false}
        className="infinite-canvas-action-button__image"
      />
    </button>
  )
}
