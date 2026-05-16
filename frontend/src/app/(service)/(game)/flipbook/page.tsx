import { FlipbookPage } from '@/features/flipbook'
import { WorldHomeLink } from '@/shared/components'

export default function Page() {
  return (
    <>
      <div className="hidden sm:block">
        <WorldHomeLink />
      </div>
      <FlipbookPage />
    </>
  )
}
