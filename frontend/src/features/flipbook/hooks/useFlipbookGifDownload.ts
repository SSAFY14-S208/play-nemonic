'use client'

import { useCallback, useState } from 'react'

function toSafeGifFileName(fileName: string) {
  const safeFileName = fileName.trim().replace(/[\\/:*?"<>|]+/g, '-')
  const normalizedFileName = safeFileName.length > 0 ? safeFileName : 'flipbook'

  return normalizedFileName.toLowerCase().endsWith('.gif')
    ? normalizedFileName
    : `${normalizedFileName}.gif`
}

export function useFlipbookGifDownload() {
  const [isDownloadingGif, setIsDownloadingGif] = useState(false)
  const [gifDownloadError, setGifDownloadError] = useState<string | null>(null)

  const downloadGif = useCallback(
    async ({
      gifUrl,
      fileName,
    }: {
      gifUrl: string | null
      fileName: string
    }) => {
      if (!gifUrl || isDownloadingGif) return

      setIsDownloadingGif(true)
      setGifDownloadError(null)

      try {
        const response = await fetch(gifUrl)
        if (!response.ok) {
          throw new Error('GIF 파일을 불러오지 못했습니다.')
        }

        const gifBlob = await response.blob()
        const objectUrl = window.URL.createObjectURL(gifBlob)
        const downloadLink = document.createElement('a')

        downloadLink.href = objectUrl
        downloadLink.download = toSafeGifFileName(fileName)
        downloadLink.rel = 'noopener noreferrer'
        document.body.append(downloadLink)
        downloadLink.click()
        downloadLink.remove()

        window.setTimeout(() => {
          window.URL.revokeObjectURL(objectUrl)
        }, 1000)
      } catch {
        setGifDownloadError('GIF 저장에 실패했습니다. 잠시 후 다시 시도해주세요.')
      } finally {
        setIsDownloadingGif(false)
      }
    },
    [isDownloadingGif],
  )

  return {
    downloadGif,
    gifDownloadError,
    isDownloadingGif,
  }
}
