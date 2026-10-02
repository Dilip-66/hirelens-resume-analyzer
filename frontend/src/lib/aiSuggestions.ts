import type { AnalysisResponse } from "@/types/analysis";

/**
 * Score-band rules for the assistant's prompts.
 *
 * <p>Mirrors `QuestionCatalog#contextualQuestion` and
 * `QuestionCatalog#catalogueQuestion` on the server, which own the same rules.
 * Kept client-side deliberately: both values are needed to render the
 * post-analysis prompt the instant a report arrives, and fetching a string to
 * avoid ~10 duplicated lines would add a request to the critical path of every
 * analysis and delay the prompt for no benefit.
 *
 * <p>These are UI prompts only. Nothing here reads or writes a score - the
 * score always comes from the stored analysis.
 */

/** Headline shown to the user. Copy written to be read, not asked. */
export function contextualHeadline(matchScore: number | null): string {
  if (matchScore === null) return "Want to know more about this analysis?";
  if (matchScore >= 90) return "Want to know what makes your resume a strong match?";
  if (matchScore >= 70)
    return "Want to know what is keeping your score from being higher?";
  if (matchScore >= 40)
    return "Want to know which skills are holding your score back?";
  if (matchScore >= 1)
    return "Want to know which skills are missing from your resume?";
  return "Want to understand why your resume didn't match this job?";
}

/**
 * The question the post-analysis CTA actually submits.
 *
 * <p>Never the headline: "Want to know what is keeping your score from being
 * higher?" is a sentence addressed to the user, and sending it to the model
 * would arrive as something to agree with rather than something to answer.
 */
export function contextualQuestion(matchScore: number | null): string {
  if (matchScore === null) return "Why did I get this score?";
  if (matchScore >= 90) return "What parts of my resume matched the job?";
  if (matchScore >= 70) return "What is lowering my match score?";
  if (matchScore >= 1) return "Which skills am I missing?";
  return "Why did I get this score?";
}

/**
 * The questions worth putting in front of the user for a specific report.
 *
 * <p>Ordered by what is most likely to be useful next given the actual result -
 * a zero or weak score leads with the cause, a strong one with what to improve.
 * Every entry is a catalogue question, so the model has a grounded way to answer
 * whatever gets clicked.
 */
export function prioritizedQuestions(
  analysis: Pick<AnalysisResponse, "matchScore" | "missingSkills"> | null
): string[] {
  if (!analysis) return [];

  const hasMissing = analysis.missingSkills.length > 0;
  if (analysis.matchScore <= 0) {
    return [
      "Why did I get this score?",
      "Which skills am I missing?",
      "What are the most important requirements in this job?",
    ];
  }
  if (analysis.matchScore < 70) {
    return [
      "What is lowering my match score?",
      "Which skills am I missing?",
      "What should I change first?",
    ];
  }
  return [
    "What is lowering my match score?",
    hasMissing ? "Which skills am I missing?" : "How can I increase my match score?",
    "How can I improve my resume?",
  ];
}