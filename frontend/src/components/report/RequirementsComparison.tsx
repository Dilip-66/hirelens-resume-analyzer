import { Check, AlertTriangle } from "lucide-react";
import ReportSection from "@/components/report/ReportSection";
import type { AnalysisResponse } from "@/types/analysis";

const COLUMN_LIMIT = 10;

interface RequirementsComparisonProps {
  analysis: AnalysisResponse;
}

/**
 * Side-by-side view of the two skill sets behind the comparison.
 *
 * <p>Left is what the scan found in the resume; right is everything the scan
 * looked for. Only two states exist in the data - present or absent - so the
 * legend stays at two and no "partial" marker is implied.
 */
export default function RequirementsComparison({ analysis }: RequirementsComparisonProps) {
  const { matchedSkills, missingSkills } = analysis;
  const requirements = [...matchedSkills, ...missingSkills];

  if (requirements.length === 0) return null;

  const matchedShown = matchedSkills.slice(0, COLUMN_LIMIT);
  const matchedRest = matchedSkills.length - matchedShown.length;
  const requiredShown = requirements.slice(0, COLUMN_LIMIT);
  const requiredRest = requirements.length - requiredShown.length;

  return (
    <ReportSection
      title="Resume vs Job Requirements"
      subtitle="Every skill the comparison looked for, and whether your resume covers it."
    >
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="grid divide-y divide-slate-100 md:grid-cols-2 md:divide-x md:divide-y-0">
          <div className="px-6 py-7 sm:px-8">
            <div className="flex items-baseline justify-between gap-4">
              <h3 className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                Your resume
              </h3>
              <span className="text-xs font-medium text-muted-foreground">
                {matchedSkills.length} skills
              </span>
            </div>
            <ul className="mt-5 space-y-0.5">
              {matchedShown.map((skill, index) => (
                <li key={`${index}-${skill}`} className="flex items-center gap-2.5 py-1.5 text-sm">
                  <Check
                    className="h-4 w-4 shrink-0 text-emerald-600"
                    aria-hidden="true"
                  />
                  <span className="truncate text-foreground">{skill}</span>
                </li>
              ))}
              {matchedSkills.length === 0 && (
                <li className="py-1.5 text-sm text-muted-foreground">
                  No skills from this role were detected in the resume.
                </li>
              )}
              {matchedRest > 0 && (
                <li className="pt-2 text-xs text-muted-foreground">
                  and {matchedRest} more
                </li>
              )}
            </ul>
          </div>

          <div className="px-6 py-7 sm:px-8">
            <div className="flex items-baseline justify-between gap-4">
              <h3 className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                Job requires
              </h3>
              <span className="text-xs font-medium text-muted-foreground">
                {requirements.length} skills
              </span>
            </div>
            <ul className="mt-5 space-y-0.5">
              {requiredShown.map((skill, index) => {
                const isMissing = missingSkills.includes(skill);
                return (
                  <li
                    key={`${index}-${skill}`}
                    className="flex items-center gap-2.5 py-1.5 text-sm"
                  >
                    {isMissing ? (
                      <AlertTriangle
                        className="h-4 w-4 shrink-0 text-amber-600"
                        aria-hidden="true"
                      />
                    ) : (
                      <Check
                        className="h-4 w-4 shrink-0 text-emerald-600"
                        aria-hidden="true"
                      />
                    )}
                    <span
                      className={`truncate ${isMissing ? "text-foreground/70" : "text-foreground"}`}
                    >
                      {skill}
                    </span>
                    <span className="sr-only">{isMissing ? "missing" : "matched"}</span>
                  </li>
                );
              })}
              {requiredRest > 0 && (
                <li className="pt-2 text-xs text-muted-foreground">
                  and {requiredRest} more
                </li>
              )}
            </ul>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-x-6 gap-y-2 border-t border-slate-100 px-6 py-4 text-xs text-muted-foreground sm:px-8">
          <span className="inline-flex items-center gap-1.5">
            <Check className="h-3.5 w-3.5 text-emerald-600" aria-hidden="true" />
            Present in your resume
          </span>
          <span className="inline-flex items-center gap-1.5">
            <AlertTriangle className="h-3.5 w-3.5 text-amber-600" aria-hidden="true" />
            Not found in your resume
          </span>
        </div>
      </div>
    </ReportSection>
  );
}
