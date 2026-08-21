export interface Resume {
  id: string;
  userId: string;
  fileName: string;
  rawText: string;
  createdAt: string;
}

export interface JobDescription {
  id: string;
  userId: string;
  title: string | null;
  company: string | null;
  rawText: string;
  createdAt: string;
}

export interface AnalysisResponse {
  id: string;
  resumeId: string;
  jobDescriptionId: string;
  matchScore: number;
  summary: string;
  strengths: string[];
  gaps: string[];
  matchedSkills: string[];
  missingSkills: string[];
  createdAt: string;
}

export interface JobDescriptionRequest {
  title: string;
  company: string;
  rawText: string;
}

export interface AnalysisRequest {
  resumeId: string;
  jobDescriptionId: string;
}

export interface UploadResponse {
  id: string;
  fileName: string;
  chunkCount: number;
  message: string;
}

export interface ApiError {
  error: string;
}
