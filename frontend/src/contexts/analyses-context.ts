import { createContext } from "react";
import type { AnalysisResponse } from "@/types/analysis";

export interface AnalysesContextValue {
  analyses: AnalysisResponse[];
  loading: boolean;
  error: string | null;
  /** Runs one analysis, then refreshes the list so it appears immediately. */
  run: (resumeId: string, jobDescriptionId: string) => Promise<AnalysisResponse>;
  refresh: () => Promise<void>;
}

/**
 * Split from the provider file, matching `auth-context.ts`.
 *
 * <p>Keeps the module that renders components free of non-component exports, so
 * Fast Refresh works on the provider while editing it in dev.
 */
export const AnalysesContext = createContext<AnalysesContextValue | null>(null);