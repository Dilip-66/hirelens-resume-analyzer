import { useState, useEffect, useCallback } from "react";
import { listResumes, uploadResume as uploadResumeApi } from "@/services/resumeService";
import type { Resume, UploadResponse } from "@/types/analysis";

export function useResumes() {
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchResumes = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await listResumes();
      setResumes(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load resumes");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchResumes();
  }, [fetchResumes]);

  const upload = async (file: File): Promise<UploadResponse> => {
    const result = await uploadResumeApi(file);
    await fetchResumes();
    return result;
  };

  return { resumes, loading, error, upload, refresh: fetchResumes };
}
