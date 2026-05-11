import Image, { type StaticImageData } from "next/image";

import { cn } from "@/shared/libs";

interface RelayLabelCardProps {
  imageSrc: StaticImageData;
  imageAlt: string;
  // px 단위. 부스 인트로 choreography에선 200(시각 임팩트), 최종 상태에선 100.
  size?: number;
  className?: string;
}

export default function RelayLabelCard({
  imageSrc,
  imageAlt,
  size = 200,
  className,
}: RelayLabelCardProps) {
  return (
    <div
      className={cn(
        "relative bg-relay-paper shadow-[0_8px_24px_rgba(184,121,22,0.12)]",
        className,
      )}
      style={{ width: size, height: size, borderRadius: size * 0.04 }}
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
