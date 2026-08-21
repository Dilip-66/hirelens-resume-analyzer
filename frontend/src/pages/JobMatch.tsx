import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import {
  ArrowLeft,
  Brain,
  CheckCircle,
  XCircle,
  FileText,
} from "lucide-react";
import ScoreRing from "@/components/analysis/ScoreRing";
import SkillTag from "@/components/analysis/SkillTag";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import ErrorAlert from "@/components/ui/ErrorAlert";
import { getAnalysis } from "@/services/analysisService";
import type { AnalysisResponse } from "@/types/analysis";

export default function JobMatch() {
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
        setError(err instanceof Error ? err.message : "Failed to load")
      )
      .finally(() => setLoading(false));
  }, [id]);

  if (loading)
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <LoadingSpinner message="Loading job match..." />
      </div>
    );
  if (error || !analysis)
    return (
      <div className="max-w-2xl mx-auto py-12">
        <ErrorAlert message={error || "Not found"} />
      </div>
    );

  return (
    <div className="space-y-6">
      <div>
        <Link
          to={`/analysis/${id}`}
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground mb-2 transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to Analysis
        </Link>
        <h1 className="font-display text-2xl sm:text-3xl font-bold text-foreground">
          Job Compatibility Analysis
        </h1>
        <p className="mt-2 text-muted-foreground">
          RAG-powered comparison of your resume against the job requirements.
        </p>
      </div>

      <div className="rounded-xl border bg-white p-8 shadow-sm text-center">
        <ScoreRing score={analysis.matchScore} label="match" size="lg" />
        <h2 className="mt-4 font-display text-xl font-semibold text-foreground">
          {analysis.matchScore}% Match
        </h2>
        <p className="mt-2 text-sm text-muted-foreground max-w-md mx-auto">
          {analysis.matchScore >= 80
            ? "Excellent match! Your resume strongly aligns with this job's requirements."
            : analysis.matchScore >= 60
            ? "Good match with some areas for improvement."
            : "Your resume may need significant updates to match this role."}
        </p>
      </div>

      <div className="rounded-xl border bg-white p-6 shadow-sm">
        <h3 className="font-display text-lg font-semibold text-foreground mb-4">
          AI Analysis Summary
        </h3>
        <p className="text-muted-foreground leading-relaxed">{analysis.summary}</p>
      </div>

      <div className="grid md:grid-cols-2 gap-6">
        <div className="rounded-xl border bg-white p-6 shadow-sm">
          <div className="flex items-center gap-2 mb-4">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-50">
              <CheckCircle className="h-4 w-4 text-emerald-600" />
            </div>
            <h3 className="font-display text-base font-semibold text-foreground">
              Matching Requirements
            </h3>
            <span className="ml-auto text-xs font-medium bg-emerald-50 text-emerald-700 px-2 py-0.5 rounded-full">
              {analysis.matchedSkills.length}
            </span>
          </div>
          {analysis.matchedSkills.length === 0 ? (
            <p className="text-sm text-muted-foreground italic">
              No matching requirements found
            </p>
          ) : (
            <div className="space-y-3">
              {analysis.matchedSkills.map((skill) => (
                <div
                  key={skill}
                  className="flex items-center gap-3 p-3 rounded-lg bg-emerald-50 border border-emerald-100"
                >
                  <SkillTag skill={skill} variant="matched" />
                  <span className="text-xs text-emerald-600">
                    Found in resume
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="rounded-xl border bg-white p-6 shadow-sm">
          <div className="flex items-center gap-2 mb-4">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-red-50">
              <XCircle className="h-4 w-4 text-red-500" />
            </div>
            <h3 className="font-display text-base font-semibold text-foreground">
              Missing Requirements
            </h3>
            <span className="ml-auto text-xs font-medium bg-red-50 text-red-600 px-2 py-0.5 rounded-full">
              {analysis.missingSkills.length}
            </span>
          </div>
          {analysis.missingSkills.length === 0 ? (
            <p className="text-sm text-muted-foreground italic">
              No missing requirements
            </p>
          ) : (
            <div className="space-y-3">
              {analysis.missingSkills.map((skill) => (
                <div
                  key={skill}
                  className="flex items-center gap-3 p-3 rounded-lg bg-red-50 border border-red-100"
                >
                  <SkillTag skill={skill} variant="missing" />
                  <span className="text-xs text-red-500">
                    Not found in resume
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {analysis.strengths.length > 0 && (
        <div className="rounded-xl border bg-white p-6 shadow-sm">
          <div className="flex items-center gap-2 mb-4">
            <FileText className="h-5 w-5 text-primary" />
            <h3 className="font-display text-base font-semibold text-foreground">
              Resume Evidence
            </h3>
          </div>
          <div className="space-y-3">
            {analysis.matchedSkills.slice(0, 4).map((skill) => (
              <div
                key={skill}
                className="p-4 rounded-lg bg-slate-50 border border-slate-100"
              >
                <div className="flex items-center gap-2 mb-2">
                  <span className="text-xs font-mono text-muted-foreground uppercase">
                    Requirement:
                  </span>
                  <SkillTag skill={skill} variant="matched" size="sm" />
                </div>
                <p className="text-sm text-foreground leading-relaxed">
                  {analysis.strengths[0] || "Evidence found in resume content"}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}

      {analysis.gaps.length > 0 && (
        <div className="rounded-xl border bg-white p-6 shadow-sm">
          <div className="flex items-center gap-2 mb-4">
            <h3 className="font-display text-base font-semibold text-foreground">
              Identified Gaps
            </h3>
          </div>
          <ul className="space-y-2.5">
            {analysis.gaps.map((gap, i) => (
              <li key={i} className="flex items-start gap-2.5 text-sm p-3 rounded-lg bg-amber-50 border border-amber-100">
                <span className="mt-0.5 h-5 w-5 rounded-full bg-amber-100 flex items-center justify-center shrink-0">
                  <span className="text-amber-600 text-xs font-bold">!</span>
                </span>
                <span className="text-amber-800">{gap}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
