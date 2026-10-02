import apiClient from "@/lib/api-client";
import type { Resume } from "@/types/analysis";

// The backend returns the persisted Resume record for an upload, not a
// separate upload envelope.
export async function uploadResume(file: File): Promise<Resume> {
  const formData = new FormData();
  formData.append("file", file);
  // No explicit Content-Type. The boundary is generated when the body is
  // serialised, and only the client that serialises it can supply one: sending
  // a hand-written "multipart/form-data" has no boundary, the backend cannot
  // parse the body, and the upload fails with a bare server error. axios clears
  // the header for FormData bodies, but relying on that is how this breaks
  // again the day the HTTP client is swapped.
  const { data } = await apiClient.post<Resume>("/api/resumes", formData);
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
