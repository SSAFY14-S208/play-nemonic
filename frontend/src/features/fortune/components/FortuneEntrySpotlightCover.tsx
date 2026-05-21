import { cn } from "@/shared/libs";

interface FortuneEntrySpotlightCoverProps {
  isLit: boolean;
}

const BASE =
  "absolute inset-0 z-[8] pointer-events-none bg-[#090211] motion-reduce:hidden";

// is-lit: clear the solid bg-color so the radial gradient's transparent center reveals the character beneath.
const LIT = cn(
  "animate-fortune-entry-blackout motion-reduce:animate-none",
  "bg-transparent",
  "bg-[radial-gradient(circle_4.8rem_at_52%_33%,rgba(9,2,17,0)_0_62%,rgba(9,2,17,0.28)_74%,rgba(9,2,17,0.98)_100%,#090211_100%)]",
  "max-[800px]:bg-[radial-gradient(circle_3.5rem_at_51%_34%,rgba(9,2,17,0)_0_62%,rgba(9,2,17,0.28)_74%,rgba(9,2,17,0.98)_100%,#090211_100%)]",
);

export default function FortuneEntrySpotlightCover({
  isLit,
}: FortuneEntrySpotlightCoverProps) {
  return <div aria-hidden className={cn(BASE, isLit && LIT)} />;
}
