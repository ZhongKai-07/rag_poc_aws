/**
 * useRagApi Hook
 * 
 * Custom hook for interacting with RAG API in React components
 * Provides loading states, error handling, and automatic retries
 */

import { useState, useCallback } from 'react';
import { sendChatMessage, queryAgreement, USE_MOCK_API, sendChatMessageMock } from '../services/ragApi';
import type { ChatRequest, ApiResponse, AgreementQueryRequest } from '../services/types';
import { getTranslation, Language } from '../i18n/translations';

interface UseRagApiOptions {
  language: Language;
  onSuccess?: (response: ApiResponse) => void;
  onError?: (error: any) => void;
  autoRetry?: boolean;
  maxRetries?: number;
}

interface UseRagApiReturn {
  sendMessage: (request: ChatRequest) => Promise<ApiResponse>;
  sendAgreementQuery: (request: AgreementQueryRequest) => Promise<ApiResponse>;
  isLoading: boolean;
  error: string | null;
  clearError: () => void;
}

export function useRagApi(options: UseRagApiOptions): UseRagApiReturn {
  const { language, onSuccess, onError, autoRetry = true, maxRetries = 2 } = options;
  
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /**
   * Clear error state
   */
  const clearError = useCallback(() => {
    setError(null);
  }, []);

  /**
   * Retry logic wrapper
   */
  const withRetry = async <T,>(
    fn: () => Promise<T>,
    retries: number = maxRetries
  ): Promise<T> => {
    try {
      return await fn();
    } catch (err: any) {
      if (retries > 0 && autoRetry) {
        console.log(`Retrying... (${maxRetries - retries + 1}/${maxRetries})`);
        await new Promise(resolve => setTimeout(resolve, 1000));
        return withRetry(fn, retries - 1);
      }
      throw err;
    }
  };

  /**
   * Send chat message to RAG model
   */
  const sendMessage = useCallback(
    async (request: ChatRequest): Promise<ApiResponse> => {
      setIsLoading(true);
      setError(null);

      try {
        const response = await withRetry(async () => {
          // Use mock API if enabled
          if (USE_MOCK_API) {
            return await sendChatMessageMock(request);
          }
          return await sendChatMessage(request);
        });

        if (response.success) {
          onSuccess?.(response);
          return response;
        } else {
          // Handle error response from API
          const errorMessage = response.error.message || getTranslation(language, 'errorNoInformation');
          setError(errorMessage);
          onError?.(response.error);
          return response;
        }
      } catch (err: any) {
        const errorMessage = err.message || getTranslation(language, 'errorNoInformation');
        setError(errorMessage);
        onError?.(err);
        
        // Return error response
        return {
          success: false,
          error: {
            code: 'NETWORK_ERROR',
            message: errorMessage,
          },
        };
      } finally {
        setIsLoading(false);
      }
    },
    [language, onSuccess, onError, autoRetry, maxRetries]
  );

  /**
   * Send agreement-specific query
   */
  const sendAgreementQuery = useCallback(
    async (request: AgreementQueryRequest): Promise<ApiResponse> => {
      setIsLoading(true);
      setError(null);

      try {
        const response = await withRetry(async () => {
          return await queryAgreement(request);
        });

        if (response.success) {
          onSuccess?.(response);
          return response;
        } else {
          const errorMessage = response.error.message || getTranslation(language, 'errorNoInformation');
          setError(errorMessage);
          onError?.(response.error);
          return response;
        }
      } catch (err: any) {
        const errorMessage = err.message || getTranslation(language, 'errorNoInformation');
        setError(errorMessage);
        onError?.(err);
        
        return {
          success: false,
          error: {
            code: 'NETWORK_ERROR',
            message: errorMessage,
          },
        };
      } finally {
        setIsLoading(false);
      }
    },
    [language, onSuccess, onError, autoRetry, maxRetries]
  );

  return {
    sendMessage,
    sendAgreementQuery,
    isLoading,
    error,
    clearError,
  };
}
