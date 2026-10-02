import type { LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";

export interface StatDefinition {
  label: string;
  value: string;
  /** Short explanation of what the number counts, shown beneath the label. */
  hint: string;
  icon: LucideIcon;
  /** Tailwind classes for the icon chip. */
  chip: string;
}

interface StatsGridProps {
  stats: StatDefinition[];
}

/**
 * Four summary tiles.
 *
 * <p>Every value is derived from the analyses already loaded for the page - the
 * grid takes no props beyond the numbers themselves, so it cannot drift from
 * the list below it. A stat with nothing behind it renders as an em dash rather
 * than a zero, because "0%" and "no data yet" are different facts.
 */
export default function StatsGrid({ stats }: StatsGridProps) {
  return (
    <section aria-label="Summary">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {stats.map((stat) => {
          const Icon = stat.icon;
          const empty = stat.value === "—";
          return (
            <div
              key={stat.label}
              className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                    {stat.label}
                  </p>
                  <p
                    className={cn(
                      "mt-1.5 font-display text-3xl font-bold leading-none",
                      empty ? "text-slate-300" : "text-foreground"
                    )}
                  >
                    {stat.value}
                  </p>
                </div>
                <span
                  className={cn(
                    "flex h-10 w-10 shrink-0 items-center justify-center rounded-lg",
                    stat.chip
                  )}
                >
                  <Icon className="h-5 w-5" />
                </span>
              </div>
              <p className="mt-3 text-xs leading-relaxed text-muted-foreground">
                {stat.hint}
              </p>
            </div>
          );
        })}
      </div>
    </section>
  );
}
