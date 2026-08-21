import { useState, useEffect, useCallback } from "react";
import {
  listAnalyses,
  runAnalysis as runAnalysisApi,
} from "@/services/analysisService";
import type { AnalysisResponse } from "@/types/analysis";

export function useAnalyses() {
  const [analyses, setAnalyses] = useState<AnalysisResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAnalyses = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await listAnalyses();
      setAnalyses(data);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load analyses"
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAnalyses();
  }, [fetchAnalyses]);

  const run = async (
    resumeId: string,
    jobDescriptionId: string
  ): Promise<AnalysisResponse> => {
    const result = await runAnalysisApi(resumeId, jobDescriptionId);
    await fetchAnalyses();
    return result;
  };

  return { analyses, loading, error, run, refresh: fetchAnalyses };
}
