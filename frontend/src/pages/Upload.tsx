import { useState, useCallback, useRef } from "react";
import { useNavigate } from "react-router-dom";
import {
  Upload as UploadIcon,
  FileText,
  X,
  Loader2,
  Briefcase,
  FileDown,
} from "lucide-react";
import Button from "@/components/ui/Button";
import Textarea from "@/components/ui/Textarea";
import Input from "@/components/ui/Input";
import ErrorAlert from "@/components/ui/ErrorAlert";
import { uploadResume } from "@/services/resumeService";
import { createJobDescription } from "@/services/jobService";
import { runAnalysis } from "@/services/analysisService";
import { useResumes } from "@/hooks/useResumes";
import { formatFileSize } from "@/lib/utils";

type Stage = "upload" | "loading" | "analyzing" | "complete" | "error";

const pipelineStages = [
  { key: "uploaded", label: "Resume uploaded" },
  { key: "extracting", label: "Extracting resume content" },
  { key: "chunking", label: "Processing resume sections" },
  { key: "analyzing", label: "Running RAG analysis" },
  { key: "generating", label: "Generating recommendations" },
];

export default function UploadPage() {
  const navigate = useNavigate();
  const { resumes, loading: resumesLoading } = useResumes();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [dragActive, setDragActive] = useState(false);
  const [targetRole, setTargetRole] = useState("");
  const [jobDescription, setJobDescription] = useState("");
  const [stage, setStage] = useState<Stage>("upload");
  const [activeStageIndex, setActiveStageIndex] = useState(-1);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState(0);

  const handleDrag = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") setDragActive(true);
    else if (e.type === "dragleave") setDragActive(false);
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    if (e.dataTransfer.files?.[0]) {
      setFile(e.dataTransfer.files[0]);
    }
  }, []);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files?.[0]) setFile(e.target.files[0]);
  };

  const simulateProgress = (startStage: number, duration: number) => {
    const interval = 50;
    const steps = duration / interval;
    let current = 0;
    const timer = setInterval(() => {
      current++;
      const baseProgress = (startStage / pipelineStages.length) * 100;
      const stageProgress = ((current / steps) * (100 / pipelineStages.length));
      setProgress(Math.min(baseProgress + stageProgress, 100));
      if (current >= steps) clearInterval(timer);
    }, interval);
    return timer;
  };

  const handleAnalyze = async () => {
    if (!file) return;

    try {
      setError(null);
      setStage("loading");

      setActiveStageIndex(0);
      setProgress(0);
      const timer1 = simulateProgress(0, 2000);

      const uploadResult = await uploadResume(file);
      clearInterval(timer1);

      setActiveStageIndex(1);
      setProgress(20);

      setActiveStageIndex(2);
      setProgress(40);

      let jobDescId: string | null = null;
      if (jobDescription.trim()) {
        const jd = await createJobDescription({
          title: targetRole || "Target Role",
          company: "",
          rawText: jobDescription,
        });
        jobDescId = jd.id;
      }

      if (!jobDescId) {
        setError("Please provide a job description to run the analysis.");
        setStage("error");
        return;
      }

      setActiveStageIndex(3);
      setStage("analyzing");
      const timer2 = simulateProgress(3, 3000);

      const analysis = await runAnalysis(uploadResult.id, jobDescId);
      clearInterval(timer2);

      setActiveStageIndex(4);
      setProgress(90);

      await new Promise((r) => setTimeout(r, 500));
      setProgress(100);

      await new Promise((r) => setTimeout(r, 300));
      setStage("complete");

      navigate(`/analysis/${analysis.id}`);
    } catch (err) {
      clearInterval(simulateProgress as unknown as ReturnType<typeof setInterval>);
      setError(
        err instanceof Error ? err.message : "Analysis failed. Please try again."
      );
      setStage("error");
    }
  };

  const reset = () => {
    setFile(null);
    setTargetRole("");
    setJobDescription("");
    setStage("upload");
    setActiveStageIndex(-1);
    setError(null);
    setProgress(0);
  };

  const acceptTypes = ".pdf,.doc,.docx";

  return (
    <div className="max-w-3xl mx-auto space-y-8">
      <div>
        <h1 className="font-display text-2xl sm:text-3xl font-bold text-foreground">
          Upload Your Resume
        </h1>
        <p className="mt-2 text-muted-foreground">
          Upload your resume and optionally provide a job description for targeted analysis.
        </p>
      </div>

      {stage === "loading" || stage === "analyzing" ? (
        <div className="rounded-xl border bg-white p-8 shadow-sm">
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-primary/10 mb-4">
              <Loader2 className="h-8 w-8 text-primary animate-spin" />
            </div>
            <h2 className="font-display text-xl font-semibold text-foreground">
              {stage === "loading" ? "Uploading Your Resume" : "Analyzing Your Resume"}
            </h2>
          </div>

          <div className="space-y-3 max-w-sm mx-auto">
            {pipelineStages.map((s, i) => {
              const isActive = i === activeStageIndex;
              const isComplete = i < activeStageIndex;
              const isPending = i > activeStageIndex;
              return (
                <div
                  key={s.key}
                  className={`flex items-center gap-3 rounded-lg p-3 transition-all ${
                    isActive
                      ? "bg-primary/5 border border-primary/20"
                      : isComplete
                      ? "bg-emerald-50 border border-emerald-100"
                      : "bg-slate-50 border border-slate-100 opacity-50"
                  }`}
                >
                  <div className={`flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold ${
                    isComplete
                      ? "bg-emerald-500 text-white"
                      : isActive
                      ? "bg-primary text-white"
                      : "bg-slate-200 text-slate-500"
                  }`}>
                    {isComplete ? "✓" : isActive ? "⟳" : "○"}
                  </div>
                  <span className={`text-sm font-medium ${
                    isActive ? "text-primary" : isComplete ? "text-emerald-700" : "text-muted-foreground"
                  }`}>
                    {s.label}
                  </span>
                </div>
              );
            })}
          </div>

          <div className="mt-6">
            <div className="h-2 rounded-full bg-slate-100 overflow-hidden">
              <div
                className="h-full rounded-full bg-gradient-to-r from-primary to-amber-500 transition-all duration-500"
                style={{ width: `${progress}%` }}
              />
            </div>
            <p className="text-xs text-muted-foreground text-center mt-2">
              {Math.round(progress)}% complete
            </p>
          </div>

          <div className="mt-6 text-center">
            <Button variant="ghost" onClick={reset}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        <>
          <div className="rounded-xl border bg-white p-6 shadow-sm">
            <input
              ref={fileInputRef}
              type="file"
              accept={acceptTypes}
              onChange={handleFileChange}
              className="hidden"
            />

            {!file ? (
              <div
                onDragEnter={handleDrag}
                onDragLeave={handleDrag}
                onDragOver={handleDrag}
                onDrop={handleDrop}
                onClick={() => fileInputRef.current?.click()}
                className={`flex flex-col items-center justify-center rounded-xl border-2 border-dashed p-12 cursor-pointer transition-all ${
                  dragActive
                    ? "border-primary bg-primary/5"
                    : "border-slate-200 hover:border-primary/50 hover:bg-slate-50"
                }`}
              >
                <div className="flex h-14 w-14 items-center justify-center rounded-full bg-primary/10 mb-4">
                  <UploadIcon className="h-7 w-7 text-primary" />
                </div>
                <p className="text-base font-medium text-foreground mb-1">
                  Drag & drop your resume here
                </p>
                <p className="text-sm text-muted-foreground">
                  or{" "}
                  <span className="text-primary font-medium hover:underline">
                    browse files
                  </span>
                </p>
                <p className="mt-3 text-xs text-muted-foreground">
                  Supports PDF, DOC, DOCX (max 10MB)
                </p>
              </div>
            ) : (
              <div className="flex items-center gap-4 p-4 rounded-lg bg-slate-50 border">
                <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-primary/10">
                  <FileText className="h-6 w-6 text-primary" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-foreground truncate">
                    {file.name}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {formatFileSize(file.size)} · {file.type || "document"}
                  </p>
                </div>
                <button
                  onClick={() => setFile(null)}
                  className="p-1.5 rounded-lg hover:bg-slate-200 text-muted-foreground"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            )}
          </div>

          <div className="rounded-xl border bg-white p-6 shadow-sm space-y-4">
            <div className="flex items-center gap-2 mb-2">
              <Briefcase className="h-5 w-5 text-muted-foreground" />
              <h3 className="font-display text-base font-semibold text-foreground">
                Target Job (Optional)
              </h3>
            </div>
            <Input
              label="Target Job Role"
              placeholder="e.g. Software Engineer, Product Manager"
              value={targetRole}
              onChange={(e) => setTargetRole(e.target.value)}
            />
            <Textarea
              label="Job Description"
              placeholder="Paste the job description here for a targeted analysis..."
              value={jobDescription}
              onChange={(e) => setJobDescription(e.target.value)}
            />
          </div>

          {error && <ErrorAlert message={error} onDismiss={() => setError(null)} />}

          <div className="flex items-center gap-3">
            <Button
              size="lg"
              onClick={handleAnalyze}
              disabled={!file}
              className="flex-1 sm:flex-none"
            >
              <FileDown className="h-4 w-4" />
              Analyze Resume
            </Button>
          </div>

          {resumes.length > 0 && (
            <div className="rounded-xl border bg-white p-6 shadow-sm">
              <h3 className="font-display text-base font-semibold text-foreground mb-4">
                Previously Uploaded
              </h3>
              <div className="space-y-2">
                {resumes.slice(0, 5).map((r) => (
                  <div
                    key={r.id}
                    className="flex items-center gap-3 p-3 rounded-lg bg-slate-50 border"
                  >
                    <FileText className="h-4 w-4 text-muted-foreground shrink-0" />
                    <span className="text-sm text-foreground truncate flex-1">
                      {r.fileName}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {new Date(r.createdAt).toLocaleDateString()}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
