import { postFileConfirm, postFilePresign, postPhoneDrawing } from '@/shared/apis'
import type { FilePurpose, PhoneDrawingSaveResponse } from '@/shared/types'

interface UploadDrawingArtifactOptions {
  fileName?: string
  contentType?: string
  purpose?: FilePurpose
  meta?: Record<string, unknown> | null
}

export async function uploadDrawingArtifact(
  blob: Blob,
  options: UploadDrawingArtifactOptions = {},
): Promise<PhoneDrawingSaveResponse> {
  const fileName = options.fileName ?? `phone-drawing-${Date.now()}.png`
  const contentType = options.contentType ?? 'image/png'
  const purpose = options.purpose ?? 'PHONE'

  const presign = await postFilePresign({
    fileName,
    contentType,
    purpose,
    byteSize: blob.size,
  })

  // presigned URL은 절대 URL이고 추가 헤더가 들어가면 서명 검증이 깨진다.
  // apiClient(ky)의 prefix와 자동 헤더를 우회하기 위해 fetch로 직접 PUT한다.
  const putResponse = await fetch(presign.presignedUrl, {
    method: 'PUT',
    body: blob,
    headers: { 'Content-Type': contentType },
  })
  if (!putResponse.ok) {
    throw new Error(`upload-failed-${putResponse.status}`)
  }

  // 파일 업로드 완료 신호. 이 호출만으론 갤러리에 들어가지 않고
  // 별도 POST /gallery/drawings 호출로 artifact + phone_artifact + gallery가 한 번에 생성된다.
  await postFileConfirm(presign.fileId)

  return postPhoneDrawing({
    imageFileId: presign.fileId,
    meta: options.meta ?? null,
  })
}
