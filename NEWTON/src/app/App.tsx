import { useState } from "react";
import { FloatingButton } from "./components/FloatingButton";
import { FileParseModal } from "./components/FileParseModal";
import { MaximizedChat } from "./components/MaximizedChat";
import { ImageWithFallback } from "./components/figma/ImageWithFallback";
import { Language } from "./i18n/translations";
// Removed figma asset import to fix build

// NEWTON Intelligent Assistant - Main App Component
export default function App() {
  // Use a default background color or image url instead of figma asset
  const desktopBg = "https://images.unsplash.com/photo-1557683316-973673baf926?q=80&w=2029&auto=format&fit=crop";

  const [isFileParserOpen, setIsFileParserOpen] = useState(false);
  const [isMaximized, setIsMaximized] = useState(true); // 直接打开聊天界面
  const [language, setLanguage] = useState<Language>("en");

  const handleOpenMaximized = () => {
    setIsMaximized(true);
  };

  const handleOpenFileParser = () => {
    setIsFileParserOpen(true);
  };

  const handleCloseFileParser = () => {
    setIsFileParserOpen(false);
  };

  const handleMinimize = () => {
    setIsMaximized(false);
  };

  return (
    <div className="size-full relative">
      {/* Desktop Background */}
      <div className="absolute inset-0">
        <img
          src={desktopBg}
          alt="Desktop Background"
          className="size-full object-cover"
        />
      </div>

      {/* Floating Assistant - 只在最小化时显示 */}
      {!isMaximized && (
        <FloatingButton 
          onDoubleClick={handleOpenMaximized}
        />
      )}

      {/* File Parse Modal */}
      <FileParseModal
        isOpen={isFileParserOpen}
        onClose={handleCloseFileParser}
        isMaximized={isMaximized}
        language={language}
      />

      {/* Maximized Chat */}
      <MaximizedChat
        isOpen={isMaximized}
        onMinimize={handleMinimize}
        onOpenFileParser={handleOpenFileParser}
        language={language}
        onLanguageChange={setLanguage}
      />
    </div>
  );
}