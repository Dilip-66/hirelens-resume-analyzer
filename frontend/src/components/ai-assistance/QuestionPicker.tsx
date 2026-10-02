import { useEffect, useRef, useState } from "react";
import { Sparkles, X, Loader2 } from "lucide-react";
import { fetchQuestionCategories } from "@/services/aiAssistService";
import type { QuestionCategory } from "@/types/ai";

interface QuestionPickerProps {
  open: boolean;
  onClose: () => void;
  onSelect: (question: string) => void;
  /** Offered first when a completed analysis exists. */
  prioritizedQuestions?: string[];
}

const CATEGORY_ICONS: Record<string, string> = {
  score: "◔",
  skills: "◈",
  experience: "◷",
  improvement: "◐",
  job: "◆",
  general: "○",
};

/**
 * Medium-sized question selector.
 *
 * <p>Deliberately shown before the chat panel rather than dumping the user into
 * a blank input: most people looking at a match score have one of a handful of
 * questions in mind, and offering them is faster and more useful than making
 * them type. Free text stays available for everything else.
 */
export default function QuestionPicker({
  open,
  onClose,
  onSelect,
  prioritizedQuestions = [],
}: QuestionPickerProps) {
  const [categories, setCategories] = useState<QuestionCategory[]>([]);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [custom, setCustom] = useState("");
  const dialogRef = useRef<HTMLDivElement>(null);
  const firstFieldRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!open || categories.length > 0) return;
    let cancelled = false;
    setLoading(true);
    fetchQuestionCategories()
      .then((data) => {
        if (!cancelled) {
          setCategories(data);
          setFailed(false);
        }
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, categories.length]);

  // Focus management: move focus in on open, and trap Escape.
  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    firstFieldRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      previous?.focus();
    };
  }, [open, onClose]);

  // Keep Tab inside the dialog while it is open.
  useEffect(() => {
    if (!open || !dialogRef.current) return;
    const node = dialogRef.current;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Tab") return;
      const focusable = node.querySelectorAll<HTMLElement>(
        'button, input, [href], select, textarea, [tabindex]:not([tabindex="-1"])'
      );
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };
    node.addEventListener("keydown", onKey);
    return () => node.removeEventListener("keydown", onKey);
  }, [open]);

  if (!open) return null;

  const submitCustom = () => {
    const value = custom.trim();
    if (!value) return;
    setCustom("");
    onSelect(value);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/40 p-0 sm:items-center sm:p-6"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="qp-title"
        className="flex max-h-[92vh] w-full flex-col overflow-hidden rounded-t-2xl border border-slate-200 bg-white shadow-xl sm:max-w-lg sm:rounded-2xl"
      >
        <div className="flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
          <div>
            <h2
              id="qp-title"
              className="flex items-center gap-2 font-display text-base font-semibold text-foreground"
            >
              <Sparkles className="h-4 w-4 text-primary" aria-hidden="true" />
              What would you like help with?
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Choose a question or ask your own.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close question picker"
            className="rounded-lg p-1.5 text-muted-foreground transition-colors hover:bg-slate-100 hover:text-foreground"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
          {loading && (
            <p className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              Loading questions...
            </p>
          )}

          {failed && (
            <p className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
              Could not load suggested questions. You can still type your own below.
            </p>
          )}

          {!loading && !failed && prioritizedQuestions.length > 0 && (
            <section className="mb-5">
              <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-primary">
                Suggested for your latest report
              </h3>
              <div className="space-y-2">
                {prioritizedQuestions.map((q) => (
                  <button
                    key={q}
                    type="button"
                    onClick={() => onSelect(q)}
                    className="flex w-full items-center gap-2.5 rounded-lg border border-primary/20 bg-primary/5 px-3.5 py-2.5 text-left text-sm text-foreground transition-colors hover:border-primary/40 hover:bg-primary/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    <Sparkles
                      className="h-3.5 w-3.5 shrink-0 text-primary"
                      aria-hidden="true"
                    />
                    {q}
                  </button>
                ))}
              </div>
            </section>
          )}

          {!loading &&
            !failed &&
            categories.map((category) => (
              <section key={category.id} className="mb-5 last:mb-0">
                <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  {category.label}
                </h3>
                <div className="space-y-1.5">
                  {category.questions.map((q) => (
                    <button
                      key={q}
                      type="button"
                      onClick={() => onSelect(q)}
                      className="flex w-full items-center gap-2.5 rounded-lg border border-slate-200 bg-white px-3.5 py-2.5 text-left text-sm text-foreground transition-colors hover:border-primary/30 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    >
                      <span
                        className="w-4 shrink-0 text-center text-xs text-slate-300"
                        aria-hidden="true"
                      >
                        {CATEGORY_ICONS[category.id] ?? "•"}
                      </span>
                      {q}
                    </button>
                  ))}
                </div>
              </section>
            ))}
        </div>

        <div className="border-t border-slate-100 bg-slate-50/60 px-5 py-4">
          <label
            htmlFor="qp-custom"
            className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-muted-foreground"
          >
            Ask your own question
          </label>
          <div className="flex gap-2">
            <input
              id="qp-custom"
              ref={firstFieldRef as React.RefObject<HTMLInputElement>}
              type="text"
              value={custom}
              onChange={(e) => setCustom(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  submitCustom();
                }
              }}
              placeholder="Type your question..."
              maxLength={500}
              className="h-10 flex-1 rounded-lg border border-slate-200 bg-white px-3 text-sm text-foreground placeholder:text-slate-400 focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            />
            <button
              type="button"
              onClick={submitCustom}
              disabled={!custom.trim()}
              aria-label="Ask your own question"
              className="h-10 shrink-0 rounded-lg bg-primary px-4 text-sm font-medium text-white transition-colors hover:bg-primary/90 disabled:pointer-events-none disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            >
              Ask
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
