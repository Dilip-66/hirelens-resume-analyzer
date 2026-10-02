export interface Resume {
  id: string;
  userId: string;
  fileName: string;
  rawText: string;
  candidateName: string | null;
  createdAt: string;
}

export interface JobDescription {
  id: string;
  userId: string;
  title: string | null;
  company: string | null;
  rawText: string;
  createdAt: string;
}

export interface AnalysisResponse {
  id: string;
  resumeId: string;
  jobDescriptionId: string;
  /** Whose resume this report is about. Falls back to the file name server-side. */
  candidateName: string | null;
  resumeFileName: string | null;
  jobTitle: string | null;
  jobCompany: string | null;
  matchScore: number;
  summary: string;
  strengths: string[];
  gaps: string[];
  matchedSkills: string[];
  missingSkills: string[];
  createdAt: string;
  /**
   * The explainable analysis: every requirement, its status, the evidence on both
   * sides, and the score dimension each one fed.
   *
   * Optional and nullable on purpose. Reports analysed before evidence-based
   * scoring have none, and the UI renders those exactly as it always did rather
   * than showing a panel of empty values that reads like a failure.
   *
   * Note that the LIST endpoint (`GET /api/analyses`) returns `detail: null` for
   * every report, including new ones - loading the evidence chain for every row on
   * a history page would cost two extra queries per report for data the list never
   * shows. Read it from the single-report endpoint.
   */
  detail?: AnalysisDetail | null;
  recommendations?: string[] | null;
}

/** The four match states. `NOT_EXPLICITLY_MENTIONED` means the resume is silent. */
export type MatchStatus =
  | "EXPLICIT_MATCH"
  | "STRONG_CONTEXTUAL_MATCH"
  | "PARTIAL_MATCH"
  | "NOT_EXPLICITLY_MENTIONED"
  | "CONFLICT";

export type RequirementCategory =
  | "REQUIRED_SKILL"
  | "PREFERRED_SKILL"
  | "EXPERIENCE"
  | "RESPONSIBILITY"
  | "EDUCATION"
  | "SOFT_SKILL"
  | "GENERAL";

export interface RequirementMatch {
  index: number;
  name: string;
  normalizedName: string;
  category: RequirementCategory;
  importance: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
  status: MatchStatus;
  confidence: number;
  /** 0-4, where 4 means the technology was used in professional work. */
  evidenceStrength: number;
  provenance: "EXPLICIT" | "INFERRED" | "NOT_EXPLICIT";
  /** The candidate's own sentences that decided the outcome. */
  resumeEvidence: string[];
  /** The job description lines the requirement came from. */
  jdEvidence: string[];
  explanation: string;
}

export interface CategoryScore {
  category: string;
  /** Null when the dimension was not measured. Never rendered as a zero. */
  score: number | null;
  weight: number;
  weightedScore: number | null;
  requirementsConsidered: number;
  assessed: boolean;
  note: string | null;
}

export interface ScoreBreakdown {
  requiredSkills: number | null;
  experience: number | null;
  responsibilities: number | null;
  preferredSkills: number | null;
  projects: number | null;
  education: number | null;
  ats: number | null;
  categories: CategoryScore[];
}

export interface ExperienceAlignment {
  candidateYears: number | null;
  requiredMinYears: number | null;
  requiredMaxYears: number | null;
  status:
    | "WITHIN_RANGE"
    | "BELOW_RANGE"
    | "ABOVE_RANGE"
    | "NOT_SPECIFIED"
    | "CANDIDATE_UNKNOWN";
  score: number | null;
  explanation: string;
}

export interface AnalysisDetail {
  /** False when the job description yielded no measurable requirements. */
  scored: boolean;
  matchLabel: string;
  /** Null when not scored, so the UI can say so rather than showing 0%. */
  overallScore: number | null;
  scoreBreakdown: ScoreBreakdown;
  experienceAlignment: ExperienceAlignment | null;
  requirements: RequirementMatch[];
  recommendations: string[];
  requirementsAnalysed: number;
  requirementsWithEvidence: number;
}

export interface JobDescriptionRequest {
  title: string;
  company: string;
  rawText: string;
}

export interface AnalysisRequest {
  resumeId: string;
  jobDescriptionId: string;
}

export interface ApiError {
  error: string;
}
