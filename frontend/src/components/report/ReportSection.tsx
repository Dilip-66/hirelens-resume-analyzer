import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface ReportSectionProps {
  title: string;
  subtitle?: string;
  /** Optional trailing control, e.g. a "show all" toggle or a link. */
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}

/**
 * The vertical rhythm of the report: title, one line of support copy, content.
 *
 * <p>Sections sit on the page background with whitespace between them rather
 * than inside a card, so the report reads as a document instead of a wall of
 * panels. `report-section` keeps a section from being split across pages when
 * the report is printed.
 */
export default function ReportSection({
  title,
  subtitle,
  action,
  children,
  className,
}: ReportSectionProps) {
  return (
    <section className={cn("report-section", className)}>
      <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-2">
        <div className="min-w-0">
          <h2 className="font-display text-lg font-semibold tracking-tight text-foreground sm:text-xl">
            {title}
          </h2>
          {subtitle && (
            <p className="mt-1.5 max-w-2xl text-sm leading-relaxed text-muted-foreground">
              {subtitle}
            </p>
          )}
        </div>
        {action && <div className="shrink-0">{action}</div>}
      </div>
      <div className="mt-6">{children}</div>
    </section>
  );
}
