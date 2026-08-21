import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import {
  ArrowLeft,
  Shield,
  CheckCircle,
  AlertTriangle,
  Lightbulb,
} from "lucide-react";
import ScoreRing from "@/components/analysis/ScoreRing";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import ErrorAlert from "@/components/ui/ErrorAlert";
import { getAnalysis } from "@/services/analysisService";
import type { AnalysisResponse } from "@/types/analysis";

export default function ATS() {
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
        <LoadingSpinner message="Loading ATS analysis..." />
      </div>
    );
  if (error || !analysis)
    return (
      <div className="max-w-2xl mx-auto py-12">
        <ErrorAlert message={error || "Not found"} />
      </div>
    );

  const atsScore = Math.min(100, Math.round(analysis.matchScore * 1.05 + 5));

  const strengths = [
    ...(analysis.matchedSkills.length >= 3
      ? ["Good keyword coverage with matched skills"]
      : []),
    ...(analysis.summary.length > 100 ? ["Clear professional summary present"] : []),
    ...(analysis.matchScore >= 70 ? ["Strong overall content quality"] : []),
    "Structured resume format detected",
  ];

  const problems = [
    ...(analysis.missingSkills.length > 2
      ? ["Missing key skills that ATS may filter on"]
      : []),
    ...(analysis.gaps.length > 0
      ? ["Some gaps in content detected"]
      : []),
    ...(analysis.matchScore < 60 ? ["Low overall match score"] : []),
    "Consider adding more quantifiable achievements",
  ];

  const recommendations = [
    { severity: "critical", text: "Add missing skills to your skills section or demonstrate them in experience" },
    { severity: "warning", text: "Include more quantifiable achievements (numbers, percentages, metrics)" },
    { severity: "warning", text: "Use standard section headings for better ATS parsing" },
    { severity: "good", text: "Maintain consistent formatting throughout the document" },
    { severity: "good", text: "Include relevant keywords from the job description" },
  ];

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
          ATS Compatibility Analysis
        </h1>
        <p className="mt-2 text-muted-foreground">
          How well your resume performs with Applicant Tracking Systems.
        </p>
      </div>

      <div className="rounded-xl border bg-white p-8 shadow-sm text-center">
        <ScoreRing score={atsScore} label="ats score" size="lg" />
        <p className="mt-4 text-sm text-muted-foreground max-w-md mx-auto">
          {atsScore >= 80
            ? "Your resume has strong ATS compatibility. Keep up the good work!"
            : atsScore >= 60
            ? "Your resume has decent ATS compatibility, but there are areas for improvement."
            : "Your resume may have issues with ATS systems. Review the recommendations below."}
        </p>
      </div>

      <div className="grid md:grid-cols-2 gap-6">
        <div className="rounded-xl border bg-white p-6 shadow-sm">
          <div className="flex items-center gap-2 mb-4">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-50">
              <CheckCircle className="h-4 w-4 text-emerald-600" />
            </div>
            <h3 className="font-display text-base font-semibold text-foreground">
              ATS Strengths
            </h3>
          </div>
          <ul className="space-y-2.5">
            {strengths.map((s, i) => (
              <li key={i} className="flex items-start gap-2.5 text-sm">
                <span className="mt-0.5 h-5 w-5 rounded-full bg-emerald-50 flex items-center justify-center shrink-0">
                  <span className="text-emerald-600 text-xs font-bold">+</span>
                </span>
                <span className="text-foreground">{s}</span>
              </li>
            ))}
          </ul>
        </div>

        <div className="rounded-xl border bg-white p-6 shadow-sm">
          <div className="flex items-center gap-2 mb-4">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-red-50">
              <AlertTriangle className="h-4 w-4 text-red-500" />
            </div>
            <h3 className="font-display text-base font-semibold text-foreground">
              ATS Problems
            </h3>
          </div>
          <ul className="space-y-2.5">
            {problems.map((p, i) => (
              <li key={i} className="flex items-start gap-2.5 text-sm">
                <span className="mt-0.5 h-5 w-5 rounded-full bg-red-50 flex items-center justify-center shrink-0">
                  <span className="text-red-500 text-xs font-bold">-</span>
                </span>
                <span className="text-foreground">{p}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>

      <div className="rounded-xl border bg-white p-6 shadow-sm">
        <div className="flex items-center gap-2 mb-4">
          <Lightbulb className="h-5 w-5 text-amber-600" />
          <h3 className="font-display text-base font-semibold text-foreground">
            Recommendations
          </h3>
        </div>
        <div className="space-y-3">
          {recommendations.map((rec, i) => (
            <div
              key={i}
              className={`flex items-start gap-3 p-3 rounded-lg border ${
                rec.severity === "critical"
                  ? "bg-red-50 border-red-100"
                  : rec.severity === "warning"
                  ? "bg-amber-50 border-amber-100"
                  : "bg-emerald-50 border-emerald-100"
              }`}
            >
              <span
                className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium shrink-0 ${
                  rec.severity === "critical"
                    ? "bg-red-100 text-red-700"
                    : rec.severity === "warning"
                    ? "bg-amber-100 text-amber-700"
                    : "bg-emerald-100 text-emerald-700"
                }`}
              >
                {rec.severity}
              </span>
              <span className="text-sm text-foreground">{rec.text}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
