import { Language } from "../i18n/translations";
import { useState, useRef, useEffect } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

interface ScenarioButtonsWebProps {
  onScenarioClick: (scenario: string) => void;
  language: Language;
}

export function ScenarioButtonsWeb({ onScenarioClick, language }: ScenarioButtonsWebProps) {
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  const scenarios = [
    { 
      key: "Account Opening",
      icon: "M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z", 
      title: language === "zh-CN" ? "客户开户" : language === "zh-TW" ? "客戶開戶" : "Onboarding",
      description: language === "zh-CN" 
        ? "咨询个人及企业客户的开户全流程。支持账户类型选择、KYC材料清单及审批进度查询" 
        : language === "zh-TW" 
        ? "咨詢個人及企業客戶的開戶全流程。支持賬戶類型選擇、KYC材料清單及審批進度查詢"
        : "Consulting on the full onboarding process for individual and corporate clients. Supports account type selection, KYC material checklist, and approval progress inquiry.",
      tagline: language === "zh-CN" ? "业务Panel A" : language === "zh-TW" ? "業務Panel A" : "Business Panel A"
    },
    { 
      key: "Regulations & Compliance",
      icon: "M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z", 
      title: language === "zh-CN" ? "认证与合规" : language === "zh-TW" ? "認證與合規" : "Certification & Compliance",
      description: language === "zh-CN" 
        ? "查询业务合规标准与操作指引。实时检索监管要求、审批流程及各类业务的合规指引" 
        : language === "zh-TW" 
        ? "查詢業務合規標準與操作指引。實時檢索監管要求、審批流程及各類業務的合規指引"
        : "Query business compliance standards and operational guidelines. Real-time retrieval of regulatory requirements, approval processes, and compliance guidelines for various businesses.",
      tagline: language === "zh-CN" ? "业务Panel B" : language === "zh-TW" ? "業務Panel B" : "Business Panel B"
    },
    { 
      key: "SOP Assistant",
      icon: "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z", 
      title: language === "zh-CN" ? "SOP 智能问答" : language === "zh-TW" ? "SOP 智能問答" : "SOP Assistant",
      description: language === "zh-CN" 
        ? "面向Ops内部团队的标准操作流程（SOP）支持。通过AI即时回答加速新员工入职学习，提升日常操作效率" 
        : language === "zh-TW" 
        ? "面向Ops內部團隊的標準操作流程（SOP）支持。通過AI即時回答加速新員工入職學習，提升日常操作效率"
        : "Standard Operating Procedure (SOP) support for internal Ops teams. AI-powered instant answers to accelerate new employee onboarding and improve daily operational efficiency.",
      tagline: language === "zh-CN" ? "场景8" : language === "zh-TW" ? "場景8" : "Scenario 8"
    },
  ];

  const scrollToCard = (index: number) => {
    if (scrollContainerRef.current) {
      const cardWidth = scrollContainerRef.current.offsetWidth / 2.5;
      scrollContainerRef.current.scrollTo({
        left: cardWidth * index,
        behavior: "smooth"
      });
    }
    setSelectedIndex(index);
  };

  const handlePrev = () => {
    const currentIdx = selectedIndex ?? 0;
    const newIndex = currentIdx > 0 ? currentIdx - 1 : scenarios.length - 1;
    scrollToCard(newIndex);
  };

  const handleNext = () => {
    const currentIdx = selectedIndex ?? 0;
    const newIndex = currentIdx < scenarios.length - 1 ? currentIdx + 1 : 0;
    scrollToCard(newIndex);
  };

  return (
    <div className="w-full px-8 flex flex-col items-center gap-6">
      {/* SVG Gradient Definition */}
      <svg width="0" height="0" style={{ position: 'absolute' }}>
        <defs>
          <linearGradient id="iconGradient" x1="0%" y1="100%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#FFA574" />
            <stop offset="25%" stopColor="#E935C6" />
            <stop offset="50%" stopColor="#FF6B9D" />
            <stop offset="75%" stopColor="#4FC3F7" />
            <stop offset="100%" stopColor="#00B4D8" />
          </linearGradient>
        </defs>
      </svg>

      {/* Navigation Arrows */}
      <div className="flex items-center gap-4 w-full max-w-6xl">
        <button
          onClick={handlePrev}
          className="p-2 rounded-full hover:bg-gray-100 transition-colors flex-shrink-0"
          aria-label="Previous"
        >
          <ChevronLeft className="w-6 h-6 text-gray-600" />
        </button>

        {/* Scrollable Cards Container */}
        <div 
          ref={scrollContainerRef}
          className="flex-1 overflow-x-auto scrollbar-hide scroll-smooth"
          style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
        >
          <div className="flex gap-4" style={{ width: 'max-content' }}>
            {scenarios.map((scenario, index) => {
              const isSelected = selectedIndex === index;
              return (
                <button
                  key={scenario.key}
                  onClick={() => {
                    setSelectedIndex(index);
                    onScenarioClick(scenario.key);
                  }}
                  className={`flex items-start gap-5 p-6 rounded-2xl transition-all text-left bg-white ${
                    isSelected 
                      ? "shadow-lg" 
                      : ""
                  } hover:shadow-md`}
                  style={{ width: "400px", minWidth: "400px" }}
                >
                  {/* 图标 - SVG路径 + 渐变色 */}
                  <div className="flex-shrink-0">
                    <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" strokeWidth={1.5}>
                      <path strokeLinecap="round" strokeLinejoin="round" stroke="url(#iconGradient)" d={scenario.icon} />
                    </svg>
                  </div>
                  
                  <div className="flex-1 min-w-0">
                    {/* 标题 */}
                    <h3 className="text-base font-bold mb-2 text-gray-900">
                      {scenario.title}
                    </h3>
                    
                    {/* 详细描述 */}
                    <p className="text-sm leading-relaxed text-gray-500">
                      {scenario.description}
                    </p>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        <button
          onClick={handleNext}
          className="p-2 rounded-full hover:bg-gray-100 transition-colors flex-shrink-0"
          aria-label="Next"
        >
          <ChevronRight className="w-6 h-6 text-gray-600" />
        </button>
      </div>
    </div>
  );
}