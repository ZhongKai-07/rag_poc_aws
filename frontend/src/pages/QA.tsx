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
import { ChevronDown, ChevronUp, Loader2, MessageSquare, TrendingUp } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { useNavigate } from "react-router-dom";

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
  const navigate = useNavigate();
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
              <mark key={`${seg.idx}-${i}`} className="bg-yellow-400/30 text-foreground rounded px-0.5">
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
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-card/50 backdrop-blur-sm">
        <div className="container mx-auto px-6 py-4 flex items-center justify-between">
          <h1 className="text-2xl font-bold bg-gradient-primary bg-clip-text text-transparent">
            Research Report QA System
          </h1>
          <Button
            onClick={() => navigate("/")}
            variant="outline"
            className="border-primary/30 hover:bg-primary/10"
          >
            Back to Upload
          </Button>
        </div>
      </header>

      <main className="container mx-auto px-6 py-12">
        <div className="max-w-4xl mx-auto space-y-8">
          <div className="text-center space-y-2">
            <h2 className="text-3xl font-bold text-foreground">Ask Questions</h2>
            <p className="text-muted-foreground">Query your financial reports with AI</p>
          </div>

          <Card className="p-8 border-border bg-gradient-card shadow-glow">
            <div className="space-y-6">
              <div className="space-y-2">
                <Label className="text-foreground">
                  Select Documents
                  {selectedFiles.length > 0 && (
                    <span className="ml-2 text-sm text-muted-foreground font-normal">
                      (已选 {selectedFiles.length} 个文档)
                    </span>
                  )}
                </Label>
                <div className="rounded-md border border-border bg-input p-3 max-h-[200px] overflow-y-auto space-y-2">
                  {processedFiles.length === 0 ? (
                    <p className="text-sm text-muted-foreground">No processed files available</p>
                  ) : (
                    processedFiles.map((file, index) => (
                      <div key={index} className="flex items-center space-x-2">
                        <Checkbox
                          id={`file-${index}`}
                          checked={selectedFiles.includes(file.filename)}
                          onCheckedChange={() => toggleFileSelection(file.filename)}
                        />
                        <label
                          htmlFor={`file-${index}`}
                          className="text-sm text-foreground cursor-pointer leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
                        >
                          {file.filename}
                        </label>
                      </div>
                    ))
                  )}
                </div>
              </div>

              {topQuestions.length > 0 && (
                <div className="space-y-2">
                  <Label className="text-foreground flex items-center gap-2">
                    <TrendingUp className="w-4 h-4 text-orange-400" />
                    Hot Questions
                  </Label>
                  <div className="flex flex-wrap gap-2">
                    {topQuestions.map((item, index) => (
                      <button
                        key={index}
                        onClick={() => setQuestion(item.question)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm
                          bg-orange-500/10 border border-orange-500/20 text-orange-300
                          hover:bg-orange-500/20 hover:border-orange-500/40 transition-colors cursor-pointer"
                      >
                        <span className="truncate max-w-[300px]">{item.question}</span>
                        <span className="text-xs text-orange-400/60 shrink-0">x{item.count}</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="question" className="text-foreground">Your Question</Label>
                <Textarea
                  id="question"
                  placeholder="Enter your question about the financial report..."
                  value={question}
                  onChange={(e) => setQuestion(e.target.value)}
                  className="min-h-[120px] bg-input border-border resize-none"
                />
              </div>

              <div className="grid grid-cols-3 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="threshold" className="text-foreground">
                    Similarity Threshold
                  </Label>
                  <Input
                    id="threshold"
                    type="number"
                    min="0"
                    max="1"
                    step="0.1"
                    value={threshold}
                    onChange={(e) => setThreshold(e.target.value)}
                    className="bg-input border-border"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="numDocs" className="text-foreground">
                    Number of Related Docs
                  </Label>
                  <Input
                    id="numDocs"
                    type="number"
                    min="1"
                    max="5"
                    value={numDocs}
                    onChange={(e) => setNumDocs(e.target.value)}
                    className="bg-input border-border"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="searchMode" className="text-foreground">Search Mode</Label>
                  <Select value={searchMode} onValueChange={setSearchMode}>
                    <SelectTrigger id="searchMode" className="bg-input border-border">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="mix">mix</SelectItem>
                      <SelectItem value="vector">vector</SelectItem>
                      <SelectItem value="text">text</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <Button
                onClick={handleAskQuestion}
                disabled={loading}
                className="w-full bg-primary hover:bg-primary/90 text-primary-foreground"
              >
                {loading ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    Processing...
                  </>
                ) : (
                  <>
                    <MessageSquare className="w-4 h-4 mr-2" />
                    Ask Question
                  </>
                )}
              </Button>
            </div>
          </Card>

          {response && (
            <Card ref={answerRef} className="p-8 border-border bg-card space-y-6">
              <div className="space-y-3">
                <h3 className="text-xl font-semibold text-foreground">Answer</h3>
                <p className="text-foreground leading-relaxed whitespace-pre-wrap">
                  {response.answer}
                </p>
              </div>

              {/* Recall Results Section */}
              <div className="space-y-3">
                <Button
                  onClick={() => setShowRecall(!showRecall)}
                  variant="outline"
                  className="w-full justify-between border-blue-500/30 hover:bg-blue-500/10"
                >
                  <span className="flex items-center gap-2">
                    <span className="inline-block w-2 h-2 rounded-full bg-blue-500" />
                    Recall Results ({response.recall_documents.length})
                  </span>
                  {showRecall ? (
                    <ChevronUp className="w-4 h-4" />
                  ) : (
                    <ChevronDown className="w-4 h-4" />
                  )}
                </Button>

                {showRecall && (
                  <div className="space-y-4 mt-4">
                    {response.recall_documents.map((doc, index) => (
                      <Card key={index} className="p-4 bg-blue-500/5 border-blue-500/20">
                        <div className="space-y-2">
                          <div className="flex items-center gap-4">
                            <span className="text-xs font-medium px-2 py-0.5 rounded bg-blue-500/10 text-blue-400">
                              #{index + 1}
                            </span>
                            <div className="flex items-center gap-2">
                              <span className="text-sm font-semibold text-blue-400">
                                Similarity Score:
                              </span>
                              <span className="text-sm text-muted-foreground">
                                {doc.score.toFixed(4)}
                              </span>
                            </div>
                          </div>
                          <div className="text-sm text-foreground leading-relaxed whitespace-pre-wrap">
                            {renderContentWithImages(doc.page_content)}
                          </div>
                        </div>
                      </Card>
                    ))}
                  </div>
                )}
              </div>

              {/* Rerank Results Section */}
              <div className="space-y-3">
                <Button
                  onClick={() => setShowRerank(!showRerank)}
                  variant="outline"
                  className="w-full justify-between border-green-500/30 hover:bg-green-500/10"
                >
                  <span className="flex items-center gap-2">
                    <span className="inline-block w-2 h-2 rounded-full bg-green-500" />
                    Rerank Results ({response.rerank_documents.length})
                  </span>
                  {showRerank ? (
                    <ChevronUp className="w-4 h-4" />
                  ) : (
                    <ChevronDown className="w-4 h-4" />
                  )}
                </Button>

                {showRerank && (
                  <div className="space-y-4 mt-4">
                    {response.rerank_documents.map((doc, index) => (
                      <Card key={index} className="p-4 bg-green-500/5 border-green-500/20">
                        <div className="space-y-2">
                          <div className="flex items-center gap-4">
                            <span className="text-xs font-medium px-2 py-0.5 rounded bg-green-500/10 text-green-400">
                              #{index + 1}
                            </span>
                            <div className="flex items-center gap-2">
                              <span className="text-sm font-semibold text-green-400">
                                Similarity Score:
                              </span>
                              <span className="text-sm text-muted-foreground">
                                {doc.score.toFixed(4)}
                              </span>
                            </div>
                            {doc.rerank_score !== undefined && (
                              <div className="flex items-center gap-2">
                                <span className="text-sm font-semibold text-green-400">
                                  Rerank Score:
                                </span>
                                <span className="text-sm text-muted-foreground">
                                  {doc.rerank_score.toFixed(4)}
                                </span>
                              </div>
                            )}
                          </div>
                          <div className="text-sm text-foreground leading-relaxed whitespace-pre-wrap">
                            {highlightContent(doc.page_content, response.answer)}
                          </div>
                        </div>
                      </Card>
                    ))}
                  </div>
                )}
              </div>
            </Card>
          )}
        </div>
      </main>
    </div>
  );
};

export default QA;
