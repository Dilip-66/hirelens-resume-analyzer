import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import {
  ArrowLeft,
  Download,
  RotateCcw,
  BarChart3,
  Target,
  Brain,
  Lightbulb,
  FileSearch,
  Shield,
  CheckCircle,
  AlertTriangle,
  Clock,
} from "lucide-react";
import ScoreRing from "@/components/analysis/ScoreRing";
import SkillList from "@/components/analysis/SkillList";
import Button from "@/components/ui/Button";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import ErrorAlert from "@/components/ui/ErrorAlert";
import { getAnalysis } from "@/services/analysisService";
import type { AnalysisResponse } from "@/types/analysis";
import { formatDate, getScoreColor } from "@/lib/utils";

const tabs = [
  { label: "Overview", to: "overview", icon: BarChart3 },
  { label: "Skills", to: "skills", icon: Target },
  { label: "ATS", to: "ats", icon: Shield },
  { label: "Job Match", to: "job-match", icon: Brain },
  { label: "Recommendations", to: "recommendations", icon: Lightbulb },
];

export default function Analysis() {
  const { id } = useParams<{ id: string }>();
  const [analysis, setAnalysis] = useState<AnalysisResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    getAnalysis(id)
      .then(setAnalysis)
      .catch((err) =>
        setError(err instanceof Error ? err.message : "Failed to load analysis")
      )
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <LoadingSpinner message="Loading analysis..." />
      </div>
    );
  }

  if (error || !analysis) {
    return (
      <div className="max-w-2xl mx-auto py-12">
        <ErrorAlert message={error || "Analysis not found."} />
        <div className="mt-4">
          <Link to="/upload">
            <Button variant="outline">
              <ArrowLeft className="h-4 w-4" />
              Back to Upload
            </Button>
          </Link>
        </div>
      </div>
    );
  }

  const atsScore = Math.min(100, Math.round(analysis.matchScore * 1.05 + 5));
  const skillsMatch = analysis.matchedSkills.length > 0
    ? Math.round(
        (analysis.matchedSkills.length /
          (analysis.matchedSkills.length + analysis.missingSkills.length)) *
          100
      )
    : 0;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <Link
            to="/history"
            className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground mb-2 transition-colors"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to Analyses
          </Link>
          <h1 className="font-display text-2xl sm:text-3xl font-bold text-foreground">
            Resume Analysis
          </h1>
          <div className="flex items-center gap-3 mt-1 text-sm text-muted-foreground">
            <span className="flex items-center gap-1">
              <Clock className="h-3.5 w-3.5" />
              {formatDate(analysis.createdAt)}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Link to="/upload">
            <Button variant="outline" size="sm">
              <RotateCcw className="h-4 w-4" />
              Analyze Again
            </Button>
          </Link>
        </div>
      </div>

      <div className="flex overflow-x-auto gap-1 border-b bg-white rounded-t-xl p-1">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          return (
            <Link
              key={tab.to}
              to={`/analysis/${id}/${tab.to}`}
              className="flex items-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium text-muted-foreground hover:text-foreground hover:bg-slate-50 transition-colors whitespace-nowrap"
            >
              <Icon className="h-4 w-4" />
              {tab.label}
            </Link>
          );
        })}
      </div>

      <div className="rounded-xl border bg-white p-6 shadow-sm">
        <div className="flex flex-col md:flex-row items-start gap-8">
          <ScoreRing score={analysis.matchScore} label="match" size="lg" />
          <div className="flex-1">
            <h2 className="font-display text-xl font-semibold text-foreground mb-3">
              AI Resume Summary
            </h2>
            <p className="text-muted-foreground leading-relaxed">
              {analysis.summary}
            </p>
            <div className="mt-6 grid grid-cols-3 gap-4">
              <div className="rounded-lg bg-slate-50 border p-3 text-center">
                <p className={`font-display text-2xl font-bold ${getScoreColor(analysis.matchScore)}`}>
                  {analysis.matchScore}
                </p>
                <p className="text-xs text-muted-foreground mt-0.5">Match Score</p>
              </div>
              <div className="rounded-lg bg-slate-50 border p-3 text-center">
                <p className="font-display text-2xl font-bold text-emerald-600">
                  {analysis.matchedSkills.length}
                </p>
                <p className="text-xs text-muted-foreground mt-0.5">Matched Skills</p>
              </div>
              <div className="rounded-lg bg-slate-50 border p-3 text-center">
                <p className="font-display text-2xl font-bold text-red-500">
                  {analysis.missingSkills.length}
                </p>
                <p className="text-xs text-muted-foreground mt-0.5">Missing Skills</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="grid md:grid-cols-2 gap-6">
        <div className="rounded-xl border bg-white p-6 shadow-sm">
          <div className="flex items-center gap-2 mb-4">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-50">
              <CheckCircle className="h-4 w-4 text-emerald-600" />
            </div>
            <h3 className="font-display text-base font-semibold text-foreground">
              Strengths
            </h3>
            <span className="ml-auto text-xs font-medium bg-emerald-50 text-emerald-700 px-2 py-0.5 rounded-full">
              {analysis.strengths.length}
            </span>
          </div>
          <ul className="space-y-2.5">
            {analysis.strengths.length === 0 ? (
              <li className="text-sm text-muted-foreground italic">No strengths identified</li>
            ) : (
              analysis.strengths.map((s, i) => (
                <li key={i} className="flex items-start gap-2.5 text-sm">
                  <span className="mt-0.5 h-5 w-5 rounded-full bg-emerald-50 flex items-center justify-center shrink-0">
                    <span className="text-emerald-600 text-xs font-bold">+</span>
                  </span>
                  <span className="text-foreground">{s}</span>
                </li>
              ))
            )}
          </ul>
        </div>

        <div className="rounded-xl border bg-white p-6 shadow-sm">
          <div className="flex items-center gap-2 mb-4">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-red-50">
              <AlertTriangle className="h-4 w-4 text-red-500" />
            </div>
            <h3 className="font-display text-base font-semibold text-foreground">
              Gaps
            </h3>
            <span className="ml-auto text-xs font-medium bg-red-50 text-red-600 px-2 py-0.5 rounded-full">
              {analysis.gaps.length}
            </span>
          </div>
          <ul className="space-y-2.5">
            {analysis.gaps.length === 0 ? (
              <li className="text-sm text-muted-foreground italic">No gaps identified</li>
            ) : (
              analysis.gaps.map((g, i) => (
                <li key={i} className="flex items-start gap-2.5 text-sm">
                  <span className="mt-0.5 h-5 w-5 rounded-full bg-red-50 flex items-center justify-center shrink-0">
                    <span className="text-red-500 text-xs font-bold">-</span>
                  </span>
                  <span className="text-foreground">{g}</span>
                </li>
              ))
            )}
          </ul>
        </div>
      </div>

      <div className="rounded-xl border bg-white p-6 shadow-sm">
        <h3 className="font-display text-base font-semibold text-foreground mb-4">
          Skills Analysis
        </h3>
        <div className="space-y-5">
          <div>
            <div className="flex items-center gap-2 mb-3">
              <span className="h-2 w-2 rounded-full bg-amber-500" />
              <p className="text-sm font-medium text-foreground">Matched</p>
              <span className="text-xs font-medium bg-amber-50 text-amber-700 px-2 py-0.5 rounded-full">
                {analysis.matchedSkills.length}
              </span>
            </div>
            <SkillList
              skills={analysis.matchedSkills}
              variant="matched"
              emptyMessage="No matched skills found"
            />
          </div>
          <div className="border-t pt-4">
            <div className="flex items-center gap-2 mb-3">
              <span className="h-2 w-2 rounded-full bg-slate-300" />
              <p className="text-sm font-medium text-foreground">Missing</p>
              <span className="text-xs font-medium bg-slate-100 text-slate-600 px-2 py-0.5 rounded-full">
                {analysis.missingSkills.length}
              </span>
            </div>
            <SkillList
              skills={analysis.missingSkills}
              variant="missing"
              emptyMessage="No missing skills found"
            />
          </div>
        </div>
      </div>

      <div className="flex gap-3">
        <Link to={`/analysis/${id}/skills`}>
          <Button variant="outline" size="sm">
            <Target className="h-4 w-4" />
            Detailed Skills
          </Button>
        </Link>
        <Link to={`/analysis/${id}/ats`}>
          <Button variant="outline" size="sm">
            <Shield className="h-4 w-4" />
            ATS Analysis
          </Button>
        </Link>
        <Link to={`/analysis/${id}/job-match`}>
          <Button variant="outline" size="sm">
            <Brain className="h-4 w-4" />
            Job Match
          </Button>
        </Link>
        <Link to={`/analysis/${id}/recommendations`}>
          <Button variant="outline" size="sm">
            <Lightbulb className="h-4 w-4" />
            Recommendations
          </Button>
        </Link>
      </div>
    </div>
  );
}
