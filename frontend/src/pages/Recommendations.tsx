import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import {
  ArrowLeft,
  Lightbulb,
  Wrench,
  BookOpen,
  Briefcase,
  FolderOpen,
  Shield,
  Target,
} from "lucide-react";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import ErrorAlert from "@/components/ui/ErrorAlert";
import { getAnalysis } from "@/services/analysisService";
import type { AnalysisResponse } from "@/types/analysis";

interface Recommendation {
  category: string;
  icon: React.ElementType;
  color: string;
  bgColor: string;
  borderColor: string;
  items: {
    problem: string;
    whyItMatters: string;
    action: string;
    priority: "high" | "medium" | "low";
  }[];
}

export default function Recommendations() {
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
        <LoadingSpinner message="Loading recommendations..." />
      </div>
    );
  if (error || !analysis)
    return (
      <div className="max-w-2xl mx-auto py-12">
        <ErrorAlert message={error || "Not found"} />
      </div>
    );

  const categories: Recommendation[] = [
    {
      category: "Resume Improvements",
      icon: Wrench,
      color: "text-blue-600",
      bgColor: "bg-blue-50",
      borderColor: "border-blue-100",
      items: analysis.gaps.slice(0, 3).map((gap) => ({
        problem: gap,
        whyItMatters:
          "Addressing this gap will improve your resume's overall effectiveness and ATS compatibility.",
        action:
          "Review and update this section to better align with industry standards and the target role.",
        priority: "high" as const,
      })),
    },
    {
      category: "Skills to Learn",
      icon: BookOpen,
      color: "text-amber-600",
      bgColor: "bg-amber-50",
      borderColor: "border-amber-100",
      items: analysis.missingSkills.slice(0, 4).map((skill) => ({
        problem: `Missing skill: ${skill}`,
        whyItMatters:
          "This skill is frequently required for the target role and would strengthen your candidacy.",
        action: `Consider taking a course or certification in ${skill} to add to your skillset.`,
        priority: "high" as const,
      })),
    },
    {
      category: "Experience Improvements",
      icon: Briefcase,
      color: "text-purple-600",
      bgColor: "bg-purple-50",
      borderColor: "border-purple-100",
      items: [
        {
          problem: "Quantify achievements with specific metrics",
          whyItMatters:
            "Quantifiable results demonstrate impact and are more compelling to recruiters.",
          action:
            "Add specific numbers, percentages, or dollar amounts to your accomplishment statements.",
          priority: "medium" as const,
        },
        {
          problem: "Use stronger action verbs",
          whyItMatters:
            "Action verbs make your experience more dynamic and impactful.",
          action:
            "Replace generic verbs with specific ones like 'architected', 'optimized', 'spearheaded'.",
          priority: "low" as const,
        },
      ],
    },
    {
      category: "Project Improvements",
      icon: FolderOpen,
      color: "text-emerald-600",
      bgColor: "bg-emerald-50",
      borderColor: "border-emerald-100",
      items: [
        {
          problem: "Highlight relevant project experience",
          whyItMatters:
            "Projects demonstrate practical application of skills.",
          action:
            "Include 2-3 relevant projects with clear descriptions of your role and the technologies used.",
          priority: "medium" as const,
        },
      ],
    },
    {
      category: "ATS Improvements",
      icon: Shield,
      color: "text-rose-600",
      bgColor: "bg-rose-50",
      borderColor: "border-rose-100",
      items: [
        {
          problem: "Ensure standard section headings",
          whyItMatters:
            "ATS systems look for standard headings to parse your resume correctly.",
          action:
            "Use conventional headings: 'Experience', 'Education', 'Skills', 'Summary'.",
          priority: "high" as const,
        },
        {
          problem: "Include keywords from the job description",
          whyItMatters:
            "ATS filters based on keyword matching with the job description.",
          action:
            "Mirror the exact language and keywords used in the target job posting.",
          priority: "high" as const,
        },
      ],
    },
    {
      category: "Job-Specific Recommendations",
      icon: Target,
      color: "text-primary",
      bgColor: "bg-primary/5",
      borderColor: "border-primary/10",
      items: analysis.strengths.slice(0, 2).map((s) => ({
        problem: `Leverage your strength: ${s}`,
        whyItMatters: "Building on existing strengths creates a more compelling profile.",
        action: "Emphasize this strength more prominently in your resume and cover letter.",
        priority: "medium" as const,
      })),
    },
  ].filter((cat) => cat.items.length > 0);

  const priorityColors = {
    high: "bg-red-100 text-red-700",
    medium: "bg-amber-100 text-amber-700",
    low: "bg-slate-100 text-slate-600",
  };

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
          AI Recommendations
        </h1>
        <p className="mt-2 text-muted-foreground">
          Personalized, actionable improvements for your resume.
        </p>
      </div>

      <div className="space-y-6">
        {categories.map((cat) => {
          const Icon = cat.icon;
          return (
            <div
              key={cat.category}
              className="rounded-xl border bg-white p-6 shadow-sm"
            >
              <div className="flex items-center gap-2 mb-5">
                <div className={`flex h-8 w-8 items-center justify-center rounded-lg ${cat.bgColor}`}>
                  <Icon className={`h-4 w-4 ${cat.color}`} />
                </div>
                <h3 className="font-display text-lg font-semibold text-foreground">
                  {cat.category}
                </h3>
                <span className="ml-auto text-xs font-medium bg-slate-100 text-slate-600 px-2 py-0.5 rounded-full">
                  {cat.items.length}
                </span>
              </div>

              <div className="space-y-4">
                {cat.items.map((item, i) => (
                  <div
                    key={i}
                    className={`rounded-lg border p-4 ${cat.borderColor} ${cat.bgColor}`}
                  >
                    <div className="flex items-start justify-between gap-3 mb-2">
                      <h4 className="text-sm font-semibold text-foreground">
                        {item.problem}
                      </h4>
                      <span
                        className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium shrink-0 ${
                          priorityColors[item.priority]
                        }`}
                      >
                        {item.priority}
                      </span>
                    </div>
                    <p className="text-sm text-muted-foreground mb-2">
                      <span className="font-medium">Why it matters:</span>{" "}
                      {item.whyItMatters}
                    </p>
                    <p className="text-sm text-foreground">
                      <span className="font-medium">Action:</span> {item.action}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
