'use client'

import { useCallback, useMemo, useState } from 'react'

type NemonicImagePrintStatus = 'idle' | 'preparing' | 'error'

interface UseNemonicImagePrintParams {
  imageUrl: string | null
  isImageLoading: boolean
  onPrintBlocked?: (message: string) => void
}

const PRINT_READY_DELAY_FRAMES = 2

function waitForNextPaint(): Promise<void> {
  return new Promise((resolve) => {
    let frameCount = 0

    const waitFrame = () => {
      frameCount += 1
      if (frameCount >= PRINT_READY_DELAY_FRAMES) {
        resolve()
        return
      }

      window.requestAnimationFrame(waitFrame)
    }

    window.requestAnimationFrame(waitFrame)
  })
}

function preloadImage(imageUrl: string): Promise<void> {
  return new Promise((resolve, reject) => {
    if (typeof window === 'undefined') {
      reject(new Error('window is unavailable'))
      return
    }

    const imageElement = new window.Image()
    imageElement.onload = () => resolve()
    imageElement.onerror = () => reject(new Error('image load failed'))
    imageElement.src = imageUrl

    if (imageElement.complete && imageElement.naturalWidth > 0) {
      resolve()
    }
  })
}

export function useNemonicImagePrint({
  imageUrl,
  isImageLoading,
  onPrintBlocked,
}: UseNemonicImagePrintParams) {
  const [printStatus, setPrintStatus] =
    useState<NemonicImagePrintStatus>('idle')

  const isPreparingPrint = printStatus === 'preparing'
  const isPrintDisabled = isPreparingPrint || isImageLoading || !imageUrl

  const printMessage = useMemo(() => {
    if (isPreparingPrint) return '인쇄창을 준비하고 있어요.'
    if (isImageLoading) return '상세 이미지를 불러오는 중이에요.'
    if (!imageUrl) return '출력할 이미지가 없어요.'
    if (printStatus === 'error') return '출력 이미지를 불러오지 못했어요.'
    return null
  }, [imageUrl, isImageLoading, isPreparingPrint, printStatus])

  const printImage = useCallback(
    async (overrideImageUrl?: string) => {
      if (typeof window === 'undefined') return

      const selectedImageUrl = overrideImageUrl ?? imageUrl

      if (!overrideImageUrl && isImageLoading) {
        onPrintBlocked?.('상세 이미지를 불러오는 중이에요.')
        return
      }

      if (!selectedImageUrl) {
        onPrintBlocked?.('출력할 이미지가 없어요.')
        return
      }

      setPrintStatus('preparing')

      try {
        await preloadImage(selectedImageUrl)
        await waitForNextPaint()
        window.print()
        setPrintStatus('idle')
      } catch {
        setPrintStatus('error')
        onPrintBlocked?.('출력 이미지를 불러오지 못했어요.')
      }
    },
    [imageUrl, isImageLoading, onPrintBlocked],
  )

  return {
    isPreparingPrint,
    isPrintDisabled,
    printImage,
    printMessage,
  }
}
