import React, { useState } from "react";
import ReactMarkdown from "react-markdown";
import { FileText } from "lucide-react";
import { Citation } from "./CitationModal";

interface MessageWithCitationsProps {
  content: string;
  citations?: Citation[];
  showCitations?: boolean;
  agreementType?: string;
  counterparty?: string;
}

export function MessageWithCitations({ content, citations, showCitations = true, agreementType, counterparty }: MessageWithCitationsProps) {
  const [expandedCitations, setExpandedCitations] = useState<Set<string>>(new Set());
  const [isAgreementSourceExpanded, setIsAgreementSourceExpanded] = useState(false);

  const hasAgreementInfo = agreementType && counterparty;

  const renderContentWithCitations = () => {
    if (!citations || citations.length === 0 || !showCitations) {
      const cleanContent = content.replace(/\{cite:\d+\}/g, '');
      return <ReactMarkdown>{cleanContent}</ReactMarkdown>;
    }

    const parts = content.split(/(\{cite:\d+\})/g);
    
    return (
      <div className="prose prose-sm max-w-none text-[15px] leading-relaxed whitespace-pre-wrap">
        {parts.map((part, partIndex) => {
          const citationMatch = part.match(/\{cite:(\d+)\}/);
          
          if (citationMatch) {
            const citationIndex = parseInt(citationMatch[1]) - 1;
            const citation = citations[citationIndex];
            
            if (!citation) return null;
            
            const isExpanded = expandedCitations.has(citation.id);
            
            return (
              <React.Fragment key={`cite-${partIndex}`}>
                <button
                  onClick={() => {
                    setExpandedCitations(prev => {
                      const newSet = new Set(prev);
                      if (newSet.has(citation.id)) {
                        newSet.delete(citation.id);
                      } else {
                        newSet.add(citation.id);
                      }
                      return newSet;
                    });
                  }}
                  className="inline-flex items-center justify-center w-4 h-4 bg-gray-300 hover:bg-gray-400 text-white text-[9px] font-medium rounded-full transition-colors ml-0.5 mr-1.5 align-baseline"
                  title={citation.source}
                >
                  {citationIndex + 1}
                </button>
                
                {isExpanded && (
                  <span className="inline-block ml-1.5 px-2 py-0.5 bg-gray-50 border border-gray-300 rounded text-[11px] text-gray-600 align-baseline">
                    <span className="inline-flex items-center gap-1">
                      <FileText className="w-3 h-3" />
                      <span className="font-medium">{citation.source}</span>
                    </span>
                  </span>
                )}
              </React.Fragment>
            );
          }
          
          if (part) {
            return (
              <ReactMarkdown 
                key={`text-${partIndex}`}
                components={{
                  p: ({children}) => <span className="inline">{children}</span>,
                  strong: ({children}) => <strong className="inline">{children}</strong>,
                  em: ({children}) => <em className="inline">{children}</em>,
                  li: ({children}) => <li>{children}</li>,
                  ul: ({children}) => <ul className="block list-disc pl-6 my-2">{children}</ul>,
                  ol: ({children}) => <ol className="block list-decimal pl-6 my-2">{children}</ol>,
                  h1: ({children}) => <h1 className="block">{children}</h1>,
                  h2: ({children}) => <h2 className="block">{children}</h2>,
                  h3: ({children}) => <h3 className="block">{children}</h3>,
                }}
              >
                {part}
              </ReactMarkdown>
            );
          }
          return null;
        })}
      </div>
    );
  };

  return (
    <>
      {renderContentWithCitations()}
      {hasAgreementInfo && (
        <div className="mt-4">
          <button
            onClick={() => setIsAgreementSourceExpanded(!isAgreementSourceExpanded)}
            className="flex items-start gap-2 px-4 py-3 bg-gray-50 border border-gray-200 rounded-lg hover:bg-gray-100 transition-colors w-full text-left"
          >
            <div className="flex-1">
              <div className="text-sm text-gray-700">
                <span 
                  className="font-semibold bg-gradient-to-r from-[#E935C6] via-[#FF6B9D] to-[#4FC3F7] bg-clip-text text-transparent"
                >
                  Agreement Type:
                </span>{" "}
                {agreementType}
                <span className="mx-3"></span>
                <span 
                  className="font-semibold bg-gradient-to-r from-[#E935C6] via-[#FF6B9D] to-[#4FC3F7] bg-clip-text text-transparent"
                >
                  Counterparty:
                </span>{" "}
                {counterparty}
              </div>
            </div>
            <svg
              className={`w-5 h-5 text-gray-600 flex-shrink-0 mt-0.5 transition-transform ${isAgreementSourceExpanded ? 'rotate-180' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </button>
          {isAgreementSourceExpanded && (
            <div className="mt-2 p-4 bg-gray-50 border border-gray-200 rounded-lg">
              <div className="flex items-start gap-2 mb-3">
                <FileText className="w-4 h-4 text-gray-400 flex-shrink-0 mt-0.5" />
                <div className="flex-1">
                  <div className="font-medium text-gray-900 text-sm mb-1">
                    {agreementType} Master Agreement - Credit Support Annex
                  </div>
                  <div className="text-sm text-gray-500">
                    Huatai Securities (Hong Kong) Limited & {counterparty}
                  </div>
                </div>
              </div>
              <div className="pl-6 text-sm text-gray-600 italic border-l-2 border-gray-300">
                <p>
                  This agreement provides for bilateral collateralization between the parties. 
                  All collateral transfers are subject to the terms and conditions specified in the Credit Support Annex, 
                  including Minimum Transfer Amount, Threshold Amount, and Eligible Collateral provisions.
                </p>
              </div>
            </div>
          )}
        </div>
      )}
    </>
  );
}