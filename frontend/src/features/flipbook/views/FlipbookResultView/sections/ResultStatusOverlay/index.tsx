import { Loader2 } from 'lucide-react'

interface ResultLoadingOverlayProps {
  resultCount: number
}

interface ResultActionMessageProps {
  message: string | null
}

export function ResultLoadingOverlay({
  resultCount,
}: ResultLoadingOverlayProps) {
  return (
    <div className="absolute left-1/2 top-1/2 z-[120] grid -translate-x-1/2 -translate-y-1/2 justify-items-center gap-3 rounded-[8px] border border-white/70 bg-white/82 px-8 py-6 text-center shadow-[0_18px_40px_rgb(120_80_80_/_16%)] backdrop-blur-md">
      <Loader2 className="size-7 animate-spin text-[#e56883]" aria-hidden />
      <div>
        <p className="body-b text-[#332222]">결과를 불러오는 중이에요</p>
        <p className="caption-m mt-1 text-[#c07182]">
          완성된 작품 {resultCount}개를 정리하고 있어요
        </p>
      </div>
    </div>
  )
}

export function ResultActionMessage({
  message,
}: ResultActionMessageProps) {
  if (!message) return null

  return (
    <p className="caption-b absolute bottom-[calc(7.75rem+env(safe-area-inset-bottom))] left-4 right-4 z-[120] rounded-full bg-white/86 px-5 py-3 text-center text-[#b84e66] shadow-[0_8px_18px_rgb(120_80_80_/_14%)] backdrop-blur-md sm:bottom-6 sm:left-1/2 sm:right-auto sm:-translate-x-1/2">
      {message}
    </p>
  )
}
