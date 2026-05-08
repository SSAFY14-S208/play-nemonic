import { postFileConfirm, postFilePresign } from '@/shared/apis'

interface UploadDrawingArtifactOptions {
  fileName?: string
  contentType?: string
  purpose?: string
}

export async function uploadDrawingArtifact(
  blob: Blob,
  options: UploadDrawingArtifactOptions = {},
): Promise<{ fileId: string }> {
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

  await postFileConfirm(presign.fileId)
  return { fileId: presign.fileId }
}
