import type { LucideIcon } from "lucide-react";
import { Award, FileSearch, TrendingDown, TrendingUp } from "lucide-react";
import type { AnalysisResponse } from "@/types/analysis";
import { countDistinct, mostFrequent } from "@/lib/utils";

interface Insight {
  label: string;
  /** null means the backend has no data for this yet - shown as a dash. */
  value: string | null;
  caption: string;
  icon: LucideIcon;
  chip: string;
}

interface ResumeInsightsProps {
  analyses: AnalysisResponse[];
}

/**
 * Four tiles summarising what the existing analyses actually contain.
 *
 * <p>Everything here is computed from analysis records with the same
 * deterministic logic that produces matchedSkills and missingSkills on the
 * backend, so the numbers can be traced back to a real report. Where the
 * backend exposes no such signal - ATS readiness is the one today - the tile
 * renders a dash and says so, rather than inventing a percentage that would
 * look authoritative and mean nothing.
 */
export default function ResumeInsights({ analyses }: ResumeInsightsProps) {
  const matchedLists = analyses.map((a) => a.matchedSkills);
  const missingLists = analyses.map((a) => a.missingSkills);

  const topSkill = mostFrequent(matchedLists);
  const topMissing = mostFrequent(missingLists);
  const distinctMissing = countDistinct(missingLists);
  const hasData = analyses.length > 0;

  const insights: Insight[] = [
    {
      label: "Top Matching Skill",
      value: topSkill,
      caption: topSkill
        ? `Appears across your analyses most often`
        : hasData
        ? "No skills matched yet"
        : "Run an analysis to see this",
      icon: TrendingUp,
      chip: "bg-emerald-100 text-emerald-700",
    },
    {
      label: "Most Requested Skill",
      value: topMissing,
      caption: topMissing
        ? `Asked for by these jobs, absent from your resume`
        : hasData
        ? "No missing skills detected"
        : "Run an analysis to see this",
      icon: Award,
      chip: "bg-blue-100 text-blue-700",
    },
    {
      label: "Skill Gap",
      value: hasData && distinctMissing > 0 ? String(distinctMissing) : null,
      caption:
        hasData && distinctMissing > 0
          ? "Distinct skills missing across your analyses"
          : hasData
          ? "No skill gaps found"
          : "Run an analysis to see this",
      icon: TrendingDown,
      chip: "bg-amber-100 text-amber-700",
    },
    {
      // Honest placeholder: the analyses table stores no ATS signal today.
      // The dedicated /ats/:id view computes its own view from the same report,
      // but there is no persisted readiness value to summarise here.
      label: "ATS Readiness",
      value: null,
      caption: "Not tracked in analysis data yet",
      icon: FileSearch,
      chip: "bg-slate-100 text-slate-500",
    },
  ];

  return (
    <section aria-labelledby="insights-heading">
      <div className="mb-3.5">
        <h2
          id="insights-heading"
          className="font-display text-base font-semibold text-foreground sm:text-lg"
        >
          Resume Insights
        </h2>
        <p className="mt-0.5 text-sm text-muted-foreground">
          Patterns across your saved analyses.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {insights.map((insight) => {
          const Icon = insight.icon;
          const empty = insight.value === null;
          return (
            <div
              key={insight.label}
              className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                    {insight.label}
                  </p>
                  <p
                    className={
                      empty
                        ? "mt-1.5 font-display text-2xl font-bold leading-none text-slate-300"
                        : "mt-1.5 truncate font-display text-2xl font-bold leading-none text-foreground"
                    }
                  >
                    {insight.value ?? "—"}
                  </p>
                </div>
                <span
                  className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${insight.chip}`}
                >
                  <Icon className="h-4 w-4" />
                </span>
              </div>
              <p className="mt-2.5 text-xs leading-relaxed text-muted-foreground">
                {insight.caption}
              </p>
            </div>
          );
        })}
      </div>
    </section>
  );
}
