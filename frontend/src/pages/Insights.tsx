import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import {
  ArrowLeft,
  FileText,
  CheckCircle,
  AlertTriangle,
  Lightbulb,
} from "lucide-react";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import ErrorAlert from "@/components/ui/ErrorAlert";
import { getAnalysis } from "@/services/analysisService";
import { getResume } from "@/services/resumeService";
import type { AnalysisResponse, Resume } from "@/types/analysis";

interface SectionAnalysis {
  name: string;
  strengths: string[];
  issues: string[];
  suggestions: string[];
}

export default function Insights() {
  const { id } = useParams<{ id: string }>();
  const [analysis, setAnalysis] = useState<AnalysisResponse | null>(null);
  const [resume, setResume] = useState<Resume | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    getAnalysis(id)
      .then((a) => {
        setAnalysis(a);
        return getResume(a.resumeId);
      })
      .then(setResume)
      .catch((err) =>
        setError(err instanceof Error ? err.message : "Failed to load")
      )
      .finally(() => setLoading(false));
  }, [id]);

  if (loading)
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <LoadingSpinner message="Loading insights..." />
      </div>
    );
  if (error || !analysis)
    return (
      <div className="max-w-2xl mx-auto py-12">
        <ErrorAlert message={error || "Not found"} />
      </div>
    );

  const sections: SectionAnalysis[] = [
    {
      name: "Contact Information",
      strengths: ["Resume includes contact details"],
      issues: [],
      suggestions: ["Ensure email and phone are professional"],
    },
    {
      name: "Professional Summary",
      strengths: analysis.strengths.slice(0, 2),
      issues: analysis.gaps.slice(0, 1),
      suggestions: ["Tailor summary to the target role"],
    },
    {
      name: "Experience",
      strengths: analysis.matchedSkills.length > 0 ? ["Relevant experience demonstrated"] : [],
      issues: analysis.gaps.slice(0, 2),
      suggestions: ["Quantify achievements with metrics", "Use action verbs"],
    },
    {
      name: "Skills",
      strengths: analysis.matchedSkills.slice(0, 3),
      issues: analysis.missingSkills.slice(0, 3),
      suggestions: ["Add missing critical skills", "Organize skills by proficiency"],
    },
    {
      name: "Education",
      strengths: ["Education section present"],
      issues: [],
      suggestions: ["Include relevant coursework if applicable"],
    },
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
          Resume Insights
        </h1>
        <p className="mt-2 text-muted-foreground">
          Detailed section-by-section analysis of your resume.
        </p>
      </div>

      <div className="space-y-4">
        {sections.map((section) => (
          <div key={section.name} className="rounded-xl border bg-white p-6 shadow-sm">
            <div className="flex items-center gap-2 mb-4">
              <FileText className="h-5 w-5 text-primary" />
              <h3 className="font-display text-lg font-semibold text-foreground">
                {section.name}
              </h3>
            </div>

            <div className="grid md:grid-cols-3 gap-4">
              <div className="rounded-lg bg-emerald-50 border border-emerald-100 p-4">
                <div className="flex items-center gap-1.5 mb-2">
                  <CheckCircle className="h-4 w-4 text-emerald-600" />
                  <span className="text-sm font-semibold text-emerald-800">Strengths</span>
                </div>
                {section.strengths.length === 0 ? (
                  <p className="text-xs text-emerald-600 italic">None identified</p>
                ) : (
                  <ul className="space-y-1.5">
                    {section.strengths.map((s, i) => (
                      <li key={i} className="text-sm text-emerald-700 flex items-start gap-1.5">
                        <span className="mt-1 text-emerald-500">+</span>
                        {s}
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <div className="rounded-lg bg-red-50 border border-red-100 p-4">
                <div className="flex items-center gap-1.5 mb-2">
                  <AlertTriangle className="h-4 w-4 text-red-500" />
                  <span className="text-sm font-semibold text-red-700">Issues</span>
                </div>
                {section.issues.length === 0 ? (
                  <p className="text-xs text-red-500 italic">None found</p>
                ) : (
                  <ul className="space-y-1.5">
                    {section.issues.map((s, i) => (
                      <li key={i} className="text-sm text-red-600 flex items-start gap-1.5">
                        <span className="mt-1 text-red-400">-</span>
                        {s}
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <div className="rounded-lg bg-amber-50 border border-amber-100 p-4">
                <div className="flex items-center gap-1.5 mb-2">
                  <Lightbulb className="h-4 w-4 text-amber-600" />
                  <span className="text-sm font-semibold text-amber-800">Suggestions</span>
                </div>
                <ul className="space-y-1.5">
                  {section.suggestions.map((s, i) => (
                    <li key={i} className="text-sm text-amber-700 flex items-start gap-1.5">
                      <span className="mt-1 text-amber-500">~</span>
                      {s}
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
