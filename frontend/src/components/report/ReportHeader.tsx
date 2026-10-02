import { Link } from "react-router-dom";
import { ChevronRight, Download, Sparkles } from "lucide-react";
import Button from "@/components/ui/Button";
import { formatDate } from "@/lib/utils";
import type { AnalysisResponse } from "@/types/analysis";
import { reportCandidate, reportComparisonLine, reportRole } from "@/lib/reportNarrative";

interface ReportHeaderProps {
  analysis: AnalysisResponse;
  onAskAi: () => void;
  /**
   * Downloads the report through the browser's own print-to-PDF.
   *
   * <p>There is no server-side PDF renderer in the project, so this deliberately
   * reuses the browser rather than introducing a PDF pipeline for a UI change.
   */
  onDownload: () => void;
}

export default function ReportHeader({
  analysis,
  onAskAi,
  onDownload,
}: ReportHeaderProps) {
  const candidate = reportCandidate(analysis);
  const role = reportRole(analysis);

  return (
    <header className="report-header print:hidden">
      <nav aria-label="Breadcrumb" className="print:hidden">
        <ol className="flex items-center gap-1 text-xs text-muted-foreground">
          <li>
            <Link to="/history" className="transition-colors hover:text-foreground">
              Analyses
            </Link>
          </li>
          <ChevronRight className="h-3 w-3" aria-hidden="true" />
          <li className="font-medium text-foreground" aria-current="page">
            Resume Analysis
          </li>
        </ol>
      </nav>

      <div className="mt-5 flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
        <div className="min-w-0">
          <h1 className="sr-only">Resume Analysis — {candidate}</h1>
          <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
            Resume Analysis
          </p>
          <p className="mt-3 font-display text-3xl font-bold leading-tight tracking-tight text-foreground sm:text-4xl">
            {candidate}
          </p>
          <p className="mt-2 max-w-xl text-sm leading-relaxed text-muted-foreground">
            <span className="font-medium text-foreground/80">{role}</span>
            <span className="mx-2 text-slate-300" aria-hidden="true">
              &bull;
            </span>
            {reportComparisonLine(analysis)}
          </p>
          <p className="mt-1.5 text-xs text-muted-foreground">
            Analysed {formatDate(analysis.createdAt)}
          </p>
        </div>

        <div className="flex shrink-0 flex-col gap-2.5 print:hidden sm:flex-row">
          <Button variant="outline" onClick={onDownload} className="w-full sm:w-auto">
            <Download className="h-4 w-4" aria-hidden="true" />
            Download Report
          </Button>
          <Button onClick={onAskAi} className="w-full sm:w-auto">
            <Sparkles className="h-4 w-4" aria-hidden="true" />
            Ask HireLens AI
          </Button>
        </div>
      </div>
    </header>
  );
}
