import { useContext } from "react";
import {
  AnalysesContext,
  type AnalysesContextValue,
} from "@/contexts/analyses-context";

/**
 * Reads the shared analysis list.
 *
 * <p>Lives here rather than in the provider so every existing call site keeps
 * importing from `@/hooks/useAnalyses` unchanged, while all of them share the
 * single list held by `AnalysesProvider` - previously each consumer kept its own
 * copy and issued its own `GET /api/analyses`.
 */
export function useAnalyses(): AnalysesContextValue {
  const ctx = useContext(AnalysesContext);
  if (!ctx) {
    throw new Error("useAnalyses must be used within an AnalysesProvider");
  }
  return ctx;
}
