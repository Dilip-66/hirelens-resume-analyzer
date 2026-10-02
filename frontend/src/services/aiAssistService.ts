import apiClient from "@/lib/api-client";
import type { AiChatResponse, AiChatTurn, QuestionCategory } from "@/types/ai";

export async function fetchQuestionCategories(): Promise<QuestionCategory[]> {
  const { data } = await apiClient.get<QuestionCategory[]>("/api/ai-assistance/questions");
  return data;
}

/**
 * Sends one assistant turn.
 *
 * <p>Only the analysis id travels to the server - never resume text. The
 * backend resolves the documents itself and enforces ownership, so a tampered
 * request cannot point the assistant at someone else's report.
 */
export async function askAssistant(
  question: string,
  analysisId: string | null,
  history: AiChatTurn[],
  signal?: AbortSignal
): Promise<AiChatResponse> {
  const { data } = await apiClient.post<AiChatResponse>(
    "/api/ai-assistance/chat",
    {
      question,
      analysisId: analysisId ?? undefined,
      history: history
        .filter((turn) => !turn.pending && !turn.error)
        .map((turn) => ({ role: turn.role, content: turn.content })),
    },
    { signal }
  );
  return data;
}
