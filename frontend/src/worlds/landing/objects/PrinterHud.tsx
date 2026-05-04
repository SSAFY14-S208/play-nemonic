import { Html } from "@react-three/drei";
import Link from "next/link";
import { cn } from "@/shared/libs";

interface PrinterHudProps {
  position: [number, number, number];
}

export default function PrinterHud({ position }: PrinterHudProps) {
  return (
    <Html position={position} center>
      <Link
        href="/hub"
        className={cn(
          "body-b text-fg-inverse bg-primary-1",
          "rounded-[var(--radius-md)] px-4 py-2",
          "shadow-lg whitespace-nowrap",
          "hover:opacity-90 transition-opacity",
        )}
      >
        허브로 이동
      </Link>
    </Html>
  );
}
