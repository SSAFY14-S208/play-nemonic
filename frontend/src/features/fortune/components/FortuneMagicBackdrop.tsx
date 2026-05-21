import { cn } from '@/shared/libs'

const BASE = cn(
  'absolute inset-0 z-0 pointer-events-none',
  'bg-[linear-gradient(180deg,rgba(255,255,255,0.22),rgba(255,255,255,0)_34%),radial-gradient(ellipse_at_50%_18%,rgba(255,247,206,0.42),rgba(255,247,206,0)_36%),radial-gradient(ellipse_at_50%_82%,rgba(202,174,255,0.44),rgba(202,174,255,0)_52%),linear-gradient(135deg,#f3e8ff_0%,#eadff7_38%,#fff7df_100%)]',
)

const BEFORE = cn(
  "before:content-[''] before:absolute before:inset-0 before:pointer-events-none before:opacity-[0.38]",
  'before:[background-image:linear-gradient(115deg,rgba(255,255,255,0)_0_45%,rgba(255,255,255,0.46)_48%,rgba(255,255,255,0)_52%),linear-gradient(65deg,rgba(255,255,255,0)_0_44%,rgba(255,224,149,0.28)_49%,rgba(255,255,255,0)_54%)]',
  'before:[background-size:34rem_100%,28rem_100%]',
  'before:mix-blend-screen',
)

const AFTER = cn(
  "after:content-[''] after:absolute after:inset-0 after:pointer-events-none after:opacity-[0.42]",
  'after:[background-image:radial-gradient(circle,rgba(255,245,184,0.72)_0_1px,rgba(255,245,184,0)_1.5px),radial-gradient(circle,rgba(137,104,196,0.32)_0_1px,rgba(137,104,196,0)_1.5px)]',
  'after:[background-position:1.6rem_2.4rem,4.2rem_4.8rem]',
  'after:[background-size:7.4rem_7.4rem,9.2rem_9.2rem]',
  'max-[800px]:after:[background-size:5.8rem_5.8rem,7.2rem_7.2rem] max-[800px]:after:opacity-[0.34]',
)

export default function FortuneMagicBackdrop() {
  return <div className={cn(BASE, BEFORE, AFTER)} aria-hidden />
}
