import { AnimatePresence, motion } from 'motion/react'

import type { RelayResultSegment } from '../../../../constants'
import ResultSegmentTags from '../ResultSegmentTags'

interface SegmentTagsOverlayProps {
  segments: RelayResultSegment[]
  isVisible: boolean
  skipped: boolean
}

export default function SegmentTagsOverlay({
  segments,
  isVisible,
  skipped,
}: SegmentTagsOverlayProps) {
  return (
    <AnimatePresence>
      {isVisible && segments.length > 0 && (
        <motion.div
          key="segment-tags"
          className="pointer-events-none absolute inset-5"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={
            skipped ? { duration: 0 } : { duration: 0.4, ease: 'easeOut' }
          }
        >
          <ResultSegmentTags segments={segments} />
        </motion.div>
      )}
    </AnimatePresence>
  )
}
