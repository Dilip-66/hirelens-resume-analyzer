import { useCallback, useState } from "react";
import { uploadResume } from "@/services/resumeService";
import { createJobDescription } from "@/services/jobService";
import { runAnalysis } from "@/services/analysisService";
import type { AnalysisResponse } from "@/types/analysis";

export type AnalysisPhase = "idle" | "uploading" | "analyzing" | "done" | "error";

/**
 * The resume-to-report workflow, shared by the dashboard and the upload page.
 *
 * <p>Extracted so both surfaces drive the identical sequence of real API calls -
 * upload, create job description, run analysis - rather than each page
 * re-implementing (and slowly diverging from) it.
 *
 * <p>Progress is reported as discrete phases that flip on actual request
 * boundaries, not a simulated percentage. A timer-driven bar that reaches 100%
 * while the model is still generating is worse than no bar at all, because it
 * tells the user they have a finished report before one exists.
 */
export function useAnalysisRunner() {
  const [file, setFile] = useState<File | null>(null);
  const [targetRole, setTargetRole] = useState("");
  const [jobDescription, setJobDescription] = useState("");
  const [phase, setPhase] = useState<AnalysisPhase>("idle");
  const [error, setError] = useState<string | null>(null);

  const busy = phase === "uploading" || phase === "analyzing";

  const reset = useCallback(() => {
    setFile(null);
    setTargetRole("");
    setJobDescription("");
    setPhase("idle");
    setError(null);
  }, []);

  const run = useCallback(async (): Promise<AnalysisResponse | null> => {
    if (!file) {
      setError("Please choose a resume file first.");
      setPhase("error");
      return null;
    }
    // Checked before uploading: the backend needs a job description to retrieve
    // against, and uploading first would leave an orphan resume in the account
    // on every failed attempt.
    if (!jobDescription.trim()) {
      setError("Please paste a job description to run the analysis.");
      setPhase("error");
      return null;
    }

    try {
      setError(null);
      setPhase("uploading");

      const resume = await uploadResume(file);
      const job = await createJobDescription({
        title: targetRole.trim() || "Target Role",
        company: "",
        rawText: jobDescription.trim(),
      });

      setPhase("analyzing");
      const analysis = await runAnalysis(resume.id, job.id);

      setPhase("done");
      return analysis;
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Analysis failed. Please try again."
      );
      setPhase("error");
      return null;
    }
  }, [file, jobDescription, targetRole]);

  return {
    file,
    setFile,
    targetRole,
    setTargetRole,
    jobDescription,
    setJobDescription,
    phase,
    error,
    setError,
    busy,
    /** True when there is enough input to run a meaningful analysis. */
    canRun: Boolean(file) && jobDescription.trim().length > 0,
    run,
    reset,
  };
}
