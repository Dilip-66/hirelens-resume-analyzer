import { useNavigate } from "react-router-dom";
import { Briefcase, Sparkles } from "lucide-react";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import Textarea from "@/components/ui/Textarea";
import ErrorAlert from "@/components/ui/ErrorAlert";
import ResumeDropzone from "@/components/upload/ResumeDropzone";
import AnalysisProgress from "@/components/upload/AnalysisProgress";
import { useAnalysisRunner } from "@/hooks/useAnalysisRunner";
import { useAnalyses } from "@/hooks/useAnalyses";

/**
 * The start-an-analysis panel on the dashboard.
 *
 * <p>Drives the real workflow through the shared useAnalysisRunner hook, then
 * refreshes the analyses list so the report the user just generated appears in
 * Recent Analyses without a manual reload.
 */
export default function NewAnalysisCard() {
  const navigate = useNavigate();
  const { refresh } = useAnalyses();
  const runner = useAnalysisRunner();

  const handleAnalyze = async () => {
    const analysis = await runner.run();
    if (analysis) {
      await refresh();
      navigate(`/analysis/${analysis.id}`);
    }
  };

  if (runner.busy) {
    return <AnalysisProgress phase={runner.phase} />;
  }

  return (
    <section
      aria-labelledby="new-analysis-heading"
      className="rounded-xl border border-slate-200 bg-white shadow-sm"
    >
      <div className="border-b border-slate-100 px-5 py-4 sm:px-6">
        <h2
          id="new-analysis-heading"
          className="font-display text-base font-semibold text-foreground sm:text-lg"
        >
          Analyze a New Resume
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Upload your resume and a job description to get an AI-powered
          compatibility analysis.
        </p>
      </div>

      <div className="space-y-5 p-5 sm:p-6">
        <ResumeDropzone
          file={runner.file}
          onFileChange={runner.setFile}
          disabled={runner.busy}
        />

        <div className="space-y-4 rounded-xl border border-slate-100 bg-slate-50/60 p-4">
          <div className="flex items-center gap-2">
            <Briefcase className="h-4 w-4 text-muted-foreground" />
            <h3 className="text-sm font-semibold text-foreground">
              Target job
            </h3>
            <span className="text-xs text-muted-foreground">(optional)</span>
          </div>

          <Input
            label="Target job role"
            placeholder="e.g. Backend Engineer"
            value={runner.targetRole}
            onChange={(e) => runner.setTargetRole(e.target.value)}
            disabled={runner.busy}
          />

          <Textarea
            label="Job description"
            placeholder="Paste the full job description here..."
            value={runner.jobDescription}
            onChange={(e) => runner.setJobDescription(e.target.value)}
            disabled={runner.busy}
            required
          />
        </div>

        {runner.error && (
          <ErrorAlert message={runner.error} onDismiss={() => runner.setError(null)} />
        )}

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <Button
            size="lg"
            onClick={handleAnalyze}
            disabled={!runner.canRun}
            className="w-full sm:w-auto"
          >
            <Sparkles className="h-4 w-4" />
            Analyze Resume
          </Button>
          <p className="text-xs text-muted-foreground">
            Supports PDF and DOCX · Analysis usually takes a few seconds
          </p>
        </div>
      </div>
    </section>
  );
}
