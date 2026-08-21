import apiClient from "@/lib/api-client";
import type { JobDescription, JobDescriptionRequest } from "@/types/analysis";

export async function createJobDescription(
  input: JobDescriptionRequest
): Promise<JobDescription> {
  const { data } = await apiClient.post<JobDescription>(
    "/api/job-descriptions",
    input
  );
  return data;
}

export async function listJobDescriptions(): Promise<JobDescription[]> {
  const { data } = await apiClient.get<JobDescription[]>(
    "/api/job-descriptions"
  );
  return data;
}
