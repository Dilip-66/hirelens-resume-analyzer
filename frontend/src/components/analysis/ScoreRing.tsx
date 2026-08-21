interface ScoreRingProps {
  score: number;
  label?: string;
  size?: "sm" | "md" | "lg";
}

const sizes = {
  sm: { width: 80, radius: 32, stroke: 6, fontSize: "text-xl" },
  md: { width: 120, radius: 48, stroke: 8, fontSize: "text-3xl" },
  lg: { width: 160, radius: 64, stroke: 10, fontSize: "text-4xl" },
};

function getColor(score: number): string {
  if (score >= 80) return "#10b981";
  if (score >= 60) return "#f59e0b";
  return "#ef4444";
}

function getBgColor(score: number): string {
  if (score >= 80) return "#d1fae5";
  if (score >= 60) return "#fef3c7";
  return "#fee2e2";
}

export default function ScoreRing({ score, label, size = "md" }: ScoreRingProps) {
  const s = sizes[size];
  const circumference = 2 * Math.PI * s.radius;
  const offset = circumference - (score / 100) * circumference;
  const color = getColor(score);
  const bgColor = getBgColor(score);

  return (
    <div className="relative inline-flex items-center justify-center">
      <svg width={s.width} height={s.width} viewBox={`0 0 ${s.width} ${s.width}`}>
        <circle
          cx={s.width / 2}
          cy={s.width / 2}
          r={s.radius}
          fill="none"
          stroke={bgColor}
          strokeWidth={s.stroke}
        />
        <circle
          cx={s.width / 2}
          cy={s.width / 2}
          r={s.radius}
          fill="none"
          stroke={color}
          strokeWidth={s.stroke}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
          transform={`rotate(-90 ${s.width / 2} ${s.width / 2})`}
          style={{ transition: "stroke-dashoffset 1s ease-out" }}
        />
      </svg>
      <div className="absolute flex flex-col items-center">
        <span className={`font-display font-bold ${s.fontSize}`} style={{ color }}>
          {score}
        </span>
        {label && (
          <span className="text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
            {label}
          </span>
        )}
      </div>
    </div>
  );
}
