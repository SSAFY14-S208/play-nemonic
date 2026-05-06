import ky from 'ky'

import { runtime } from '@/shared/config'
import { useUserStore } from '@/shared/stores'

type Query = Record<string, string | number | boolean>

const client = ky.create({
  prefix: `${runtime.apiUrl}/api/v1`,
  timeout: 30_000,
  hooks: {
    beforeRequest: [
      ({ request }) => {
        const userUuid = useUserStore.getState().userUuid
        if (userUuid) {
          request.headers.set('Anonymous-User-UUID', userUuid)
        }
      },
    ],
  },
})

export const api = {
  get: <T>(path: string, searchParams?: Query) =>
    client.get(path, searchParams ? { searchParams } : undefined).json<T>(),
  post: <T>(path: string, body?: unknown) =>
    client.post(path, body !== undefined ? { json: body } : undefined).json<T>(),
  put: <T>(path: string, body?: unknown) =>
    client.put(path, body !== undefined ? { json: body } : undefined).json<T>(),
  patch: <T>(path: string, body?: unknown) =>
    client.patch(path, body !== undefined ? { json: body } : undefined).json<T>(),
  delete: <T>(path: string, searchParams?: Query) =>
    client.delete(path, searchParams ? { searchParams } : undefined).json<T>(),
  postForm: <T>(path: string, formData: FormData, searchParams?: Query) =>
    client
      .post(path, {
        body: formData,
        ...(searchParams && { searchParams }),
      })
      .json<T>(),
}
