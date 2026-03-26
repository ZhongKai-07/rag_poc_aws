import { motion, AnimatePresence } from "motion/react";
import { X, ChevronDown, AlertTriangle } from "lucide-react";
import { useState } from "react";
import { Language } from "../i18n/translations";

interface AgreementAssistantModalProps {
  isOpen: boolean;
  onClose: () => void;
  language: Language;
  onAsk?: (agreementType: string, counterparty: string, selectedTab: TabType) => void;
  onDownloadSuccess?: (agreementType: string, counterparty: string) => void;
}

type AgreementType = "ISDA" | "GMRA";
type TabType = "agreement" | "counterparty";

export function AgreementAssistantModal({ isOpen, onClose, language, onAsk, onDownloadSuccess }: AgreementAssistantModalProps) {
  const [selectedTab, setSelectedTab] = useState<TabType>("counterparty");
  const [selectedAgreement, setSelectedAgreement] = useState<AgreementType | "">("");
  const [counterparty, setCounterparty] = useState("");
  const [showDropdown, setShowDropdown] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [showError, setShowError] = useState(false);

  const agreementTypes: AgreementType[] = ["ISDA", "GMRA"];

  // Check if both fields are filled
  const canSubmit = selectedAgreement !== "" && counterparty.trim() !== "" && !isLoading;

  const handleAsk = () => {
    if (!canSubmit) return;
    setIsLoading(true);
    setShowError(false);
    
    // Simulate locking file process
    setTimeout(() => {
      setIsLoading(false);
      
      // Only show error if counterparty is "fail"
      if (counterparty.toLowerCase().trim() === "fail") {
        setShowError(true);
      } else {
        // Success - proceed with the query
        if (onAsk) {
          onAsk(selectedAgreement, counterparty, selectedTab);
        }
      }
    }, 2000); // 2 seconds loading time
  };

  const handleDownload = () => {
    if (!canSubmit) return;
    setIsLoading(true);
    setShowError(false);
    
    // Simulate locking file process
    setTimeout(() => {
      setIsLoading(false);
      
      // Only show error if counterparty is "fail"
      if (counterparty.toLowerCase().trim() === "fail") {
        setShowError(true);
      } else {
        // Success - close this modal first, then show download modal
        onClose();
        
        // Wait for the close animation to complete before showing download modal
        setTimeout(() => {
          if (onDownloadSuccess) {
            onDownloadSuccess(selectedAgreement, counterparty);
          }
        }, 300); // Wait for modal close animation
      }
    }, 2000); // 2 seconds loading time
  };

  const handleRetry = () => {
    setShowError(false);
    setIsLoading(false);
    // Clear all form data
    setSelectedAgreement("");
    setCounterparty("");
    setSelectedTab("counterparty");
  };

  const handleCancel = () => {
    setShowError(false);
    setIsLoading(false);
    onClose();
  };

  return (
    <>
      <AnimatePresence>
        {isOpen && (
          <>
            {/* Backdrop */}
            <motion.div
              key="agreement-backdrop"
              className="fixed inset-0 bg-black/60 z-[70]"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={onClose}
            />

            {/* Modal */}
            <motion.div
              key="agreement-modal"
              className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-[420px] rounded-2xl shadow-2xl z-[70] border border-gray-800"
              style={{
                background: "linear-gradient(180deg, #FAFAFA 0%, #F5F5F5 50%, #EFEFEF 100%)"
              }}
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.9, opacity: 0 }}
              onClick={(e) => e.stopPropagation()}
            >
              {/* Header */}
              <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
                <h3 className="text-lg font-semibold text-gray-900 mx-auto">
                  Agreement Assistant
                </h3>
                <button
                  onClick={onClose}
                  className="p-1.5 hover:bg-gray-100 rounded-lg transition-colors absolute right-6"
                >
                  <X className="w-5 h-5 text-gray-500" />
                </button>
              </div>

              {/* Content */}
              <div className="p-5 space-y-4 min-h-[280px]">
                {/* Tabs */}
                <div className="bg-gray-100 rounded-full p-1.5 flex gap-2">
                  <button
                    onClick={() => setSelectedTab("agreement")}
                    className={`flex-1 px-5 py-2 text-center rounded-full font-medium transition-all ${
                      selectedTab === "agreement"
                        ? "bg-white text-gray-900 shadow-sm"
                        : "bg-transparent text-gray-600"
                    }`}
                  >
                    Agreement Type
                  </button>
                  <button
                    onClick={() => setSelectedTab("counterparty")}
                    className={`flex-1 px-5 py-2 text-center rounded-full font-medium transition-all ${
                      selectedTab === "counterparty"
                        ? "bg-black text-white"
                        : "bg-transparent text-gray-600"
                    }`}
                  >
                    Counterparty
                  </button>
                </div>

                {/* Agreement Type Dropdown */}
                {selectedTab === "agreement" && (
                  <div className="space-y-2 relative">
                    <div className="relative">
                      <button
                        onClick={() => setShowDropdown(!showDropdown)}
                        className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-full text-gray-900 text-left flex items-center justify-between hover:bg-gray-100 transition-colors focus:outline-none"
                      >
                        <span className={selectedAgreement ? "text-gray-900" : "text-gray-400"}>
                          {selectedAgreement || "Please select agreement type"}
                        </span>
                        <ChevronDown className={`w-5 h-5 text-gray-400 transition-transform ${showDropdown ? 'rotate-180' : ''}`} />
                      </button>
                      
                      <AnimatePresence>
                        {showDropdown && (
                          <motion.div
                            className="absolute top-full left-0 right-0 mt-2 bg-white border border-gray-200 rounded-lg overflow-hidden z-50 shadow-lg"
                            initial={{ opacity: 0, y: -10 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: -10 }}
                          >
                            {agreementTypes.map((type) => (
                              <button
                                key={type}
                                onClick={() => {
                                  setSelectedAgreement(type);
                                  setShowDropdown(false);
                                }}
                                className={`w-full px-4 py-3 text-left transition-colors ${
                                  selectedAgreement === type
                                    ? "bg-gray-100 text-gray-900"
                                    : "text-gray-700 hover:bg-gray-50"
                                }`}
                              >
                                {type}
                              </button>
                            ))}
                          </motion.div>
                        )}
                      </AnimatePresence>
                    </div>
                  </div>
                )}

                {/* Counterparty Input */}
                {selectedTab === "counterparty" && (
                  <div className="space-y-2">
                    <input
                      type="text"
                      value={counterparty}
                      onChange={(e) => setCounterparty(e.target.value)}
                      placeholder={language === "zh-CN" ? "请输入counterparty" : language === "zh-TW" ? "請輸入counterparty" : "Please enter counterparty"}
                      className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-full text-gray-900 placeholder-gray-400 focus:outline-none focus:border-gray-200 focus:bg-white transition-colors"
                    />
                  </div>
                )}

                {/* Ask Button */}
                <div className="px-4 pt-4">
                  <style>{`
                    @keyframes border-flow {
                      0% {
                        background-position: 0% 0%;
                      }
                      100% {
                        background-position: 300% 0%;
                      }
                    }
                    .flowing-border {
                      border: 2px solid transparent;
                      background-image: 
                        linear-gradient(#F5F5F5, #F5F5F5),
                        linear-gradient(90deg, 
                          #E935C6 0%, 
                          #FF6B9D 10%, 
                          #FFA574 20%, 
                          #4FC3F7 30%, 
                          #00B4D8 40%,
                          #E935C6 50%,
                          #FF6B9D 60%, 
                          #FFA574 70%, 
                          #4FC3F7 80%, 
                          #00B4D8 90%,
                          #E935C6 100%
                        );
                      background-origin: border-box;
                      background-clip: padding-box, border-box;
                      background-size: 300% 100%;
                      animation: border-flow 3s linear infinite;
                    }
                  `}</style>
                  
                  {showError ? (
                    <div className="flex items-center justify-center gap-2 py-2">
                      <AlertTriangle className="w-5 h-5 text-red-500" />
                      <span className="text-gray-700 text-sm">No relevant agreement found</span>
                    </div>
                  ) : (
                    <button
                      onClick={handleAsk}
                      disabled={!canSubmit}
                      className={`relative w-full px-6 py-2 rounded-full font-medium transition-colors overflow-hidden ${
                        isLoading ? 'flowing-border' : 'disabled:opacity-50 disabled:cursor-not-allowed'
                      }`}
                      style={!isLoading ? {
                        background: 'transparent',
                        border: '2px solid transparent',
                        backgroundImage: 'linear-gradient(#F5F5F5, #F5F5F5), linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                        backgroundOrigin: 'border-box',
                        backgroundClip: 'padding-box, border-box'
                      } : {}}
                    >
                      <span className="relative z-10 text-gray-900">
                        {isLoading ? "Locking File....." : "Ask"}
                      </span>
                    </button>
                  )}
                </div>

                {/* Download Link or Error Actions */}
                {showError ? (
                  <div className="text-center flex items-center justify-center gap-3">
                    <button
                      onClick={handleRetry}
                      className="relative text-sm font-medium hover:opacity-80 transition-opacity"
                    >
                      <span
                        style={{
                          background: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                          WebkitBackgroundClip: 'text',
                          WebkitTextFillColor: 'transparent',
                          backgroundClip: 'text'
                        }}
                      >
                        Retry
                      </span>
                      <div 
                        className="absolute bottom-0 left-0 right-0 h-[1px]"
                        style={{
                          background: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)'
                        }}
                      />
                    </button>
                    <span className="text-gray-400 text-sm">or</span>
                    <button
                      onClick={handleCancel}
                      className="relative text-sm font-medium hover:opacity-80 transition-opacity"
                    >
                      <span
                        style={{
                          background: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                          WebkitBackgroundClip: 'text',
                          WebkitTextFillColor: 'transparent',
                          backgroundClip: 'text'
                        }}
                      >
                        Cancel
                      </span>
                      <div 
                        className="absolute bottom-0 left-0 right-0 h-[1px]"
                        style={{
                          background: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)'
                        }}
                      />
                    </button>
                  </div>
                ) : !isLoading && (
                  <div className="text-center">
                    <button
                      onClick={handleDownload}
                      disabled={!canSubmit}
                      className={`relative text-sm font-medium transition-opacity ${!canSubmit ? 'opacity-50 cursor-not-allowed' : 'hover:opacity-80'}`}
                    >
                      <span
                        style={{
                          background: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)',
                          WebkitBackgroundClip: 'text',
                          WebkitTextFillColor: 'transparent',
                          backgroundClip: 'text'
                        }}
                      >
                        Download
                      </span>
                      <div 
                        className="absolute bottom-0 left-0 right-0 h-[1px]"
                        style={{
                          background: 'linear-gradient(90deg, #E935C6 0%, #FF6B9D 25%, #FFA574 50%, #4FC3F7 75%, #00B4D8 100%)'
                        }}
                      />
                    </button>
                  </div>
                )}
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </>
  );
}