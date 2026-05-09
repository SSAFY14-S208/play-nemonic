import { NextRequest, NextResponse } from 'next/server'

const ALLOWED_IMAGE_PROTOCOLS = new Set(['http:', 'https:'])
const DEFAULT_ALLOWED_IMAGE_HOSTS = ['localhost', '127.0.0.1', 'k14s208.p.ssafy.io']

export const dynamic = 'force-dynamic'
export const runtime = 'nodejs'

function collectAllowedImageHosts() {
  const allowedHosts = new Set(DEFAULT_ALLOWED_IMAGE_HOSTS)
  const configuredHosts = [
    process.env.NEXT_PUBLIC_API_URL,
    process.env.NEXT_PUBLIC_WEBSOCKET_URL?.replace(/^ws/, 'http'),
    process.env.NEXT_PUBLIC_IMAGE_PROXY_ALLOWED_HOSTS,
  ]

  configuredHosts.forEach((configuredHost) => {
    if (!configuredHost) return

    configuredHost.split(',').forEach((hostCandidate) => {
      const trimmedHostCandidate = hostCandidate.trim()
      if (!trimmedHostCandidate) return

      try {
        allowedHosts.add(new URL(trimmedHostCandidate).hostname)
      } catch {
        allowedHosts.add(trimmedHostCandidate)
      }
    })
  })

  return allowedHosts
}

export async function GET(request: NextRequest) {
  const targetImageUrl = request.nextUrl.searchParams.get('url')

  if (!targetImageUrl) {
    return NextResponse.json({ message: '이미지 URL이 필요합니다.' }, { status: 400 })
  }

  let parsedImageUrl: URL

  try {
    parsedImageUrl = new URL(targetImageUrl)
  } catch {
    return NextResponse.json({ message: '이미지 URL 형식이 올바르지 않습니다.' }, { status: 400 })
  }

  if (!ALLOWED_IMAGE_PROTOCOLS.has(parsedImageUrl.protocol)) {
    return NextResponse.json({ message: '지원하지 않는 이미지 URL입니다.' }, { status: 400 })
  }

  if (!collectAllowedImageHosts().has(parsedImageUrl.hostname)) {
    return NextResponse.json({ message: '허용되지 않은 이미지 호스트입니다.' }, { status: 400 })
  }

  try {
    const imageResponse = await fetch(parsedImageUrl, {
      cache: 'no-store',
      headers: {
        Accept: 'image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8',
      },
    })

    if (!imageResponse.ok) {
      return NextResponse.json(
        { message: `이미지를 가져오지 못했습니다. (${imageResponse.status})` },
        { status: imageResponse.status },
      )
    }

    const contentType = imageResponse.headers.get('content-type') ?? 'application/octet-stream'

    return new NextResponse(imageResponse.body, {
      status: 200,
      headers: {
        'Cache-Control': 'public, max-age=300',
        'Content-Type': contentType,
      },
    })
  } catch {
    return NextResponse.json({ message: '이미지 서버에 연결하지 못했습니다.' }, { status: 502 })
  }
}
