import { useNavigate } from "react-router-dom";
import { FileText, Sparkles } from "lucide-react";
import Button from "@/components/ui/Button";
import Textarea from "@/components/ui/Textarea";
import Input from "@/components/ui/Input";
import ErrorAlert from "@/components/ui/ErrorAlert";
import ResumeDropzone from "@/components/upload/ResumeDropzone";
import AnalysisProgress from "@/components/upload/AnalysisProgress";
import { useAnalysisRunner } from "@/hooks/useAnalysisRunner";
import { useResumes } from "@/hooks/useResumes";

/**
 * Dedicated upload page.
 *
 * <p>Deliberately shares useAnalysisRunner and ResumeDropzone with the
 * dashboard's "Analyze a New Resume" card, so both entry points run the
 * identical sequence of real API calls and behave the same way. The only
 * difference is framing: this page is the full-screen flow, the dashboard is
 * the compact one.
 */
export default function UploadPage() {
  const navigate = useNavigate();
  const { resumes } = useResumes();
  const runner = useAnalysisRunner();

  const handleAnalyze = async () => {
    const analysis = await runner.run();
    if (analysis) {
      navigate(`/analysis/${analysis.id}`);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-8 pb-4">
      <div>
        <h1 className="font-display text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
          Upload Your Resume
        </h1>
        <p className="mt-1.5 text-sm text-muted-foreground">
          Upload your resume and a job description to get a targeted match analysis.
        </p>
      </div>

      {runner.busy ? (
        <AnalysisProgress phase={runner.phase} />
      ) : (
        <>
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
            <ResumeDropzone
              file={runner.file}
              onFileChange={runner.setFile}
            />
            <p className="mt-3 text-xs text-muted-foreground">
              Scanned or image-only PDFs can't be read — export a text-based PDF
              instead.
            </p>
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
            <h2 className="mb-4 font-display text-base font-semibold text-foreground">
              Target Job
            </h2>
            <div className="space-y-4">
              <Input
                label="Target job role (optional)"
                placeholder="e.g. Software Engineer, Product Manager"
                value={runner.targetRole}
                onChange={(e) => runner.setTargetRole(e.target.value)}
              />
              <Textarea
                label="Job description"
                placeholder="Paste the job description here for a targeted analysis..."
                value={runner.jobDescription}
                onChange={(e) => runner.setJobDescription(e.target.value)}
                required
              />
            </div>
          </section>

          {runner.error && (
            <ErrorAlert message={runner.error} onDismiss={() => runner.setError(null)} />
          )}

          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <Button
              size="lg"
              onClick={handleAnalyze}
              disabled={!runner.canRun}
              className="w-full sm:w-auto"
            >
              <Sparkles className="h-4 w-4" />
              Analyze Resume
            </Button>
            <Button variant="ghost" onClick={runner.reset} className="w-full sm:w-auto">
              Clear
            </Button>
          </div>

          {resumes.length > 0 && (
            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
              <h2 className="mb-4 font-display text-base font-semibold text-foreground">
                Previously Uploaded
              </h2>
              <div className="space-y-2">
                {resumes.slice(0, 5).map((r) => (
                  <div
                    key={r.id}
                    className="flex items-center gap-3 rounded-lg border border-slate-100 bg-slate-50/70 p-3"
                  >
                    <FileText className="h-4 w-4 shrink-0 text-muted-foreground" />
                    <span className="min-w-0 flex-1 truncate text-sm text-foreground">
                      {r.candidateName || r.fileName}
                    </span>
                    <span className="shrink-0 text-xs text-muted-foreground">
                      {new Date(r.createdAt).toLocaleDateString()}
                    </span>
                  </div>
                ))}
              </div>
            </section>
          )}
        </>
      )}
    </div>
  );
}
