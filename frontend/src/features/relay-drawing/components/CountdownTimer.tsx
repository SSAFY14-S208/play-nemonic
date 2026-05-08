"use client";

import { cn } from "@/shared/libs";
import { Clock } from "lucide-react";

interface CountdownTimerProps {
  formattedTime: string;
  isExpiring: boolean;
}

export default function CountdownTimer({
  formattedTime,
  isExpiring,
}: CountdownTimerProps) {
  return (
    <div
      className={cn(
        "flex items-center gap-2 rounded-[12px] border border-relay-line bg-relay-paper px-4 py-4 shadow-sm",
        isExpiring && "border-red-500 bg-red-50",
      )}
    >
      <Clock
        size={18}
        className={cn("text-relay-muted", isExpiring && "text-red-500")}
      />
      <span
        className={cn(
          "h3-b tabular-nums",
          isExpiring ? "text-red-500" : "text-relay-ink",
        )}
      >
        {formattedTime}
      </span>
    </div>
  );
}
