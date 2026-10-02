import ReportSection from "@/components/report/ReportSection";
import { formatDate, getMatchBand } from "@/lib/utils";
import type { AnalysisResponse } from "@/types/analysis";
import { reportCandidate, reportRole, snapshotParagraph } from "@/lib/reportNarrative";

interface ApplicationSnapshotProps {
  analysis: AnalysisResponse;
}

/**
 * The report in one paragraph.
 *
 * <p>Assembled from the stored analysis rather than generated, so it can never
 * contradict the numbers above it or block the page on another model call.
 */
export default function ApplicationSnapshot({ analysis }: ApplicationSnapshotProps) {
  const band = getMatchBand(analysis.matchScore);

  return (
    <ReportSection title="Your application snapshot">
      <div className="max-w-3xl rounded-2xl border border-slate-200 bg-white p-7 shadow-sm sm:p-9">
        <p className="text-[15px] leading-relaxed text-foreground/85">
          {snapshotParagraph(analysis)}
        </p>

        <dl className="mt-7 flex flex-wrap items-center gap-x-8 gap-y-3 border-t border-slate-100 pt-5 text-sm">
          <div className="flex items-center gap-2">
            <dt className="text-muted-foreground">Candidate</dt>
            <dd className="font-medium text-foreground">{reportCandidate(analysis)}</dd>
          </div>
          <div className="flex items-center gap-2">
            <dt className="text-muted-foreground">Role</dt>
            <dd className="font-medium text-foreground">{reportRole(analysis)}</dd>
          </div>
          <div className="flex items-center gap-2">
            <dt className="text-muted-foreground">Result</dt>
            <dd className={`font-medium ${band.text}`}>
              {analysis.matchScore}% &middot; {band.label}
            </dd>
          </div>
          <div className="flex items-center gap-2">
            <dt className="text-muted-foreground">Analysed</dt>
            <dd className="font-medium text-foreground">{formatDate(analysis.createdAt)}</dd>
          </div>
        </dl>
      </div>
    </ReportSection>
  );
}
