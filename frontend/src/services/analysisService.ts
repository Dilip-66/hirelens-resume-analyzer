import apiClient from "@/lib/api-client";
import type { AnalysisResponse, AnalysisRequest } from "@/types/analysis";

export async function runAnalysis(
  resumeId: string,
  jobDescriptionId: string
): Promise<AnalysisResponse> {
  const payload: AnalysisRequest = { resumeId, jobDescriptionId };
  const { data } = await apiClient.post<AnalysisResponse>(
    "/api/analyses",
    payload
  );
  return data;
}

export async function listAnalyses(): Promise<AnalysisResponse[]> {
  const { data } = await apiClient.get<AnalysisResponse[]>("/api/analyses");
  return data;
}

export async function getAnalysis(id: string): Promise<AnalysisResponse> {
  const { data } = await apiClient.get<AnalysisResponse>(`/api/analyses/${id}`);
  return data;
}
