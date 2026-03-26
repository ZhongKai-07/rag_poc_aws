import { motion, AnimatePresence } from "motion/react";
import { X, Download, FileText, Check, Pencil, AlertTriangle } from "lucide-react";
import { useState } from "react";
import { Language } from "../i18n/translations";
import { Dialog, DialogContent, DialogTitle } from "./ui/dialog";
import { Input } from "./ui/input";
import { Button } from "./ui/button";

interface DownloadModalProps {
  isOpen: boolean;
  onClose: () => void;
  language: Language;
  agreementType?: string;
  counterparty?: string;
}

export function DownloadModal({ isOpen, onClose, language, agreementType, counterparty }: DownloadModalProps) {
  const [downloadStep, setDownloadStep] = useState(1);
  const [isExtracting, setIsExtracting] = useState(false);
  const [isExtractionComplete, setIsExtractionComplete] = useState(false);
  const [isExtractionFailed, setIsExtractionFailed] = useState(false);
  const [isDownloadFailed, setIsDownloadFailed] = useState(false);
  const [editingRow, setEditingRow] = useState<string | null>(null);
  const [tempEditValue, setTempEditValue] = useState("");
  const [selectedPath, setSelectedPath] = useState("Browser default download folder");
  const [fileName, setFileName] = useState(`${agreementType || 'ISDA'}_Agreement_${new Date().getTime()}.pdf`);
  const [tableData, setTableData] = useState<Record<string, string>>({
    "Agreement Type": agreementType || "ISDA",
    "Counterparty": counterparty || "Goldman Sachs",
    "Effective Date": "2024-01-15",
    "Termination Date": "2029-01-15",
    "Governing Law": "New York",
    "Currency": "USD",
  });

  const handleEdit = (key: string) => {
    setEditingRow(key);
    setTempEditValue(tableData[key]);
  };

  const handleSaveEdit = (key: string) => {
    setTableData(prev => ({ ...prev, [key]: tempEditValue }));
    setEditingRow(null);
    setTempEditValue("");
  };

  const handleCancelEdit = () => {
    setEditingRow(null);
    setTempEditValue("");
  };

  const handleNextStep = () => {
    if (downloadStep === 1 && !isExtractionComplete && !isExtractionFailed) {
      // Start extraction animation
      setIsExtracting(true);
      
      // Check if counterparty is "download" to trigger failure
      const shouldFail = counterparty?.toLowerCase().trim() === "download";
      const extractionTime = shouldFail ? 5000 : 2000;
      
      setTimeout(() => {
        setIsExtracting(false);
        if (shouldFail) {
          setIsExtractionFailed(true);
        } else {
          setIsExtractionComplete(true);
        }
      }, extractionTime);
    } else if (downloadStep === 1 && isExtractionComplete) {
      // Move to next step
      setDownloadStep(2);
    } else if (downloadStep === 3) {
      // Move to step 4 and simulate download
      setDownloadStep(4);
      // Simulate download - check if fileName contains "complete" to trigger failure
      const shouldFail = fileName.toLowerCase().includes("complete");
      if (shouldFail) {
        setIsDownloadFailed(true);
      } else {
        setIsDownloadFailed(false);
      }
    } else if (downloadStep < 4) {
      setDownloadStep(downloadStep + 1);
    }
  };

  const handleRetry = () => {
    setIsExtractionFailed(false);
    setIsExtracting(false);
    setIsExtractionComplete(false);
  };

  const handleCancelFailed = () => {
    setIsExtractionFailed(false);
    setIsExtracting(false);
    setIsExtractionComplete(false);
    onClose();
  };

  const handleRetryDownload = () => {
    setIsDownloadFailed(false);
    setDownloadStep(3);
  };

  const handleCancelDownloadFailed = () => {
    setIsDownloadFailed(false);
    setDownloadStep(1);
    setIsExtractionComplete(false);
    onClose();
  };

  const handlePreviousStep = () => {
    if (downloadStep > 1 && downloadStep < 4) {
      setDownloadStep(downloadStep - 1);
    }
  };

  const handleClose = () => {
    setDownloadStep(1);
    setIsExtracting(false);
    setIsExtractionComplete(false);
    onClose();
  };

  const handleBrowse = async () => {
    try {
      // Check if File System Access API is supported
      if ('showDirectoryPicker' in window) {
        const directoryHandle = await (window as any).showDirectoryPicker({
          mode: 'readwrite'
        });
        setSelectedPath(directoryHandle.name);
      } else {
        // Fallback: show message that browser default will be used
        alert('Your browser does not support directory selection. The file will be downloaded to your browser\'s default download folder.');
      }
    } catch (err) {
      // User cancelled the picker or an error occurred
      console.log('Directory selection cancelled or failed:', err);
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={handleClose}>
      <DialogContent 
        className="sm:max-w-[820px] p-0 gap-0 bg-white border-none" 
        aria-describedby={undefined}
      >
        <DialogTitle className="sr-only">Download Process</DialogTitle>
        
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-5 border-b border-gray-200">
          <h2 className="text-xl font-bold text-gray-900">Smart File Parsing</h2>
        </div>
        
        <div className="flex flex-col px-8 py-8">
          {/* Step Indicator */}
          <div className="flex items-center justify-between mb-10 px-4">
            <div className="flex flex-col items-center gap-2 flex-1">
              <div 
                className={`flex items-center justify-center w-10 h-10 rounded-full text-sm font-medium transition-colors ${downloadStep >= 1 ? 'text-transparent' : 'bg-gray-200 text-gray-500'}`}
                style={downloadStep >= 1 ? {
                  backgroundImage: 'linear-gradient(white, white), linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                  backgroundOrigin: 'border-box',
                  backgroundClip: 'padding-box, border-box',
                  border: '2px solid transparent',
                } : {}}
              >
                <span 
                  style={downloadStep >= 1 ? {
                    backgroundImage: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                    backgroundClip: 'text',
                  } : {}}
                >
                  1
                </span>
              </div>
              <span className="text-xs text-gray-600">Smart Parsing</span>
            </div>
            <div className={`flex-1 h-0.5 -mx-2 mt-[-20px] ${downloadStep >= 2 ? 'bg-[linear-gradient(90deg,_#E935C6_0%,_#FF6B9D_25%,_#FFA574_50%,_#4FC3F7_75%,_#00B4D8_100%)]' : 'bg-gray-200'}`} />
            <div className="flex flex-col items-center gap-2 flex-1">
              <div 
                className={`flex items-center justify-center w-10 h-10 rounded-full text-sm font-medium transition-colors ${downloadStep >= 2 ? 'text-transparent' : 'bg-gray-200 text-gray-500'}`}
                style={downloadStep >= 2 ? {
                  backgroundImage: 'linear-gradient(white, white), linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                  backgroundOrigin: 'border-box',
                  backgroundClip: 'padding-box, border-box',
                  border: '2px solid transparent',
                } : {}}
              >
                <span 
                  style={downloadStep >= 2 ? {
                    backgroundImage: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                    backgroundClip: 'text',
                  } : {}}
                >
                  2
                </span>
              </div>
              <span className="text-xs text-gray-600">Preview</span>
            </div>
            <div className={`flex-1 h-0.5 -mx-2 mt-[-20px] ${downloadStep >= 3 ? 'bg-[linear-gradient(90deg,_#E935C6_0%,_#FF6B9D_25%,_#FFA574_50%,_#4FC3F7_75%,_#00B4D8_100%)]' : 'bg-gray-200'}`} />
            <div className="flex flex-col items-center gap-2 flex-1">
              <div 
                className={`flex items-center justify-center w-10 h-10 rounded-full text-sm font-medium transition-colors ${downloadStep >= 3 ? 'text-transparent' : 'bg-gray-200 text-gray-500'}`}
                style={downloadStep >= 3 ? {
                  backgroundImage: 'linear-gradient(white, white), linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                  backgroundOrigin: 'border-box',
                  backgroundClip: 'padding-box, border-box',
                  border: '2px solid transparent',
                } : {}}
              >
                <span 
                  style={downloadStep >= 3 ? {
                    backgroundImage: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                    backgroundClip: 'text',
                  } : {}}
                >
                  3
                </span>
              </div>
              <span className="text-xs text-gray-600">Download</span>
            </div>
            <div className={`flex-1 h-0.5 -mx-2 mt-[-20px] ${downloadStep >= 4 ? 'bg-[linear-gradient(90deg,_#E935C6_0%,_#FF6B9D_25%,_#FFA574_50%,_#4FC3F7_75%,_#00B4D8_100%)]' : 'bg-gray-200'}`} />
            <div className="flex flex-col items-center gap-2 flex-1">
              <div 
                className={`flex items-center justify-center w-10 h-10 rounded-full text-sm font-medium transition-colors ${downloadStep >= 4 ? 'text-transparent' : 'bg-gray-200 text-gray-500'}`}
                style={downloadStep >= 4 ? {
                  backgroundImage: 'linear-gradient(white, white), linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                  backgroundOrigin: 'border-box',
                  backgroundClip: 'padding-box, border-box',
                  border: '2px solid transparent',
                } : {}}
              >
                <span 
                  style={downloadStep >= 4 ? {
                    backgroundImage: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                    backgroundClip: 'text',
                  } : {}}
                >
                  4
                </span>
              </div>
              <span className="text-xs text-gray-600">Complete</span>
            </div>
          </div>
          
          <div className="w-full space-y-6">
            {/* Step 1: AI智能提取 */}
            {downloadStep === 1 && (
              <div className="flex flex-col items-center py-8">
                <div className="mb-4 w-32 h-32 relative flex items-center justify-center">
                  {isExtracting ? (
                    // Extracting animation - gradient loading circle
                    <div className="w-32 h-32 relative flex items-center justify-center">
                      {/* Spinning gradient circle */}
                      <motion.svg
                        className="w-32 h-32 absolute inset-0"
                        viewBox="0 0 100 100"
                        animate={{ rotate: 360 }}
                        transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
                      >
                        <defs>
                          <linearGradient id="spinnerGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                            <stop offset="0%" stopColor="#E935C6" />
                            <stop offset="25%" stopColor="#FF6B9D" />
                            <stop offset="50%" stopColor="#FFA574" />
                            <stop offset="75%" stopColor="#4FC3F7" />
                            <stop offset="100%" stopColor="#00B4D8" />
                          </linearGradient>
                        </defs>
                        <circle
                          cx="50"
                          cy="50"
                          r="45"
                          fill="none"
                          stroke="url(#spinnerGradient)"
                          strokeWidth="4"
                          strokeLinecap="round"
                          strokeDasharray="220 283"
                        />
                      </motion.svg>
                      
                      {/* File icon in center */}
                      <svg
                        className="w-16 h-16 relative z-10"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="#9CA3AF"
                        strokeWidth="1.5"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      >
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                        <path d="M14 2v6h6" />
                        <path d="M16 13H8" />
                        <path d="M16 17H8" />
                        <path d="M10 9H8" />
                      </svg>
                    </div>
                  ) : isExtractionFailed ? (
                    // Failed icon - Alert Triangle
                    <AlertTriangle className="w-32 h-32 text-red-500" />
                  ) : isExtractionComplete ? (
                    // Success - Gradient file icon
                    <svg
                      className="w-32 h-32"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="url(#gradientStroke)"
                      strokeWidth="1.5"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <defs>
                        <linearGradient id="gradientStroke" x1="0%" y1="0%" x2="100%" y2="100%">
                          <stop offset="0%" stopColor="#E935C6" />
                          <stop offset="25%" stopColor="#FF6B9D" />
                          <stop offset="50%" stopColor="#FFA574" />
                          <stop offset="75%" stopColor="#4FC3F7" />
                          <stop offset="100%" stopColor="#00B4D8" />
                        </linearGradient>
                      </defs>
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                      <path d="M14 2v6h6" />
                      <path d="M16 13H8" />
                      <path d="M16 17H8" />
                      <path d="M10 9H8" />
                    </svg>
                  ) : (
                    // Initial state - Gray file icon
                    <svg
                      className="w-32 h-32"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="#9CA3AF"
                      strokeWidth="1.5"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                      <path d="M14 2v6h6" />
                      <path d="M16 13H8" />
                      <path d="M16 17H8" />
                      <path d="M10 9H8" />
                    </svg>
                  )}
                </div>
                {isExtractionComplete && (
                  <motion.p 
                    className="text-sm text-gray-600 text-center"
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.3 }}
                  >
                    AI has completed agreement information extraction
                  </motion.p>
                )}
                {isExtractionFailed && (
                  <motion.p 
                    className="text-sm text-gray-700 text-center"
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.3 }}
                  >
                    Smart Parsing Failed
                  </motion.p>
                )}
              </div>
            )}

            {/* Step 2: 预览 */}
            {downloadStep === 2 && (
              <div className="flex flex-col">
                <div className="w-full bg-white rounded-lg border border-gray-200 max-h-80 overflow-y-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-gray-50 sticky top-0">
                      <tr>
                        <th className="text-left px-4 py-3 font-semibold text-gray-700 border-b border-gray-200">Keyword</th>
                        <th className="text-left px-4 py-3 font-semibold text-gray-700 border-b border-gray-200">Value</th>
                        <th className="w-12 px-4 py-3 border-b border-gray-200"></th>
                      </tr>
                    </thead>
                    <tbody>
                      {Object.entries(tableData).map(([key, value]) => (
                        <tr key={key} className="border-b border-gray-100 hover:bg-gray-50">
                          <td className="px-4 py-3 text-gray-600 bg-gray-50/50 whitespace-nowrap">{key}</td>
                          <td className="px-4 py-3 text-gray-800">
                            {editingRow === key ? (
                              <div className="flex items-center gap-2">
                                <Input
                                  type="text"
                                  value={tempEditValue}
                                  onChange={(e) => setTempEditValue(e.target.value)}
                                  className="flex-1 h-8 bg-gray-50 rounded-lg border border-gray-300 px-3 text-sm"
                                />
                                <Button
                                  onClick={() => handleSaveEdit(key)}
                                  className="h-8 px-3 bg-green-500 hover:bg-green-600 text-white rounded-lg text-xs whitespace-nowrap"
                                >
                                  Save
                                </Button>
                                <Button
                                  onClick={handleCancelEdit}
                                  className="h-8 px-3 bg-gray-200 hover:bg-gray-300 text-gray-700 rounded-lg text-xs whitespace-nowrap"
                                >
                                  Cancel
                                </Button>
                              </div>
                            ) : (
                              <span className="text-gray-800 whitespace-nowrap">{value}</span>
                            )}
                          </td>
                          <td className="px-4 py-3 text-center">
                            {editingRow !== key && (
                              <button
                                onClick={() => handleEdit(key)}
                                className="text-gray-400 hover:text-gray-600 transition-colors"
                              >
                                <Pencil className="w-4 h-4" />
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* Step 3: 选择下载路径 */}
            {downloadStep === 3 && (
              <div className="flex flex-col">
                <h3 className="text-lg font-semibold text-gray-800 mb-6 text-center">Choose Download Path</h3>
                
                <div className="w-full space-y-4">
                  <div>
                    <label className="text-sm font-medium text-gray-700 mb-2 block">File Name</label>
                    <Input
                      type="text"
                      value={fileName}
                      onChange={(e) => setFileName(e.target.value)}
                      className="w-full h-10 bg-gray-50 rounded-lg border border-gray-300 px-4 text-sm"
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium text-gray-700 mb-2 block">Save Path</label>
                    <div className="flex gap-2">
                      <Input
                        type="text"
                        value={selectedPath}
                        onChange={(e) => setSelectedPath(e.target.value)}
                        className="flex-1 h-10 bg-gray-50 rounded-lg border border-gray-300 px-4 text-sm"
                      />
                      <Button 
                        onClick={handleBrowse}
                        className="h-10 px-4 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg text-sm"
                      >
                        Browse
                      </Button>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Step 4: 完成 */}
            {downloadStep === 4 && (
              <div className="flex flex-col items-center py-8">
                {isDownloadFailed ? (
                  <>
                    {/* Download Failed */}
                    <div className="mb-4">
                      <AlertTriangle className="w-32 h-32 text-red-500" />
                    </div>
                    <motion.p 
                      className="text-sm text-gray-700 text-center"
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ duration: 0.3 }}
                    >
                      Download Failed
                    </motion.p>
                  </>
                ) : (
                  <>
                    {/* Download Success */}
                    <div className="mb-4 w-20 h-20 relative">
                      <svg width="80" height="80" viewBox="0 0 80 80" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <defs>
                          <linearGradient id="checkGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                            <stop offset="0%" stopColor="#E935C6" />
                            <stop offset="25%" stopColor="#FF6B9D" />
                            <stop offset="50%" stopColor="#FFA574" />
                            <stop offset="75%" stopColor="#4FC3F7" />
                            <stop offset="100%" stopColor="#00B4D8" />
                          </linearGradient>
                        </defs>
                        {/* Circle */}
                        <circle 
                          cx="40" 
                          cy="40" 
                          r="30" 
                          stroke="url(#checkGradient)" 
                          strokeWidth="2.5" 
                          fill="none"
                        />
                        {/* Check mark */}
                        <path 
                          d="M25 40 L35 50 L55 30" 
                          stroke="url(#checkGradient)" 
                          strokeWidth="3.5" 
                          strokeLinecap="round" 
                          strokeLinejoin="round"
                          fill="none"
                        />
                      </svg>
                    </div>
                    <h3 className="text-xl font-bold text-gray-800 mb-2">Download Complete</h3>
                  </>
                )}
              </div>
            )}

            {/* Navigation Buttons */}
            <div className="flex flex-col gap-3 pt-4">
              {!isExtractionFailed && downloadStep < 4 && (
                <Button 
                  onClick={handleNextStep}
                  disabled={downloadStep === 1 && isExtracting}
                  className="w-full h-10 bg-transparent hover:bg-gradient-to-r hover:from-pink-500/10 hover:to-blue-500/10 text-gray-800 rounded-full font-medium border border-transparent bg-origin-border text-sm disabled:opacity-50"
                  style={{
                    backgroundImage: 'linear-gradient(white, white), linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                    backgroundOrigin: 'border-box',
                    backgroundClip: 'padding-box, border-box',
                  }}
                >
                  {downloadStep === 1 && isExtracting ? 'Extracting...' : downloadStep === 2 ? 'Confirm Review' : downloadStep === 3 ? 'Confirm Download' : 'Next'}
                </Button>
              )}
              
              {downloadStep > 1 && downloadStep < 4 && !isExtractionFailed && (
                <div className="flex justify-center">
                  <button 
                    onClick={handlePreviousStep}
                    className="inline-block text-center text-sm font-medium relative pb-1 hover:opacity-80 transition-opacity"
                    style={{
                      backgroundImage: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                      WebkitBackgroundClip: 'text',
                      WebkitTextFillColor: 'transparent',
                      backgroundClip: 'text',
                    }}
                  >
                    Previous
                    <div 
                      className="absolute bottom-0 left-0 right-0 h-0.5 rounded-full"
                      style={{
                        backgroundImage: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                      }}
                    />
                  </button>
                </div>
              )}
              
              {isExtractionFailed && (
                <div className="flex justify-center gap-3">
                  <Button
                    onClick={handleRetry}
                    className="w-[45%] h-10 bg-transparent hover:bg-gradient-to-r hover:from-pink-500/10 hover:to-blue-500/10 text-gray-800 rounded-full font-medium border border-transparent bg-origin-border text-sm"
                    style={{
                      backgroundImage: 'linear-gradient(white, white), linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                      backgroundOrigin: 'border-box',
                      backgroundClip: 'padding-box, border-box',
                    }}
                  >
                    Retry
                  </Button>
                  <Button
                    onClick={handleCancelFailed}
                    className="w-[45%] h-10 bg-transparent hover:bg-gradient-to-r hover:from-pink-500/10 hover:to-blue-500/10 text-gray-800 rounded-full font-medium border border-transparent bg-origin-border text-sm"
                    style={{
                      backgroundImage: 'linear-gradient(white, white), linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                      backgroundOrigin: 'border-box',
                      backgroundClip: 'padding-box, border-box',
                    }}
                  >
                    Cancel
                  </Button>
                </div>
              )}
              
              {isDownloadFailed && (
                <div className="flex justify-center gap-3">
                  <Button
                    onClick={handleRetryDownload}
                    className="w-[45%] h-10 bg-transparent hover:bg-gradient-to-r hover:from-pink-500/10 hover:to-blue-500/10 text-gray-800 rounded-full font-medium border border-transparent bg-origin-border text-sm"
                    style={{
                      backgroundImage: 'linear-gradient(white, white), linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                      backgroundOrigin: 'border-box',
                      backgroundClip: 'padding-box, border-box',
                    }}
                  >
                    Retry
                  </Button>
                  <Button
                    onClick={handleCancelDownloadFailed}
                    className="w-[45%] h-10 bg-transparent hover:bg-gradient-to-r hover:from-pink-500/10 hover:to-blue-500/10 text-gray-800 rounded-full font-medium border border-transparent bg-origin-border text-sm"
                    style={{
                      backgroundImage: 'linear-gradient(white, white), linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                      backgroundOrigin: 'border-box',
                      backgroundClip: 'padding-box, border-box',
                    }}
                  >
                    Cancel
                  </Button>
                </div>
              )}
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}