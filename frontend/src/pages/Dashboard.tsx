import { useMemo } from "react";
import {
  Award,
  BarChart3,
  FileText,
  ListChecks,
  TrendingUp,
} from "lucide-react";
import DashboardHeader from "@/components/dashboard/DashboardHeader";
import StatsGrid, { type StatDefinition } from "@/components/dashboard/StatsGrid";
import NewAnalysisCard from "@/components/dashboard/NewAnalysisCard";
import RecentAnalyses from "@/components/dashboard/RecentAnalyses";
import ResumeInsights from "@/components/dashboard/ResumeInsights";
import RecommendedActions from "@/components/dashboard/RecommendedActions";
import HowItWorks from "@/components/dashboard/HowItWorks";
import AiAssistanceCard from "@/components/ai-assistance/AiAssistanceCard";
import ErrorAlert from "@/components/ui/ErrorAlert";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import { useAnalyses } from "@/hooks/useAnalyses";

export default function Dashboard() {
  const { analyses, loading, error, refresh } = useAnalyses();

  const stats: StatDefinition[] = useMemo(() => {
    const empty = analyses.length === 0;

    // Resumes Analyzed counts distinct resumes that have at least one report,
    // rather than total uploads, so re-analysing the same CV cannot inflate it.
    const distinctResumes = new Set(analyses.map((a) => a.resumeId)).size;

    const avg = empty
      ? 0
      : Math.round(
          analyses.reduce((sum, a) => sum + a.matchScore, 0) / analyses.length
        );

    const strong = analyses.filter((a) => a.matchScore >= 90).length;
    const improvements = analyses.reduce(
      (sum, a) => sum + a.missingSkills.length,
      0
    );

    return [
      {
        label: "Resumes Analyzed",
        value: empty ? "—" : String(distinctResumes),
        hint: empty
          ? "Upload a resume to get started"
          : `Across ${analyses.length} ${analyses.length === 1 ? "report" : "reports"}`,
        icon: FileText,
        chip: "bg-primary/10 text-primary",
      },
      {
        label: "Average Match Score",
        value: empty ? "—" : `${avg}%`,
        hint: empty
          ? "No scores to average yet"
          : avg >= 70
          ? "On a strong footing overall"
          : avg >= 40
          ? "Some room to strengthen"
          : "Consider tailoring your resume",
        icon: TrendingUp,
        chip: "bg-emerald-100 text-emerald-700",
      },
      {
        label: "Strong Matches",
        value: empty ? "—" : String(strong),
        hint: empty
          ? "Reports scoring 90% or higher"
          : "Reports scoring 90% or higher",
        icon: Award,
        chip: "bg-blue-100 text-blue-700",
      },
      {
        label: "Improvements Suggested",
        value: empty ? "—" : String(improvements),
        hint: empty
          ? "Skills your target roles ask for"
          : "Skills your target roles ask for",
        icon: ListChecks,
        chip: "bg-amber-100 text-amber-700",
      },
    ];
  }, [analyses]);

  if (loading) {
    return (
      <div className="flex min-h-[400px] items-center justify-center">
        <LoadingSpinner message="Loading your dashboard..." />
      </div>
    );
  }

  return (
    <div className="space-y-8 pb-4">
      <DashboardHeader hasAnalyses={analyses.length > 0} />

      {error && (
        <ErrorAlert
          message={error}
          onDismiss={() => {
            void refresh();
          }}
        />
      )}

      <StatsGrid stats={stats} />

      <NewAnalysisCard />

      <RecentAnalyses analyses={analyses} />

      <AiAssistanceCard />

      {analyses.length > 0 && (
        <>
          <ResumeInsights analyses={analyses} />
          <RecommendedActions analyses={analyses} />
        </>
      )}

      <HowItWorks />

      {analyses.length === 0 && (
        <p className="flex items-center justify-center gap-2 text-xs text-muted-foreground">
          <BarChart3 className="h-3.5 w-3.5" />
          Insights appear here after your first analysis.
        </p>
      )}
    </div>
  );
}
