import type { NextConfig } from "next";

const IMMUTABLE_ASSET_CACHE_CONTROL =
  "public, max-age=31536000, immutable";

const nextConfig: NextConfig = {
  allowedDevOrigins: ["127.0.0.1"],
  output: "standalone",
  reactCompiler: true,

  reactStrictMode: false,
  // Next.js의 <Image> 컴포넌트는 외부 도메인 이미지를 사용할 때 remotePatterns에 해당 호스트를 등록해야 함
  images: {
    qualities: [75, 92],
    remotePatterns: [
      {
        protocol: "https",
        hostname: "k14s208.p.ssafy.io",
        port: "9443",
        pathname: "/nemonic/**",
      },
    ],
  },
  // 3D 자산은 한 번 받으면 다시 변하지 않는다(이름이 바뀌면 새 경로). 영구 캐싱.
  async headers() {
    return [
      {
        source: "/models/:path*",
        headers: [
          { key: "Cache-Control", value: IMMUTABLE_ASSET_CACHE_CONTROL },
        ],
      },
      {
        source: "/sounds/:path*",
        headers: [
          { key: "Cache-Control", value: IMMUTABLE_ASSET_CACHE_CONTROL },
        ],
      },
      {
        source: "/videos/:path*",
        headers: [
          { key: "Cache-Control", value: IMMUTABLE_ASSET_CACHE_CONTROL },
        ],
      },
    ];
  },
};

export default nextConfig;
