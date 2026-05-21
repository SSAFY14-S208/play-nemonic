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
        "relative bg-relay-paper shadow-[0_6px_16px_rgba(0,0,0,0.25),0_2px_4px_rgba(0,0,0,0.15)]",
        size === undefined &&
          "w-30 aspect-[3/2] rounded-sm lg:w-45 lg:rounded-sm",
        className,
      )}
      style={
        size !== undefined
          ? { width: size, aspectRatio: "3 / 2", borderRadius: size * 0.04 }
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
