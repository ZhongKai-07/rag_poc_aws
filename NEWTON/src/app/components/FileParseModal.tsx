import { motion, AnimatePresence } from "motion/react";
import { X, Upload, FileText, Check, Edit3, Eye, Download, Folder, Trash2, AlertCircle, Loader2, ChevronLeft, ChevronRight, FileSearch } from "lucide-react";
import { useState, useEffect } from "react";
import { Language, getTranslation } from "../i18n/translations";
import { uploadRealFiles } from "../services/ragApi";

interface FileParseModalProps {
  isOpen: boolean;
  onClose: () => void;
  isMaximized?: boolean;
  language: Language;
}

type Step = "upload" | "file-list" | "parsing" | "preview" | "confirm" | "download-path";

interface UploadedFileItem {
  id: string;
  file: File;
  status: "pending" | "parsing" | "success" | "error";
  parsedData?: any;
  error?: string;
}

export function FileParseModal({ isOpen, onClose, isMaximized, language }: FileParseModalProps) {
  const [currentStep, setCurrentStep] = useState<Step>("upload");
  const [uploadedFiles, setUploadedFiles] = useState<UploadedFileItem[]>([]);
  const [currentFileIndex, setCurrentFileIndex] = useState(0);
  const [parsedData, setParsedData] = useState<any>(null);
  const [editingField, setEditingField] = useState<string | null>(null);
  const [countdown, setCountdown] = useState(20);
  const [hasDownloaded, setHasDownloaded] = useState(false);
  const [selectedPath, setSelectedPath] = useState("下載");
  const [fileName, setFileName] = useState("");

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      const newFiles: UploadedFileItem[] = Array.from(files).map((file) => ({
        id: Math.random().toString(36).substring(7),
        file,
        status: "pending",
      }));
      setUploadedFiles(newFiles);
      setCurrentStep("file-list");
    }
  };

  const handleDeleteFile = (id: string) => {
    setUploadedFiles((prev) => prev.filter((f) => f.id !== id));
  };

  const handleConfirmFiles = async () => {
    if (uploadedFiles.length === 0) return;
    setCurrentStep("parsing");
    
    const now = new Date();
    const dateTime = now.toISOString().slice(0, 16).replace('T', '-').replace(':', '-'); // YYYY-MM-DD-HH-MM format
    const directoryPath = `./documents/${dateTime}`;

    // Process files sequentially or in parallel. Let's do parallel to be faster but update states.
    const uploadPromises = uploadedFiles.map(async (fileItem) => {
      // Set to parsing
      setUploadedFiles((prev) =>
        prev.map((f) => (f.id === fileItem.id ? { ...f, status: "parsing" } : f))
      );

      try {
        const response = await uploadRealFiles(fileItem.file, directoryPath);
        if (response.ok) {
          setUploadedFiles((prev) =>
            prev.map((f) =>
              f.id === fileItem.id ? { ...f, status: "success" } : f
            )
          );
        } else {
          const errorText = await response.text().catch(() => 'Upload failed');
          setUploadedFiles((prev) =>
            prev.map((f) =>
              f.id === fileItem.id ? { ...f, status: "error", error: errorText } : f
            )
          );
        }
      } catch (error: any) {
        setUploadedFiles((prev) =>
          prev.map((f) =>
            f.id === fileItem.id ? { ...f, status: "error", error: error.message || "Failed to connect" } : f
          )
        );
      }
    });

    await Promise.all(uploadPromises);

    // After all uploads finish
    setTimeout(() => {
      // The real API doesn't return JSON parsed data to edit, it just uploads and indexes.
      // So we skip the "preview" and "download-path" steps, going straight to "confirm".
      setCurrentStep("confirm");
    }, 1500);
  };

  const handleEdit = (field: string) => {
    setEditingField(field);
  };

  const handleFieldChange = (key: string, value: string) => {
    setParsedData((prev: any) => ({
      ...prev,
      [key]: value,
    }));
  };

  const handleConfirm = () => {
    // 设置默认文件名并显示路径选择界面
    const defaultFileName = `解析结果_${new Date().toISOString().split("T")[0]}.json`;
    setFileName(defaultFileName);
    setCurrentStep("download-path");
  };

  // 倒计时逻辑
  useEffect(() => {
    if (currentStep === "confirm" && countdown > 0) {
      const timer = setTimeout(() => {
        setCountdown(countdown - 1);
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, [currentStep, countdown]);

  const handleDownload = () => {
    // 设置默认文件名并显示路径选择界面
    const defaultFileName = `解析结果_${new Date().toISOString().split("T")[0]}.json`;
    setFileName(defaultFileName);
    setCurrentStep("download-path");
  };

  const handleConfirmDownload = () => {
    // 创建 JSON 数据
    const dataStr = JSON.stringify(parsedData, null, 2);
    const blob = new Blob([dataStr], { type: "application/json" });
    
    // 使用传统下载方式
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    
    document.body.appendChild(link);
    link.click();
    
    setTimeout(() => {
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    }, 100);
    
    setHasDownloaded(true);
    setCurrentStep("confirm");
  };

  const handleReupload = () => {
    setCurrentStep("upload");
    setUploadedFiles([]);
    setParsedData(null);
  };

  const handleClose = () => {
    setCurrentStep("upload");
    setUploadedFiles([]);
    setParsedData(null);
    onClose();
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop */}
          <motion.div
            className="fixed inset-0 bg-black/40 z-[60]"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={handleClose}
          />

          {/* Modal */}
          <motion.div
            className={`fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 ${
              isMaximized ? "w-[900px] max-h-[85vh]" : "w-[600px] max-h-[80vh]"
            } bg-white rounded-xl shadow-2xl z-[60] flex flex-col overflow-hidden`}
            initial={{ scale: 0.9, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0.9, opacity: 0 }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-border bg-gradient-to-r from-gray-50 to-gray-100">
              <div className="flex items-center gap-3">
                <FileText className="w-6 h-6 text-gray-600" />
                <div>
                  <h3 className="font-semibold text-gray-900">{getTranslation(language, "fileParserTitle")}</h3>
                  <p className="text-xs text-muted-foreground">
                    {currentStep === "upload" && getTranslation(language, "uploadFiles")}
                    {currentStep === "file-list" && getTranslation(language, "filesSelected").replace("{count}", uploadedFiles.length.toString())}
                    {currentStep === "parsing" && getTranslation(language, "parsing")}
                    {currentStep === "preview" && getTranslation(language, "preview")}
                    {currentStep === "download-path" && getTranslation(language, "selectDownloadSettings")}
                    {currentStep === "confirm" && getTranslation(language, "confirm")}
                  </p>
                </div>
              </div>
              <button
                onClick={handleClose}
                className="p-2 hover:bg-destructive/10 rounded-lg transition-colors"
              >
                <X className="w-5 h-5 text-destructive" />
              </button>
            </div>

            {/* Progress Steps */}
            <div className="px-6 py-4 border-b border-border">
              <div className="flex items-center justify-between">
                {[
                  language === "zh-CN" ? "智能解析" : language === "zh-TW" ? "智能解析" : "Smart Parse",
                  getTranslation(language, "previewStep"),
                  getTranslation(language, "downloadStep"),
                  getTranslation(language, "completeStep")
                ].map((label, index) => (
                  <div key={label} className="flex items-center">
                    <div className="flex items-center gap-2">
                      <div
                        className={`w-8 h-8 rounded-full flex items-center justify-center transition-colors ${
                          (index === 0 && currentStep !== "upload") ||
                          (index === 1 && (currentStep === "confirm" || currentStep === "download-path")) ||
                          (index === 2 && currentStep === "confirm") ||
                          (index === 3 && currentStep === "confirm")
                            ? "bg-primary text-primary-foreground"
                            : index === 0 && currentStep === "upload"
                            ? "bg-primary text-primary-foreground"
                            : index === 1 && (currentStep === "preview" || currentStep === "parsing" || currentStep === "file-list")
                            ? "bg-primary text-primary-foreground"
                            : index === 2 && currentStep === "download-path"
                            ? "bg-primary text-primary-foreground"
                            : index === 3 && currentStep === "confirm"
                            ? "bg-primary text-primary-foreground"
                            : "bg-gray-200 text-gray-500"
                        }`}
                      >
                        {(index === 0 && currentStep !== "upload") ||
                        (index === 1 && (currentStep === "confirm" || currentStep === "download-path")) ||
                        (index === 2 && currentStep === "confirm") ||
                        (index === 3 && currentStep === "confirm") ? (
                          <Check className="w-4 h-4" />
                        ) : (
                          index + 1
                        )}
                      </div>
                      <span className="text-sm">{label}</span>
                    </div>
                    {index < 3 && (
                      <div className="w-16 h-0.5 bg-gray-200 mx-2" />
                    )}
                  </div>
                ))}
              </div>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-y-auto px-6 py-6">
              {currentStep === "upload" && (
                <motion.div
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="space-y-4"
                >
                  <div className="relative rounded-lg p-12 text-center bg-gray-900">
                    {/* SVG 渐变虚线边框 */}
                    <svg className="absolute inset-0 w-full h-full pointer-events-none" style={{ borderRadius: '8px' }}>
                      <defs>
                        <linearGradient id="borderGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                          <stop offset="0%" style={{ stopColor: '#FF6B9D', stopOpacity: 1 }} />
                          <stop offset="50%" style={{ stopColor: '#9333EA', stopOpacity: 1 }} />
                          <stop offset="100%" style={{ stopColor: '#4FC3F7', stopOpacity: 1 }} />
                        </linearGradient>
                      </defs>
                      <rect
                        x="1"
                        y="1"
                        width="calc(100% - 2px)"
                        height="calc(100% - 2px)"
                        fill="none"
                        stroke="url(#borderGradient)"
                        strokeWidth="2"
                        strokeDasharray="8 6"
                        rx="8"
                      />
                    </svg>
                    
                    {/* 内容区域 */}
                    <div className="relative z-10">
                      <Upload className="w-12 h-12 text-white mx-auto mb-4" />
                      <p className="text-sm text-white mb-2">
                        拖拽文件至此或点击上传
                      </p>
                      <p className="text-xs text-gray-400 mb-4">
                        支持 PDF, JPG, PNG 格式，最大 10MB
                      </p>
                      <label className="inline-block">
                        <input
                          type="file"
                          className="hidden"
                          accept=".pdf,.jpg,.jpeg,.png"
                          onChange={handleFileUpload}
                          multiple
                        />
                        <span className="px-6 py-2 bg-gray-800 text-white rounded-lg hover:bg-gray-700 transition-colors cursor-pointer inline-block">
                          选择文件
                        </span>
                      </label>
                    </div>
                  </div>
                </motion.div>
              )}

              {currentStep === "file-list" && (
                <motion.div
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="space-y-4"
                >
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                      <FileText className="w-5 h-5 text-primary" />
                      <h4 className="font-semibold text-primary">已选择 {uploadedFiles.length} 个文件</h4>
                    </div>
                    <button
                      onClick={handleConfirmFiles}
                      disabled={uploadedFiles.length === 0}
                      className="flex items-center gap-2 px-4 py-2 text-sm rounded-lg transition-colors bg-primary text-primary-foreground hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      <FileSearch className="w-4 h-4" />
                      确认解析
                    </button>
                  </div>

                  {uploadedFiles.length > 0 ? (
                    <div className="space-y-2">
                      {uploadedFiles.map((fileItem) => (
                        <div
                          key={fileItem.id}
                          className="flex items-center justify-between px-4 py-2.5 bg-gray-50 rounded-lg border border-border hover:bg-gray-100 transition-colors"
                        >
                          <div className="flex items-center gap-3 flex-1 min-w-0">
                            <FileText className="w-5 h-5 text-primary flex-shrink-0" />
                            <div className="flex-1 min-w-0">
                              <p className="text-sm text-foreground truncate">{fileItem.file.name}</p>
                              <p className="text-xs text-muted-foreground">
                                {(fileItem.file.size / 1024).toFixed(1)} KB
                              </p>
                            </div>
                          </div>
                          <button
                            onClick={() => handleDeleteFile(fileItem.id)}
                            className="p-1.5 hover:bg-red-500/10 rounded-lg transition-colors flex-shrink-0 ml-2"
                            title="删除"
                          >
                            <Trash2 className="w-4 h-4 text-red-500" />
                          </button>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="border-2 border-dashed border-primary/30 rounded-lg p-12 text-center hover:border-primary/50 transition-colors">
                      <Upload className="w-12 h-12 text-primary mx-auto mb-4" />
                      <p className="text-sm text-foreground mb-2">
                        文件已全部删除，请重新选择
                      </p>
                      <p className="text-xs text-muted-foreground mb-4">
                        支持 PDF, JPG, PNG 格式，最大 10MB
                      </p>
                      <label className="inline-block">
                        <input
                          type="file"
                          className="hidden"
                          accept=".pdf,.jpg,.jpeg,.png"
                          onChange={handleFileUpload}
                          multiple
                        />
                        <span className="px-6 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors cursor-pointer inline-block">
                          选择文件
                        </span>
                      </label>
                    </div>
                  )}
                </motion.div>
              )}

              {currentStep === "parsing" && (
                <motion.div
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="space-y-4"
                >
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                      {uploadedFiles.every(f => f.status === "success" || f.status === "error") ? (
                        <>
                          <Check className="w-5 h-5 text-accent" />
                          <h4 className="font-semibold text-primary">
                            解析完成：
                            <span className="text-accent ml-2">
                              {uploadedFiles.filter(f => f.status === "success").length} 个成功
                            </span>
                            {uploadedFiles.filter(f => f.status === "error").length > 0 && (
                              <span className="text-red-500 ml-2">
                                {uploadedFiles.filter(f => f.status === "error").length} 个失败
                              </span>
                            )}
                          </h4>
                        </>
                      ) : (
                        <>
                          <Loader2 className="w-5 h-5 text-accent animate-spin" />
                          <h4 className="font-semibold text-primary">解析中</h4>
                        </>
                      )}
                    </div>
                  </div>

                  <div className="space-y-3">
                    {uploadedFiles.map((fileItem) => (
                      <div
                        key={fileItem.id}
                        className="bg-gray-50 rounded-lg border border-border overflow-hidden"
                      >
                        <div className="grid grid-cols-[140px_1fr]">
                          <div className="bg-gray-100 px-4 py-3 border-r border-border">
                            <label className="text-xs text-muted-foreground font-medium">
                              文件名
                            </label>
                          </div>
                          <div className="px-4 py-3">
                            <p className="text-sm text-foreground">{fileItem.file.name}</p>
                          </div>
                        </div>
                        <div className="flex items-center justify-between px-4 py-3 bg-white border-t border-border">
                          <div className="flex items-center gap-2">
                            {fileItem.status === "parsing" && (
                              <Loader2 className="w-4 h-4 text-accent animate-spin" />
                            )}
                            {fileItem.status === "success" && (
                              <Check className="w-4 h-4 text-accent" />
                            )}
                            {fileItem.status === "error" && (
                              <AlertCircle className="w-4 h-4 text-red-500" />
                            )}
                            <span className={`text-sm ${
                              fileItem.status === "success" ? "text-accent" :
                              fileItem.status === "error" ? "text-red-500" :
                              "text-muted-foreground"
                            }`}>
                              {fileItem.status === "parsing" && "析中"}
                              {fileItem.status === "success" && "解析成功"}
                              {fileItem.status === "error" && fileItem.error}
                            </span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>

                  {uploadedFiles.every(f => f.status === "success" || f.status === "error") && (
                    <motion.div
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      className="flex gap-3 mt-6"
                    >
                      <button
                        onClick={handleReupload}
                        className="flex-1 px-4 py-2 border border-border text-foreground rounded-lg hover:bg-gray-50 transition-colors"
                      >
                        重新上傳
                      </button>
                      {uploadedFiles.some(f => f.status === "success") && (
                        <button
                          onClick={() => {
                            const firstSuccess = uploadedFiles.find(f => f.status === "success");
                            if (firstSuccess) {
                              setParsedData(firstSuccess.parsedData);
                              setCurrentStep("preview");
                            }
                          }}
                          className="flex-1 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
                        >
                          查看結果
                        </button>
                      )}
                    </motion.div>
                  )}
                </motion.div>
              )}

              {currentStep === "preview" && parsedData && (() => {
                const successFiles = uploadedFiles.filter(f => f.status === "success");
                const currentSuccessIndex = successFiles.findIndex(f => f.parsedData === parsedData);
                const currentPosition = currentSuccessIndex + 1;
                const totalSuccess = successFiles.length;

                return (
                  <motion.div
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="space-y-4"
                  >
                    <div className="flex items-center justify-between mb-4">
                      <div className="flex items-center gap-3">
                        <Eye className="w-5 h-5 text-primary" />
                        <h4 className="font-semibold text-primary">
                          解析结果
                          {totalSuccess > 1 && (
                            <span className="ml-2 text-muted-foreground font-normal">
                              ({currentPosition}/{totalSuccess})
                            </span>
                          )}
                        </h4>
                      </div>
                      {totalSuccess > 1 && (
                        <div className="flex items-center gap-1">
                          <button
                            onClick={() => {
                              const prevIndex = currentSuccessIndex > 0 ? currentSuccessIndex - 1 : successFiles.length - 1;
                              setParsedData(successFiles[prevIndex].parsedData);
                              setEditingField(null);
                            }}
                            className="p-1.5 hover:bg-primary/10 rounded-lg transition-colors"
                            title="上一个"
                          >
                            <ChevronLeft className="w-4 h-4 text-primary" />
                          </button>
                          <button
                            onClick={() => {
                              const nextIndex = currentSuccessIndex < successFiles.length - 1 ? currentSuccessIndex + 1 : 0;
                              setParsedData(successFiles[nextIndex].parsedData);
                              setEditingField(null);
                            }}
                            className="p-1.5 hover:bg-primary/10 rounded-lg transition-colors"
                            title="下一个"
                          >
                            <ChevronRight className="w-4 h-4 text-primary" />
                          </button>
                        </div>
                      )}
                    </div>

                    <div className="space-y-3">
                      {Object.entries(parsedData).map(([key, value]) => {
                        const fieldLabels: Record<string, string> = {
                          clientName: "客戶姓名",
                          idNumber: "證件號碼",
                          address: "地址",
                          phone: "電話",
                          email: "電郵",
                          accountType: "賬戶類型",
                          riskLevel: "風險等級",
                        };

                        return (
                          <div
                            key={key}
                            className="bg-gray-50 rounded-lg border border-border overflow-hidden"
                          >
                            <div className="grid grid-cols-[140px_1fr_auto]">
                              <div className="bg-gray-100 px-4 py-3 border-r border-border">
                                <label className="text-xs text-muted-foreground font-medium">
                                  {fieldLabels[key] || key}
                                </label>
                              </div>
                              <div className="px-4 py-3 min-w-0">
                                {editingField === key ? (
                                  <input
                                    type="text"
                                    value={value as string}
                                    onChange={(e) => handleFieldChange(key, e.target.value)}
                                    onBlur={() => setEditingField(null)}
                                    autoFocus
                                    className="text-sm text-foreground w-full bg-white border border-primary/30 rounded px-2 py-1 focus:outline-none focus:ring-2 focus:ring-primary/50"
                                  />
                                ) : (
                                  <p className="text-sm text-foreground truncate">{value as string}</p>
                                )}
                              </div>
                              <div className="px-3 py-3 border-l border-border">
                                <button
                                  onClick={() => setEditingField(editingField === key ? null : key)}
                                  className={`p-1.5 rounded-lg transition-colors ${
                                    editingField === key
                                      ? "bg-accent/10 text-accent"
                                      : "hover:bg-primary/10 text-primary"
                                  }`}
                                  title={editingField === key ? "完成" : "編輯"}
                                >
                                  {editingField === key ? (
                                    <Check className="w-4 h-4" />
                                  ) : (
                                    <Edit3 className="w-4 h-4" />
                                  )}
                                </button>
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>

                    <div className="flex gap-3 mt-6">
                      <button
                        onClick={handleReupload}
                        className="flex-1 px-4 py-2 border border-border text-foreground rounded-lg hover:bg-gray-50 transition-colors"
                      >
                        重新上傳
                      </button>
                      <button
                        onClick={handleConfirm}
                        className="flex-1 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
                      >
                        確認無誤
                      </button>
                    </div>
                  </motion.div>
                );
              })()}

              {currentStep === "confirm" && (
                <motion.div
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  className="text-center py-12"
                >
                  <div className="w-16 h-16 bg-accent/20 rounded-full flex items-center justify-center mx-auto mb-4">
                    <Check className="w-8 h-8 text-accent" />
                  </div>
                  <div className="flex items-center justify-center gap-3 mb-2">
                    <h4 className="font-semibold text-primary">解析完成</h4>
                    <span className="inline-flex items-center justify-center min-w-[40px] px-2 py-1 bg-primary/10 text-primary text-sm font-medium rounded-md">
                      {countdown}s
                    </span>
                  </div>
                  <p className="text-sm text-muted-foreground mb-8">
                    文件已成功解析並保存
                  </p>
                  
                  <div className="flex items-center justify-center gap-3">
                    {hasDownloaded && (
                      <motion.button
                        initial={{ opacity: 0, x: -10 }}
                        animate={{ opacity: 1, x: 0 }}
                        onClick={handleClose}
                        className="inline-flex items-center gap-2 px-5 py-2.5 bg-accent/10 text-accent border border-accent/30 rounded-lg font-medium hover:bg-accent/20 transition-colors cursor-pointer"
                      >
                        <Check className="w-4 h-4" />
                        保存成功
                      </motion.button>
                    )}
                    
                    <button
                      className="inline-flex items-center gap-2 px-5 py-2.5 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors font-medium disabled:opacity-50 disabled:cursor-not-allowed"
                      onClick={handleDownload}
                      disabled={hasDownloaded}
                    >
                      <Download className="w-4 h-4" />
                      {hasDownloaded ? "已下載" : "下載結果"}
                    </button>
                  </div>
                </motion.div>
              )}

              {currentStep === "download-path" && (
                <motion.div
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="space-y-6 py-4"
                >
                  <div className="text-center mb-6">
                    <div className="w-16 h-16 bg-primary/10 rounded-full flex items-center justify-center mx-auto mb-4">
                      <Download className="w-8 h-8 text-primary" />
                    </div>
                    <h4 className="font-semibold text-primary mb-2">選擇下載設置</h4>
                    <p className="text-sm text-muted-foreground">
                      請確認文件名稱和保存位置
                    </p>
                  </div>

                  <div className="space-y-4">
                    {/* 存位置選擇 */}
                    <div className="space-y-2">
                      <label className="text-sm font-medium text-foreground">保存位置</label>
                      <div className="relative">
                        <select
                          value={selectedPath}
                          onChange={(e) => setSelectedPath(e.target.value)}
                          className="w-full px-4 py-2.5 pr-10 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/50 bg-white appearance-none text-sm"
                        >
                          <option value="下載">下載</option>
                          <option value="桌面">桌面</option>
                          <option value="文件">文件</option>
                          <option value="我的文檔">我的文檔</option>
                        </select>
                        <Folder className="w-5 h-5 text-gray-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                      </div>
                    </div>

                    {/* 文件名稱 */}
                    <div className="space-y-2">
                      <label className="text-sm font-medium text-foreground">文件名稱</label>
                      <div className="relative">
                        <input
                          type="text"
                          value={fileName}
                          onChange={(e) => setFileName(e.target.value)}
                          className="w-full px-4 py-2.5 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/50 bg-white text-sm"
                          placeholder="請輸入文件名稱"
                        />
                        <FileText className="w-5 h-5 text-gray-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                      </div>
                    </div>

                    {/* 完整路徑預覽 */}
                    <div className="bg-gray-50 border border-border rounded-lg p-3">
                      <p className="text-xs text-muted-foreground mb-1">完整路徑</p>
                      <p className="text-sm text-foreground font-mono">
                        {selectedPath}/{fileName}
                      </p>
                    </div>
                  </div>

                  <div className="flex gap-3 pt-4">
                    <button
                      onClick={() => setCurrentStep("confirm")}
                      className="flex-1 px-4 py-2.5 border border-border text-foreground rounded-lg hover:bg-gray-50 transition-colors"
                    >
                      取消
                    </button>
                    <button
                      onClick={handleConfirmDownload}
                      disabled={!fileName.trim()}
                      className="flex-1 px-4 py-2.5 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      確認下載
                    </button>
                  </div>
                </motion.div>
              )}
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}