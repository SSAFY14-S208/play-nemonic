import { HTTPError } from 'ky'
import { ApiError } from '@/shared/apis'
import type { ApiResponse } from '@/shared/types'

export function hasConfiguredNickname(nickname: string | null) {
  return Boolean(nickname?.trim())
}

export async function getFlipbookActionError(
  error: unknown,
  fallbackRequiresNickname = false,
) {
  if (error instanceof ApiError) {
    return {
      message: error.message,
      requiresNickname: isNicknameRequiredMessage(error.message),
    }
  }

  if (error instanceof HTTPError) {
    try {
      const responseText = await error.response.clone().text()
      const responseBody = JSON.parse(responseText) as Partial<ApiResponse<unknown>>
      const message = responseBody.message ?? error.message

      return {
        message,
        requiresNickname:
          isNicknameRequiredMessage(message) ||
          (fallbackRequiresNickname && error.response.status === 400),
      }
    } catch {
      return {
        message: error.message,
        requiresNickname:
          isNicknameRequiredMessage(error.message) ||
          (fallbackRequiresNickname && error.response.status === 400),
      }
    }
  }

  return {
    message: error instanceof Error ? error.message : '플립북 요청에 실패했습니다.',
    requiresNickname: false,
  }
}

function isNicknameRequiredMessage(message: string | undefined) {
  if (!message) return false
  return message.includes('닉네임') && message.includes('설정')
}
