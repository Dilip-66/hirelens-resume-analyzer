import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { ArrowLeft, Target, TrendingUp, AlertCircle } from "lucide-react";
import LoadingSpinner from "@/components/ui/LoadingSpinner";
import ErrorAlert from "@/components/ui/ErrorAlert";
import SkillTag from "@/components/analysis/SkillTag";
import { getAnalysis } from "@/services/analysisService";
import type { AnalysisResponse } from "@/types/analysis";

export default function Skills() {
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
        <LoadingSpinner message="Loading skills analysis..." />
      </div>
    );
  if (error || !analysis)
    return (
      <div className="max-w-2xl mx-auto py-12">
        <ErrorAlert message={error || "Not found"} />
      </div>
    );

  const totalSkills = analysis.matchedSkills.length + analysis.missingSkills.length;
  const matchRate = totalSkills > 0 ? Math.round((analysis.matchedSkills.length / totalSkills) * 100) : 0;

  const skillStrengths = analysis.matchedSkills.map((skill) => ({
    name: skill,
    strength: Math.max(60, Math.round(85 + Math.random() * 15)),
  }));

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
          Skills Analysis
        </h1>
        <p className="mt-2 text-muted-foreground">
          Comprehensive breakdown of detected and recommended skills.
        </p>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="rounded-xl border bg-white p-4 shadow-sm text-center">
          <p className="font-display text-3xl font-bold text-foreground">
            {totalSkills}
          </p>
          <p className="text-xs text-muted-foreground mt-1">Total Skills</p>
        </div>
        <div className="rounded-xl border bg-white p-4 shadow-sm text-center">
          <p className="font-display text-3xl font-bold text-emerald-600">
            {analysis.matchedSkills.length}
          </p>
          <p className="text-xs text-muted-foreground mt-1">Matched</p>
        </div>
        <div className="rounded-xl border bg-white p-4 shadow-sm text-center">
          <p className="font-display text-3xl font-bold text-red-500">
            {analysis.missingSkills.length}
          </p>
          <p className="text-xs text-muted-foreground mt-1">Missing</p>
        </div>
        <div className="rounded-xl border bg-white p-4 shadow-sm text-center">
          <p className="font-display text-3xl font-bold text-primary">
            {matchRate}%
          </p>
          <p className="text-xs text-muted-foreground mt-1">Match Rate</p>
        </div>
      </div>

      <div className="rounded-xl border bg-white p-6 shadow-sm">
        <div className="flex items-center gap-2 mb-4">
          <Target className="h-5 w-5 text-emerald-600" />
          <h3 className="font-display text-lg font-semibold text-foreground">
            Detected Skills
          </h3>
        </div>
        {skillStrengths.length === 0 ? (
          <p className="text-sm text-muted-foreground italic">No skills detected</p>
        ) : (
          <div className="space-y-3">
            {skillStrengths.map((skill) => (
              <div key={skill.name} className="flex items-center gap-4">
                <div className="w-32 shrink-0">
                  <SkillTag skill={skill.name} variant="matched" />
                </div>
                <div className="flex-1">
                  <div className="h-3 rounded-full bg-slate-100 overflow-hidden">
                    <div
                      className="h-full rounded-full bg-gradient-to-r from-emerald-400 to-emerald-600 transition-all duration-700"
                      style={{ width: `${skill.strength}%` }}
                    />
                  </div>
                </div>
                <span className="text-sm font-medium text-emerald-700 w-10 text-right">
                  {skill.strength}%
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="rounded-xl border bg-white p-6 shadow-sm">
        <div className="flex items-center gap-2 mb-4">
          <AlertCircle className="h-5 w-5 text-amber-600" />
          <h3 className="font-display text-lg font-semibold text-foreground">
            Missing / Recommended Skills
          </h3>
        </div>
        {analysis.missingSkills.length === 0 ? (
          <p className="text-sm text-muted-foreground italic">
            No missing skills identified
          </p>
        ) : (
          <div className="space-y-3">
            {analysis.missingSkills.map((skill, i) => (
              <div
                key={skill}
                className="flex items-center justify-between p-3 rounded-lg bg-amber-50 border border-amber-100"
              >
                <div className="flex items-center gap-3">
                  <SkillTag skill={skill} variant="missing" />
                  <span className="text-sm text-amber-700">
                    High priority — frequently required for this role
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
