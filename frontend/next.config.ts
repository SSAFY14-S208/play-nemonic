import type { NextConfig } from "next";

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
};

export default nextConfig;
