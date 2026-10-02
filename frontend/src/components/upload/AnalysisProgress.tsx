import { Check, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import type { AnalysisPhase } from "@/hooks/useAnalysisRunner";

const STEPS = [
  { key: "uploading", label: "Extracting content", hint: "Reading and chunking your resume" },
  { key: "analyzing", label: "Matching skills", hint: "Comparing against the job description" },
  { key: "generating", label: "Evaluating experience", hint: "Weighing strengths and gaps" },
  { key: "insights", label: "Generating insights", hint: "Writing your match report" },
] as const;

const ORDER: Record<AnalysisPhase, number> = {
  idle: -1,
  uploading: 0,
  analyzing: 2,
  done: 4,
  error: 0,
};

interface AnalysisProgressProps {
  phase: AnalysisPhase;
  className?: string;
}

/**
 * Indeterminate progress across the real pipeline phases.
 *
 * <p>Deliberately has no percentage. The analysis is a single long request, so
 * any percentage would be invented rather than measured; the only honest signal
 * is which real phase the request has reached. The bar animates at a steady,
 * restrained rate and stops as soon as the phase completes.
 */
export default function AnalysisProgress({ phase, className }: AnalysisProgressProps) {
  const activeIndex = ORDER[phase];

  return (
    <div className={cn("rounded-xl border border-slate-200 bg-white p-8 shadow-sm", className)}>
      <div className="mb-7 text-center">
        <span className="mb-4 inline-flex h-14 w-14 items-center justify-center rounded-full bg-primary/10">
          <Loader2 className="h-6 w-6 animate-spin text-primary" />
        </span>
        <h3 className="font-display text-lg font-semibold text-foreground">
          Analyzing your resume
        </h3>
        <p className="mt-1 text-sm text-muted-foreground">
          This usually takes under a minute on a local model.
        </p>
      </div>

      <div className="mx-auto max-w-sm space-y-2.5">
        {STEPS.map((step, i) => {
          const done = i < activeIndex;
          const active = i === activeIndex;
          return (
            <div key={step.key} className="flex items-start gap-3">
              <span
                className={cn(
                  "mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full transition-colors",
                  done && "bg-emerald-500 text-white",
                  active && "bg-primary text-white",
                  !done && !active && "bg-slate-100 text-slate-400"
                )}
              >
                {done ? (
                  <Check className="h-3.5 w-3.5" />
                ) : active ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                ) : (
                  <span className="h-1.5 w-1.5 rounded-full bg-current" />
                )}
              </span>
              <span className="min-w-0">
                <span
                  className={cn(
                    "block text-sm font-medium transition-colors",
                    active && "text-foreground",
                    done && "text-emerald-700",
                    !done && !active && "text-muted-foreground"
                  )}
                >
                  {step.label}
                </span>
                {active && (
                  <span className="mt-0.5 block text-xs text-muted-foreground">
                    {step.hint}
                  </span>
                )}
              </span>
            </div>
          );
        })}
      </div>

      <div
        className="mt-7 h-1 overflow-hidden rounded-full bg-slate-100"
        role="progressbar"
        aria-label="Analysis in progress"
        aria-valuetext="Analysis in progress"
      >
        <div className="h-full w-1/3 rounded-full bg-primary/70 motion-safe:animate-[loading_1.6s_ease-in-out_infinite]" />
      </div>
    </div>
  );
}
