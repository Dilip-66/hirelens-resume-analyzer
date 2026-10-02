import { Link } from "react-router-dom";
import { RotateCcw } from "lucide-react";
import Button from "@/components/ui/Button";

interface ReportErrorProps {
  onRetry: () => void;
}

/**
 * Failure state for a report that could not be loaded.
 *
 * <p>Deliberately says nothing about why: the underlying message can be an
 * axios error, a network string or an API payload, none of which are useful to
 * a candidate and all of which leak internals.
 */
export default function ReportError({ onRetry }: ReportErrorProps) {
  return (
    <div className="mx-auto max-w-lg py-20 text-center">
      <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
        Something went wrong while analysing your resume.
      </h1>
      <p className="mt-3 text-[15px] leading-relaxed text-muted-foreground">
        We could not load this report. Your resume and job description are still
        saved, so trying again is safe.
      </p>

      <div className="mt-8 flex flex-col items-center gap-3 sm:flex-row sm:justify-center">
        <Button onClick={onRetry}>
          <RotateCcw className="h-4 w-4" aria-hidden="true" />
          Try Again
        </Button>
        <Link to="/upload">
          <Button variant="outline" className="w-full sm:w-auto">
            Run a new analysis
          </Button>
        </Link>
      </div>
    </div>
  );
}
