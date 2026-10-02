import { Link } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import ReportSection from "@/components/report/ReportSection";
import type { AnalysisResponse } from "@/types/analysis";
import { nextSteps } from "@/lib/reportNarrative";

interface NextStepsProps {
  analysis: AnalysisResponse;
}

/**
 * The three changes worth making first.
 *
 * <p>A numbered list rather than three cards: these are a sequence to work
 * through, and the full breakdown already lives on the recommendations page.
 */
export default function NextSteps({ analysis }: NextStepsProps) {
  const steps = nextSteps(analysis);

  if (steps.length === 0) return null;

  return (
    <ReportSection
      title="Recommended next steps"
      subtitle="The issues the analysis ranked highest for this role."
    >
      <ol className="max-w-3xl">
        {steps.map((step, index) => (
          <li
            key={`${index}-${step.detail}`}
            className="flex items-start gap-5 border-b border-slate-100 py-4 first:pt-0 last:border-b-0"
          >
            <span className="font-display text-sm font-semibold tabular-nums text-slate-400">
              {String(index + 1).padStart(2, "0")}
            </span>
            <span className="text-[15px] leading-relaxed text-foreground/85">
              {step.detail}
            </span>
          </li>
        ))}
      </ol>

      <Link
        to={`/analysis/${analysis.id}/recommendations`}
        className="mt-6 inline-flex items-center gap-1.5 text-sm font-medium text-primary transition-colors hover:text-primary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
      >
        See the full recommendations
        <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
      </Link>
    </ReportSection>
  );
}
