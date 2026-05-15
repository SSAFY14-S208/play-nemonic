import Image, { type StaticImageData } from "next/image";

import { cn } from "@/shared/libs";

interface RelayLabelCardProps {
  imageSrc: StaticImageData;
  imageAlt: string;
  // px 단위. 인트로 choreography처럼 애니메이션 수치 계산이 필요한 곳에서만 명시.
  // 생략하면 Tailwind 반응형 클래스(100px / lg:150px)로 렌더링.
  size?: number;
  className?: string;
}

export default function RelayLabelCard({
  imageSrc,
  imageAlt,
  size,
  className,
}: RelayLabelCardProps) {
  return (
    <div
      className={cn(
        "relative bg-relay-paper shadow-[0_8px_24px_rgba(184,121,22,0.12)]",
        size === undefined &&
          "size-25 rounded-sm lg:size-37.5 lg:rounded-md",
        className,
      )}
      style={
        size !== undefined
          ? { width: size, height: size, borderRadius: size * 0.04 }
          : undefined
      }
    >
      <div className="relative h-full w-full">
        <Image
          src={imageSrc}
          alt={imageAlt}
          priority
          fill
          style={{ objectFit: "contain" }}
        />
      </div>
    </div>
  );
}
