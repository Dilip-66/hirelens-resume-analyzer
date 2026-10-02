import { useEffect, useState } from "react";
import { Sparkles, X } from "lucide-react";
import { contextualHeadline, contextualQuestion } from "@/lib/aiSuggestions";

interface ContextualAiPromptProps {
  /** Match score from the completed analysis; drives the headline. */
  matchScore: number | null;
  analysisId: string;
  onAsk: (question: string) => void;
}

/**
 * The post-analysis prompt: a small, dismissible card offering to explain the
 * score that was just produced.
 *
 * <p>Shown once per analysis, never as a modal, and dismissible without
 * consequence. It is an offer, not an interruption.
 */
export default function ContextualAiPrompt({
  matchScore,
  analysisId,
  onAsk,
}: ContextualAiPromptProps) {
  const storageKey = `hirelens_ai_prompt_dismissed:${analysisId}`;
  const [dismissed, setDismissed] = useState(true);

  // Start hidden, then reveal after mount if not previously dismissed for this
  // analysis. Avoids a flash for users who already said no.
  useEffect(() => {
    try {
      setDismissed(window.localStorage.getItem(storageKey) === "1");
    } catch {
      setDismissed(false);
    }
  }, [storageKey]);

  if (dismissed) return null;

  const question = contextualHeadline(matchScore);

  return (
    <div
      role="status"
      aria-live="polite"
      className="flex flex-col gap-3 rounded-xl border border-primary/20 bg-white p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between"
    >
      <div className="flex min-w-0 items-start gap-3">
        <span
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10"
          aria-hidden="true"
        >
          <Sparkles className="h-4 w-4 text-primary" />
        </span>
        <div className="min-w-0">
          <p className="text-sm font-semibold text-foreground">Your resume has been analysed.</p>
          <p className="mt-0.5 text-sm text-muted-foreground">{question}</p>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-2">
        <button
          type="button"
          onClick={() => onAsk(contextualQuestion(matchScore))}
          className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-primary px-3.5 text-sm font-medium text-white transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />
          Ask HireLens AI
        </button>
        <button
          type="button"
          onClick={() => {
            setDismissed(true);
            try {
              window.localStorage.setItem(storageKey, "1");
            } catch {
              // Private mode: dismissal simply will not persist.
            }
          }}
          aria-label="Dismiss AI suggestion"
          className="rounded-lg p-1.5 text-muted-foreground transition-colors hover:bg-slate-100 hover:text-foreground"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
