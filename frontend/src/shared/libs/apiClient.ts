import ky from 'ky'

function getToken(): string | null {
  if (typeof window === 'undefined') return null
  return localStorage.getItem('token')
}

const client = ky.create({
  prefix: process.env.NEXT_PUBLIC_API_URL,
  timeout: 30_000,
  hooks: {
    beforeRequest: [
      ({ request }) => {
        const token = getToken()
        if (token) request.headers.set('Authorization', `Bearer ${token}`)
      },
    ],
    afterResponse: [
      async ({ response }) => {
        if (response.status === 401) {
          // TODO: 인증 만료 처리
        }
        return response
      },
    ],
  },
})

export const api = {
  get: <T>(path: string) => client.get(path).json<T>(),
  post: <T>(path: string, body: unknown) => client.post(path, { json: body }).json<T>(),
  put: <T>(path: string, body: unknown) => client.put(path, { json: body }).json<T>(),
  delete: <T>(path: string) => client.delete(path).json<T>(),
}
