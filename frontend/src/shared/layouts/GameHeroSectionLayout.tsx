interface GameHeroSectionLayoutProps {
  content: React.ReactNode
  visual: React.ReactNode
}

export function GameHeroSectionLayout({ content, visual }: GameHeroSectionLayoutProps) {
  return (
    <section className="relative grid min-h-screen items-center overflow-hidden px-5 py-24 sm:px-8 lg:px-12 lg:py-16">
      <div className="mx-auto grid w-full max-w-[1440px] items-center gap-10 lg:grid-cols-[minmax(420px,560px)_minmax(560px,1fr)] lg:gap-16 xl:gap-20">
        <div className="flex w-full max-w-[560px] flex-col gap-5 lg:justify-self-start">
          {content}
        </div>
        <div className="flex w-full items-center justify-center lg:justify-end">
          {visual}
        </div>
      </div>
    </section>
  )
}
