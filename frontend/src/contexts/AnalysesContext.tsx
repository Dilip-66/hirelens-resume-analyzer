import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import {
  listAnalyses,
  runAnalysis as runAnalysisApi,
} from "@/services/analysisService";
import type { AnalysisResponse } from "@/types/analysis";
import {
  AnalysesContext,
  type AnalysesContextValue,
} from "@/contexts/analyses-context";

/**
 * Single owner of the user's analysis list.
 *
 * <p>Previously every consumer of `useAnalyses` kept its own copy of the state
 * and therefore issued its own `GET /api/analyses` on mount. On the dashboard
 * that meant three identical requests for one page (the dashboard itself, the
 * start-an-analysis card, and the shell that grounds the AI assistant), and the
 * copies could disagree - the assistant offering a different "latest analysis"
 * than the Recent Analyses list, which is exactly the sort of inconsistency that
 * makes a product feel broken rather than fast.
 *
 * <p>One fetch, one source of truth; `refresh()` after a new analysis updates
 * every consumer at once.
 */
export function AnalysesProvider({ children }: { children: ReactNode }) {
  const [analyses, setAnalyses] = useState<AnalysisResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      setAnalyses(await listAnalyses());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load analyses");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const run = useCallback(
    async (resumeId: string, jobDescriptionId: string) => {
      const result = await runAnalysisApi(resumeId, jobDescriptionId);
      await refresh();
      return result;
    },
    [refresh]
  );

  const value = useMemo<AnalysesContextValue>(
    () => ({ analyses, loading, error, run, refresh }),
    [analyses, loading, error, run, refresh]
  );

  return (
    <AnalysesContext.Provider value={value}>{children}</AnalysesContext.Provider>
  );
}
