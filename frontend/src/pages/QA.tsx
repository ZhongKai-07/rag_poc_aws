import { useState, useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ChevronDown, ChevronUp, Loader2, MessageSquare, TrendingUp, Settings, FileText, Search, Sparkles } from "lucide-react";
import { useToast } from "@/hooks/use-toast";

interface ProcessedFile {
  filename: string;
  index_name: string;
}

interface SourceDocument {
  page_content: string;
  score: number;
  rerank_score?: number;
}

interface RecallDocument {
  page_content: string;
  score: number;
}

interface RAGResponse {
  answer: string;
  source_documents: SourceDocument[];
  recall_documents: RecallDocument[];
  rerank_documents: SourceDocument[];
}

interface TopQuestion {
  question: string;
  count: number;
}

const QA = () => {
  const [processedFiles, setProcessedFiles] = useState<ProcessedFile[]>([]);
  const [selectedFiles, setSelectedFiles] = useState<string[]>([]);
  const [question, setQuestion] = useState("");
  const [threshold, setThreshold] = useState("0");
  const [numDocs, setNumDocs] = useState("3");
  const [searchMode, setSearchMode] = useState("mix");
  const [loading, setLoading] = useState(false);
  const [response, setResponse] = useState<RAGResponse | null>(null);
  const [showSources, setShowSources] = useState(false);
  const [showRecall, setShowRecall] = useState(false);
  const [showRerank, setShowRerank] = useState(false);
  const [topQuestions, setTopQuestions] = useState<TopQuestion[]>([]);
  const { toast } = useToast();
  const answerRef = useRef<HTMLDivElement>(null);

  const extractPhrases = (answer: string): string[] => {
    // Step 1: Split by punctuation (NOT spaces) to keep multi-word phrases intact
    const rawPhrases = answer
      .split(/[。，！？；：、\n,.!?;:]+/)
      .map((p) => p.trim())
      .filter((p) => p.length > 0);

    // Step 2: Further split by connectors (与/和/及/以及/or/and) to get sub-phrases
    const allPhrases: string[] = [];
    for (const phrase of rawPhrases) {
      allPhrases.push(phrase);
      const subPhrases = phrase
        .split(/\s*(?:与|和|及|以及|\band\b|\bor\b)\s*/i)
        .map((p) => p.trim())
        .filter((p) => p.length >= 3);
      allPhrases.push(...subPhrases);
    }

    // Deduplicate and sort by length descending (longer matches first)
    const unique = [...new Set(allPhrases.filter((p) => p.length >= 3))];
    return unique.sort((a, b) => b.length - a.length);
  };

  const highlightContent = (content: string, answer: string) => {
    // First handle images - extract image segments and text segments
    const imageRegex = /!\[Image\]\(data:image\/[^;]+;base64,([^)]+)\)/g;
    const segments: { type: "text" | "image"; value: string; idx: number }[] = [];
    let lastIndex = 0;
    let match;

    while ((match = imageRegex.exec(content)) !== null) {
      if (match.index > lastIndex) {
        segments.push({ type: "text", value: content.slice(lastIndex, match.index), idx: lastIndex });
      }
      segments.push({ type: "image", value: match[0], idx: match.index });
      lastIndex = match.index + match[0].length;
    }
    if (lastIndex < content.length) {
      segments.push({ type: "text", value: content.slice(lastIndex), idx: lastIndex });
    }

    const phrases = extractPhrases(answer);
    if (phrases.length === 0) {
      return segments.length > 1
        ? segments.map((seg) =>
            seg.type === "image" ? (
              <img key={seg.idx} src={seg.value.slice(9, -1)} alt="Document Image" className="max-w-full h-auto my-2 rounded border" />
            ) : (
              <span key={seg.idx}>{seg.value}</span>
            )
          )
        : content;
    }

    // Build a single regex from all phrases (escape special chars)
    const escaped = phrases.map((p) => p.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
    const highlightRegex = new RegExp(`(${escaped.join("|")})`, "g");

    const result: React.ReactNode[] = [];
    for (const seg of segments) {
      if (seg.type === "image") {
        result.push(
          <img key={seg.idx} src={seg.value.slice(9, -1)} alt="Document Image" className="max-w-full h-auto my-2 rounded border" />
        );
      } else {
        // Split text by highlight matches
        const parts = seg.value.split(highlightRegex);
        parts.forEach((part, i) => {
          if (highlightRegex.test(part)) {
            result.push(
              <mark key={`${seg.idx}-${i}`} className="bg-yellow-400/30 text-foreground rounded px-0.5 font-medium">
                {part}
              </mark>
            );
          } else if (part) {
            result.push(<span key={`${seg.idx}-${i}`}>{part}</span>);
          }
          // Reset regex lastIndex since we reuse it
          highlightRegex.lastIndex = 0;
        });
      }
    }

    return result;
  };

  const renderContentWithImages = (content: string) => {
    const imageRegex = /!\[Image\]\(data:image\/[^;]+;base64,([^)]+)\)/g;
    const parts = [];
    let lastIndex = 0;
    let match;

    while ((match = imageRegex.exec(content)) !== null) {
      if (match.index > lastIndex) {
        parts.push(content.slice(lastIndex, match.index));
      }
      parts.push(
        <img
          key={match.index}
          src={match[0].slice(9, -1)}
          alt="Document Image"
          className="max-w-full h-auto my-2 rounded border"
        />
      );
      lastIndex = match.index + match[0].length;
    }

    if (lastIndex < content.length) {
      parts.push(content.slice(lastIndex));
    }

    return parts.length > 1 ? parts : content;
  };

  useEffect(() => {
    fetchProcessedFiles();
  }, []);

  const toggleFileSelection = (filename: string) => {
    setSelectedFiles((prev) =>
      prev.includes(filename)
        ? prev.filter((f) => f !== filename)
        : [...prev, filename]
    );
  };

  useEffect(() => {
    if (selectedFiles.length > 0) {
      fetchTopQuestions();
    } else {
      setTopQuestions([]);
    }
  }, [selectedFiles]);

  const fetchProcessedFiles = async () => {
    try {
      const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/processed_files`);
      const data = await response.json();
      if (data.status === "success") {
        setProcessedFiles(data.files);
      }
    } catch (error) {
      console.error("Error fetching processed files:", error);
    }
  };

  const fetchTopQuestions = async () => {
    try {
      const indexNames = processedFiles
        .filter((f) => selectedFiles.includes(f.filename))
        .map((f) => f.index_name);
      if (indexNames.length === 0) return;
      const res = await fetch(
        `${import.meta.env.VITE_API_BASE_URL}/top_questions_multi?index_names=${encodeURIComponent(indexNames.join(","))}`
      );
      const data = await res.json();
      if (data.status === "success") {
        setTopQuestions(data.questions);
      }
    } catch (error) {
      console.error("Error fetching top questions:", error);
    }
  };

  const handleAskQuestion = async () => {
    if (selectedFiles.length === 0 || !question.trim()) {
      toast({
        title: "Missing Information",
        description: "Please select at least one document and enter a question.",
        variant: "destructive",
      });
      return;
    }

    const indexNames = processedFiles
      .filter((f) => selectedFiles.includes(f.filename))
      .map((f) => f.index_name);
    if (indexNames.length === 0) return;

    setLoading(true);
    setResponse(null);
    setShowSources(false);
    setShowRecall(false);
    setShowRerank(false);

    try {
      const sessionId = `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

      const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/rag_answer`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          session_id: sessionId,
          index_names: indexNames,
          query: question,
          module: "RAG",
          vec_docs_num: parseInt(numDocs),
          txt_docs_num: parseInt(numDocs),
          vec_score_threshold: 0.0,
          text_score_threshold: 0.0,
          rerank_score_threshold: parseFloat(threshold),
          search_method: searchMode,
        }),
      });

      if (response.ok) {
        const data: RAGResponse = await response.json();
        setResponse(data);
        fetchTopQuestions();
        setTimeout(() => {
          answerRef.current?.scrollIntoView({ behavior: 'smooth' });
        }, 100);
      } else {
        throw new Error("Failed to get answer");
      }
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to get answer. Please try again.",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col space-y-8 animate-fade-in pb-12">
      <div className="text-center space-y-4">
        <h1 className="text-4xl font-bold tracking-tight text-foreground">
          Interactive Q&A
        </h1>
        <p className="text-muted-foreground text-lg">
          Query your documents with precision and context.
        </p>
      </div>

      <div className="grid lg:grid-cols-[300px_1fr] gap-8 items-start max-w-7xl mx-auto w-full">
        {/* Left Sidebar: Settings & Files */}
        <div className="space-y-6">
          <Card className="p-5 glass-card space-y-6">
            <div className="flex items-center gap-2 font-semibold text-foreground">
              <Settings className="w-5 h-5 text-primary" />
              <span>Configuration</span>
            </div>
            
            <div className="space-y-4">
              <div className="space-y-2">
                <Label className="text-xs font-medium text-muted-foreground uppercase">Search Mode</Label>
                <Select value={searchMode} onValueChange={setSearchMode}>
                  <SelectTrigger className="bg-background/50 border-white/10">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="mix">Hybrid (Text + Vector)</SelectItem>
                    <SelectItem value="vector">Vector Only</SelectItem>
                    <SelectItem value="text">Keyword Only</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label className="text-xs font-medium text-muted-foreground uppercase">Similarity Threshold ({threshold})</Label>
                <Input
                  type="number"
                  min="0"
                  max="1"
                  step="0.1"
                  value={threshold}
                  onChange={(e) => setThreshold(e.target.value)}
                  className="bg-background/50 border-white/10"
                />
              </div>

              <div className="space-y-2">
                <Label className="text-xs font-medium text-muted-foreground uppercase">Docs to Retrieve ({numDocs})</Label>
                <Input
                  type="number"
                  min="1"
                  max="10"
                  value={numDocs}
                  onChange={(e) => setNumDocs(e.target.value)}
                  className="bg-background/50 border-white/10"
                />
              </div>
            </div>
          </Card>

          <Card className="p-5 glass-card space-y-4 h-[400px] flex flex-col">
            <div className="flex items-center justify-between font-semibold text-foreground">
              <div className="flex items-center gap-2">
                <FileText className="w-5 h-5 text-primary" />
                <span>Documents</span>
              </div>
              <span className="text-xs bg-primary/10 text-primary px-2 py-0.5 rounded-full">
                {selectedFiles.length}
              </span>
            </div>

            <div className="flex-1 overflow-y-auto space-y-2 custom-scrollbar pr-2">
              {processedFiles.length === 0 ? (
                <div className="text-sm text-muted-foreground text-center py-8">
                  No files found. Please upload first.
                </div>
              ) : (
                processedFiles.map((file, index) => (
                  <div key={index} className="flex items-center space-x-3 p-2 rounded-lg hover:bg-white/5 transition-colors">
                    <Checkbox
                      id={`file-${index}`}
                      checked={selectedFiles.includes(file.filename)}
                      onCheckedChange={() => toggleFileSelection(file.filename)}
                    />
                    <label
                      htmlFor={`file-${index}`}
                      className="text-sm text-foreground cursor-pointer leading-tight line-clamp-2"
                    >
                      {file.filename}
                    </label>
                  </div>
                ))
              )}
            </div>
          </Card>
        </div>

        {/* Right Area: Chat */}
        <div className="space-y-6">
          <Card className="p-6 glass-card space-y-6">
            {/* Hot Questions */}
            {topQuestions.length > 0 && (
              <div className="space-y-3">
                <Label className="flex items-center gap-2 text-sm text-muted-foreground">
                  <TrendingUp className="w-4 h-4 text-accent" />
                  Suggested Questions
                </Label>
                <div className="flex flex-wrap gap-2">
                  {topQuestions.map((item, index) => (
                    <button
                      key={index}
                      onClick={() => setQuestion(item.question)}
                      className="text-xs px-3 py-1.5 rounded-full bg-accent/10 border border-accent/20 text-accent hover:bg-accent/20 transition-all cursor-pointer truncate max-w-[200px]"
                    >
                      {item.question}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Input Area */}
            <div className="space-y-4">
              <div className="relative">
                <Textarea
                  placeholder="Ask a question about your documents..."
                  value={question}
                  onChange={(e) => setQuestion(e.target.value)}
                  className="min-h-[140px] p-4 bg-background/50 border-white/10 resize-none focus:ring-primary/50 text-lg shadow-inner"
                />
                <Button
                  onClick={handleAskQuestion}
                  disabled={loading}
                  className="absolute bottom-4 right-4 rounded-full bg-primary hover:bg-primary/90 text-white shadow-glow"
                >
                  {loading ? (
                    <Loader2 className="w-5 h-5 animate-spin" />
                  ) : (
                    <div className="flex items-center gap-2">
                      <span>Ask AI</span>
                      <Sparkles className="w-4 h-4" />
                    </div>
                  )}
                </Button>
              </div>
            </div>
          </Card>

          {/* Response Area */}
          {response && (
            <div ref={answerRef} className="space-y-6 animate-fade-in-up">
              <Card className="p-8 border-primary/20 bg-gradient-to-br from-background to-primary/5 shadow-glow relative overflow-hidden">
                <div className="absolute top-0 left-0 w-1 h-full bg-gradient-to-b from-primary to-secondary" />
                
                <div className="space-y-4 relative z-10">
                  <h3 className="text-xl font-bold text-foreground flex items-center gap-2">
                    <Sparkles className="w-5 h-5 text-primary" />
                    AI Analysis
                  </h3>
                  <div className="prose prose-invert max-w-none">
                    <p className="text-foreground/90 leading-7 text-lg whitespace-pre-wrap">
                      {response.answer}
                    </p>
                  </div>
                </div>
              </Card>

              {/* Evidence Accordions */}
              <div className="grid md:grid-cols-2 gap-4">
                {/* Recall Results */}
                <div className="space-y-2">
                  <Button
                    onClick={() => setShowRecall(!showRecall)}
                    variant="outline"
                    className="w-full justify-between bg-background/40 border-white/10 hover:bg-white/5"
                  >
                    <span className="flex items-center gap-2">
                      <Search className="w-4 h-4 text-blue-400" />
                      Recall Evidence ({response.recall_documents.length})
                    </span>
                    {showRecall ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                  </Button>

                  {showRecall && (
                    <div className="space-y-3 animate-fade-in">
                      {response.recall_documents.map((doc, index) => (
                        <Card key={index} className="p-4 bg-blue-500/5 border-blue-500/20 text-sm">
                          <div className="flex justify-between items-center mb-2">
                            <span className="text-xs font-mono text-blue-400 bg-blue-500/10 px-1.5 py-0.5 rounded">
                              Score: {doc.score.toFixed(3)}
                            </span>
                          </div>
                          <p className="text-muted-foreground line-clamp-4 hover:line-clamp-none transition-all cursor-help">
                            {renderContentWithImages(doc.page_content)}
                          </p>
                        </Card>
                      ))}
                    </div>
                  )}
                </div>

                {/* Rerank Results */}
                <div className="space-y-2">
                  <Button
                    onClick={() => setShowRerank(!showRerank)}
                    variant="outline"
                    className="w-full justify-between bg-background/40 border-white/10 hover:bg-white/5"
                  >
                    <span className="flex items-center gap-2">
                      <TrendingUp className="w-4 h-4 text-green-400" />
                      Rerank Evidence ({response.rerank_documents.length})
                    </span>
                    {showRerank ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                  </Button>

                  {showRerank && (
                    <div className="space-y-3 animate-fade-in">
                      {response.rerank_documents.map((doc, index) => (
                        <Card key={index} className="p-4 bg-green-500/5 border-green-500/20 text-sm">
                          <div className="flex justify-between items-center mb-2">
                            <span className="text-xs font-mono text-green-400 bg-green-500/10 px-1.5 py-0.5 rounded">
                              Rerank: {doc.rerank_score?.toFixed(3)}
                            </span>
                          </div>
                          <div className="text-muted-foreground">
                            {highlightContent(doc.page_content, response.answer)}
                          </div>
                        </Card>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default QA;
