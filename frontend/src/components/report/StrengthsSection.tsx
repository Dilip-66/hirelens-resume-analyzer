import { useState } from "react";
import SkillTag from "@/components/analysis/SkillTag";
import ReportSection from "@/components/report/ReportSection";
import type { AnalysisResponse } from "@/types/analysis";

const PREVIEW_COUNT = 8;

interface StrengthsSectionProps {
  analysis: AnalysisResponse;
}

/**
 * The skills both documents have in common.
 *
 * <p>A chip list rather than a card per skill: the point is the density of
 * overlap, not the individual entries.
 */
export default function StrengthsSection({ analysis }: StrengthsSectionProps) {
  const [expanded, setExpanded] = useState(false);
  const skills = analysis.matchedSkills;

  if (skills.length === 0) return null;

  const visible = expanded ? skills : skills.slice(0, PREVIEW_COUNT);
  const hidden = skills.length - visible.length;

  return (
    <ReportSection
      title="Your strongest matches"
      subtitle="Skills detected in your resume that also appear in the job description."
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
      <div className="flex flex-wrap gap-2">
        {visible.map((skill, index) => (
          <SkillTag key={`${index}-${skill}`} skill={skill} variant="matched" />
        ))}
      </div>
    </ReportSection>
  );
}
