import { useState, useEffect, useCallback } from "react";
import {
  listJobDescriptions,
  createJobDescription as createJobDescriptionApi,
} from "@/services/jobService";
import type { JobDescription, JobDescriptionRequest } from "@/types/analysis";

export function useJobs() {
  const [jobs, setJobs] = useState<JobDescription[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchJobs = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await listJobDescriptions();
      setJobs(data);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load job descriptions"
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchJobs();
  }, [fetchJobs]);

  const create = async (input: JobDescriptionRequest): Promise<JobDescription> => {
    const result = await createJobDescriptionApi(input);
    await fetchJobs();
    return result;
  };

  return { jobs, loading, error, create, refresh: fetchJobs };
}
