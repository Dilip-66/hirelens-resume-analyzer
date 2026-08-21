import apiClient from "@/lib/api-client";
import type { Resume, UploadResponse } from "@/types/analysis";

export async function uploadResume(file: File): Promise<UploadResponse> {
  const formData = new FormData();
  formData.append("file", file);
  const { data } = await apiClient.post<UploadResponse>("/api/resumes", formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return data;
}

export async function listResumes(): Promise<Resume[]> {
  const { data } = await apiClient.get<Resume[]>("/api/resumes");
  return data;
}

export async function getResume(id: string): Promise<Resume> {
  const { data } = await apiClient.get<Resume>(`/api/resumes/${id}`);
  return data;
}
