import { useEffect, useRef, useState } from "react";
import { Sparkles, X, Send, RotateCcw, FileText, Briefcase, BarChart3 } from "lucide-react";
import { askAssistant } from "@/services/aiAssistService";
import type { AiChatTurn } from "@/types/ai";
import { cn } from "@/lib/utils";

interface AiChatDrawerProps {
  open: boolean;
  onClose: () => void;
  /** Null means general mode: no resume, answers explicitly ungrounded. */
  analysisId: string | null;
  candidateName?: string | null;
  jobTitle?: string | null;
  /** Question chosen in the picker, sent as soon as the panel opens. */
  seedQuestion?: string | null;
  onSeedConsumed?: () => void;
  onBrowseQuestions: () => void;
}

const SOURCE_ICON = {
  resume: FileText,
  jobDescription: Briefcase,
  analysis: BarChart3,
} as const;

const SOURCE_LABEL = {
  resume: "Resume",
  jobDescription: "Job description",
  analysis: "Analysis",
} as const;

/** True when a request was cancelled by us (panel closed / analysis switched). */
function isAbort(err: unknown): boolean {
  return (
    typeof err === "object" &&
    err !== null &&
    "code" in err &&
    (err as { code?: string }).code === "ERR_CANCELED"
  );
}

/**
 * The assistant conversation panel.
 *
 * <p>Right-side drawer on desktop, full screen on mobile. Holds its own message
 * list and sends the bounded history with each turn; the server re-caps it, so a
 * long conversation cannot grow the prompt without limit.
 */
export default function AiChatDrawer({
  open,
  onClose,
  analysisId,
  candidateName,
  jobTitle,
  seedQuestion,
  onSeedConsumed,
  onBrowseQuestions,
}: AiChatDrawerProps) {
  const [turns, setTurns] = useState<AiChatTurn[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  // The transcript is kept across a trip through the question picker, but reset
  // when the assistant is re-anchored to a different analysis: those answers
  // were grounded in documents the user is no longer looking at.
  const analysedRef = useRef<string | null>(analysisId);
  useEffect(() => {
    if (analysedRef.current !== analysisId) {
      analysedRef.current = analysisId;
      setTurns([]);
    }
  }, [analysisId]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [turns]);

  // A question chosen in the picker is sent immediately on open. `seededRef` is
  // re-armed on close so re-asking the same question in a later conversation is
  // not silently swallowed by the dedupe.
  const seededRef = useRef<string | null>(null);
  useEffect(() => {
    if (!open) seededRef.current = null;
  }, [open]);
  useEffect(() => {
    if (!open || !seedQuestion) return;
    if (seededRef.current === seedQuestion) return;
    seededRef.current = seedQuestion;
    onSeedConsumed?.();
    void send(seedQuestion);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, seedQuestion]);

  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    const focusTimer = setTimeout(() => inputRef.current?.focus(), 50);
    return () => {
      document.removeEventListener("keydown", onKey);
      clearTimeout(focusTimer);
      // Abort any in-flight request so closing mid-answer does not resolve into
      // a dismissed panel.
      abortRef.current?.abort();
      previous?.focus();
    };
  }, [open, onClose]);

  const send = async (question: string) => {
    const value = question.trim();
    if (!value || sending) return;

    setInput("");
    setSending(true);
    setTurns((prev) => {
      // Guard against a seeded turn being appended twice: the question may
      // already be the last thing on screen from the picker.
      const last = prev[prev.length - 1];
      if (last?.role === "user" && last.content === value) {
        return [...prev, { role: "assistant", content: "", pending: true }];
      }
      return [
        ...prev,
        { role: "user", content: value },
        { role: "assistant", content: "", pending: true },
      ];
    });

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const history = turns;
      const response = await askAssistant(value, analysisId, history, controller.signal);
      setTurns((prev) => {
        const next = [...prev];
        // Replace the pending placeholder with the real answer.
        const last = next[next.length - 1];
        if (last?.pending) {
          next[next.length - 1] = {
            role: "assistant",
            content: response.answer,
            sources: response.sources,
            followUps: response.suggestedFollowUps,
            grounded: response.grounded,
          };
        }
        return next;
      });
    } catch (err) {
      // Closing the panel aborts the request on purpose. The user did not see
      // an error, so showing one - or worse, writing it into a transcript they
      // will read on reopen - would be noise.
      if (isAbort(err)) return;
      const message =
        err instanceof Error ? err.message : "AI Assistance is temporarily unavailable.";
      setTurns((prev) => {
        const next = [...prev];
        const last = next[next.length - 1];
        if (last?.pending) {
          next[next.length - 1] = {
            role: "assistant",
            content: message,
            error: true,
          };
        }
        return next;
      });
    } finally {
      setSending(false);
      abortRef.current = null;
    }
  };

  const lastAssistant = [...turns]
    .reverse()
    .find((t) => t.role === "assistant" && !t.pending && !t.error);
  const followUps = lastAssistant?.followUps ?? [];

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex justify-end bg-slate-900/40"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <aside
        role="dialog"
        aria-modal="true"
        aria-label="HireLens AI assistant"
        className="flex h-full w-full flex-col border-l border-slate-200 bg-white shadow-2xl sm:w-[460px] lg:w-[500px]"
      >
        <header className="flex items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
          <div className="min-w-0">
            <h2 className="flex items-center gap-2 font-display text-base font-semibold text-foreground">
              <span
                className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary/10"
                aria-hidden="true"
              >
                <Sparkles className="h-4 w-4 text-primary" />
              </span>
              HireLens AI
            </h2>
            <p className="mt-0.5 text-xs text-muted-foreground">
              Your resume analysis assistant
            </p>
            {(candidateName || jobTitle) && (
              <p className="mt-1.5 truncate text-xs text-muted-foreground">
                {candidateName || "Resume"}
                {jobTitle ? ` · ${jobTitle}` : ""}
              </p>
            )}
          </div>
          <div className="flex shrink-0 items-center gap-1">
            {turns.length > 0 && (
              <button
                type="button"
                onClick={() => setTurns([])}
                aria-label="Clear conversation"
                className="rounded-lg p-1.5 text-muted-foreground transition-colors hover:bg-slate-100 hover:text-foreground"
              >
                <RotateCcw className="h-4 w-4" />
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              aria-label="Close assistant"
              className="rounded-lg p-1.5 text-muted-foreground transition-colors hover:bg-slate-100 hover:text-foreground"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </header>

        <div
          ref={scrollRef}
          // Announces new answers to assistive tech; the panel opens on a
          // suggested question, so the conversation is the whole content of the
          // dialog and silence would otherwise look like a broken button.
          aria-live="polite"
          aria-busy={sending}
          className="min-h-0 flex-1 space-y-4 overflow-y-auto px-5 py-5"
        >
          {turns.length === 0 && (
            <div className="rounded-xl border border-slate-100 bg-slate-50/60 p-5 text-center">
              <p className="text-sm text-foreground">
                {analysisId
                  ? "Ask anything about your score, skills or this job."
                  : "Ask a general resume question."}
              </p>
              <p className="mt-1.5 text-xs text-muted-foreground">
                {analysisId
                  ? "Answers are grounded in your resume and job description."
                  : "No resume is attached, so answers will be general advice."}
              </p>
              <button
                type="button"
                onClick={onBrowseQuestions}
                className="mt-4 inline-flex h-9 items-center gap-1.5 rounded-lg border border-primary/30 bg-white px-3.5 text-sm font-medium text-primary transition-colors hover:bg-primary/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />
                Browse suggested questions
              </button>
            </div>
          )}

          {turns.map((turn, i) => (
            <div
              key={i}
              className={cn("flex", turn.role === "user" ? "justify-end" : "justify-start")}
            >
              <div
                className={cn(
                  "max-w-[85%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed",
                  turn.role === "user"
                    ? "bg-primary text-primary-foreground"
                    : turn.error
                    ? "border border-red-200 bg-red-50 text-red-800"
                    : "border border-slate-200 bg-slate-50 text-foreground"
                )}
              >
                {turn.pending ? (
                  <span className="flex items-center gap-2 text-muted-foreground">
                    <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-primary" />
                    <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-primary [animation-delay:150ms]" />
                    <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-primary [animation-delay:300ms]" />
                    <span className="ml-1 text-xs">Thinking</span>
                  </span>
                ) : (
                  <>
                    {/* General-mode answers are labelled as such, so advice that
                        is not grounded in this user's documents is never read as
                        if it were. */}
                    {turn.grounded === false && (
                      <p className="mb-1.5 text-[11px] font-medium text-muted-foreground">
                        General advice — no resume attached
                      </p>
                    )}
                    <p className="whitespace-pre-wrap">{turn.content}</p>
                    {turn.sources && turn.sources.length > 0 && (
                      <div className="mt-2.5 border-t border-slate-200 pt-2">
                        <p className="mb-1.5 text-[11px] font-medium text-muted-foreground">
                          Based on:
                        </p>
                        <div className="flex flex-wrap gap-1.5">
                          {turn.sources.map((source, j) => {
                            const Icon = SOURCE_ICON[source.type] ?? FileText;
                            return (
                              <span
                                key={j}
                                className="inline-flex items-center gap-1 rounded-md border border-slate-200 bg-white px-1.5 py-0.5 text-[11px] text-slate-600"
                              >
                                <Icon className="h-3 w-3" aria-hidden="true" />
                                {SOURCE_LABEL[source.type] ?? source.type} — {source.section}
                              </span>
                            );
                          })}
                        </div>
                      </div>
                    )}
                  </>
                )}
              </div>
            </div>
          ))}
        </div>

        {followUps.length > 0 && (
          <div className="flex flex-wrap gap-2 border-t border-slate-100 px-5 py-3">
            {followUps.map((q) => (
              <button
                key={q}
                type="button"
                onClick={() => send(q)}
                disabled={sending}
                className="rounded-full border border-primary/25 bg-primary/5 px-3 py-1.5 text-xs text-primary transition-colors hover:bg-primary/10 disabled:pointer-events-none disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {q}
              </button>
            ))}
          </div>
        )}

        <form
          className="flex items-center gap-2 border-t border-slate-100 bg-slate-50/60 px-5 py-4"
          onSubmit={(e) => {
            e.preventDefault();
            void send(input);
          }}
        >
          <input
            ref={inputRef}
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask a follow-up question..."
            aria-label="Ask a follow-up question"
            maxLength={500}
            disabled={sending}
            className="h-10 flex-1 rounded-lg border border-slate-200 bg-white px-3 text-sm text-foreground placeholder:text-slate-400 focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary disabled:opacity-60"
          />
          <button
            type="submit"
            disabled={!input.trim() || sending}
            aria-label="Send question"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary text-white transition-colors hover:bg-primary/90 disabled:pointer-events-none disabled:opacity-50"
          >
            <Send className="h-4 w-4" />
          </button>
        </form>
      </aside>
    </div>
  );
}
