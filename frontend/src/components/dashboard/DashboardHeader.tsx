import { Link } from "react-router-dom";
import { Plus } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";

interface DashboardHeaderProps {
  hasAnalyses: boolean;
}

/**
 * Page heading and primary call to action.
 *
 * <p>The CTA routes to the existing /upload workflow rather than opening a
 * modal, so there is exactly one place an analysis is started.
 */
export default function DashboardHeader({ hasAnalyses }: DashboardHeaderProps) {
  const { user } = useAuth();

  return (
    <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <h1 className="font-display text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
          Welcome back{user?.name ? `, ${user.name}` : ""} 👋
        </h1>
        <p className="mt-1.5 max-w-xl text-sm text-muted-foreground">
          Analyze your resume against any job description and discover exactly
          where you stand.
        </p>
      </div>

      <Link
        to="/upload"
        className="inline-flex h-11 shrink-0 items-center justify-center gap-2 self-start rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground shadow-sm transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 sm:self-auto"
      >
        <Plus className="h-4 w-4" />
        {hasAnalyses ? "New Analysis" : "Start Your First Analysis"}
      </Link>
    </header>
  );
}
