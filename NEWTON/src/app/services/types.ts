// API Request and Response Types for RAG Model Integration

/**
 * Citation information returned from RAG model
 */
export interface Citation {
  id: string;
  source: string;
  content: string;
  highlight: string;
}

/**
 * Request payload for chat completion
 */
export interface ChatRequest {
  question: string;
  scenario?: string;
  conversationHistory?: ConversationMessage[];
  language?: string;
  // Agreement Assistant specific parameters
  agreementType?: string;
  counterparty?: string;
}

/**
 * Conversation message for context
 */
export interface ConversationMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp?: string;
}

/**
 * Successful response from RAG model
 */
export interface ChatResponse {
  success: true;
  answer: string;
  citations?: Citation[];
  metadata?: {
    modelUsed?: string;
    processingTime?: number;
    confidence?: number;
  };
}

/**
 * Error response from RAG model
 */
export interface ChatErrorResponse {
  success: false;
  error: {
    code: string;
    message: string;
    details?: any;
  };
}

/**
 * Request for Agreement Assistant with specific parameters
 */
export interface AgreementQueryRequest {
  question: string;
  agreementType: string;
  counterparty: string;
  language?: string;
}

/**
 * Response type union
 */
export type ApiResponse = ChatResponse | ChatErrorResponse;

/**
 * File upload request for parsing
 */
export interface FileParseRequest {
  file: File;
  fileType: 'pdf' | 'jpg' | 'png';
}

/**
 * File parse response
 */
export interface FileParseResponse {
  success: boolean;
  parsedData?: Record<string, any>;
  error?: string;
}

// ==================== REAL BACKEND TYPES ====================

export interface ProcessedFile {
  filename: string;
  index_name: string;
}

export interface SourceDocument {
  page_content: string;
  score: number;
  rerank_score?: number;
}

export interface RecallDocument {
  page_content: string;
  score: number;
}

export interface RAGResponse {
  answer: string;
  source_documents: SourceDocument[];
  recall_documents: RecallDocument[];
  rerank_documents: SourceDocument[];
}

export interface TopQuestion {
  question: string;
  count: number;
}

export interface RealChatRequest {
  session_id: string;
  index_names: string[];
  query: string;
  module: string;
  vec_docs_num: number;
  txt_docs_num: number;
  vec_score_threshold: number;
  text_score_threshold: number;
  rerank_score_threshold: number;
  search_method: string;
}
