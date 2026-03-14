const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:5000';

export interface AnalysisResult {
  score: number;
  summary: string;
  rules_assessment_results: RuleAssessmentResult[];
}

export interface FileAnalysisResult {
  fileName: string;
  result: AnalysisResult;
  error?: string;
}

export interface RuleAssessmentResult {
  ruleName: string;
  ruleDescription: string;
  passed: string | boolean;
  ruleScore: number;
  weightedScore: number;
  correctParts: EmailPart[];
  incorrectParts: EmailPart[];
}

export interface EmailPart {
  "email id": number;
  reasoning: string;
  "original sentence": string;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

export async function analyzeEmail(file: File): Promise<AnalysisResult> {
  const formData = new FormData();
  formData.append('file', file);

  try {
    const response = await fetch(`${API_BASE_URL}/api/analyze`, {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({ detail: 'Unknown error' }));
      throw new ApiError(response.status, errorData.detail || `HTTP ${response.status}`);
    }

    const result = await response.json();
    return result;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    
    // Handle network errors
    if (error instanceof TypeError && error.message.includes('fetch')) {
      throw new ApiError(0, 'Unable to connect to the server. Please ensure the backend is running.');
    }
    
    throw new ApiError(500, error instanceof Error ? error.message : 'Unknown error occurred');
  }
}

export async function analyzeMultipleEmails(files: File[]): Promise<FileAnalysisResult[]> {
  const formData = new FormData();
  files.forEach(file => {
    formData.append('files', file);
  });

  try {
    const response = await fetch(`${API_BASE_URL}/api/analyze-multiple`, {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({ detail: 'Unknown error' }));
      throw new ApiError(response.status, errorData.detail || `HTTP ${response.status}`);
    }

    const results = await response.json();
    return results;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    
    // Handle network errors
    if (error instanceof TypeError && error.message.includes('fetch')) {
      throw new ApiError(0, 'Unable to connect to the server. Please ensure the backend is running.');
    }
    
    throw new ApiError(500, error instanceof Error ? error.message : 'Unknown error occurred');
  }
}

export async function healthCheck(): Promise<{ status: string }> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/health`);
    
    if (!response.ok) {
      throw new ApiError(response.status, `Health check failed: HTTP ${response.status}`);
    }
    
    return await response.json();
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    
    throw new ApiError(0, 'Unable to connect to the server');
  }
}