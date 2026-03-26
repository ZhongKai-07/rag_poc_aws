import { X } from "lucide-react";
import { motion, AnimatePresence } from "motion/react";
import { useEffect, useRef } from "react";

export interface Citation {
  id: string;
  source: string; // 文档来源名称，如 "Fenergo"
  content: string; // 引用的文档内容
  highlight: string; // 需要高亮的文本
}

interface CitationModalProps {
  isOpen: boolean;
  onClose: () => void;
  citation: Citation | null;
}

export function CitationModal({ isOpen, onClose, citation }: CitationModalProps) {
  const contentRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (isOpen && citation && contentRef.current) {
      // 滚动到高亮区域
      const highlightElement = contentRef.current.querySelector('.citation-highlight');
      if (highlightElement) {
        highlightElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }
  }, [isOpen, citation]);

  if (!citation) return null;

  // 处理文本高亮
  const renderContentWithHighlight = (content: string, highlight: string) => {
    if (!highlight) return content;

    const parts = content.split(new RegExp(`(${highlight})`, 'gi'));
    return parts.map((part, index) => {
      if (part.toLowerCase() === highlight.toLowerCase()) {
        return (
          <mark key={index} className="citation-highlight bg-yellow-200 px-1 rounded">
            {part}
          </mark>
        );
      }
      return <span key={index}>{part}</span>;
    });
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop */}
          <motion.div
            className="fixed inset-0 bg-black/50 z-50"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
          />

          {/* Modal */}
          <motion.div
            className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[700px] max-h-[80vh] bg-white rounded-xl shadow-2xl z-50 flex flex-col overflow-hidden"
            initial={{ scale: 0.9, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0.9, opacity: 0 }}
            transition={{ type: "spring", damping: 25, stiffness: 300 }}
          >
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-border bg-gradient-to-r from-primary/5 to-accent/5">
              <div>
                <h3 className="font-semibold text-primary">引用来源</h3>
                <p className="text-sm text-muted-foreground mt-0.5">{citation.source}</p>
              </div>
              <button
                onClick={onClose}
                className="p-2 hover:bg-destructive/10 rounded-lg transition-colors"
                title="关闭"
              >
                <X className="w-5 h-5 text-destructive" />
              </button>
            </div>

            {/* Content */}
            <div 
              ref={contentRef}
              className="flex-1 overflow-y-auto px-6 py-4"
            >
              <div className="prose prose-sm max-w-none text-gray-700 leading-relaxed whitespace-pre-wrap">
                {renderContentWithHighlight(citation.content, citation.highlight)}
              </div>
            </div>

            {/* Footer */}
            <div className="px-6 py-4 border-t border-border bg-gray-50 flex justify-end">
              <button
                onClick={onClose}
                className="px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
              >
                关闭
              </button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
