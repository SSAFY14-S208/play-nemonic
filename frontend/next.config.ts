import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  reactCompiler: true,

  reactStrictMode: false,

  images: {
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
