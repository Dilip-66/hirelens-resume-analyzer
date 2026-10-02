import { Link } from "react-router-dom";
import { ArrowRight, Clock, FileText } from "lucide-react";
import type { AnalysisResponse } from "@/types/analysis";
import { cn, formatDateTime, getMatchBand } from "@/lib/utils";

/** Two-letter monogram so rows are recognisable at a glance. */
function initials(name: string): string {
  const words = name.replace(/[^A-Za-z\s.-]/g, "").split(/[\s.-]+/).filter(Boolean);
  if (words.length === 0) return "?";
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

interface AnalysisCardProps {
  analysis: AnalysisResponse;
}

/**
 * One row in Recent Analyses.
 *
 * <p>The score is rendered exactly as the backend returned it; the band label
 * beside it is a description only and never feeds back into the number.
 */
export default function AnalysisCard({ analysis }: AnalysisCardProps) {
  const band = getMatchBand(analysis.matchScore);
  const name =
    analysis.candidateName || analysis.resumeFileName || "Unnamed candidate";
  const role = analysis.jobTitle || "Target role not set";

  return (
    <Link
      to={`/analysis/${analysis.id}`}
      className="group block rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition-all hover:border-primary/30 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 sm:p-5"
    >
      <div className="flex items-center gap-3 sm:gap-4">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 font-display text-sm font-bold text-primary transition-colors group-hover:bg-primary/15">
          {initials(name)}
        </span>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <h3 className="truncate font-display text-sm font-semibold text-foreground">
              {name}
            </h3>
            <span className="text-xs text-slate-300" aria-hidden="true">
              ·
            </span>
            <span className="truncate text-xs font-medium text-muted-foreground">
              {role}
            </span>
            {analysis.jobCompany && (
              <>
                <span className="text-xs text-slate-300" aria-hidden="true">
                  ·
                </span>
                <span className="truncate text-xs text-muted-foreground">
                  {analysis.jobCompany}
                </span>
              </>
            )}
          </div>

          <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
            <span
              className={cn(
                "inline-flex items-center rounded-full border px-2 py-0.5 font-medium",
                band.surface,
                band.text,
                band.border
              )}
            >
              {band.label}
            </span>
            <span className="flex items-center gap-1">
              <Clock className="h-3 w-3" />
              {formatDateTime(analysis.createdAt)}
            </span>
            <span className="flex items-center gap-1">
              <FileText className="h-3 w-3" />
              {analysis.matchedSkills.length} matched
              {analysis.missingSkills.length > 0
                ? ` · ${analysis.missingSkills.length} missing`
                : ""}
            </span>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-3">
          <span
            className={cn(
              "inline-flex items-center rounded-full border px-2.5 py-1 text-sm font-bold",
              band.surface,
              band.text,
              band.border
            )}
          >
            {analysis.matchScore}%
          </span>
          <span className="hidden items-center gap-1 text-xs font-medium text-primary opacity-0 transition-opacity group-hover:opacity-100 sm:inline-flex">
            View
            <ArrowRight className="h-3.5 w-3.5" />
          </span>
        </div>
      </div>
    </Link>
  );
}
