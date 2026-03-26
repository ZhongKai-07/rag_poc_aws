/**
 * API Configuration
 * 
 * Centralized configuration for RAG API integration
 */

export const API_CONFIG = {
  // ==================== API BASE URL ====================
  // Set this in your .env file as REACT_APP_RAG_API_URL
  BASE_URL: process.env.REACT_APP_RAG_API_URL || 'http://localhost:8000/api',

  // ==================== TIMEOUTS ====================
  // Maximum time to wait for API response (milliseconds)
  REQUEST_TIMEOUT: parseInt(process.env.REACT_APP_API_TIMEOUT || '30000'),
  
  // Health check timeout
  HEALTH_CHECK_TIMEOUT: 5000,

  // ==================== RETRY CONFIGURATION ====================
  // Number of retry attempts for failed requests
  MAX_RETRIES: parseInt(process.env.REACT_APP_MAX_RETRIES || '2'),
  
  // Delay between retries (milliseconds)
  RETRY_DELAY: 1000,

  // ==================== MOCK MODE ====================
  // Use mock data instead of real API (for development)
  USE_MOCK: process.env.REACT_APP_USE_MOCK === 'true',

  // ==================== FEATURES ====================
  // Enable/disable specific features
  FEATURES: {
    CHAT: true,
    AGREEMENT_ASSISTANT: true,
    FILE_PARSING: true,
    CONVERSATION_HISTORY: true,
  },

  // ==================== ENDPOINTS ====================
  ENDPOINTS: {
    CHAT: '/chat',
    AGREEMENT_QUERY: '/agreement/query',
    FILE_PARSE: '/file/parse',
    HEALTH: '/health',
    FEEDBACK: '/feedback',
  },

  // ==================== REQUEST HEADERS ====================
  getHeaders: (includeAuth: boolean = true): HeadersInit => {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
    };

    // Add authentication token if available
    if (includeAuth) {
      const token = localStorage.getItem('auth_token');
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }
    }

    return headers;
  },
};

/**
 * Environment helper functions
 */
export const ENV = {
  isDevelopment: process.env.NODE_ENV === 'development',
  isProduction: process.env.NODE_ENV === 'production',
  isTest: process.env.NODE_ENV === 'test',
};
