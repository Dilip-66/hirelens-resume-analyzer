import { FileUp, Search, BarChart3, TrendingUp } from "lucide-react";

const STEPS = [
  {
    icon: FileUp,
    title: "Upload",
    body: "Add your resume and target job description.",
  },
  {
    icon: Search,
    title: "Analyze",
    body: "HireLens compares your resume with the job requirements.",
  },
  {
    icon: BarChart3,
    title: "Understand",
    body: "See skills, gaps, strengths, and match score.",
  },
  {
    icon: TrendingUp,
    title: "Improve",
    body: "Use the recommendations to strengthen your application.",
  },
];

/** Visually lightweight closing explainer. */
export default function HowItWorks() {
  return (
    <section
      aria-labelledby="how-heading"
      className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6"
    >
      <h2
        id="how-heading"
        className="mb-5 font-display text-base font-semibold text-foreground sm:text-lg"
      >
        How HireLens Works
      </h2>

      <ol className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-4">
        {STEPS.map((step, i) => {
          const Icon = step.icon;
          return (
            <li key={step.title} className="flex items-start gap-3">
              <div className="relative shrink-0">
                <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <Icon className="h-4 w-4" />
                </span>
                {i < STEPS.length - 1 && (
                  <span
                    aria-hidden="true"
                    className="absolute left-full top-1/2 ml-1 hidden h-px w-4 bg-slate-200 xl:block"
                  />
                )}
              </div>
              <div className="min-w-0">
                <h3 className="text-sm font-semibold text-foreground">
                  <span className="mr-1.5 text-xs font-bold text-primary">
                    {i + 1}.
                  </span>
                  {step.title}
                </h3>
                <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
                  {step.body}
                </p>
              </div>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
