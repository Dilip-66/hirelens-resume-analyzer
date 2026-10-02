import { ArrowRight, Sparkles } from "lucide-react";

interface AssistantCtaProps {
  /** Opens the assistant with this report already grounding the conversation. */
  onAsk: () => void;
}

/**
 * The hand-off to the grounded assistant.
 *
 * <p>Tinted with the brand colour rather than a purple AI gradient: this is part
 * of HireLens, not a bolted-on chat widget.
 */
export default function AssistantCta({ onAsk }: AssistantCtaProps) {
  return (
    <section className="report-section rounded-2xl border border-primary/20 bg-primary/[0.035] px-7 py-9 sm:px-10 sm:py-10">
      <div className="flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0 max-w-xl">
          <p className="inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-primary">
            <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />
            HireLens AI
          </p>
          <h2 className="mt-3 font-display text-xl font-semibold tracking-tight text-foreground">
            Need help understanding your result?
          </h2>
          <p className="mt-2 text-sm leading-relaxed text-foreground/75">
            Ask about your score, the missing skills, what the job description really
            asks for, or how to revise your resume. The assistant answers from this
            resume, this job description and this analysis.
          </p>
        </div>

        <button
          type="button"
          onClick={onAsk}
          className="inline-flex h-11 shrink-0 items-center justify-center gap-2 rounded-lg bg-primary px-5 text-sm font-medium text-primary-foreground shadow-sm transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 print:hidden"
        >
          Ask HireLens AI
          <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
    </section>
  );
}
