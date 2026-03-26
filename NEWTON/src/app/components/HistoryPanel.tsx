import { motion, AnimatePresence } from "motion/react";
import { X, MessageSquare, Clock, Search } from "lucide-react";
import { Language } from "../i18n/translations";
import { useState } from "react";

interface HistoryPanelProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectHistory: (historyId: number) => void;
  isMaximized?: boolean; // 标识是否为全屏模式
  language: Language; // 添加语言参数
}

interface HistoryItem {
  id: number;
  title: string;
  time: string;
  preview: string;
  date: string; // 日期分组标志
  messages: Array<{
    id: string;
    type: "user" | "bot";
    content: string;
    timestamp: Date;
    feedback?: "like" | "dislike" | null;
  }>;
}

// Multi-language history items
const historyItemsData = {
  "zh-CN": [
    // 当天
    {
      id: 1,
      title: "开户清单查询",
      time: "2小时前",
      preview: "查看开户所需材料...",
      date: "当天",
    },
    {
      id: 2,
      title: "合规流程咨询",
      time: "5小时前",
      preview: "了解合规审批流程...",
      date: "当天",
    },
    // 三天前
    {
      id: 3,
      title: "KYC文档要求",
      time: "2天前",
      preview: "KYC文档要求清单...",
      date: "三天前",
    },
    {
      id: 4,
      title: "风险评估材料",
      time: "3天前",
      preview: "风险评估所需材料...",
      date: "三天前",
    },
    // 一周前
    {
      id: 5,
      title: "法规条款查询",
      time: "5天前",
      preview: "查询开户管理手册...",
      date: "一周前",
    },
    {
      id: 6,
      title: "账户类型咨询",
      time: "6天前",
      preview: "了解不同账户类型...",
      date: "一周前",
    },
  ],
  "zh-TW": [
    // 當天
    {
      id: 1,
      title: "開戶清單查詢",
      time: "2小時前",
      preview: "查看開戶所需材料...",
      date: "當天",
    },
    {
      id: 2,
      title: "合規流程諮詢",
      time: "5小時前",
      preview: "了解合規審批流程...",
      date: "當天",
    },
    // 三天前
    {
      id: 3,
      title: "KYC文檔要求",
      time: "2天前",
      preview: "KYC文檔要求清單...",
      date: "三天前",
    },
    {
      id: 4,
      title: "風險評估材料",
      time: "3天前",
      preview: "風險評估所需材料...",
      date: "三天前",
    },
    // 一週前
    {
      id: 5,
      title: "法規條款查詢",
      time: "5天前",
      preview: "查詢開戶管理手冊...",
      date: "一週前",
    },
    {
      id: 6,
      title: "賬戶類型諮詢",
      time: "6天前",
      preview: "了解不同賬戶類型...",
      date: "一週前",
    },
  ],
  "en": [
    // Today
    {
      id: 1,
      title: "Account Opening Checklist",
      time: "2 hours ago",
      preview: "Required documents for opening...",
      date: "Today",
    },
    {
      id: 2,
      title: "Compliance Process",
      time: "5 hours ago",
      preview: "Compliance approval workflow...",
      date: "Today",
    },
    // 3 days ago
    {
      id: 3,
      title: "KYC Documentation",
      time: "2 days ago",
      preview: "KYC document requirements...",
      date: "3 Days Ago",
    },
    {
      id: 4,
      title: "Risk Assessment",
      time: "3 days ago",
      preview: "Risk assessment materials...",
      date: "3 Days Ago",
    },
    // A week ago
    {
      id: 5,
      title: "Regulatory Inquiry",
      time: "5 days ago",
      preview: "Account opening regulations...",
      date: "A Week Ago",
    },
    {
      id: 6,
      title: "Account Types Inquiry",
      time: "6 days ago",
      preview: "Different account types...",
      date: "A Week Ago",
    },
  ],
};

const historyItems: HistoryItem[] = [
  {
    id: Date.now() - 7200000, // 2小时前
    title: "开户清单查询",
    time: "2小时前",
    preview: "查看开户所需材料...",
    date: "今天",
    messages: [
      {
        id: "h1-1",
        type: "user",
        content: "我需要开户，请问需要准备哪些材料？",
        timestamp: new Date(Date.now() - 7200000),
        feedback: null,
      },
      {
        id: "h1-2",
        type: "bot",
        content:
          "您好！开户需要准备以下材料：1) 身份证明文件 2) 地址证明 3) 银行账户证明 4) 风险披露声明",
        timestamp: new Date(Date.now() - 7190000),
        feedback: "like",
      },
      {
        id: "h1-3",
        type: "user",
        content: "地址证明具体指什么？",
        timestamp: new Date(Date.now() - 7100000),
        feedback: null,
      },
      {
        id: "h1-4",
        type: "bot",
        content:
          "地址证明可以是：最近3个月的水电费账单、银行对账单、政府信件等，需要显示您的姓名和居住地址。",
        timestamp: new Date(Date.now() - 7090000),
        feedback: "like",
      },
      {
        id: "h1-5",
        type: "user",
        content: "办理需要多长时间？",
        timestamp: new Date(Date.now() - 7000000),
        feedback: null,
      },
      {
        id: "h1-6",
        type: "bot",
        content:
          "一般情况下资料齐全后1-3个工作日即可完成审核并开通账户。",
        timestamp: new Date(Date.now() - 6990000),
        feedback: null,
      },
    ],
  },
  {
    id: Date.now() - 18000000, // 5小时前
    title: "合规流程咨询",
    time: "5小时前",
    preview: "了解合规审批流程...",
    date: "今天",
    messages: [
      {
        id: "h2-1",
        type: "user",
        content: "合规审批流程是怎样的？",
        timestamp: new Date(Date.now() - 18000000),
        feedback: null,
      },
      {
        id: "h2-2",
        type: "bot",
        content:
          "合规审批流程包括：1) 提交开户申请 2) KYC尽职调查 3) 风险评估 4) 合规审核 5) 户开通",
        timestamp: new Date(Date.now() - 17990000),
        feedback: "like",
      },
      {
        id: "h2-3",
        type: "user",
        content: "KYC调查需要多久？",
        timestamp: new Date(Date.now() - 17800000),
        feedback: null,
      },
      {
        id: "h2-4",
        type: "bot",
        content:
          "KYC尽职调查通常需要1-2个工作日，如遇复杂情况可能需要延长至3-5个日。",
        timestamp: new Date(Date.now() - 17790000),
        feedback: null,
      },
      {
        id: "h2-5",
        type: "user",
        content: "风险评估主要评估什么？",
        timestamp: new Date(Date.now() - 17700000),
        feedback: null,
      },
      {
        id: "h2-6",
        type: "bot",
        content:
          "风险评估主要评估您的投资经验、财务状况、风险承受能力、投资目标等方面。",
        timestamp: new Date(Date.now() - 17690000),
        feedback: "like",
      },
    ],
  },
  {
    id: Date.now() - 172800000, // 2天前
    title: "KYC文档要求",
    time: "2天前",
    preview: "KYC文档要求清单...",
    date: "三天前",
    messages: [
      {
        id: "h3-1",
        type: "user",
        content: "KYC需要提供什么文档？",
        timestamp: new Date(Date.now() - 172800000),
        feedback: null,
      },
      {
        id: "h3-2",
        type: "bot",
        content:
          "KYC文档包括：1) 有效身份证件 2) 地址证明 3) 收入证明 4) 资金来源证明 5) 投资经验声明",
        timestamp: new Date(Date.now() - 172790000),
        feedback: "like",
      },
      {
        id: "h3-3",
        type: "user",
        content: "收入证明怎么提供？",
        timestamp: new Date(Date.now() - 172700000),
        feedback: null,
      },
      {
        id: "h3-4",
        type: "bot",
        content:
          "收入证明可以是：工资单、税单、银行流水、雇主证明信等。",
        timestamp: new Date(Date.now() - 172690000),
        feedback: null,
      },
      {
        id: "h3-5",
        type: "user",
        content: "这些文档有效期是多久？",
        timestamp: new Date(Date.now() - 172600000),
        feedback: null,
      },
      {
        id: "h3-6",
        type: "bot",
        content:
          "一般要求提供3个月内的文档，身份证件需在有效期内。",
        timestamp: new Date(Date.now() - 172590000),
        feedback: "like",
      },
    ],
  },
  {
    id: Date.now() - 259200000, // 3天前
    title: "风险评估材料",
    time: "3天前",
    preview: "风险评估所需材料...",
    date: "三天前",
    messages: [
      {
        id: "h4-1",
        type: "user",
        content: "风评估要准备什么？",
        timestamp: new Date(Date.now() - 259200000),
        feedback: null,
      },
      {
        id: "h4-2",
        type: "bot",
        content:
          "需要填写风险评估问卷，包括投资经验、财务状况、风险偏好等信息。",
        timestamp: new Date(Date.now() - 259190000),
        feedback: "like",
      },
      {
        id: "h4-3",
        type: "user",
        content: "风险等级有哪些？",
        timestamp: new Date(Date.now() - 259100000),
        feedback: null,
      },
      {
        id: "h4-4",
        type: "bot",
        content:
          "风险等级分为：保守型、稳健型、平衡型、进取型、激进型五个级别。",
        timestamp: new Date(Date.now() - 259090000),
        feedback: "like",
      },
      {
        id: "h4-5",
        type: "user",
        content: "评估结果会影响什么？",
        timestamp: new Date(Date.now() - 259000000),
        feedback: null,
      },
      {
        id: "h4-6",
        type: "bot",
        content:
          "评估结果会影响您可投资的产品类型和杠杆倍数，确保投资产品与您的风险承受能力匹配。",
        timestamp: new Date(Date.now() - 258990000),
        feedback: null,
      },
    ],
  },
  {
    id: Date.now() - 432000000, // 5天前
    title: "法规条款查询",
    time: "5天前",
    preview: "查询开户管理手册...",
    date: "一周前",
    messages: [
      {
        id: "h5-1",
        type: "user",
        content: "开户有哪些法规要求？",
        timestamp: new Date(Date.now() - 432000000),
        feedback: null,
      },
      {
        id: "h5-2",
        type: "bot",
        content:
          "主要遵循香港证监会的《证券及期货条例》，包括客户识别、反洗钱、投资者保护等要求。",
        timestamp: new Date(Date.now() - 431900000),
        feedback: "like",
      },
      {
        id: "h5-3",
        type: "user",
        content: "反洗钱有什么要求？",
        timestamp: new Date(Date.now() - 431000000),
        feedback: null,
      },
      {
        id: "h5-4",
        type: "bot",
        content:
          "需要核实客户身份、了解资金来源、监测可疑交易、保留相关记录至少7年。",
        timestamp: new Date(Date.now() - 430900000),
        feedback: "like",
      },
      {
        id: "h5-5",
        type: "user",
        content: "哪些情况会被拒绝开户？",
        timestamp: new Date(Date.now() - 430000000),
        feedback: null,
      },
      {
        id: "h5-6",
        type: "bot",
        content:
          "以下情况可能被拒绝：1) 提供虚假信息 2) 法提供要文档 3) 涉及洗钱风险 4) 不符合管要求",
        timestamp: new Date(Date.now() - 429900000),
        feedback: null,
      },
    ],
  },
  {
    id: Date.now() - 518400000, // 6天前
    title: "账户类型咨询",
    time: "6天前",
    preview: "了解不同账户类型...",
    date: "一周前",
    messages: [
      {
        id: "h6-1",
        type: "user",
        content: "有哪些账户类型？",
        timestamp: new Date(Date.now() - 518400000),
        feedback: null,
      },
      {
        id: "h6-2",
        type: "bot",
        content:
          "我们提供多种账户类型，包括标准账户、VIP账户、专业账户等，满足不同客户的需求。",
        timestamp: new Date(Date.now() - 518300000),
        feedback: "like",
      },
      {
        id: "h6-3",
        type: "user",
        content: "VIP账户有什么优势？",
        timestamp: new Date(Date.now() - 518200000),
        feedback: null,
      },
      {
        id: "h6-4",
        type: "bot",
        content:
          "VIP账户提供更高的杠杆倍数、更低的交易费用、更快的执行速度等优势。",
        timestamp: new Date(Date.now() - 518100000),
        feedback: "like",
      },
      {
        id: "h6-5",
        type: "user",
        content: "如何选择账户类型？",
        timestamp: new Date(Date.now() - 518000000),
        feedback: null,
      },
      {
        id: "h6-6",
        type: "bot",
        content:
          "您可以根据自己的投资经验、资金规模、风险承受能力等因素选择合适的账户类型。",
        timestamp: new Date(Date.now() - 517900000),
        feedback: null,
      },
    ],
  },
];

export { historyItems, historyItemsData };
export type { HistoryItem };

export function HistoryPanel({ isOpen, onClose, onSelectHistory, isMaximized = false, language }: HistoryPanelProps) {
  // Translations for UI text
  const historyTitle = language === "zh-CN" ? "聊天记录" : language === "zh-TW" ? "聊天記錄" : "Chat History";
  const viewAllText = language === "zh-CN" ? "查看全部历史记录" : language === "zh-TW" ? "查看全部歷史記錄" : "View All History";
  const searchPlaceholder = language === "zh-CN" ? "搜索历史记录..." : language === "zh-TW" ? "搜索歷史記錄..." : "Search history...";

  // Get localized history items
  const localizedItems = historyItemsData[language];
  
  // Group items by date
  const groupedItems: Array<{ date: string; items: typeof localizedItems }> = [];
  let currentDate = "";
  let currentGroup: typeof localizedItems = [];
  
  localizedItems.forEach((item) => {
    if (item.date !== currentDate) {
      if (currentGroup.length > 0) {
        groupedItems.push({ date: currentDate, items: currentGroup });
      }
      currentDate = item.date;
      currentGroup = [item];
    } else {
      currentGroup.push(item);
    }
  });
  
  if (currentGroup.length > 0) {
    groupedItems.push({ date: currentDate, items: currentGroup });
  }
  
  // Search functionality
  const [searchQuery, setSearchQuery] = useState("");
  
  const filteredItems = groupedItems.map(group => ({
    date: group.date,
    items: group.items.filter(item => item.title.toLowerCase().includes(searchQuery.toLowerCase()))
  })).filter(group => group.items.length > 0);
  
  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop for maximized mode */}
          {isMaximized && (
            <motion.div
              className="fixed inset-0 bg-black/20 z-40"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={onClose}
            />
          )}
          
          <motion.div
            className={`fixed bg-white shadow-2xl flex flex-col overflow-hidden ${
              isMaximized 
                ? "top-0 left-0 h-full w-80 z-50 rounded-none border-r border-border" 
                : "z-[60] rounded-xl border border-border"
            }`}
            style={
              isMaximized 
                ? {} 
                : {
                    width: 480,
                    height: "70vh",
                    bottom: 100,
                    right: 20,
                  }
            }
            initial={{ x: isMaximized ? "-100%" : "100%" }}
            animate={{ x: 0 }}
            exit={{ x: isMaximized ? "-100%" : "100%" }}
            transition={{ 
              type: "tween", 
              duration: 0.15, 
              ease: "easeOut" 
            }}
          >
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-4 border-b border-gray-200 bg-white">
              <div className="flex items-center gap-2">
                <Clock className="w-5 h-5 text-gray-700" />
                <h3 className="font-semibold text-gray-900">{historyTitle}</h3>
              </div>
              <button
                onClick={onClose}
                className="p-1 hover:bg-gray-100 rounded transition-colors"
              >
                <X className="w-5 h-5 text-gray-500" />
              </button>
            </div>

            {/* Search Bar */}
            <div className="px-4 py-2 border-b border-gray-200 bg-white">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-500" />
                <input
                  type="text"
                  placeholder={searchPlaceholder}
                  className="w-full py-2 pl-10 pr-4 border border-gray-300 rounded-lg focus:outline-none focus:border-blue-500"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </div>
            </div>

            {/* History List */}
            <div className="flex-1 overflow-y-auto p-4">
              {filteredItems.map((group, groupIndex) => (
                <div key={group.date} className="mb-4">
                  {/* Date Divider */}
                  <div className="flex items-center gap-2 mb-3 px-2">
                    <div className="flex-1 h-px bg-gray-200"></div>
                    <span className="text-xs text-gray-500 font-medium">{group.date}</span>
                    <div className="flex-1 h-px bg-gray-200"></div>
                  </div>
                  
                  {/* History Items in this date group */}
                  <div className="space-y-1">
                    {group.items.map((item, index) => (
                      <motion.button
                        key={item.id}
                        className="w-full text-left py-3 px-2 hover:bg-gray-50 rounded-lg transition-all"
                        initial={{ opacity: 0, x: 20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: (groupIndex * group.items.length + index) * 0.05 }}
                        whileHover={{ scale: 1.02 }}
                        whileTap={{ scale: 0.98 }}
                        onClick={() => {
                          onSelectHistory(item.id);
                          onClose();
                        }}
                      >
                        <div className="font-medium text-sm text-gray-900">
                          {item.title}
                        </div>
                        <div className="text-xs text-gray-500 mt-1">{item.time}</div>
                      </motion.button>
                    ))}
                  </div>
                </div>
              ))}
            </div>

            {/* Footer */}
            <div className="p-4 border-t border-gray-200">
              <button className="w-full py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-lg transition-colors font-medium">
                {viewAllText}
              </button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}