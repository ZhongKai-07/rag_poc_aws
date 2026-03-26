import React, { useState, useRef, useEffect } from "react";
import { X, Send, Menu, Clock, ThumbsUp, ThumbsDown, Copy, RotateCcw, Bot, Minimize2, Wand2, FileText, Gavel, ChevronDown, ChevronUp, Search } from "lucide-react";
import { motion, AnimatePresence } from "motion/react";
import ReactMarkdown from "react-markdown";
import { ScenarioButtonsWeb } from "./ScenarioButtonsWeb";
import { HotQuestions } from "./HotQuestions";
import { historyItems as initialHistoryItems, historyItemsData, HistoryItem } from "./HistoryPanel";
// Removed figma asset import
// import botIcon from "figma:asset/27f58b3bbbd18d9ecbe925d4e9093be37a9712b3.png";
const botIcon = "https://images.unsplash.com/photo-1557683316-973673baf926?q=80&w=200&auto=format&fit=crop";
import { Language, getTranslation, getHotQuestions } from "../i18n/translations";
import { CitationModal, Citation } from "./CitationModal";
import { AgreementAssistantModal } from "./AgreementAssistantModal";
import { MessageWithCitations } from "./MessageWithCitations";
import { DownloadModal } from "./DownloadModal";
import { Tooltip } from "./Tooltip";
import { fetchProcessedFiles, fetchTopQuestionsMulti, askRealQuestion } from "../services/ragApi";
import { ProcessedFile, RAGResponse, TopQuestion } from "../services/types";

interface MaximizedChatProps {
  isOpen: boolean;
  onMinimize: () => void;
  onOpenFileParser: () => void;
  language: Language;
  onLanguageChange: (lang: Language) => void;
}

type MessageType = "user" | "bot";

interface Message {
  id: string;
  type: MessageType;
  content: string;
  timestamp: Date;
  scenario?: string;
  feedback?: "like" | "dislike" | null;
  citations?: Citation[];
  agreementType?: string;
  counterparty?: string;
}

export function MaximizedChat({ isOpen, onMinimize, onOpenFileParser, language, onLanguageChange }: MaximizedChatProps) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputValue, setInputValue] = useState("");
  const [showHistory, setShowHistory] = useState(false);
  const [currentScenario, setCurrentScenario] = useState<string>("");
  const [showLanguageMenu, setShowLanguageMenu] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [selectedCitation, setSelectedCitation] = useState<Citation | null>(null);
  const [showCitationModal, setShowCitationModal] = useState(false);
  const [showAgreementModal, setShowAgreementModal] = useState(false);
  const [isReferencedDocsExpanded, setIsReferencedDocsExpanded] = useState(true);
  const [showSmartFileParsingModal, setShowSmartFileParsingModal] = useState(false);
  const [fileParsingData, setFileParsingData] = useState<{ agreementType: string; counterparty: string }>({ agreementType: '', counterparty: '' });
  const [copiedMessageId, setCopiedMessageId] = useState<string | null>(null);
  const [isComposing, setIsComposing] = useState(false); // Track IME composition status
  const [historyList, setHistoryList] = useState<HistoryItem[]>(initialHistoryItems);
  const [historySearchQuery, setHistorySearchQuery] = useState("");
  const chatRef = useRef<HTMLDivElement>(null);

  // Real API states
  const [processedFiles, setProcessedFiles] = useState<ProcessedFile[]>([]);
  const [selectedFiles, setSelectedFiles] = useState<string[]>([]);
  const [searchMode, setSearchMode] = useState("mix");
  const [threshold, setThreshold] = useState("0");
  const [numDocs, setNumDocs] = useState("3");
  const [topQuestions, setTopQuestions] = useState<TopQuestion[]>([]);
  const [currentCitations, setCurrentCitations] = useState<Citation[]>([]);

  useEffect(() => {
    // Fetch available documents on mount
    fetchProcessedFiles().then(res => {
      if (res.status === "success" && res.files) {
        setProcessedFiles(res.files);
        // Default to selecting all files for the best experience
        setSelectedFiles(res.files.map(f => f.filename));
      }
    });
  }, []);

  useEffect(() => {
    if (selectedFiles.length > 0 && processedFiles.length > 0) {
      const indexNames = processedFiles
        .filter(f => selectedFiles.includes(f.filename))
        .map(f => f.index_name);
      if (indexNames.length > 0) {
        fetchTopQuestionsMulti(indexNames).then(res => {
          if (res.status === "success" && res.questions) {
            setTopQuestions(res.questions);
          }
        });
      }
    } else {
      setTopQuestions([]);
    }
  }, [selectedFiles, processedFiles]);

  // Helper function to get localized date/time labels based on history item ID (timestamp)
  const getLocalizedTime = (itemId: number): { date: string; time: string } => {
    const now = Date.now();
    const diff = now - itemId;
    
    // Calculate time difference
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);
    
    let time = "";
    let date = "";
    
    if (language === "zh-CN") {
      if (minutes < 60) {
        time = minutes <= 1 ? "刚刚" : `${minutes}分钟前`;
      } else if (hours < 24) {
        time = `${hours}小时前`;
      } else {
        time = `${days}天前`;
      }
      // 三大栏分组：当天、三天前、一周前
      if (days === 0) {
        date = "当天";
      } else if (days <= 3) {
        date = "三天前";
      } else {
        date = "一周前";
      }
    } else if (language === "zh-TW") {
      if (minutes < 60) {
        time = minutes <= 1 ? "剛剛" : `${minutes}分鐘前`;
      } else if (hours < 24) {
        time = `${hours}小時前`;
      } else {
        time = `${days}天前`;
      }
      // 三大栏分组：當天、三天前、一週前
      if (days === 0) {
        date = "當天";
      } else if (days <= 3) {
        date = "三天前";
      } else {
        date = "一週前";
      }
    } else {
      // English
      if (minutes < 60) {
        time = minutes <= 1 ? "Just now" : `${minutes} minutes ago`;
      } else if (hours < 24) {
        time = hours === 1 ? "1 hour ago" : `${hours} hours ago`;
      } else {
        time = days === 1 ? "1 day ago" : `${days} days ago`;
      }
      // 三大栏分组：Today、3 Days Ago、A Week Ago
      if (days === 0) {
        date = "Today";
      } else if (days <= 3) {
        date = "3 Days Ago";
      } else {
        date = "A Week Ago";
      }
    }
    
    return { date, time };
  };

  // Save current conversation to history when starting a new chat
  const saveToHistory = () => {
    if (messages.length === 0) return;

    const firstUserMessage = messages.find(m => m.type === "user");
    if (!firstUserMessage) return;

    const newHistoryItem: HistoryItem = {
      id: Date.now(),
      title: firstUserMessage.content.slice(0, 20) + (firstUserMessage.content.length > 20 ? "..." : ""),
      time: "", // Will be computed dynamically based on current language
      preview: firstUserMessage.content.slice(0, 30) + "...",
      date: "", // Will be computed dynamically based on current language
      messages: messages,
    };

    setHistoryList([newHistoryItem, ...historyList]);
  };

  // "New Chat" button - keep current scenario, clear messages, save to history
  const handleNewChat = () => {
    saveToHistory();
    setMessages([]);
    // Keep currentScenario to stay on current page
  };

  // "NEWTON" brand click - return to home page
  const handleBackToWelcome = () => {
    setMessages([]);
    setCurrentScenario(""); // Clear scenario to return to home page
  };

  const handleCopyMessage = (messageId: string, content: string) => {
    // Remove citation markers like {cite:1}, {cite:2}, etc.
    const cleanedContent = content.replace(/\{cite:\d+\}/g, '').trim();
    
    // Try modern Clipboard API first, fall back to document.execCommand
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(cleanedContent)
        .then(() => {
          setCopiedMessageId(messageId);
          setTimeout(() => {
            setCopiedMessageId(null);
          }, 2000);
        })
        .catch(() => {
          // Fallback to execCommand if Clipboard API fails
          fallbackCopyTextToClipboard(cleanedContent, messageId);
        });
    } else {
      // Use fallback for browsers without Clipboard API
      fallbackCopyTextToClipboard(cleanedContent, messageId);
    }
  };

  // Fallback copy method using textarea and execCommand
  const fallbackCopyTextToClipboard = (text: string, messageId: string) => {
    const textArea = document.createElement("textarea");
    textArea.value = text;
    textArea.style.position = "fixed";
    textArea.style.top = "0";
    textArea.style.left = "0";
    textArea.style.width = "2em";
    textArea.style.height = "2em";
    textArea.style.padding = "0";
    textArea.style.border = "none";
    textArea.style.outline = "none";
    textArea.style.boxShadow = "none";
    textArea.style.background = "transparent";
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();

    try {
      const successful = document.execCommand('copy');
      if (successful) {
        setCopiedMessageId(messageId);
        setTimeout(() => {
          setCopiedMessageId(null);
        }, 2000);
      }
    } catch (err) {
      console.error('Fallback: Unable to copy', err);
    }

    document.body.removeChild(textArea);
  };

  const handleLanguageChange = (lang: Language) => {
    onLanguageChange(lang);
    setShowLanguageMenu(false);
  };

  const handleScenarioClick = (scenario: string) => {
    setCurrentScenario(scenario);
  };

  const handleQuestionClick = async (question: string) => {
    const userMessage: Message = {
      id: `msg-${Date.now()}`,
      type: "user",
      content: question,
      timestamp: new Date(),
    };

    setMessages(prev => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const indexNames = processedFiles
        .filter(f => selectedFiles.includes(f.filename))
        .map(f => f.index_name);

      if (indexNames.length === 0) {
        throw new Error("Please select at least one document first.");
      }

      const sessionId = `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

      const response = await askRealQuestion({
        session_id: sessionId,
        index_names: indexNames,
        query: question,
        module: "RAG",
        vec_docs_num: parseInt(numDocs) || 3,
        txt_docs_num: parseInt(numDocs) || 3,
        vec_score_threshold: 0.0,
        text_score_threshold: 0.0,
        rerank_score_threshold: parseFloat(threshold) || 0,
        search_method: searchMode,
      });

      const mappedCitations: Citation[] = [];
      let index = 1;
      if (response.recall_documents) {
        response.recall_documents.forEach((doc) => {
          mappedCitations.push({
            id: `cite-${index++}`,
            source: `Document Segment`,
            content: doc.page_content,
            highlight: "Evidence from text",
          });
        });
      }
      setCurrentCitations(mappedCitations);

      const botMessage: Message = {
        id: `msg-${Date.now() + 1}`,
        type: "bot",
        content: response.answer,
        timestamp: new Date(),
        feedback: null,
        citations: mappedCitations,
      };
      setMessages(prev => [...prev, botMessage]);
    } catch (error: any) {
      const botMessage: Message = {
        id: `msg-${Date.now() + 1}`,
        type: "bot",
        content: `System Error: ${error.message}`,
        timestamp: new Date(),
        feedback: null,
      };
      setMessages(prev => [...prev, botMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSend = async () => {
    if (!inputValue.trim()) return;

    const lastMessage = messages[messages.length - 1];
    const agreementType = currentScenario === "Agreement Assistant" && lastMessage ? lastMessage.agreementType : undefined;
    const counterparty = currentScenario === "Agreement Assistant" && lastMessage ? lastMessage.counterparty : undefined;

    const userMessage: Message = {
      id: `msg-${Date.now()}`,
      type: "user",
      content: inputValue,
      timestamp: new Date(),
      agreementType,
      counterparty
    };

    const savedInputValue = inputValue;
    setMessages(prev => [...prev, userMessage]);
    setInputValue("");
    setIsSending(true);
    setIsLoading(true);

    setTimeout(() => {
      setIsSending(false);
    }, 600);

    try {
      if (currentScenario === "Agreement Assistant") {
        setTimeout(() => {
          const botMessage: Message = {
            id: `msg-${Date.now() + 1}`,
            type: "bot",
            content: "Mock Agreement Assistant Answer",
            timestamp: new Date(),
            feedback: null,
          };
          setMessages(prev => [...prev, botMessage]);
          setIsLoading(false);
        }, 1000);
      } else {
        const indexNames = processedFiles
          .filter(f => selectedFiles.includes(f.filename))
          .map(f => f.index_name);

        if (indexNames.length === 0) {
          throw new Error("Please select at least one document first.");
        }

        const sessionId = `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

        const response = await askRealQuestion({
          session_id: sessionId,
          index_names: indexNames,
          query: savedInputValue,
          module: "RAG",
          vec_docs_num: parseInt(numDocs) || 3,
          txt_docs_num: parseInt(numDocs) || 3,
          vec_score_threshold: 0.0,
          text_score_threshold: 0.0,
          rerank_score_threshold: parseFloat(threshold) || 0,
          search_method: searchMode,
        });

        const mappedCitations: Citation[] = [];
        let index = 1;
        if (response.recall_documents) {
          response.recall_documents.forEach((doc) => {
            mappedCitations.push({
              id: `cite-${index++}`,
              source: `Document Segment`,
              content: doc.page_content,
              highlight: "Evidence from text",
            });
          });
        }

        setCurrentCitations(mappedCitations);

        const botMessage: Message = {
          id: `msg-${Date.now() + 1}`,
          type: "bot",
          content: response.answer,
          timestamp: new Date(),
          feedback: null,
          citations: mappedCitations,
        };
        setMessages(prev => [...prev, botMessage]);
        setIsLoading(false);
      }
    } catch (error: any) {
      const botMessage: Message = {
        id: `msg-${Date.now() + 1}`,
        type: "bot",
        content: `System Error: ${error.message}`,
        timestamp: new Date(),
        feedback: null,
      };
      setMessages(prev => [...prev, botMessage]);
      setIsLoading(false);
    }
  };

  const handleFeedback = (messageId: string, feedbackType: "like" | "dislike") => {
    setMessages(prev =>
      prev.map(msg =>
        msg.id === messageId
          ? { ...msg, feedback: msg.feedback === feedbackType ? null : feedbackType }
          : msg
      )
    );
  };

  const handleRegenerate = async (messageId: string) => {
    const messageIndex = messages.findIndex(msg => msg.id === messageId);
    if (messageIndex <= 0) return;
    
    const userMessage = messages[messageIndex - 1];
    if (userMessage.type !== "user") return;
    
    setMessages(prev => prev.filter(msg => msg.id !== messageId));
    setIsLoading(true);
    
    try {
      if (currentScenario === "Agreement Assistant") {
        setTimeout(() => {
          const botMessage: Message = {
            id: `msg-${Date.now() + 1}`,
            type: "bot",
            content: "Mock Regenerated Answer",
            timestamp: new Date(),
            feedback: null,
          };
          setMessages(prev => [...prev, botMessage]);
          setIsLoading(false);
        }, 1000);
      } else {
        const indexNames = processedFiles
          .filter(f => selectedFiles.includes(f.filename))
          .map(f => f.index_name);

        if (indexNames.length === 0) {
          throw new Error("Please select at least one document first.");
        }

        const sessionId = `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

        const response = await askRealQuestion({
          session_id: sessionId,
          index_names: indexNames,
          query: userMessage.content,
          module: "RAG",
          vec_docs_num: parseInt(numDocs) || 3,
          txt_docs_num: parseInt(numDocs) || 3,
          vec_score_threshold: 0.0,
          text_score_threshold: 0.0,
          rerank_score_threshold: parseFloat(threshold) || 0,
          search_method: searchMode,
        });

        const mappedCitations: Citation[] = [];
        let index = 1;
        if (response.recall_documents) {
          response.recall_documents.forEach((doc) => {
            mappedCitations.push({
              id: `cite-${index++}`,
              source: `Document Segment`,
              content: doc.page_content,
              highlight: "Evidence from text",
            });
          });
        }

        setCurrentCitations(mappedCitations);

        const botMessage: Message = {
          id: `msg-${Date.now() + 1}`,
          type: "bot",
          content: response.answer,
          timestamp: new Date(),
          feedback: null,
          citations: mappedCitations,
        };
        setMessages(prev => [...prev, botMessage]);
        setIsLoading(false);
      }
    } catch (error: any) {
      const botMessage: Message = {
        id: `msg-${Date.now() + 1}`,
        type: "bot",
        content: `System Error: ${error.message}`,
        timestamp: new Date(),
        feedback: null,
      };
      setMessages(prev => [...prev, botMessage]);
      setIsLoading(false);
    }
  };

  const handleSelectHistory = (historyId: number) => {
    const selectedHistory = historyList.find(item => item.id === historyId);
    if (selectedHistory) {
      const last5Messages = selectedHistory.messages.slice(-5);
      setMessages(last5Messages);
      setCurrentScenario("");
    }
    setShowHistory(false);
  };

  useEffect(() => {
    if (chatRef.current) {
      chatRef.current.scrollTop = chatRef.current.scrollHeight;
    }
  }, [messages]);

  return (
    <>
      <AnimatePresence>
        {isOpen && (
          <motion.div
            key="maximized-chat"
            className="fixed inset-0 bg-white z-50 flex"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
          {/* Left Sidebar - Dynamic Width Based on History State */}
          <motion.div 
            className={`border-r border-gray-200 bg-[#F9FAFB] flex flex-col transition-all duration-300 ${
              showHistory ? 'w-64' : 'w-20'
            }`}
            initial={false}
            animate={{ width: showHistory ? 256 : 80 }}
          >
            {/* Sidebar Header */}
            <div className="px-4 py-6">
              {/* Menu Button */}
              <Tooltip content={getTranslation(language, "history")} position="right">
                <button
                  onClick={() => setShowHistory(!showHistory)}
                  className="p-2.5 hover:bg-gray-200 rounded-lg transition-colors w-full flex items-center justify-start"
                >
                  <svg className="w-5 h-5 text-gray-700 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                  </svg>
                </button>
              </Tooltip>
            </div>

            {/* Sidebar Content */}
            {showHistory ? (
              <div className="flex-1 flex flex-col overflow-hidden">
                {/* Search Bar */}
                <div className="px-3 pb-3">
                  <div className="relative">
                    <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-gray-500" />
                    <input
                      type="text"
                      placeholder={language === "zh-CN" ? "搜索历史记录..." : language === "zh-TW" ? "搜索歷史記錄..." : "Search history..."}
                      className="w-full py-2 pl-9 pr-3 text-sm border border-gray-300 rounded-lg focus:outline-none focus:border-blue-500"
                      value={historySearchQuery}
                      onChange={(e) => setHistorySearchQuery(e.target.value)}
                    />
                  </div>
                </div>

                {/* Scrollable History List */}
                <div className="flex-1 overflow-y-auto px-3">
                  {/* New Chat Button */}
                  {(messages.length > 0 || currentScenario) && (
                    <button
                      onClick={handleNewChat}
                      className="w-full mb-4 px-3 py-2.5 hover:bg-gray-200 rounded-lg transition-colors flex items-center gap-2 text-gray-800"
                    >
                      <Wand2 className="w-4 h-4 flex-shrink-0" />
                      <span className="text-sm font-medium">
                        {language === "zh-CN" ? "新对话" : language === "zh-TW" ? "新對話" : "New chat"}
                      </span>
                    </button>
                  )}

                  {/* Chat History with Date Groups */}
                  <div className="mb-4">
                    {/* Group history items by date */}
                    {(() => {
                      // Filter by search query first
                      const filteredList = historyList.filter(item =>
                        item.title.toLowerCase().includes(historySearchQuery.toLowerCase())
                      );

                      const groupedItems: Array<{ date: string; items: HistoryItem[] }> = [];
                      let currentDate = "";
                      let currentGroup: HistoryItem[] = [];
                      
                      filteredList.forEach((item) => {
                        const { date } = getLocalizedTime(item.id);
                        if (date !== currentDate) {
                          if (currentGroup.length > 0) {
                            groupedItems.push({ date: currentDate, items: currentGroup });
                          }
                          currentDate = date;
                          currentGroup = [item];
                        } else {
                          currentGroup.push(item);
                        }
                      });
                      
                      if (currentGroup.length > 0) {
                        groupedItems.push({ date: currentDate, items: currentGroup });
                      }
                      
                      return groupedItems.map((group) => (
                      <div key={group.date} className="mb-3">
                        {/* Date Label */}
                        <h3 className="px-3 mb-2 text-xs text-gray-400">
                          {group.date}
                        </h3>
                        
                        {/* Items in this date group */}
                        <div className="space-y-0.5">
                          {group.items.map((item) => {
                            const { time } = getLocalizedTime(item.id);
                            return (
                              <button
                                key={item.id}
                                onClick={() => {
                                  handleSelectHistory(item.id);
                                  setShowHistory(false);
                                }}
                                className="w-full px-3 py-2 text-left hover:bg-gray-200 rounded-lg transition-colors group"
                              >
                                <div className="text-sm text-gray-900 font-semibold truncate">
                                  {item.title}
                                </div>
                                <div className="text-xs text-gray-500 mt-0.5">
                                  {time}
                                </div>
                              </button>
                            );
                          })}
                        </div>
                      </div>
                    ));
                  })()}
                </div>
              </div>
            </div>
            ) : (
              <div className="flex-1 flex flex-col items-center gap-6">
                {/* New Chat Button - Icon Only */}
                {(messages.length > 0 || currentScenario) && (
                  <Tooltip content={language === "zh-CN" ? "新对话" : language === "zh-TW" ? "新對話" : "New chat"} position="right">
                    <button
                      onClick={handleNewChat}
                      className="p-2.5 hover:bg-gray-200 rounded-lg transition-colors"
                    >
                      <Wand2 className="w-5 h-5 text-gray-700" />
                    </button>
                  </Tooltip>
                )}
                
                {/* Gavel Icon Button - Agreement Assistant */}
                <Tooltip content={language === "zh-CN" ? "智能协议助手" : language === "zh-TW" ? "智能協議助手" : "Agreement Assistant"} position="right">
                  <button
                    onClick={() => setShowAgreementModal(true)}
                    className="p-2.5 hover:bg-gray-200 rounded-lg transition-colors"
                  >
                    <Gavel className="w-5 h-5 text-gray-700" />
                  </button>
                </Tooltip>
              </div>
            )}
          </motion.div>

          {/* Main Content */}
          <div className="flex-1 flex flex-col overflow-hidden">
            {/* Top Bar with Newton and Controls */}
            <div className="flex items-center justify-between px-6 py-4">
              {/* Newton Brand Name - Only show when not on welcome page */}
              {(messages.length > 0 || currentScenario) && (
                <Tooltip content={language === "zh-CN" ? "返回首页" : language === "zh-TW" ? "返回首頁" : "Back to Home"} position="bottom">
                  <button 
                    onClick={handleBackToWelcome}
                    className="text-xl font-bold bg-[linear-gradient(90deg,_#E935C6_0%,_#FF6B9D_25%,_#FFA574_50%,_#4FC3F7_75%,_#00B4D8_100%)] bg-clip-text text-transparent hover:opacity-80 transition-opacity cursor-pointer"
                  >
                    NEWTON
                  </button>
                </Tooltip>
              )}

              {/* Top Right Controls */}
              <div className={`flex items-center gap-3 ${messages.length === 0 && !currentScenario ? 'ml-auto' : ''}`}>
                {/* User Avatar with Info */}
                <div className="flex items-center gap-2.5">
                  <svg className="w-9 h-9" viewBox="0 0 32 32" fill="none">
                    <defs>
                      <linearGradient id="avatarGradient" x1="0%" y1="100%" x2="100%" y2="0%">
                        <stop offset="0%" stopColor="#FFA574" />
                        <stop offset="25%" stopColor="#E935C6" />
                        <stop offset="50%" stopColor="#FF6B9D" />
                        <stop offset="75%" stopColor="#4FC3F7" />
                        <stop offset="100%" stopColor="#00B4D8" />
                      </linearGradient>
                    </defs>
                    <circle cx="16" cy="16" r="15" stroke="url(#avatarGradient)" strokeWidth="1.5" fill="none" />
                    <path d="M16 8a3 3 0 100 6 3 3 0 000-6zM11 20a5 5 0 0110 0v1H11v-1z" fill="url(#avatarGradient)" />
                  </svg>
                  {/* User Info */}
                  <div className="flex flex-col">
                    <span className="text-xs font-medium text-gray-900">
                      {language === "zh-CN" ? "工号" : language === "zh-TW" ? "工號" : "ID"}: HK8888
                    </span>
                    <span className="text-xs text-gray-600">
                      COB
                    </span>
                  </div>
                </div>

                {/* Language Menu */}
                <div className="relative">
                  <Tooltip content={language === "zh-CN" ? "语言" : language === "zh-TW" ? "語言" : "Language"} position="bottom">
                    <button
                      onClick={() => setShowLanguageMenu(!showLanguageMenu)}
                      className="p-2.5 hover:bg-gray-100 rounded-lg transition-colors flex items-center justify-center"
                    >
                      <span className="text-xs font-medium text-gray-700">
                        {language === "zh-CN" ? "简" : language === "zh-TW" ? "繁" : "EN"}
                      </span>
                    </button>
                  </Tooltip>
                  {showLanguageMenu && (
                    <motion.div
                      className="absolute top-full right-0 mt-2 bg-white rounded-lg shadow-lg border border-border z-50 min-w-[120px]"
                      initial={{ opacity: 0, y: -10 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, y: -10 }}
                    >
                      <button
                        onClick={() => handleLanguageChange("zh-CN")}
                        className={`block w-full px-3 py-2 text-left text-sm hover:bg-gray-100 ${
                          language === "zh-CN" ? "font-medium text-gray-900" : "text-gray-700"
                        }`}
                      >
                        简体中文
                      </button>
                      <button
                        onClick={() => handleLanguageChange("zh-TW")}
                        className={`block w-full px-3 py-2 text-left text-sm hover:bg-gray-100 ${
                          language === "zh-TW" ? "font-medium text-gray-900" : "text-gray-700"
                        }`}
                      >
                        繁體中文
                      </button>
                      <button
                        onClick={() => handleLanguageChange("en")}
                        className={`block w-full px-3 py-2 text-left text-sm hover:bg-gray-100 ${
                          language === "en" ? "font-medium text-gray-900" : "text-gray-700"
                        }`}
                      >
                        English
                      </button>
                    </motion.div>
                  )}
                </div>

                {/* Minimize Button */}
                <Tooltip content={language === "zh-CN" ? "最小化" : language === "zh-TW" ? "最小化" : "Minimize"} position="bottom">
                  <button
                    onClick={onMinimize}
                    className="p-2.5 hover:bg-gray-100 rounded-lg transition-colors"
                  >
                    <Minimize2 className="w-5 h-5 text-gray-700" />
                  </button>
                </Tooltip>
              </div>
            </div>

            {/* Loading Progress Bar */}
            <div className="relative h-[2px] w-full overflow-visible bg-transparent">
              {isLoading ? (
                <motion.div
                  className="absolute inset-0 h-full"
                  initial={{ x: "-100%" }}
                  animate={{ x: "100%" }}
                  transition={{
                    duration: 1.5,
                    repeat: Infinity,
                    ease: "linear"
                  }}
                  style={{
                    background: "linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)",
                    width: "100%",
                    filter: "blur(1.5px)",
                    boxShadow: "0 0 8px rgba(233, 53, 198, 0.6)"
                  }}
                />
              ) : null}
            </div>

            {/* Messages or Welcome Area */}
            {messages.length === 0 && !currentScenario ? (
              <div className="flex-1 flex flex-col items-center justify-center px-8 py-12">
                {/* Welcome Icon and Name - Horizontal Layout */}
                <div className="mb-8 flex items-center gap-4">
                  <div className="w-20 h-20 flex items-center justify-center flex-shrink-0">
                    <img src={botIcon} alt="AI Assistant" className="w-full h-full object-contain" />
                  </div>
                  <h1 className="text-[80px] font-bold leading-[80px] bg-[linear-gradient(90deg,_#E935C6_0%,_#FF6B9D_25%,_#FFA574_50%,_#4FC3F7_75%,_#00B4D8_100%)] bg-clip-text text-transparent">
                    NEWTON
                  </h1>
                </div>

                {/* Large Input Box */}
                <div className="w-full max-w-3xl mb-8">
                  <div className="relative">
                    <button
                      className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400 cursor-not-allowed z-10"
                      title="添加"
                      disabled
                    >
                      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                      </svg>
                    </button>
                    <motion.div
                      className="relative w-full rounded-full"
                      animate={isSending ? {
                        boxShadow: [
                          "0 0 0 0 rgba(233, 53, 198, 0)",
                          "0 0 20px 3px rgba(233, 53, 198, 0.6)",
                          "0 0 20px 3px rgba(255, 107, 157, 0.6)",
                          "0 0 20px 3px rgba(255, 165, 116, 0.6)",
                          "0 0 20px 3px rgba(79, 195, 247, 0.6)",
                          "0 0 20px 3px rgba(0, 180, 216, 0.6)",
                          "0 0 0 0 rgba(0, 180, 216, 0)"
                        ]
                      } : {}}
                      transition={{ duration: 0.6, ease: "easeOut" }}
                    >
                      <input
                        type="text"
                        value={inputValue}
                        onChange={(e) => setInputValue(e.target.value)}
                        onCompositionStart={() => setIsComposing(true)}
                        onCompositionEnd={() => setIsComposing(false)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter" && !e.shiftKey && !isComposing) {
                            e.preventDefault();
                            handleSend();
                          }
                        }}
                        placeholder={getTranslation(language, "inputPlaceholder")}
                        className="w-full pl-12 pr-14 py-6 border border-gray-300 rounded-full focus:outline-none focus:ring-0 focus:border-gray-300 bg-white text-base placeholder:text-gray-400"
                      />
                    </motion.div>
                    <button
                      onClick={handleSend}
                      disabled={!inputValue.trim()}
                      className="absolute right-4 top-1/2 -translate-y-1/2 text-blue-400 hover:text-blue-500 transition-colors disabled:opacity-40 disabled:hover:text-blue-400 z-10"
                    >
                      <Send className="w-5 h-5" />
                    </button>
                  </div>
                </div>

                {/* Scenario Buttons Below Input */}
                <div className="w-full">
                  <ScenarioButtonsWeb onScenarioClick={handleScenarioClick} language={language} />
                </div>
              </div>
            ) : currentScenario && messages.length === 0 ? (
              <div className="flex-1 overflow-y-auto relative bg-white">
                {/* Single Column Layout - No Documents Yet */}
                <div className="min-h-full flex justify-center px-8 py-6">
                  {/* Center Column: Popular Questions and Input */}
                  <div className="w-full max-w-[720px] flex flex-col">
                    {/* Popular Questions Area */}
                    <div className="flex-1 mb-6">
                      <div className="space-y-2">
                        {getHotQuestions(language, currentScenario).map((question: string, index: number) => (
                          <button
                            key={index}
                            onClick={() => handleQuestionClick(question)}
                            className="w-full text-left px-4 py-3 text-sm text-gray-700 hover:bg-gray-50 rounded-lg transition-all flex items-start gap-3"
                          >
                            {/* Search Icon - Purple-Magenta Gradient */}
                            <svg className="w-4 h-4 flex-shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                              <defs>
                                <linearGradient id={`searchGradient-${index}`} x1="0%" y1="0%" x2="100%" y2="100%">
                                  <stop offset="0%" stopColor="#9333EA" />
                                  <stop offset="50%" stopColor="#C026D3" />
                                  <stop offset="100%" stopColor="#E935C6" />
                                </linearGradient>
                              </defs>
                              {/* Search circle */}
                              <circle 
                                cx="11" 
                                cy="11" 
                                r="7" 
                                stroke={`url(#searchGradient-${index})`}
                                strokeWidth="2"
                                fill="none"
                              />
                              {/* Search handle */}
                              <path 
                                d="M16 16L21 21" 
                                stroke={`url(#searchGradient-${index})`}
                                strokeWidth="2"
                                strokeLinecap="round"
                              />
                            </svg>
                            <span className="flex-1">{question}</span>
                          </button>
                        ))}
                      </div>
                    </div>

                    {/* Input Area - Fixed at Bottom */}
                    <div className="sticky bottom-0 left-0 right-0 py-4 bg-white">
                      <div className="relative">
                        <button
                          className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400 cursor-not-allowed"
                          title="添加附件"
                          disabled
                        >
                          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                          </svg>
                        </button>
                        <input
                          type="text"
                          value={inputValue}
                          onChange={(e) => setInputValue(e.target.value)}
                          onCompositionStart={() => setIsComposing(true)}
                          onCompositionEnd={() => setIsComposing(false)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter" && !e.shiftKey && !isComposing) {
                              e.preventDefault();
                              handleSend();
                            }
                          }}
                          placeholder={language === "zh-CN" ? "问我任何问题" : language === "zh-TW" ? "問我任何問題" : "Ask anything"}
                          className="w-full pl-12 pr-12 py-3.5 bg-gray-100 rounded-full focus:outline-none focus:bg-gray-200 text-base text-gray-900 placeholder:text-gray-500 border-0"
                        />
                        <button
                          onClick={handleSend}
                          disabled={!inputValue.trim()}
                          className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-600 hover:text-primary transition-colors disabled:text-gray-400 disabled:opacity-50"
                          title="发送"
                        >
                          <Send className="w-5 h-5" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            ) : !messages.some(m => m.type === "bot") ? (
              /* First Question Sent - Waiting for Bot Response (Single Column) */
              <div className="flex-1 overflow-y-auto relative bg-white">
                <div className="min-h-full flex justify-center px-8 py-6">
                  <div className="w-full max-w-[720px] flex flex-col">
                    {/* Messages Area */}
                    <div className="flex-1">
                      <div className="space-y-6">
                        {/* Today Divider */}
                        <div className="flex items-center justify-center mb-6">
                          <div className="bg-gray-200 text-gray-600 px-4 py-1 rounded-full text-xs">
                            {language === "zh-CN" && "今天"}
                            {language === "zh-TW" && "今天"}
                            {language === "en" && "Today"}
                          </div>
                        </div>

                        {messages.map((message) => (
                          <div key={message.id}>
                            {message.type === "user" && (
                              <div className="flex justify-end mb-6">
                                <div className="bg-gray-50 text-gray-900 px-5 py-3 rounded-2xl max-w-[65%] text-[15px] border border-gray-100">
                                  {message.content}
                                </div>
                              </div>
                            )}
                          </div>
                        ))}
                        
                        {/* Loading indicator when waiting for bot response */}
                        {isLoading && !messages.some(m => m.type === "bot") && (
                          <div className="mb-8">
                            <div className="flex items-start gap-3">
                              <div className="w-10 h-10 flex items-center justify-center flex-shrink-0"></div>
                              <div className="flex-1">
                                <div className="px-2 py-2">
                                  <div className="flex items-center gap-1">
                                    <style>{`
                                      @keyframes bounce-dot {
                                        0%, 60%, 100% {
                                          transform: translateY(0);
                                        }
                                        30% {
                                          transform: translateY(-8px);
                                        }
                                      }
                                      .dot-1 {
                                        animation: bounce-dot 1.4s infinite;
                                        animation-delay: 0s;
                                      }
                                      .dot-2 {
                                        animation: bounce-dot 1.4s infinite;
                                        animation-delay: 0.2s;
                                      }
                                      .dot-3 {
                                        animation: bounce-dot 1.4s infinite;
                                        animation-delay: 0.4s;
                                      }
                                    `}</style>
                                    <div 
                                      className="dot-1 w-2 h-2 rounded-full"
                                      style={{
                                        background: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 100%)'
                                      }}
                                    />
                                    <div 
                                      className="dot-2 w-2 h-2 rounded-full"
                                      style={{
                                        background: 'linear-gradient(90deg, #FF6B9D 0%, #FFA574 100%)'
                                      }}
                                    />
                                    <div 
                                      className="dot-3 w-2 h-2 rounded-full"
                                      style={{
                                        background: 'linear-gradient(90deg, #4FC3F7 0%, #00B4D8 100%)'
                                      }}
                                    />
                                  </div>
                                </div>
                              </div>
                            </div>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Input Area - Fixed at Bottom */}
                    <div className="sticky bottom-0 left-0 right-0 py-4 bg-white">
                      <motion.div
                        className="relative rounded-full"
                        animate={isSending ? {
                          boxShadow: [
                            "0 0 0 0 rgba(233, 53, 198, 0)",
                            "0 0 20px 3px rgba(233, 53, 198, 0.6)",
                            "0 0 20px 3px rgba(255, 107, 157, 0.6)",
                            "0 0 20px 3px rgba(255, 165, 116, 0.6)",
                            "0 0 20px 3px rgba(79, 195, 247, 0.6)",
                            "0 0 20px 3px rgba(0, 180, 216, 0.6)",
                            "0 0 0 0 rgba(0, 180, 216, 0)"
                          ]
                        } : {}}
                        transition={{ duration: 0.6, ease: "easeOut" }}
                      >
                        <button
                          className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400 cursor-not-allowed z-10"
                          title="添加附件"
                          disabled
                        >
                          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                          </svg>
                        </button>
                        <input
                          type="text"
                          value={inputValue}
                          onChange={(e) => setInputValue(e.target.value)}
                          onCompositionStart={() => setIsComposing(true)}
                          onCompositionEnd={() => setIsComposing(false)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter" && !e.shiftKey && !isComposing) {
                              e.preventDefault();
                              handleSend();
                            }
                          }}
                          placeholder={language === "zh-CN" ? "问我任何问题" : language === "zh-TW" ? "問我任何問題" : "Ask anything"}
                          className="w-full pl-12 pr-12 py-3.5 bg-gray-100 rounded-full focus:outline-none focus:bg-gray-200 text-base text-gray-900 placeholder:text-gray-500 border-0"
                        />
                        <button
                          onClick={handleSend}
                          disabled={!inputValue.trim()}
                          className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-600 hover:text-primary transition-colors disabled:text-gray-400 disabled:opacity-50 z-10"
                          title="发送"
                        >
                          <Send className="w-5 h-5" />
                        </button>
                      </motion.div>
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              <div className="flex-1 overflow-y-auto relative bg-white">
                {/* Main Content Area - Conditional Layout */}
                <div className={`min-h-full ${currentScenario === "Agreement Assistant" ? "flex justify-center" : "flex gap-4 max-w-[1200px]"} mx-auto px-8 py-6`}>
                  {/* Left Column: Messages and Input */}
                  <div className={`flex-1 flex flex-col ${currentScenario === "Agreement Assistant" ? "max-w-[800px]" : "max-w-[720px]"}`}>
                    {/* Messages Area */}
                    <div className="flex-1">
                      <div className="space-y-6">
                        {/* Today Divider */}
                        <div className="flex items-center justify-center mb-6">
                          <div className="bg-gray-200 text-gray-600 px-4 py-1 rounded-full text-xs">
                            {language === "zh-CN" && "天"}
                            {language === "zh-TW" && "今天"}
                            {language === "en" && "Today"}
                          </div>
                        </div>

                        {messages.map((message, index) => (
                          <div key={message.id}>
                            {message.type === "user" ? (
                              <div className="flex justify-end mb-6">
                                <div className="bg-gray-50 text-gray-900 px-5 py-3 rounded-2xl max-w-[65%] text-[15px] border border-gray-100">
                                  {message.content}
                                </div>
                              </div>
                            ) : (
                              <div className="mb-8">
                                <div className="flex items-start gap-3 mb-3">
                                  <div className="w-10 h-10 flex items-center justify-center flex-shrink-0">
                                    {isLoading && index === messages.length - 1 ? null : (
                                      <img src={botIcon} alt="AI Assistant" className="w-full h-full object-contain" />
                                    )}
                                  </div>
                                  <div className="flex-1">
                                    <>
                                    {isLoading && index === messages.length - 1 ? (
                                      /* Three Bouncing Dots with NEWTON Gradient - Aligned with answer text */
                                      <div className="px-2 py-2">
                                        <div className="flex items-center gap-1">
                                          <style>{`
                                            @keyframes bounce-dot {
                                              0%, 60%, 100% {
                                                transform: translateY(0);
                                              }
                                              30% {
                                                transform: translateY(-8px);
                                              }
                                            }
                                            .dot-1 {
                                              animation: bounce-dot 1.4s infinite;
                                              animation-delay: 0s;
                                            }
                                            .dot-2 {
                                              animation: bounce-dot 1.4s infinite;
                                              animation-delay: 0.2s;
                                            }
                                            .dot-3 {
                                              animation: bounce-dot 1.4s infinite;
                                              animation-delay: 0.4s;
                                            }
                                          `}</style>
                                          <div 
                                            className="dot-1 w-2 h-2 rounded-full"
                                            style={{
                                              background: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 100%)'
                                            }}
                                          />
                                          <div 
                                            className="dot-2 w-2 h-2 rounded-full"
                                            style={{
                                              background: 'linear-gradient(90deg, #FF6B9D 0%, #FFA574 100%)'
                                            }}
                                          />
                                          <div 
                                            className="dot-3 w-2 h-2 rounded-full"
                                            style={{
                                              background: 'linear-gradient(90deg, #4FC3F7 0%, #00B4D8 100%)'
                                            }}
                                          />
                                        </div>
                                      </div>
                                    ) : (
                                    <div className="text-gray-900 px-2 py-2">
                                      {/* Render message with inline citations */}
                                      <MessageWithCitations 
                                        content={message.content} 
                                        citations={message.citations}
                                        showCitations={currentScenario !== "Agreement Assistant"}
                                        agreementType={message.agreementType}
                                        counterparty={message.counterparty}
                                      />
                                    </div>
                                    )}
                                    
                                    {/* Action Buttons and Time */}
                                    <div className="flex items-center justify-between mt-2 px-2">
                                      <div className="flex items-center gap-0.5">
                                        <Tooltip content={language === "zh-CN" ? "重新生成" : language === "zh-TW" ? "重新生成" : "Regenerate"} position="top">
                                          <button
                                            onClick={() => handleRegenerate(message.id)}
                                            className="p-2 rounded-lg transition-all hover:bg-gray-200 text-gray-500 hover:text-gray-700"
                                          >
                                            <RotateCcw className="w-4 h-4" />
                                          </button>
                                        </Tooltip>
                                        <Tooltip content={copiedMessageId === message.id ? (language === "zh-CN" ? "已复制" : language === "zh-TW" ? "已複製" : "Copied") : (language === "zh-CN" ? "复制" : language === "zh-TW" ? "複製" : "Copy")} position="top">
                                          <button
                                            onClick={() => handleCopyMessage(message.id, message.content)}
                                            className={`p-2 rounded-lg transition-all ${
                                              copiedMessageId === message.id
                                                ? "bg-green-50 text-green-600"
                                                : "hover:bg-gray-200 text-gray-500 hover:text-gray-700"
                                            }`}
                                          >
                                            <Copy className="w-4 h-4" />
                                          </button>
                                        </Tooltip>
                                        <Tooltip content={language === "zh-CN" ? "点赞" : language === "zh-TW" ? "點讚" : "Like"} position="top">
                                          <button
                                            onClick={() => handleFeedback(message.id, "like")}
                                            className={`p-2 rounded-lg transition-all hover:bg-primary/10 ${
                                              message.feedback === "like"
                                                ? "bg-primary/10 text-primary"
                                                : "text-gray-500 hover:text-primary"
                                            }`}
                                          >
                                            <ThumbsUp className="w-4 h-4" />
                                          </button>
                                        </Tooltip>
                                        <Tooltip content={language === "zh-CN" ? "点踩" : language === "zh-TW" ? "點踩" : "Dislike"} position="top">
                                          <button
                                            onClick={() => handleFeedback(message.id, "dislike")}
                                            className={`p-2 rounded-lg transition-all hover:bg-red-50 ${
                                              message.feedback === "dislike"
                                                ? "bg-red-50 text-red-500"
                                                : "text-gray-500 hover:text-red-500"
                                            }`}
                                          >
                                            <ThumbsDown className="w-4 h-4" />
                                          </button>
                                        </Tooltip>
                                      </div>
                                      <div className="text-xs text-gray-400">
                                        {message.timestamp.toLocaleTimeString(language === "zh-CN" ? "zh-CN" : language === "zh-TW" ? "zh-TW" : "en-US", { 
                                          hour: '2-digit', 
                                          minute: '2-digit',
                                          hour12: language === "en"
                                        })}
                                      </div>
                                    </div>
                                    
                                    {/* Related Questions removed per user request */}
                                    </>
                                  </div>
                                </div>
                              </div>
                            )}
                          </div>
                        ))}
                        
                        {/* Loading indicator when waiting for bot response */}
                        {isLoading && messages.length > 0 && messages[messages.length - 1].type === "user" && (
                          <div className="mb-8">
                            <div className="flex items-start gap-3">
                              <div className="w-10 h-10 flex items-center justify-center flex-shrink-0"></div>
                              <div className="flex-1">
                                <div className="px-2 py-2">
                                  <div className="flex items-center gap-1">
                                    <style>{`
                                      @keyframes bounce-dot-main {
                                        0%, 60%, 100% {
                                          transform: translateY(0);
                                        }
                                        30% {
                                          transform: translateY(-8px);
                                        }
                                      }
                                      .dot-main-1 {
                                        animation: bounce-dot-main 1.4s infinite;
                                        animation-delay: 0s;
                                      }
                                      .dot-main-2 {
                                        animation: bounce-dot-main 1.4s infinite;
                                        animation-delay: 0.2s;
                                      }
                                      .dot-main-3 {
                                        animation: bounce-dot-main 1.4s infinite;
                                        animation-delay: 0.4s;
                                      }
                                    `}</style>
                                    <div 
                                      className="dot-main-1 w-2 h-2 rounded-full"
                                      style={{
                                        background: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 100%)'
                                      }}
                                    />
                                    <div 
                                      className="dot-main-2 w-2 h-2 rounded-full"
                                      style={{
                                        background: 'linear-gradient(90deg, #FF6B9D 0%, #FFA574 100%)'
                                      }}
                                    />
                                    <div 
                                      className="dot-main-3 w-2 h-2 rounded-full"
                                      style={{
                                        background: 'linear-gradient(90deg, #4FC3F7 0%, #00B4D8 100%)'
                                      }}
                                    />
                                  </div>
                                </div>
                              </div>
                            </div>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Input Area - Fixed at Bottom */}
                    <div className="sticky bottom-0 left-0 right-0 py-4 bg-white">
                      <motion.div
                        className="relative rounded-full"
                        animate={isSending ? {
                          boxShadow: [
                            "0 0 0 0 rgba(233, 53, 198, 0)",
                            "0 0 20px 3px rgba(233, 53, 198, 0.6)",
                            "0 0 20px 3px rgba(255, 107, 157, 0.6)",
                            "0 0 20px 3px rgba(255, 165, 116, 0.6)",
                            "0 0 20px 3px rgba(79, 195, 247, 0.6)",
                            "0 0 20px 3px rgba(0, 180, 216, 0.6)",
                            "0 0 0 0 rgba(0, 180, 216, 0)"
                          ]
                        } : {}}
                        transition={{ duration: 0.6, ease: "easeOut" }}
                      >
                        <button
                          className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400 cursor-not-allowed z-10"
                          title="添加附件"
                          disabled
                        >
                          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                          </svg>
                        </button>
                        <input
                          type="text"
                          value={inputValue}
                          onChange={(e) => setInputValue(e.target.value)}
                          onCompositionStart={() => setIsComposing(true)}
                          onCompositionEnd={() => setIsComposing(false)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter" && !e.shiftKey && !isComposing) {
                              e.preventDefault();
                              handleSend();
                            }
                          }}
                          placeholder={language === "zh-CN" ? "问任何问题" : language === "zh-TW" ? "問我任何題" : "Ask anything"}
                          className="w-full pl-12 pr-12 py-3.5 bg-gray-100 rounded-full focus:outline-none focus:bg-gray-200 text-base text-gray-900 placeholder:text-gray-500 border-0"
                        />
                        <button
                          onClick={handleSend}
                          disabled={!inputValue.trim()}
                          className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-600 hover:text-primary transition-colors disabled:text-gray-400 disabled:opacity-50 z-10"
                          title="发送"
                        >
                          <Send className="w-5 h-5" />
                        </button>
                      </motion.div>
                    </div>
                  </div>

                  {/* Right Column: Document Reference Card - Hidden for Agreement Assistant */}
                  {currentScenario !== "Agreement Assistant" && (
                    <div className="w-[300px] flex-shrink-0">
                    <div className="bg-white border border-gray-200 rounded-xl p-4 shadow-sm sticky top-6">
                      {/* Header */}
                      <div className={`${isReferencedDocsExpanded ? 'mb-3 pb-3 border-b border-gray-100' : ''}`}>
                        <div className="flex items-center justify-between">
                          <h3 className="text-sm font-medium bg-gradient-to-r from-[#E935C6] via-[#FF6B9D] via-[#FFA574] via-[#4FC3F7] to-[#00B4D8] bg-clip-text text-transparent">
                            {language === "zh-CN" ? "引用的文档" : language === "zh-TW" ? "引用的文檔" : "Referenced Documents"}
                          </h3>
                          <button 
                            className="p-0.5 hover:bg-gray-100 rounded-full transition-colors"
                            onClick={() => setIsReferencedDocsExpanded(!isReferencedDocsExpanded)}
                          >
                            {isReferencedDocsExpanded ? (
                              <svg className="w-3.5 h-3.5 text-gray-500" viewBox="0 0 20 20" fill="none" stroke="currentColor">
                                <line x1="4" y1="10" x2="16" y2="10" strokeWidth="2" strokeLinecap="round"/>
                              </svg>
                            ) : (
                              <svg className="w-3.5 h-3.5 text-gray-500" fill="currentColor" viewBox="0 0 20 20">
                                <circle cx="10" cy="4" r="1.5"/>
                                <circle cx="10" cy="10" r="1.5"/>
                                <circle cx="10" cy="16" r="1.5"/>
                              </svg>
                            )}
                          </button>
                        </div>
                      </div>
                      
                      {/* Document List */}
                      {isReferencedDocsExpanded && (
                      <>
                      <div className="space-y-0 max-h-[500px] overflow-y-auto">
                        {/* Document Card 1 */}
                        <div className="group py-3 border-b border-gray-100 last:border-0">
                          <a href="#" className="block" onClick={(e) => e.preventDefault()}>
                            <div className="flex items-start gap-2">
                              <div className="inline-flex items-center justify-center w-4 h-4 bg-gray-300 text-white text-[9px] font-medium rounded-full flex-shrink-0 mt-0.5">
                                1
                              </div>
                              <div className="flex-1 min-w-0">
                                <h3 className="text-sm font-medium text-gray-900 mb-0.5 leading-tight group-hover:underline">
                                  {language === "zh-CN" ? "开户指引" : language === "zh-TW" ? "開戶指引" : "Account Opening Guide"}
                                </h3>
                                <p className="text-xs text-gray-500 leading-relaxed line-clamp-2">
                                  {language === "zh-CN"
                                    ? "完整开户流程说明"
                                    : language === "zh-TW"
                                    ? "完整開戶流程說明"
                                    : "Complete account opening process"}
                                </p>
                              </div>
                            </div>
                          </a>
                        </div>

                        {/* Document Card 2 */}
                        <div className="group py-3 border-b border-gray-100 last:border-0">
                          <a href="#" className="block" onClick={(e) => e.preventDefault()}>
                            <div className="flex items-start gap-2">
                              <div className="inline-flex items-center justify-center w-4 h-4 bg-gray-300 text-white text-[9px] font-medium rounded-full flex-shrink-0 mt-0.5">
                                2
                              </div>
                              <div className="flex-1 min-w-0">
                                <h3 className="text-sm font-medium text-gray-900 mb-0.5 leading-tight group-hover:underline">
                                  {language === "zh-CN" ? "客户尽职调查" : language === "zh-TW" ? "客盡職調查" : "Customer Due Diligence"}
                                </h3>
                                <p className="text-xs text-gray-500 leading-relaxed line-clamp-2">
                                  {language === "zh-CN"
                                    ? "KYC要求和文件���单"
                                    : language === "zh-TW"
                                    ? "KYC要求和文件清單"
                                    : "KYC requirements and documents"}
                                </p>
                              </div>
                            </div>
                          </a>
                        </div>

                        {/* Document Card 3 */}
                        <div className="group py-3 border-b border-gray-100 last:border-0">
                          <a href="#" className="block" onClick={(e) => e.preventDefault()}>
                            <div className="flex items-start gap-2">
                              <div className="inline-flex items-center justify-center w-4 h-4 bg-gray-300 text-white text-[9px] font-medium rounded-full flex-shrink-0 mt-0.5">
                                3
                              </div>
                              <div className="flex-1 min-w-0">
                                <h3 className="text-sm font-medium text-gray-900 mb-0.5 leading-tight group-hover:underline">
                                  {language === "zh-CN" ? "证监会规定" : language === "zh-TW" ? "證監會規定" : "SFC Regulations"}
                                </h3>
                                <p className="text-xs text-gray-500 leading-relaxed line-clamp-2">
                                  {language === "zh-CN"
                                    ? "相关法规条文"
                                    : language === "zh-TW"
                                    ? "相關法規條文"
                                    : "Relevant regulatory provisions"}
                                </p>
                              </div>
                            </div>
                          </a>
                        </div>

                        {/* Document Card 4 */}
                        <div className="group py-3 border-b border-gray-100 last:border-0">
                          <a href="#" className="block" onClick={(e) => e.preventDefault()}>
                            <div className="flex items-start gap-2">
                              <div className="inline-flex items-center justify-center w-4 h-4 bg-gray-300 text-white text-[9px] font-medium rounded-full flex-shrink-0 mt-0.5">
                                4
                              </div>
                              <div className="flex-1 min-w-0">
                                <h3 className="text-sm font-medium text-gray-900 mb-0.5 leading-tight group-hover:underline">
                                  {language === "zh-CN" ? "账户激活流程" : language === "zh-TW" ? "賬戶激活流程" : "Account Activation"}
                                </h3>
                                <p className="text-xs text-gray-500 leading-relaxed line-clamp-2">
                                  {language === "zh-CN"
                                    ? "开户后激活步骤"
                                    : language === "zh-TW"
                                    ? "開戶後激活步驟"
                                    : "Post-opening activation steps"}
                                </p>
                              </div>
                            </div>
                          </a>
                        </div>
                      </div>

                      {/* Show all button */}
                      <div className="mt-3 pt-3 border-t border-gray-100">
                        <button className="w-full text-center py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-lg transition-colors font-medium">
                          {language === "zh-CN" ? "Show all" : language === "zh-TW" ? "Show all" : "Show all"}
                        </button>
                      </div>
                      </>
                      )}
                    </div>
                  </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </motion.div>
      )}
      </AnimatePresence>
      
      {/* Citation Modal */}
      <CitationModal
        isOpen={showCitationModal}
        onClose={() => {
          setShowCitationModal(false);
          setSelectedCitation(null);
        }}
        citation={selectedCitation}
      />
      
      {/* Agreement Assistant Modal */}
      <AgreementAssistantModal
        isOpen={showAgreementModal}
        onClose={() => setShowAgreementModal(false)}
        language={language}
        onAsk={(agreementType, counterparty, selectedTab) => {
          setShowAgreementModal(false);
          setCurrentScenario("Agreement Assistant");
          
          // Create initial user message with the query
          const query = selectedTab === "agreement" 
            ? `Tell me about ${agreementType} agreement`
            : `Show me information about counterparty: ${counterparty}`;
          
          const userMessage: Message = {
            id: `msg-${Date.now()}`,
            type: "user",
            content: query,
            timestamp: new Date(),
            scenario: "Agreement Assistant",
            agreementType,
            counterparty
          };

          setMessages([userMessage]);
          setIsLoading(true);

          // Simulate bot response
          setTimeout(() => {
            const botResponse: Message = {
              id: `msg-${Date.now()}-bot`,
              type: "bot",
              content: selectedTab === "agreement"
                ? `I can help you with the **${agreementType}** agreement. This is a standard financial agreement used in derivatives trading.\n\n**Key features:**\n- Standardized legal framework\n- Governing law and jurisdiction clauses\n- Credit support provisions\n- Termination and events of default\n\nWhat specific information would you like to know about this agreement?`
                : `I found information about counterparty **${counterparty}**.\n\n**Profile Summary:**\n- Legal Entity: ${counterparty}\n- Jurisdiction: United States\n- Agreement Types: ISDA, CSA, GMRA\n- Risk Rating: A+\n\nWhat would you like to know about this counterparty?`,
              timestamp: new Date(),
              scenario: "Agreement Assistant",
              agreementType,
              counterparty
            };
            setMessages(prev => [...prev, botResponse]);
            setIsLoading(false);
            
            // Create new history item
            const newHistoryId = Date.now();
            const historyTitle = `${agreementType} - ${counterparty}`;
            const historyPreview = selectedTab === "agreement" 
              ? `${agreementType} agreement details...`
              : `Counterparty ${counterparty} information...`;
            
            const newHistoryItem: HistoryItem = {
              id: newHistoryId,
              title: historyTitle,
              time: getLocalizedTime(newHistoryId).time,
              preview: historyPreview,
              date: getLocalizedTime(newHistoryId).date,
              messages: [
                {
                  id: userMessage.id,
                  type: userMessage.type,
                  content: userMessage.content,
                  timestamp: userMessage.timestamp,
                  feedback: null
                },
                {
                  id: botResponse.id,
                  type: botResponse.type,
                  content: botResponse.content,
                  timestamp: botResponse.timestamp,
                  feedback: null
                }
              ]
            };
            
            // Add to history list (prepend to show at the top)
            setHistoryList(prev => [newHistoryItem, ...prev]);
          }, 1500);
        }}
        onDownloadSuccess={(agreementType, counterparty) => {
          // Store data for Smart File Parsing modal
          setFileParsingData({ agreementType, counterparty });
          // Show Smart File Parsing modal
          setShowSmartFileParsingModal(true);
        }}
      />
      
      {/* Smart File Parsing Modal */}
      <DownloadModal
        isOpen={showSmartFileParsingModal}
        onClose={() => setShowSmartFileParsingModal(false)}
        language={language}
        agreementType={fileParsingData.agreementType}
        counterparty={fileParsingData.counterparty}
      />
    </>
  );
}