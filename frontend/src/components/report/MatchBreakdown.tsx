import { useState } from "react";
import { Check, Minus, ChevronDown } from "lucide-react";
import ReportSection from "@/components/report/ReportSection";
import type {
  AnalysisResponse,
  CategoryScore,
  ExperienceAlignment,
  MatchStatus,
  RequirementCategory,
  RequirementMatch,
} from "@/types/analysis";
import { skillCoverage } from "@/lib/reportNarrative";

interface MatchBreakdownProps {
  analysis: AnalysisResponse;
}

/**
 * The reasoning behind the score.
 *
 * <p>With a detail block, this shows the dimensions the engine actually scored and
 * the requirements behind each one. Without one - a report analysed before
 * evidence-based scoring - it falls back to the single ratio it can compute
 * honestly over real data, as it always did.
 *
 * <p>Dimensions that were never measured are listed as "not scored" with the
 * engine's reason, never as a percentage. The overall score is a weighted mean
 * over the dimensions that <em>were</em> assessed, so showing a fabricated 80% for
 * education that was never compared against the requirement would both mislead and
 * contradict the headline number.
 */
export default function MatchBreakdown({ analysis }: MatchBreakdownProps) {
  const { matched, missing, total, percent } = skillCoverage(analysis);
  const detail = analysis.detail;

  const hasReasons = analysis.strengths.length > 0 || analysis.gaps.length > 0;
  const hasCoverage = total > 0 && percent !== null;

  if (detail?.scored && detail.scoreBreakdown) {
    return <ScoredBreakdown analysis={analysis} />;
  }

  if (!hasCoverage && !hasReasons) return null;

  return (
    <ReportSection
      title="Why you received this score"
      subtitle="A quick breakdown of what influenced your match."
    >
      <div className="space-y-10">
        {hasCoverage && (
          <div className="max-w-2xl">
            <div className="flex items-baseline justify-between gap-4">
              <p className="text-sm font-medium text-foreground">Job skill coverage</p>
              <p className="font-display text-sm font-semibold text-foreground">{percent}%</p>
            </div>
            <div
              className="mt-3 h-2.5 overflow-hidden rounded-full bg-slate-200"
              role="img"
              aria-label={`${matched} of ${total} job skills found in the resume`}
            >
              <div
                className="h-full rounded-full bg-emerald-600 transition-[width] duration-700 ease-out motion-reduce:transition-none"
                style={{ width: `${percent}%` }}
              />
            </div>
            <p className="mt-3 text-sm text-muted-foreground">
              {matched} matched and {missing} missing, out of {total} skills detected in
              the job description.
            </p>
          </div>
        )}

        {hasReasons && (
          <div className="grid gap-x-12 gap-y-9 sm:grid-cols-2">
            {analysis.strengths.length > 0 && (
              <div>
                <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
                  <span className="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-50">
                    <Check className="h-3.5 w-3.5 text-emerald-600" aria-hidden="true" />
                  </span>
                  Credited by the analysis
                </h3>
                <ul className="mt-4 space-y-3">
                  {analysis.strengths.map((strength, index) => (
                    <li
                      key={`${index}-${strength}`}
                      className="text-sm leading-relaxed text-foreground/80"
                    >
                      {strength}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {analysis.gaps.length > 0 && (
              <div>
                <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
                  <span className="flex h-6 w-6 items-center justify-center rounded-full bg-amber-50">
                    <Minus className="h-3.5 w-3.5 text-amber-600" aria-hidden="true" />
                  </span>
                  Held the score back
                </h3>
                <ul className="mt-4 space-y-3">
                  {analysis.gaps.map((gap, index) => (
                    <li key={`${index}-${gap}`} className="text-sm leading-relaxed text-foreground/80">
                      {gap}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </div>
    </ReportSection>
  );
}

const DIMENSION_ORDER = [
  { key: "requiredSkills", label: "Required skills", weight: 0.4 },
  { key: "experience", label: "Experience", weight: 0.2 },
  { key: "responsibilities", label: "Responsibilities", weight: 0.15 },
  { key: "preferredSkills", label: "Preferred skills", weight: 0.1 },
  { key: "projects", label: "Projects", weight: 0.05 },
  { key: "education", label: "Education", weight: 0.05 },
  { key: "ats", label: "ATS readiness", weight: 0.05 },
] as const;

/**
 * Maps a breakdown key to the category name the API reports it under, so the
 * per-dimension note ("why this was not scored") can be shown against the right
 * row rather than matched by string coincidence.
 */
const DIMENSION_CATEGORY: Record<(typeof DIMENSION_ORDER)[number]["key"], string> = {
  requiredSkills: "REQUIRED_SKILLS",
  experience: "EXPERIENCE",
  responsibilities: "RESPONSIBILITIES",
  preferredSkills: "PREFERRED_SKILLS",
  projects: "PROJECTS",
  education: "EDUCATION",
  ats: "ATS",
};

const GROUP_ORDER: RequirementCategory[] = [
  "REQUIRED_SKILL",
  "PREFERRED_SKILL",
  "RESPONSIBILITY",
  "EXPERIENCE",
  "SOFT_SKILL",
  "GENERAL",
];

const GROUP_LABELS: Record<RequirementCategory, string> = {
  REQUIRED_SKILL: "Required skills",
  PREFERRED_SKILL: "Preferred skills",
  RESPONSIBILITY: "Responsibilities",
  EXPERIENCE: "Experience",
  EDUCATION: "Education",
  SOFT_SKILL: "Ways of working",
  GENERAL: "Other requirements",
};

const STATUS_STYLES: Record<MatchStatus, { label: string; dot: string; text: string }> = {
  EXPLICIT_MATCH: { label: "Demonstrated", dot: "bg-emerald-600", text: "text-emerald-700" },
  STRONG_CONTEXTUAL_MATCH: { label: "Supported", dot: "bg-teal-600", text: "text-teal-700" },
  PARTIAL_MATCH: { label: "Partial", dot: "bg-amber-500", text: "text-amber-700" },
  NOT_EXPLICITLY_MENTIONED: { label: "Not explicitly mentioned", dot: "bg-slate-400", text: "text-slate-600" },
  CONFLICT: { label: "Not yet demonstrated", dot: "bg-rose-500", text: "text-rose-700" },
};

function ScoredBreakdown({ analysis }: { analysis: AnalysisResponse }) {
  const detail = analysis.detail!;
  const breakdown = detail.scoreBreakdown;
  const scored = DIMENSION_ORDER.filter((d) => breakdown[d.key] !== null);
  const unscored = DIMENSION_ORDER.filter((d) => breakdown[d.key] === null);
  const requirementsByGroup = new Map<RequirementCategory, RequirementMatch[]>();
  for (const requirement of detail.requirements) {
    const group = GROUP_ORDER.includes(requirement.category) ? requirement.category : "GENERAL";
    const list = requirementsByGroup.get(group) ?? [];
    list.push(requirement);
    requirementsByGroup.set(group, list);
  }

  return (
    <ReportSection
      title="Why you received this score"
      subtitle="Every requirement in the job description, the evidence behind it, and how much it counted for."
    >
      <div className="space-y-12">
        <div>
          <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
            <h3 className="text-sm font-semibold text-foreground">Score by dimension</h3>
            <p className="text-xs text-muted-foreground">
              Weighted to {Math.round(scored.reduce((sum, d) => sum + d.weight, 0) * 100)}% of the
              total; the rest was not measured
            </p>
          </div>
          <ul className="mt-5 space-y-4">
            {scored.map((dimension) => {
              const value = breakdown[dimension.key]!;
              const note = noteFor(breakdown.categories, dimension.key);
              return (
                <li key={dimension.key}>
                  <div className="flex items-baseline justify-between gap-4">
                    <p className="text-sm font-medium text-foreground">{dimension.label}</p>
                    <p className="font-display text-sm font-semibold text-foreground">{value}%</p>
                  </div>
                  <div
                    className="mt-2 h-2 overflow-hidden rounded-full bg-slate-200"
                    role="img"
                    aria-label={`${dimension.label}: ${value} out of 100`}
                  >
                    <div
                      className="h-full rounded-full bg-emerald-600 transition-[width] duration-700 ease-out motion-reduce:transition-none"
                      style={{ width: `${value}%` }}
                    />
                  </div>
                  {note && <p className="mt-1.5 text-xs text-muted-foreground">{note}</p>}
                </li>
              );
            })}
          </ul>

          {unscored.length > 0 && (
            <div className="mt-6 rounded-xl border border-dashed border-slate-200 bg-slate-50/60 px-5 py-4">
              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                Not scored
              </p>
              <ul className="mt-2.5 space-y-1.5">
                {unscored.map((dimension) => {
                  const note = noteFor(breakdown.categories, dimension.key);
                  return (
                    <li key={dimension.key} className="text-sm text-muted-foreground">
                      <span className="font-medium text-foreground/70">{dimension.label}</span>
                      {note ? ` — ${note}` : " — not measured for this analysis"}
                    </li>
                  );
                })}
              </ul>
            </div>
          )}
        </div>

        {detail.experienceAlignment && (
          <div className="rounded-xl border border-slate-200 bg-white px-5 py-4">
            <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
              <h3 className="text-sm font-semibold text-foreground">Experience alignment</h3>
              <p className="text-xs text-muted-foreground">
                {formatRange(detail.experienceAlignment)}
              </p>
            </div>
            <p className="mt-2 text-sm leading-relaxed text-foreground/80">
              {detail.experienceAlignment.explanation}
            </p>
          </div>
        )}

        {[...requirementsByGroup.entries()].map(([group, requirements]) => (
          <div key={group}>
            <h3 className="text-sm font-semibold text-foreground">
              {GROUP_LABELS[group]}
              <span className="ml-2 text-xs font-normal text-muted-foreground">
                {requirements.filter((r) => r.resumeEvidence.length > 0).length} of{" "}
                {requirements.length} with evidence
              </span>
            </h3>
            <ul className="mt-4 space-y-2">
              {requirements.map((requirement) => (
                <RequirementRow key={requirement.index} requirement={requirement} />
              ))}
            </ul>
          </div>
        ))}
      </div>
    </ReportSection>
  );
}

function RequirementRow({ requirement }: { requirement: RequirementMatch }) {
  const [open, setOpen] = useState(false);
  const style = STATUS_STYLES[requirement.status];
  const hasDetail = requirement.resumeEvidence.length > 0 || requirement.jdEvidence.length > 0;

  return (
    <li className="rounded-lg border border-slate-200 bg-white">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        className="flex w-full items-start gap-3 px-4 py-3 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
      >
        <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${style.dot}`} aria-hidden="true" />
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-baseline gap-x-2">
            <span className="text-sm font-medium text-foreground">{requirement.name}</span>
            <span className={`text-xs font-medium ${style.text}`}>{style.label}</span>
          </span>
          <span className="mt-1 block text-xs text-muted-foreground">
            {strengthLabel(requirement)} · {Math.round(requirement.confidence * 100)}% confidence
            {requirement.importance === "CRITICAL" ? " · load-bearing" : ""}
          </span>
        </span>
        {hasDetail && (
          <ChevronDown
            className={`mt-1 h-4 w-4 shrink-0 text-muted-foreground transition-transform ${
              open ? "rotate-180" : ""
            }`}
            aria-hidden="true"
          />
        )}
      </button>

      {open && (
        <div className="space-y-3 border-t border-slate-100 px-4 py-3">
          <p className="text-sm leading-relaxed text-foreground/80">{requirement.explanation}</p>
          {requirement.resumeEvidence.length > 0 && (
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                From your resume
              </p>
              <ul className="mt-1.5 space-y-1">
                {requirement.resumeEvidence.map((line) => (
                  <li key={line} className="border-l-2 border-emerald-200 pl-3 text-sm italic text-foreground/75">
                    {line}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {requirement.jdEvidence.length > 0 && (
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                What the job asked for
              </p>
              <ul className="mt-1.5 space-y-1">
                {requirement.jdEvidence.map((line) => (
                  <li key={line} className="border-l-2 border-slate-200 pl-3 text-sm text-foreground/70">
                    {line}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </li>
  );
}

/**
 * How good the evidence was, in words the user can check.
 *
 * <p>Reads provenance as well as strength, because strength 3 has two quite
 * different sources: a technology named in a skills list, and a contextual cue in
 * a sentence that shows a result. Calling both "listed as a skill" would describe
 * the second one wrongly.
 */
function strengthLabel(requirement: RequirementMatch): string {
  if (requirement.evidenceStrength >= 4) return "used in professional work";
  if (requirement.provenance === "INFERRED") {
    return requirement.evidenceStrength === 3
      ? "related work, with a result"
      : "related evidence";
  }
  if (requirement.evidenceStrength === 3) return "listed as a skill";
  if (requirement.evidenceStrength === 2) return "related evidence";
  if (requirement.evidenceStrength === 1) return "weak evidence";
  return "no evidence found";
}

/** The engine's own explanation for a dimension, which may be absent. */
function noteFor(categories: CategoryScore[], key: (typeof DIMENSION_ORDER)[number]["key"]): string | null {
  return categories.find((c) => c.category === DIMENSION_CATEGORY[key])?.note ?? null;
}

function formatRange(alignment: ExperienceAlignment | null): string {
  if (!alignment) return "";
  const { candidateYears, requiredMinYears, requiredMaxYears } = alignment;
  const target =
    requiredMinYears !== null && requiredMaxYears !== null && requiredMinYears !== requiredMaxYears
      ? `${requiredMinYears}-${requiredMaxYears} years`
      : requiredMinYears !== null
        ? `${requiredMinYears}+ years`
        : "no requirement";
  return `you stated ${candidateYears ?? "no figure"} · role asks for ${target}`;
}
