import { useCallback, useRef, useState } from "react";
import { Upload as UploadIcon, FileText, X } from "lucide-react";
import { cn, formatFileSize } from "@/lib/utils";

interface ResumeDropzoneProps {
  file: File | null;
  onFileChange: (file: File | null) => void;
  accept?: string;
  disabled?: boolean;
  className?: string;
}

/**
 * Drag-and-drop resume picker with a selected-file preview.
 *
 * <p>Shared by the dashboard and the upload page so both accept the same formats
 * and behave identically. The clickable region is a real button, so it is
 * reachable by keyboard and announced correctly, rather than a bare div with an
 * onClick.
 */
export default function ResumeDropzone({
  file,
  onFileChange,
  accept = ".pdf,.doc,.docx,.txt",
  disabled = false,
  className,
}: ResumeDropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragActive, setDragActive] = useState(false);

  const handleDrag = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (disabled) return;
    if (e.type === "dragenter" || e.type === "dragover") setDragActive(true);
    else if (e.type === "dragleave") setDragActive(false);
  }, [disabled]);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setDragActive(false);
      if (disabled) return;
      const dropped = e.dataTransfer.files?.[0];
      if (dropped) onFileChange(dropped);
    },
    [disabled, onFileChange]
  );

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files?.[0]) onFileChange(e.target.files[0]);
  };

  return (
    <div className={className}>
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        onChange={handleFileChange}
        className="sr-only"
        id="resume-file-input"
        disabled={disabled}
      />

      {file ? (
        <div className="flex items-center gap-4 rounded-xl border border-slate-200 bg-slate-50/70 p-4">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-primary/10">
            <FileText className="h-5 w-5 text-primary" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-foreground">{file.name}</p>
            <p className="text-xs text-muted-foreground">
              {formatFileSize(file.size)}
              {file.type ? ` · ${file.type.split("/").pop()?.toUpperCase()}` : ""}
            </p>
          </div>
          <button
            type="button"
            onClick={() => onFileChange(null)}
            disabled={disabled}
            aria-label="Remove selected resume"
            className="rounded-lg p-1.5 text-muted-foreground transition-colors hover:bg-slate-200 hover:text-foreground disabled:opacity-50"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          onDragEnter={handleDrag}
          onDragLeave={handleDrag}
          onDragOver={handleDrag}
          onDrop={handleDrop}
          disabled={disabled}
          aria-label="Upload your resume. Drag and drop, or browse for a file."
          className={cn(
            "flex w-full flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 py-10 text-center transition-colors",
            "disabled:cursor-not-allowed disabled:opacity-60",
            dragActive
              ? "border-primary bg-primary/5"
              : "border-slate-200 bg-white hover:border-primary/40 hover:bg-slate-50/60"
          )}
        >
          <span className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-primary/10">
            <UploadIcon className="h-6 w-6 text-primary" />
          </span>
          <span className="text-sm font-medium text-foreground">
            Drag &amp; drop your resume here
          </span>
          <span className="mt-1 text-xs text-muted-foreground">
            or <span className="font-medium text-primary">browse files</span>
          </span>
          <span className="mt-3 text-xs text-muted-foreground">
            PDF, DOCX, DOC or TXT · up to 10MB
          </span>
        </button>
      )}
    </div>
  );
}
