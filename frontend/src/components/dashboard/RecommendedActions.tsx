import { Link } from "react-router-dom";
import { ArrowRight, Lightbulb, ListChecks, Target } from "lucide-react";
import type { AnalysisResponse } from "@/types/analysis";
import { getMatchBand } from "@/lib/utils";

interface RecommendedActionsProps {
  analyses: AnalysisResponse[];
}

/**
 * Actionable next steps for the most recent analysis.
 *
 * <p>Derived from the same `gaps` and `missingSkills` the backend produced for
 * that report - the same inputs the dedicated Recommendations page uses - so
 * nothing here is generic filler presented as if it were personalised. When a
 * report has no gaps the card says so instead of inventing three items to fill
 * the space.
 */
export default function RecommendedActions({ analyses }: RecommendedActionsProps) {
  const latest = analyses[0];

  if (!latest) {
    return null;
  }

  const band = getMatchBand(latest.matchScore);
  const actions: string[] = [];

  // Personalised, in priority order, capped so the card stays scannable.
  if (latest.gaps.length > 0) {
    actions.push(latest.gaps[0]);
  }
  if (latest.missingSkills.length > 0) {
    const skills = latest.missingSkills.slice(0, 3).join(", ");
    actions.push(
      latest.missingSkills.length <= 3
        ? `Add evidence for the skills these roles ask for: ${skills}.`
        : `Address the ${latest.missingSkills.length} skills these roles ask for, starting with ${latest.missingSkills.slice(0, 3).join(", ")}.`
    );
  }
  if (latest.matchedSkills.length < 5) {
    actions.push(
      "Broaden the skill coverage on your resume - few required skills were detected in it."
    );
  }
  actions.push(
    "Keep formatting simple and ATS-friendly, and lead each role with measurable outcomes."
  );

  return (
    <section aria-labelledby="actions-heading">
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-1 border-b border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div>
            <h2
              id="actions-heading"
              className="font-display text-base font-semibold text-foreground sm:text-lg"
            >
              Recommended Actions
            </h2>
            <p className="mt-0.5 text-sm text-muted-foreground">
              Improve your resume based on your latest analysis.
            </p>
          </div>
          <Link
            to={`/analysis/${latest.id}`}
            className="inline-flex shrink-0 items-center gap-1 text-sm font-medium text-primary hover:underline"
          >
            Full report
            <ArrowRight className="h-4 w-4" />
          </Link>
        </div>

        <div className="p-5 sm:p-6">
          <div className="mb-4 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <span className="font-medium text-foreground">
              {latest.candidateName || "Your latest report"}
            </span>
            <span aria-hidden="true">·</span>
            <span>{latest.jobTitle || "Target role"}</span>
            <span aria-hidden="true">·</span>
            <span className={`font-medium ${band.text}`}>{band.label}</span>
          </div>

          <ul className="space-y-2.5">
            {actions.slice(0, 4).map((action, i) => (
              <li
                key={i}
                className="flex items-start gap-3 rounded-lg border border-slate-100 bg-slate-50/60 p-3.5"
              >
                <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-white text-muted-foreground shadow-sm">
                  {i === 0 ? (
                    <Target className="h-3.5 w-3.5" />
                  ) : (
                    <ListChecks className="h-3.5 w-3.5" />
                  )}
                </span>
                <span className="text-sm leading-relaxed text-foreground">
                  {action}
                </span>
              </li>
            ))}
          </ul>

          {latest.gaps.length === 0 && latest.missingSkills.length === 0 && (
            <p className="mt-3 flex items-start gap-2 rounded-lg border border-emerald-100 bg-emerald-50 p-3.5 text-sm text-emerald-800">
              <Lightbulb className="mt-0.5 h-4 w-4 shrink-0" />
              No gaps were detected in this report. Try another job description to
              test the resume against a different role.
            </p>
          )}
        </div>
      </div>
    </section>
  );
}
