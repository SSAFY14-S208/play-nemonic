import { AnimatePresence, motion } from 'motion/react'

interface SkipRevealButtonProps {
  isVisible: boolean
  onClick: () => void
}

export default function SkipRevealButton({
  isVisible,
  onClick,
}: SkipRevealButtonProps) {
  return (
    <AnimatePresence>
      {isVisible && (
        <motion.button
          key="skip"
          type="button"
          onClick={onClick}
          className="caption-b absolute right-3 top-3 cursor-pointer rounded-full border border-relay-line bg-relay-paper/90 px-3 py-1.5 text-relay-accent-strong shadow-sm backdrop-blur-sm transition-all hover:-translate-y-0.5 hover:brightness-95"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
        >
          바로보기
        </motion.button>
      )}
    </AnimatePresence>
  )
}
