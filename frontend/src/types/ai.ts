export type AiSourceType = "resume" | "jobDescription" | "analysis";

export interface AiSource {
  type: AiSourceType;
  section: string;
}

export interface AiChatTurn {
  role: "user" | "assistant";
  content: string;
  /** Sources shown under the assistant's answer only. */
  sources?: AiSource[];
  /** Follow-up questions the backend suggested after this answer. */
  followUps?: string[];
  /** True once a real answer has been received, used to avoid flashing errors. */
  pending?: boolean;
  error?: boolean;
  /**
   * False when the backend answered without a resume/analysis behind it.
   * Surfaced in the UI so general advice is not read as personal analysis.
   */
  grounded?: boolean;
}

export interface AiChatResponse {
  analysisId: string | null;
  answer: string;
  suggestedFollowUps: string[];
  sources: AiSource[];
  /** False when the answer is general advice with no resume behind it. */
  grounded: boolean;
}

export interface QuestionCategory {
  id: string;
  label: string;
  questions: string[];
}
