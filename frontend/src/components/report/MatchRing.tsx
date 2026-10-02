import { cn } from "@/lib/utils";

const SIZE = 176;
const STROKE = 7;
const RADIUS = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/**
 * Progress colour for each band.
 *
 * <p>Deliberately muted - these are the same thresholds as `getMatchBand`, which
 * owns the label, but the hexes are hand-picked one step down from the usual
 * Tailwind 500s so a single report never reads as a traffic light.
 */
function ringColor(score: number): string {
  if (score >= 90) return "#059669";
  if (score >= 70) return "#2563eb";
  if (score >= 40) return "#d97706";
  if (score >= 1) return "#ea580c";
  return "#94a3b8";
}

interface MatchRingProps {
  score: number;
  className?: string;
}

/**
 * The report's single focal element.
 *
 * <p>A thin ring around the score, sized to sit beside text rather than fill the
 * screen. The score is printed exactly as the backend returned it.
 */
export default function MatchRing({ score, className }: MatchRingProps) {
  const safe = Math.max(0, Math.min(100, score));
  const offset = CIRCUMFERENCE - (safe / 100) * CIRCUMFERENCE;
  const color = ringColor(safe);

  return (
    <div
      role="img"
      aria-label={`${score} percent overall match`}
      className={cn(
        "relative inline-flex shrink-0 items-center justify-center",
        className
      )}
    >
      <svg
        width={SIZE}
        height={SIZE}
        viewBox={`0 0 ${SIZE} ${SIZE}`}
        aria-hidden="true"
        className="-rotate-90"
      >
        <circle
          cx={SIZE / 2}
          cy={SIZE / 2}
          r={RADIUS}
          fill="none"
          stroke="#eef1f6"
          strokeWidth={STROKE}
        />
        <circle
          cx={SIZE / 2}
          cy={SIZE / 2}
          r={RADIUS}
          fill="none"
          stroke={color}
          strokeWidth={STROKE}
          strokeDasharray={CIRCUMFERENCE}
          strokeDashoffset={offset}
          // A rounded cap on a zero-length arc would leave a visible dot, which
          // would misrepresent a 0% score as something partially drawn.
          strokeLinecap={safe > 0 ? "round" : "butt"}
          className="transition-[stroke-dashoffset] duration-700 ease-out motion-reduce:transition-none"
        />
      </svg>

      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span
          className="font-display text-[2.75rem] font-bold leading-none tracking-tight"
          style={{ color }}
        >
          {score}
          <span className="text-xl font-semibold">%</span>
        </span>
        <span className="mt-2 text-[10px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
          Match
        </span>
      </div>
    </div>
  );
}
