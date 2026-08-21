import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  Upload,
  FileText,
  Clock,
  BarChart3,
  ArrowRight,
  TrendingUp,
} from "lucide-react";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import { listAnalyses } from "@/services/analysisService";
import { listResumes } from "@/services/resumeService";
import type { AnalysisResponse } from "@/types/analysis";
import type { Resume } from "@/types/analysis";
import { formatDate, getScoreColor, getScoreBgColor, getScoreBorderColor } from "@/lib/utils";
import { useAuth } from "@/contexts/AuthContext";

export default function Dashboard() {
  const { user } = useAuth();
  const [analyses, setAnalyses] = useState<AnalysisResponse[]>([]);
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([
      listAnalyses().catch(() => []),
      listResumes().catch(() => []),
    ])
      .then(([a, r]) => { setAnalyses(a); setResumes(r); })
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load"))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <LoadingSpinner message="Loading dashboard..." />
      </div>
    );
  }

  const avgScore = analyses.length > 0
    ? Math.round(analyses.reduce((sum, a) => sum + a.matchScore, 0) / analyses.length)
    : 0;

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-2xl sm:text-3xl font-bold text-foreground">
          Welcome back{user?.name ? `, ${user.name}` : ""}
        </h1>
        <p className="mt-2 text-muted-foreground">
          Here's an overview of your resume analysis journey.
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="rounded-xl border bg-white p-5 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10">
              <FileText className="h-5 w-5 text-primary" />
            </div>
            <div>
              <p className="text-2xl font-bold text-foreground">{resumes.length}</p>
              <p className="text-xs text-muted-foreground">Resumes Uploaded</p>
            </div>
          </div>
        </div>
        <div className="rounded-xl border bg-white p-5 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-100">
              <BarChart3 className="h-5 w-5 text-emerald-600" />
            </div>
            <div>
              <p className="text-2xl font-bold text-foreground">{analyses.length}</p>
              <p className="text-xs text-muted-foreground">Analyses Completed</p>
            </div>
          </div>
        </div>
        <div className="rounded-xl border bg-white p-5 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-100">
              <TrendingUp className="h-5 w-5 text-amber-600" />
            </div>
            <div>
              <p className={`text-2xl font-bold ${avgScore > 0 ? getScoreColor(avgScore) : "text-muted-foreground"}`}>
                {avgScore > 0 ? `${avgScore}%` : "--"}
              </p>
              <p className="text-xs text-muted-foreground">Average Match Score</p>
            </div>
          </div>
        </div>
      </div>

      <div className="flex items-center justify-between">
        <h2 className="font-display text-lg font-semibold text-foreground">Recent Analyses</h2>
        <Link to="/upload" className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline">
          New Analysis <ArrowRight className="h-4 w-4" />
        </Link>
      </div>

      {error && (
        <div className="rounded-lg bg-red-50 border border-red-200 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {analyses.length === 0 ? (
        <div className="rounded-xl border bg-white p-12 shadow-sm text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-slate-100 mb-4">
            <BarChart3 className="h-8 w-8 text-slate-400" />
          </div>
          <h3 className="font-display text-lg font-semibold text-foreground mb-2">
            No analyses yet
          </h3>
          <p className="text-sm text-muted-foreground mb-6 max-w-sm mx-auto">
            Upload your first resume to start getting AI-powered insights.
          </p>
          <Link
            to="/upload"
            className="inline-flex items-center gap-2 rounded-lg bg-primary px-5 py-2.5 text-sm font-medium text-white hover:bg-primary/90 transition-colors"
          >
            <Upload className="h-4 w-4" />
            Upload Resume
          </Link>
        </div>
      ) : (
        <div className="space-y-3">
          {analyses.slice(0, 10).map((analysis) => (
            <Link
              key={analysis.id}
              to={`/analysis/${analysis.id}`}
              className="block rounded-xl border bg-white p-5 shadow-sm transition-all hover:shadow-md hover:border-primary/20 group"
            >
              <div className="flex items-center gap-4">
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10 shrink-0 group-hover:bg-primary/20 transition-colors">
                  <FileText className="h-5 w-5 text-primary" />
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className="font-display text-sm font-semibold text-foreground truncate">
                    Analysis Report
                  </h3>
                  <div className="flex items-center gap-3 text-xs text-muted-foreground mt-0.5">
                    <span className="flex items-center gap-1">
                      <Clock className="h-3 w-3" />
                      {formatDate(analysis.createdAt)}
                    </span>
                    <span>{analysis.matchedSkills.length} skills matched</span>
                    <span>{analysis.missingSkills.length} missing</span>
                  </div>
                </div>
                <span
                  className={`inline-flex items-center rounded-full px-2.5 py-1 text-sm font-bold ${getScoreBgColor(
                    analysis.matchScore
                  )} ${getScoreColor(
                    analysis.matchScore
                  )} border ${getScoreBorderColor(analysis.matchScore)}`}
                >
                  {analysis.matchScore}%
                </span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
