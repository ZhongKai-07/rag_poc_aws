/**
 * RAG API Service
 * 
 * This service handles all communication with your RAG backend model.
 * Replace the placeholder API_BASE_URL with your actual endpoint.
 */

import type { 
  ChatRequest, 
  ApiResponse, 
  AgreementQueryRequest,
  FileParseRequest,
  FileParseResponse,
  ProcessedFile,
  TopQuestion,
  RealChatRequest,
  RAGResponse
} from './types';

// ==================== CONFIGURATION ====================
// Uses the real backend API defined in .env
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8001';

// API timeout in milliseconds (default: 60 seconds for RAG)
const API_TIMEOUT = 60000;

// ==================== API ENDPOINTS ====================
const ENDPOINTS = {
  CHAT: '/rag_answer',
  UPLOAD_FILES: '/upload_files',
  PROCESSED_FILES: '/processed_files',
  TOP_QUESTIONS: '/top_questions_multi',
  HEALTH: '/health',
  // Keep legacy mock endpoints for compatibility if needed
  AGREEMENT_QUERY: '/agreement/query',
  FILE_PARSE: '/file/parse',
};

// ==================== HELPER FUNCTIONS ====================

/**
 * Create fetch request with timeout
 */
async function fetchWithTimeout(
  url: string,
  options: RequestInit,
  timeout: number = API_TIMEOUT
): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeout);

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
    });
    clearTimeout(timeoutId);
    return response;
  } catch (error: any) {
    clearTimeout(timeoutId);
    if (error.name === 'AbortError') {
      throw new Error('Request timeout');
    }
    throw error;
  }
}

/**
 * Handle API errors
 */
function handleApiError(error: any): ApiResponse {
  console.error('RAG API Error:', error);
  
  return {
    success: false,
    error: {
      code: error.code || 'UNKNOWN_ERROR',
      message: error.message || 'An unexpected error occurred',
      details: error.details || null,
    },
  };
}

// ==================== MAIN API FUNCTIONS ====================

/**
 * Send chat question to RAG model
 * 
 * @param request - Chat request with question and context
 * @returns Promise<ApiResponse> - Model response or error
 * 
 * @example
 * ```typescript
 * const response = await sendChatMessage({
 *   question: "What are the KYC requirements?",
 *   scenario: "Account Opening",
 *   language: "zh-CN"
 * });
 * 
 * if (response.success) {
 *   console.log(response.answer);
 *   console.log(response.citations);
 * }
 * ```
 */
export async function sendChatMessage(request: ChatRequest): Promise<ApiResponse> {
  try {
    const response = await fetchWithTimeout(
      `${API_BASE_URL}${ENDPOINTS.CHAT}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          // Add authentication headers if needed
          // 'Authorization': `Bearer ${getAuthToken()}`,
        },
        body: JSON.stringify(request),
      }
    );

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      return {
        success: false,
        error: {
          code: `HTTP_${response.status}`,
          message: errorData.message || response.statusText,
          details: errorData,
        },
      };
    }

    const data = await response.json();
    return {
      success: true,
      answer: data.answer,
      citations: data.citations,
      metadata: data.metadata,
    };
  } catch (error: any) {
    return handleApiError(error);
  }
}

/**
 * Query Agreement Assistant with specific agreement parameters
 * 
 * @param request - Agreement query with type and counterparty
 * @returns Promise<ApiResponse> - Model response or error
 * 
 * @example
 * ```typescript
 * const response = await queryAgreement({
 *   question: "What is the minimum transfer amount?",
 *   agreementType: "ISDA",
 *   counterparty: "HSBC",
 *   language: "en"
 * });
 * ```
 */
export async function queryAgreement(request: AgreementQueryRequest): Promise<ApiResponse> {
  try {
    const response = await fetchWithTimeout(
      `${API_BASE_URL}${ENDPOINTS.AGREEMENT_QUERY}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      }
    );

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      return {
        success: false,
        error: {
          code: `HTTP_${response.status}`,
          message: errorData.message || response.statusText,
          details: errorData,
        },
      };
    }

    const data = await response.json();
    return {
      success: true,
      answer: data.answer,
      citations: data.citations,
      metadata: data.metadata,
    };
  } catch (error: any) {
    return handleApiError(error);
  }
}

/**
 * Parse uploaded file
 * 
 * @param request - File parse request
 * @returns Promise<FileParseResponse>
 * 
 * @example
 * ```typescript
 * const file = event.target.files[0];
 * const response = await parseFile({
 *   file: file,
 *   fileType: 'pdf'
 * });
 * ```
 */
export async function parseFile(request: FileParseRequest): Promise<FileParseResponse> {
  try {
    const formData = new FormData();
    formData.append('file', request.file);
    formData.append('fileType', request.fileType);

    const response = await fetchWithTimeout(
      `${API_BASE_URL}${ENDPOINTS.FILE_PARSE}`,
      {
        method: 'POST',
        body: formData,
        // Don't set Content-Type header, browser will set it with boundary
      }
    );

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      return {
        success: false,
        error: errorData.message || response.statusText,
      };
    }

    const data = await response.json();
    return {
      success: true,
      parsedData: data.parsedData,
    };
  } catch (error: any) {
    return {
      success: false,
      error: error.message || 'File parsing failed',
    };
  }
}

/**
 * Check API health status
 * 
 * @returns Promise<boolean> - true if API is healthy
 */
export async function checkApiHealth(): Promise<boolean> {
  try {
    const response = await fetchWithTimeout(
      `${API_BASE_URL}${ENDPOINTS.HEALTH}`,
      {
        method: 'GET',
      },
      5000 // 5 second timeout for health check
    );

    return response.ok;
  } catch (error) {
    console.error('API health check failed:', error);
    return false;
  }
}

// ==================== REAL BACKEND INTEGRATION ====================

export async function fetchProcessedFiles(): Promise<{ status: string; files: ProcessedFile[] }> {
  try {
    const response = await fetchWithTimeout(`${API_BASE_URL}${ENDPOINTS.PROCESSED_FILES}`, {
      method: 'GET',
    });
    if (!response.ok) throw new Error('Failed to fetch processed files');
    return await response.json();
  } catch (error: any) {
    console.error('Error fetching processed files:', error);
    return { status: 'error', files: [] };
  }
}

export async function fetchTopQuestionsMulti(indexNames: string[]): Promise<{ status: string; questions: TopQuestion[] }> {
  try {
    if (!indexNames || indexNames.length === 0) return { status: 'success', questions: [] };
    const queryParam = encodeURIComponent(indexNames.join(','));
    const response = await fetchWithTimeout(`${API_BASE_URL}${ENDPOINTS.TOP_QUESTIONS}?index_names=${queryParam}`, {
      method: 'GET',
    });
    if (!response.ok) throw new Error('Failed to fetch top questions');
    return await response.json();
  } catch (error: any) {
    console.error('Error fetching top questions:', error);
    return { status: 'error', questions: [] };
  }
}

export async function uploadRealFiles(file: File, directoryPath: string): Promise<Response> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('directory_path', directoryPath);
  
  return await fetchWithTimeout(`${API_BASE_URL}${ENDPOINTS.UPLOAD_FILES}`, {
    method: 'POST',
    body: formData,
  }, 120000); // 2 minutes timeout for large files
}

export async function askRealQuestion(request: RealChatRequest): Promise<RAGResponse> {
  const response = await fetchWithTimeout(`${API_BASE_URL}${ENDPOINTS.CHAT}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(`Failed to get answer: ${response.status}`);
  }

  return await response.json();
}

// ==================== MOCK MODE (for development) ====================

/**
 * Enable this flag to use mock responses instead of real API calls
 * Useful for frontend development without backend
 */
export const USE_MOCK_API = import.meta.env.VITE_USE_MOCK_API === 'true';

/**
 * Mock implementation of sendChatMessage
 * Replace this with real API call when backend is ready
 */
export async function sendChatMessageMock(request: ChatRequest): Promise<ApiResponse> {
  // Simulate network delay
  await new Promise(resolve => setTimeout(resolve, 1000));

  // Simulate occasional errors (10% chance)
  if (Math.random() < 0.1) {
    return {
      success: false,
      error: {
        code: 'MOCK_ERROR',
        message: 'Mock error for testing',
      },
    };
  }

  return {
    success: true,
    answer: `Mock answer for: "${request.question}"\n\nThis is a mock response. Replace with real RAG API.{cite:1}`,
    citations: [
      {
        id: 'cite-1',
        source: 'Mock Source',
        content: 'Mock citation content',
        highlight: 'Mock highlight',
      },
    ],
  };
}
