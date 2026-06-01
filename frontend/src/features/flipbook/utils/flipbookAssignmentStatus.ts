import type {
  FlipbookAssignmentResponse,
  FlipbookFrameSubmitResponse,
} from '@/shared/types'

export function isFlipbookAssignmentSubmitted(assignment: FlipbookAssignmentResponse | null) {
  return (
    assignment?.assignmentStatus === 'SUBMITTED' ||
    assignment?.assignmentStatus === 'AUTO_SUBMITTED'
  )
}

export function isSubmittedFrameForAssignment({
  assignment,
  submittedFrame,
}: {
  assignment: FlipbookAssignmentResponse
  submittedFrame: Pick<FlipbookFrameSubmitResponse, 'round' | 'flipbookIndex' | 'frameIndex'>
}) {
  return (
    assignment.currentRound === submittedFrame.round &&
    assignment.flipbookIndex === submittedFrame.flipbookIndex &&
    assignment.frameIndex === submittedFrame.frameIndex
  )
}
