import { Link } from "react-router-dom";
import { BarChart3, Sparkles } from "lucide-react";
import AnalysisCard from "@/components/dashboard/AnalysisCard";
import type { AnalysisResponse } from "@/types/analysis";

interface RecentAnalysesProps {
  analyses: AnalysisResponse[];
}

const PAGE_SIZE = 5;

export default function RecentAnalyses({ analyses }: RecentAnalysesProps) {
  const visible = analyses.slice(0, PAGE_SIZE);
  const remaining = analyses.length - visible.length;

  return (
    <section aria-labelledby="recent-analyses-heading">
      <div className="mb-3.5 flex items-end justify-between gap-4">
        <div>
          <h2
            id="recent-analyses-heading"
            className="font-display text-base font-semibold text-foreground sm:text-lg"
          >
            Recent Analyses
          </h2>
          <p className="mt-0.5 text-sm text-muted-foreground">
            Review your latest resume-to-job matches.
          </p>
        </div>
        {analyses.length > PAGE_SIZE && (
          <Link
            to="/history"
            className="shrink-0 text-sm font-medium text-primary hover:underline"
          >
            View all {analyses.length}
          </Link>
        )}
      </div>

      {visible.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-300 bg-white p-10 text-center shadow-sm">
          <span className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-slate-100">
            <BarChart3 className="h-6 w-6 text-slate-400" />
          </span>
          <h3 className="font-display text-base font-semibold text-foreground">
            No analyses yet
          </h3>
          <p className="mx-auto mt-1.5 max-w-sm text-sm text-muted-foreground">
            Upload your first resume to see your compatibility insights here.
          </p>
          <Link
            to="/upload"
            className="mt-5 inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground shadow-sm transition-colors hover:bg-primary/90"
          >
            <Sparkles className="h-4 w-4" />
            Start Your First Analysis
          </Link>
        </div>
      ) : (
        <div className="space-y-3">
          {visible.map((analysis) => (
            <AnalysisCard key={analysis.id} analysis={analysis} />
          ))}
        </div>
      )}

      {remaining > 0 && (
        <p className="mt-3 text-center text-xs text-muted-foreground">
          and {remaining} more in{" "}
          <Link to="/history" className="text-primary hover:underline">
            Analysis Report
          </Link>
        </p>
      )}
    </section>
  );
}
