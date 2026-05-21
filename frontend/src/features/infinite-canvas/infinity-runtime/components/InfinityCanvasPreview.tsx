/* 정적 캔버스 미리보기 — 부스 뷰 우측 장식 컴포넌트
   실제 Konva 캔버스가 아닌 Tailwind 기반 정적 일러스트 */

function UserCursorLabel({
  label,
  className,
}: {
  label: string
  className: string
}) {
  return (
    <span
      className={`absolute caption-m rounded-full bg-canvas-active px-2 py-0.5 text-canvas-ink shadow-sm ${className}`}
    >
      {label}
    </span>
  )
}

export function InfinityCanvasPreview() {
  return (
    <div className="relative aspect-[4/3] w-full max-w-[660px] overflow-hidden rounded-[28px] border-2 border-canvas-border-strong bg-canvas-paper shadow-[0_22px_54px_rgb(67_102_148_/_18%)]">
      {/* 점선 배경 패턴 */}
      <div
        className="absolute inset-0 opacity-45"
        style={{
          backgroundImage: 'radial-gradient(circle, #b3ccec 1px, transparent 1px)',
          backgroundSize: '24px 24px',
        }}
      />

      {/* 도형들 */}
      <div className="absolute inset-0 overflow-hidden">
        {/* 분홍 호 */}
        <div className="absolute left-[15%] top-[20%] h-[22%] w-[24%] rounded-t-full border-[7px] border-b-0 border-[#f4a8bc]" />

        {/* 파랑 삼각형 */}
        <div
          className="absolute left-[15%] top-[45%]"
          style={{
            width: 0,
            height: 0,
            borderLeft: '40px solid transparent',
            borderRight: '40px solid transparent',
            borderBottom: '66px solid #82b9e6',
          }}
        />

        {/* 파랑 사각형 */}
        <div className="absolute left-[17%] top-[63%] h-[18%] w-[16%] border-[5px] border-[#82b9e6]" />

        {/* 보라 꽃 */}
        <div className="absolute left-[44%] top-[44%] text-3xl">✿</div>
        <div className="absolute left-[50%] top-[52%] text-2xl text-[#c9a8e8]">✿</div>

        {/* 초록 원 (빈) */}
        <div className="absolute right-[16%] top-[20%] h-[22%] w-[16%] rounded-full border-[7px] border-[#63c086]" />

        {/* 노랑 원 (빈) */}
        <div className="absolute bottom-[14%] right-[9%] h-[25%] w-[20%] rounded-full border-[7px] border-[#ffc629]" />

        {/* 노랑 방사형 선 */}
        <div className="absolute bottom-[17%] right-[7%] text-4xl text-[#ffc629]">✳</div>

        {/* 분홍 하트 */}
        <div className="absolute bottom-[28%] left-[36%] text-2xl text-[#ff5f67]">♥</div>

        {/* 파랑 호 (아래) */}
        <div className="absolute bottom-[20%] left-[40%] h-[15%] w-[19%] rounded-b-full border-[6px] border-t-0 border-[#82b9e6]" />
      </div>

      {/* 사용자 커서 라벨 */}
      <UserCursorLabel label="토끼" className="left-[30%] top-[26%]" />
      <UserCursorLabel label="고양이" className="right-[20%] top-[36%]" />
      <UserCursorLabel label="공룡이" className="right-[12%] top-[54%]" />
    </div>
  )
}
