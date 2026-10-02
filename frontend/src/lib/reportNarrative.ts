import type { AnalysisResponse } from "@/types/analysis";
import { getMatchBand } from "@/lib/utils";

/**
 * Copy for the resume intelligence report, derived from the stored analysis.
 *
 * <p>Every sentence here is assembled from fields the analysis API actually
 * returns: `matchScore`, `summary`, `strengths`, `gaps`, `matchedSkills`,
 * `missingSkills` and the resume/job identity fields. Nothing is estimated and
 * nothing is re-scored - the pass/fail band is the existing UI label from
 * `getMatchBand`, and the skill coverage ratio is plain arithmetic over the two
 * skill lists the backend scans for deterministically.
 *
 * <p>Sections for experience, projects and ATS are deliberately absent from the
 * report because the analysis response does not carry that data. See
 * `Analysis.tsx` for where they would slot in if it ever does.
 */

function clean(value: string | null | undefined): string {
  return (value ?? "").trim();
}

/** Joins a list the way a sentence reads: "a", "a and b", "a, b and c". */
export function sentenceList(values: string[], max = 3): string {
  const items = values.filter(Boolean).slice(0, max);
  if (items.length === 0) return "";
  if (items.length === 1) return items[0];
  return `${items.slice(0, -1).join(", ")} and ${items[items.length - 1]}`;
}

/**
 * Sentence-starters that should be lower-cased when folded into a sentence.
 *
 * <p>Only these. The previous version lower-cased the first letter of anything
 * capitalised, on the assumption that gaps always arrive as sentences - which is
 * how "Code reviews - not explicitly mentioned in the resume" became "ode
 * reviews - not explicitly mentioned in the resume" when spliced into the closing
 * snapshot. A proper noun at the start of a label is not a sentence-starter, and
 * the list is short enough to be exact rather than clever.
 */
const SENTENCE_STARTERS = new Set([
  "no",
  "not",
  "none",
  "lack",
  "lacks",
  "lacking",
  "missing",
  "limited",
  "little",
  "cannot",
  "can't",
  "unable",
  "does",
  "doesn't",
  "has",
  "haven't",
  "hasn't",
  "never",
  "without",
  "some",
  "there",
  "it",
  "this",
  "they",
]);

/**
 * Folds an item into the middle of a sentence.
 *
 * <p>Strengths and gaps arrive as standalone text ("No cloud experience listed"),
 * so splicing one in verbatim produces "flagged No cloud experience listed as...".
 * Only a leading capital that is genuinely a sentence-starter is touched; nothing
 * else is rewritten, and a label that begins with a proper noun is left alone.
 */
function inline(value: string): string {
  const firstWord = value.split(/\s+/, 1)[0].replace(/[^A-Za-z']/g, "").toLowerCase();
  if (firstWord && SENTENCE_STARTERS.has(firstWord)) {
    return value.slice(1).replace(/^\s+/, "");
  }
  return value;
}

/** Candidate identity, with the file name as the fallback the server also uses. */
export function reportCandidate(analysis: AnalysisResponse): string {
  return clean(analysis.candidateName) || clean(analysis.resumeFileName) || "Candidate";
}

/** Short role descriptor for the header and the closing snapshot. */
export function reportRole(analysis: AnalysisResponse): string {
  const title = clean(analysis.jobTitle);
  const company = clean(analysis.jobCompany);
  if (title && company) return `${title} at ${company}`;
  return title || "the selected role";
}

/** The "analysed against ..." half of the header line. */
export function reportComparisonLine(analysis: AnalysisResponse): string {
  const title = clean(analysis.jobTitle);
  return title
    ? `Resume analysed against the ${title} position`
    : "Resume analysed against the selected job description";
}

export interface SkillCoverage {
  matched: number;
  missing: number;
  total: number;
  /** Null when the scan found nothing at all - never render 0% for "no data". */
  percent: number | null;
}

/**
 * Share of the job's detected skills that the resume actually contains.
 *
 * <p>This is the only percentage the report can honestly draw: both lists come
 * from the backend's deterministic skill scan, so their ratio is arithmetic on
 * real data rather than a score.
 */
export function skillCoverage(analysis: AnalysisResponse): SkillCoverage {
  const matched = analysis.matchedSkills.length;
  const missing = analysis.missingSkills.length;
  const total = matched + missing;
  return {
    matched,
    missing,
    total,
    percent: total > 0 ? Math.round((matched / total) * 100) : null,
  };
}

/** One factual line under the score, in place of an invented category breakdown. */
export function coverageLine(analysis: AnalysisResponse): string | null {
  const { matched, missing, total } = skillCoverage(analysis);
  if (total === 0) return null;
  if (missing === 0) {
    return `All ${total} skills detected in the job description appear in your resume.`;
  }
  return `${matched} of ${total} skills detected in the job description appear in your resume.`;
}

export interface ReportMetric {
  label: string;
  value: string;
  hint?: string;
  tone?: "default" | "positive" | "caution";
}

/**
 * The four numbers worth seeing above the fold.
 *
 * <p>All four are counts of real list entries. Experience, education and project
 * readiness are not included because the analysis response has no such fields.
 */
export function quickMetrics(analysis: AnalysisResponse): ReportMetric[] {
  const { matched, missing } = skillCoverage(analysis);
  const metrics: ReportMetric[] = [
    {
      label: "Skills matched",
      value: String(matched),
      tone: "positive",
    },
    {
      label: "Skills missing",
      value: String(missing),
      tone: missing > 0 ? "caution" : "default",
    },
    {
      label: "Strengths",
      value: String(analysis.strengths.length),
    },
    {
      label: "Open gaps",
      value: String(analysis.gaps.length),
    },
  ];
  return metrics;
}

/**
 * The closing paragraph, composed from the actual result.
 *
 * <p>Deliberately sentence-assembled rather than handed to the model: the report
 * must never block on a generation call to describe data the client already has.
 */
export function snapshotParagraph(analysis: AnalysisResponse): string {
  const band = getMatchBand(analysis.matchScore).label.toLowerCase();
  const role = reportRole(analysis);
  const topSkills = sentenceList(analysis.matchedSkills, 3);
  const topMissing = sentenceList(analysis.missingSkills, 3);

  const sentences: string[] = [
    `This resume scores ${analysis.matchScore}% against ${role} - ${band} overall.`,
  ];

  if (topSkills) {
    sentences.push(
      `The clearest alignment is in ${topSkills}, which the analysis found directly in the resume.`
    );
  }

  if (analysis.gaps.length > 0) {
    sentences.push(
      `The analysis flagged ${sentenceList(analysis.gaps.map(inline), 2)} as the main things holding the score back.`
    );
  } else if (topMissing) {
    sentences.push(`The requirements still absent from the resume are ${topMissing}.`);
  } else {
    sentences.push("No skill gaps were flagged for this role.");
  }

  return sentences.join(" ");
}

export interface NextStep {
  /** Short label for the numbered marker; real data only. */
  detail: string;
  /** Where the step came from, so nothing reads as invented advice. */
  source: "gap" | "skill";
}

/**
 * The three things to do next.
 *
 * <p>Uses the gaps the analysis reported; when it reported none, falls back to
 * the missing skills themselves. The full list stays on the recommendations
 * page - this is the short version, not a replacement.
 */
export function nextSteps(analysis: AnalysisResponse): NextStep[] {
  if (analysis.gaps.length > 0) {
    return analysis.gaps.slice(0, 3).map((gap) => ({ detail: gap, source: "gap" }));
  }
  if (analysis.missingSkills.length > 0) {
    return analysis.missingSkills.slice(0, 3).map((skill) => ({
      detail: `Add evidence of ${skill} to your skills or projects, if you have it.`,
      source: "skill",
    }));
  }
  return [];
}
