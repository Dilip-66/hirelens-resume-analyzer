import { useState } from "react";
import ReportSection from "@/components/report/ReportSection";
import type { AnalysisResponse } from "@/types/analysis";

const PREVIEW_COUNT = 8;

interface SkillGapsSectionProps {
  analysis: AnalysisResponse;
}

/**
 * Requirements in the job description that the resume does not cover.
 *
 * <p>Every entry is a straight "missing" - the backend's skill scan produces one
 * list of absent skills with no per-skill nuance, so the report does not dress
 * them up as partial or limited. Amber rather than red: a missing skill is a
 * to-do, not a failure.
 */
export default function SkillGapsSection({ analysis }: SkillGapsSectionProps) {
  const [expanded, setExpanded] = useState(false);
  const skills = analysis.missingSkills;

  if (skills.length === 0) return null;

  const visible = expanded ? skills : skills.slice(0, PREVIEW_COUNT);
  const hidden = skills.length - visible.length;

  return (
    <ReportSection
      title="Skill gaps to address"
      subtitle="These requirements appear in the job description but were not found in your resume."
      action={
        hidden > 0 || expanded ? (
          <button
            type="button"
            onClick={() => setExpanded((value) => !value)}
            className="text-sm font-medium text-primary transition-colors hover:text-primary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          >
            {expanded ? "Show less" : `Show all ${skills.length}`}
          </button>
        ) : null
      }
    >
      <ul className="divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        {visible.map((skill, index) => (
          <li
            key={`${index}-${skill}`}
            className="flex items-center justify-between gap-4 px-6 py-3.5 sm:px-7"
          >
            <span className="min-w-0 truncate text-sm font-medium text-foreground">
              {skill}
            </span>
            <span className="shrink-0 rounded-full border border-amber-200 bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700">
              Missing
            </span>
          </li>
        ))}
      </ul>
    </ReportSection>
  );
}
