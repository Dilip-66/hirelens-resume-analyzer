import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  History as HistoryIcon,
  FileText,
  Clock,
  ExternalLink,
  Upload,
} from "lucide-react";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import ErrorAlert from "@/components/ui/ErrorAlert";
import Button from "@/components/ui/Button";
import { listAnalyses } from "@/services/analysisService";
import type { AnalysisResponse } from "@/types/analysis";
import { formatDate, getScoreColor, getScoreBgColor, getScoreBorderColor } from "@/lib/utils";

export default function History() {
  const [analyses, setAnalyses] = useState<AnalysisResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    listAnalyses()
      .then(setAnalyses)
      .catch((err) =>
        setError(err instanceof Error ? err.message : "Failed to load analyses")
      )
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <LoadingSpinner message="Loading analysis history..." />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl sm:text-3xl font-bold text-foreground">
            My Analyses
          </h1>
          <p className="mt-2 text-muted-foreground">
            View and manage your resume analysis history.
          </p>
        </div>
        <Link to="/upload">
          <Button>
            <Upload className="h-4 w-4" />
            New Analysis
          </Button>
        </Link>
      </div>

      {error && <ErrorAlert message={error} />}

      {analyses.length === 0 ? (
        <div className="rounded-xl border bg-white p-12 shadow-sm text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-slate-100 mb-4">
            <HistoryIcon className="h-8 w-8 text-slate-400" />
          </div>
          <h3 className="font-display text-lg font-semibold text-foreground mb-2">
            No analyses yet
          </h3>
          <p className="text-sm text-muted-foreground mb-6 max-w-sm mx-auto">
            Upload your first resume to start analyzing your career profile and get
            AI-powered recommendations.
          </p>
          <Link to="/upload">
            <Button>
              <Upload className="h-4 w-4" />
              Upload Resume
            </Button>
          </Link>
        </div>
      ) : (
        <div className="space-y-3">
          {analyses.map((analysis) => (
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
                  <div className="flex items-center gap-2 mb-1">
                    <h3 className="font-display text-sm font-semibold text-foreground truncate">
                      Analysis Report
                    </h3>
                    <ExternalLink className="h-3.5 w-3.5 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity" />
                  </div>
                  <div className="flex items-center gap-3 text-xs text-muted-foreground">
                    <span className="flex items-center gap-1">
                      <Clock className="h-3 w-3" />
                      {formatDate(analysis.createdAt)}
                    </span>
                    <span className="text-slate-300">|</span>
                    <span>{analysis.matchedSkills.length} skills matched</span>
                    <span className="text-slate-300">|</span>
                    <span>{analysis.missingSkills.length} missing</span>
                  </div>
                </div>

                <div className="flex items-center gap-3 shrink-0">
                  <div className="text-right hidden sm:block">
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-muted-foreground">Match</span>
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
                  </div>
                  <div className="sm:hidden">
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
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
