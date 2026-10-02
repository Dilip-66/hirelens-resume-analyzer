import type { ReportMetric } from "@/lib/reportNarrative";
import MatchRing from "@/components/report/MatchRing";
import { getMatchBand } from "@/lib/utils";
import type { AnalysisResponse } from "@/types/analysis";
import { coverageLine, quickMetrics } from "@/lib/reportNarrative";

interface MatchHeroProps {
  analysis: AnalysisResponse;
}

/**
 * The four counts worth seeing above the fold.
 *
 * <p>Separated by hairlines rather than given four cards of their own, so the
 * row reads as one strip of information.
 */
function MetricStrip({ metrics, className }: { metrics: ReportMetric[]; className?: string }) {
  return (
    <dl
      className={`grid grid-cols-2 gap-px overflow-hidden rounded-xl bg-slate-200 sm:grid-cols-4 ${
        className ?? ""
      }`}
    >
      {metrics.map((metric) => (
        <div key={metric.label} className="bg-white px-4 py-4">
          <dd
            className={`font-display text-xl font-bold leading-none ${
              metric.tone === "positive"
                ? "text-emerald-700"
                : metric.tone === "caution"
                  ? "text-amber-700"
                  : "text-foreground"
            }`}
          >
            {metric.value}
          </dd>
          <dt className="mt-1.5 text-xs leading-tight text-muted-foreground">{metric.label}</dt>
        </div>
      ))}
    </dl>
  );
}

/**
 * The answer to "how well does this resume match?" in one glance.
 *
 * <p>The score, its band and the summary the analysis produced are the only
 * things above the fold. Everything else in the report explains this number.
 */
export default function MatchHero({ analysis }: MatchHeroProps) {
  const band = getMatchBand(analysis.matchScore);
  const coverage = coverageLine(analysis);
  const metrics = quickMetrics(analysis);
  // The parser guarantees a summary today, but an empty one must not leave a
  // heading with nothing under it - in that case the counts become a full-width
  // footer of the panel instead.
  const summary = analysis.summary.trim();

  return (
    <section className="report-section overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div
        className={`grid gap-10 p-7 sm:p-10 md:gap-14 ${
          summary ? "md:grid-cols-[auto_minmax(0,1fr)]" : ""
        }`}
      >
        <div className="flex flex-col items-center text-center md:items-start md:text-left">
          <p className="mb-6 text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
            Overall Match
          </p>
          <MatchRing score={analysis.matchScore} />
          <span
            className={`mt-6 inline-flex items-center rounded-full border px-3.5 py-1 text-sm font-medium ${band.surface} ${band.text} ${band.border}`}
          >
            {band.label}
          </span>
          {coverage && (
            <p className="mt-3 max-w-[15rem] text-sm leading-relaxed text-muted-foreground">
              {coverage}
            </p>
          )}
        </div>

        {summary && (
          <div className="flex min-w-0 flex-col justify-center">
            <h2 className="font-display text-base font-semibold text-foreground">
              What the analysis found
            </h2>
            <p className="mt-3 max-w-2xl text-[15px] leading-relaxed text-foreground/80">
              {summary}
            </p>
            <MetricStrip metrics={metrics} className="mt-8" />
          </div>
        )}
      </div>

      {!summary && (
        <div className="border-t border-slate-100 px-7 py-6 sm:px-10">
          <MetricStrip metrics={metrics} />
        </div>
      )}
    </section>
  );
}