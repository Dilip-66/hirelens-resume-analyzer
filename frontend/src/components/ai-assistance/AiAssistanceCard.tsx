import { Sparkles, ArrowRight, BarChart3, Target, Wand2, MessagesSquare } from "lucide-react";
import { useAiAssistant } from "@/contexts/AiAssistantContext";

const FEATURES = [
  { icon: BarChart3, label: "Understand your score" },
  { icon: Target, label: "Find skill gaps" },
  { icon: Wand2, label: "Improve your resume" },
  { icon: MessagesSquare, label: "Ask follow-up questions" },
];

/**
 * The AI Assistance section on the dashboard.
 *
 * <p>Visually distinct from the analysis CTAs: outlined and airy rather than a
 * filled primary button, so it reads as a different kind of action rather than
 * competing with "Analyze Resume".
 */
export default function AiAssistanceCard() {
  const { openPicker, activeAnalysisId } = useAiAssistant();
  const grounded = Boolean(activeAnalysisId);

  return (
    <section
      aria-labelledby="ai-assistance-heading"
      className="overflow-hidden rounded-xl border border-primary/20 bg-white shadow-sm"
    >
      <div className="grid gap-6 p-5 sm:p-6 lg:grid-cols-[1.4fr_1fr] lg:items-center">
        <div className="min-w-0">
          <h2
            id="ai-assistance-heading"
            className="flex items-center gap-2 font-display text-base font-semibold text-foreground sm:text-lg"
          >
            <span
              className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10"
              aria-hidden="true"
            >
              <Sparkles className="h-4 w-4 text-primary" />
            </span>
            AI Assistance
          </h2>

          <p className="mt-2.5 text-sm leading-relaxed text-muted-foreground">
            {grounded
              ? "Your resume has more to say. Ask HireLens AI for a grounded explanation of your score, your skill gaps, and what to change next."
              : "Have questions about your resume or analysis? Ask HireLens AI about job requirements, ATS compatibility, and how to strengthen your resume."}
          </p>

          <button
            type="button"
            onClick={() => openPicker()}
            className="mt-4 inline-flex h-10 items-center gap-2 rounded-lg border border-primary/40 bg-white px-4 text-sm font-medium text-primary transition-colors hover:bg-primary/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          >
            <Sparkles className="h-4 w-4" aria-hidden="true" />
            Ask HireLens AI
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>

        <ul className="grid grid-cols-1 gap-2.5 rounded-xl border border-slate-100 bg-slate-50/60 p-4 sm:grid-cols-2 lg:grid-cols-1">
          {FEATURES.map((feature) => {
            const Icon = feature.icon;
            return (
              <li key={feature.label} className="flex items-center gap-2.5">
                <span
                  className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-white text-primary shadow-sm"
                  aria-hidden="true"
                >
                  <Icon className="h-3.5 w-3.5" />
                </span>
                <span className="text-sm text-foreground">{feature.label}</span>
              </li>
            );
          })}
        </ul>
      </div>

      {!grounded && (
        <p className="border-t border-slate-100 bg-slate-50/60 px-5 py-2.5 text-xs text-muted-foreground sm:px-6">
          No completed analysis yet — HireLens AI will answer general resume questions until
          you analyse a resume.
        </p>
      )}
    </section>
  );
}
