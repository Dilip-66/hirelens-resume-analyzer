import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { BarChart3, Target, Shield, Brain, Lightbulb } from "lucide-react";
import ReportHeader from "@/components/report/ReportHeader";
import MatchHero from "@/components/report/MatchHero";
import MatchBreakdown from "@/components/report/MatchBreakdown";
import StrengthsSection from "@/components/report/StrengthsSection";
import SkillGapsSection from "@/components/report/SkillGapsSection";
import RequirementsComparison from "@/components/report/RequirementsComparison";
import NextSteps from "@/components/report/NextSteps";
import AssistantCta from "@/components/report/AssistantCta";
import ApplicationSnapshot from "@/components/report/ApplicationSnapshot";
import ReportSkeleton from "@/components/report/ReportSkeleton";
import ReportError from "@/components/report/ReportError";
import { getAnalysis } from "@/services/analysisService";
import type { AnalysisResponse } from "@/types/analysis";
import { useAiAssistant } from "@/contexts/AiAssistantContext";
import { contextualQuestion, prioritizedQuestions } from "@/lib/aiSuggestions";

/** The existing detail views, kept reachable from the report. */
const detailViews = [
  { label: "Overview", to: "overview", icon: BarChart3 },
  { label: "Skills", to: "skills", icon: Target },
  { label: "ATS", to: "ats", icon: Shield },
  { label: "Job Match", to: "job-match", icon: Brain },
  { label: "Recommendations", to: "recommendations", icon: Lightbulb },
];

/**
 * The resume intelligence report.
 *
 * <p>Renders only what the analysis response carries: score, summary, the
 * strengths and gaps the analysis reported, and the matched/missing skill scan.
 * Experience match, project relevance and ATS readiness are absent on purpose -
 * the response has no data for them, so those sections are omitted rather than
 * filled with estimates.
 */
export default function Analysis() {
  const { id } = useParams<{ id: string }>();
  const { openChat } = useAiAssistant();
  const [analysis, setAnalysis] = useState<AnalysisResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!id) return;

    let active = true;
    setLoading(true);
    setFailed(false);

    getAnalysis(id)
      .then((data) => {
        if (active) setAnalysis(data);
      })
      .catch(() => {
        if (!active) return;
        setAnalysis(null);
        setFailed(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    // Guards against a slow response for a report the user has already left.
    return () => {
      active = false;
    };
  }, [id, attempt]);

  if (loading) return <ReportSkeleton />;
  if (failed || !analysis) return <ReportError onRetry={() => setAttempt((n) => n + 1)} />;

  // Grounding for the assistant: it answers from this resume, this job
  // description and this analysis, so every entry point passes the same context.
  const assistantContext = {
    analysisId: analysis.id,
    candidateName: analysis.candidateName,
    jobTitle: analysis.jobTitle,
  };
  const assistantQuestions = prioritizedQuestions(analysis);
  const assistantQuestion = assistantQuestions[0] ?? contextualQuestion(analysis.matchScore);

  return (
    <div data-report-surface className="space-y-14 sm:space-y-16">
      <div>
        <ReportHeader
          analysis={analysis}
          onAskAi={() => openChat(contextualQuestion(analysis.matchScore), assistantContext)}
          onDownload={() => window.print()}
        />

        <nav
          aria-label="Detailed views"
          className="report-detail-nav print:hidden mt-6 flex flex-wrap items-center gap-1 border-t border-slate-200 pt-3"
        >
          <span className="mr-2 hidden text-xs font-medium uppercase tracking-wider text-muted-foreground sm:inline">
            Detail
          </span>
          {detailViews.map((view) => {
            const Icon = view.icon;
            return (
              <Link
                key={view.to}
                to={`/analysis/${id}/${view.to}`}
                className="inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-white hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <Icon className="h-3.5 w-3.5" aria-hidden="true" />
                {view.label}
              </Link>
            );
          })}
        </nav>
      </div>

      <MatchHero analysis={analysis} />

      <MatchBreakdown analysis={analysis} />

      <StrengthsSection analysis={analysis} />

      <SkillGapsSection analysis={analysis} />

      <RequirementsComparison analysis={analysis} />

      <NextSteps analysis={analysis} />

      <AssistantCta onAsk={() => openChat(assistantQuestion, assistantContext)} />

      <ApplicationSnapshot analysis={analysis} />
    </div>
  );
}
