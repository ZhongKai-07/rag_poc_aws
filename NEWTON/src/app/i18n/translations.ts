export type Language = "zh-CN" | "zh-TW" | "en";

export const translations = {
  "zh-CN": {
    // Header
    assistant: "ICOA 助手",
    role: "角色: COB",
    userName: "张明",
    userRole: "角色: COB",
    fileParser: "文件解析",
    history: "历史记录",
    close: "关闭",
    backToHome: "返回首页",
    
    // Input
    inputPlaceholder: "输入您的问题...",
    
    // Scenario Buttons
    scenarioTitle: "您好！我是您的智能助手，请选择您需要的服务：",
    personalAccount: "个人开户材料清单",
    companyAccount: "公司开户材料清单",
    compliance: "合规流程查询",
    usStocks: "美股交易规则",
    hkStocks: "港股通规则",
    businessProcess: "业务办理流程查询",
    
    // Hot Questions Title
    hotQuestions: "热门问题",
    
    // Response Templates
    questionResponse: "关于「{question}」，这是详细解答：根据香港证监会规定...",
    generalResponse: "感谢您的提问。关于「{question}」，我为您查询到以下信息...",
    
    // File Parser
    fileParserTitle: "智能文件解析",
    uploadStep: "上传文件",
    previewStep: "预览",
    downloadStep: "下载",
    completeStep: "完成",
    uploadFiles: "上传",
    preview: "预览与编辑",
    parsing: "解析中",
    confirm: "确认完成",
    dragDropText: "拖拽文件至此或点击上传",
    supportFormats: "支持 PDF, JPG, PNG 格式，最大 10MB",
    selectFiles: "选择文件",
    filesSelected: "已选择 {count} 个文件",
    confirmParse: "确认解析",
    parsingComplete: "解析完成：",
    successCount: "{count} 个成功",
    failureCount: "{count} 个失败",
    reupload: "重新上传",
    viewResults: "查看结果",
    fileName: "文件名",
    parsingStatus: "解析中",
    parseSuccess: "解析成功",
    parseError: "文件格式不支持或解析失败",
    parseResult: "解析结果",
    edit: "编辑",
    done: "完成编辑",
    previous: "上一个",
    next: "下一个",
    confirmCorrect: "确认无误",
    parseComplete: "解析完成",
    fileSavedSuccess: "文件已成功解析并保存",
    downloadResult: "下载结果",
    downloaded: "已下载",
    selectDownloadSettings: "选择下载设置",
    confirmFileNamePath: "请确认文件名称和保存位置",
    savePath: "保存位置",
    fileNameLabel: "文件名称",
    fileNamePlaceholder: "请输入文件名称",
    fullPath: "完整路径",
    cancel: "取消",
    confirmDownload: "确认下载",
    downloads: "下载",
    desktop: "桌面",
    documents: "文件",
    myDocuments: "我的文档",
    filesDeleted: "文件已全部删除，请重新选择",
    clientName: "客户姓名",
    idNumber: "证件号码",
    address: "地址",
    phone: "电话",
    email: "电邮",
    accountType: "账户类型",
    riskLevel: "风险等级",
    
    // Error Messages
    errorNoInformation: "抱歉，我目前无法回答这个问题",
  },
  "zh-TW": {
    // Header
    assistant: "ICOA 助手",
    role: "角色: COB",
    userName: "張明",
    userRole: "角色: COB",
    fileParser: "文件解析",
    history: "歷史記錄",
    close: "關閉",
    backToHome: "返回首頁",
    
    // Input
    inputPlaceholder: "輸入您的問題...",
    
    // Scenario Buttons
    scenarioTitle: "您好！我是您的智能助手，請選擇您需要的服務：",
    personalAccount: "個人開戶材料清單",
    companyAccount: "公司開戶材料清單",
    compliance: "合規流程查詢",
    usStocks: "美股交易規則",
    hkStocks: "港股通規則",
    businessProcess: "業務辦理流程查詢",
    
    // Hot Questions Title
    hotQuestions: "熱門問題",
    
    // Response Templates
    questionResponse: "關於「{question}」，這是詳細解答：根據香港證監會規定...",
    generalResponse: "感謝您的提問。關於「{question}」，我為您查詢到以下信息...",
    
    // File Parser
    fileParserTitle: "智能文件解析",
    uploadStep: "上傳文件",
    previewStep: "預覽",
    downloadStep: "下載",
    completeStep: "完成",
    uploadFiles: "上傳",
    preview: "預覽與編輯",
    parsing: "解析中",
    confirm: "確認完成",
    dragDropText: "拖拽文件至此或點擊上傳",
    supportFormats: "支持 PDF, JPG, PNG 格式，最大 10MB",
    selectFiles: "選擇文件",
    filesSelected: "已選擇 {count} 個文件",
    confirmParse: "確認解析",
    parsingComplete: "解析完成：",
    successCount: "{count} 個成功",
    failureCount: "{count} 個失敗",
    reupload: "重新上傳",
    viewResults: "查看結果",
    fileName: "文件名",
    parsingStatus: "解析中",
    parseSuccess: "解析成功",
    parseError: "文件格式不支持或解析失敗",
    parseResult: "解析結果",
    edit: "編輯",
    done: "完成編輯",
    previous: "上一個",
    next: "下一個",
    confirmCorrect: "確認無誤",
    parseComplete: "解析完成",
    fileSavedSuccess: "文件已成功解析並保存",
    downloadResult: "下載結果",
    downloaded: "已下載",
    selectDownloadSettings: "選擇下載設置",
    confirmFileNamePath: "請確認文件名稱和保存位置",
    savePath: "保存位置",
    fileNameLabel: "文件名稱",
    fileNamePlaceholder: "請輸入文件名稱",
    fullPath: "完整路徑",
    cancel: "取消",
    confirmDownload: "確認下載",
    downloads: "下載",
    desktop: "桌面",
    documents: "文件",
    myDocuments: "我的文檔",
    filesDeleted: "文件已全部刪除，請重新選擇",
    clientName: "客戶姓名",
    idNumber: "證件號碼",
    address: "地址",
    phone: "電話",
    email: "電郵",
    accountType: "賬戶類型",
    riskLevel: "風險等級",
    
    // Error Messages
    errorNoInformation: "抱歉，我目前無法回答這個問題",
  },
  "en": {
    // Header
    assistant: "ICOA Assistant",
    role: "Role: COB",
    userName: "Ming Zhang",
    userRole: "Role: COB",
    fileParser: "File Parser",
    history: "History",
    close: "Close",
    backToHome: "Back to Home",
    
    // Input
    inputPlaceholder: "Enter your question...",
    
    // Scenario Buttons
    scenarioTitle: "Hello! I'm your intelligent assistant. Please select the service you need:",
    personalAccount: "Personal Account Documents",
    companyAccount: "Corporate Account Documents",
    compliance: "Compliance Process Inquiry",
    usStocks: "US Stock Trading Rules",
    hkStocks: "HK Stock Connect Rules",
    businessProcess: "Business Process Inquiry",
    
    // Hot Questions Title
    hotQuestions: "Popular Questions",
    
    // Response Templates
    questionResponse: "Regarding \"{question}\", here is a detailed answer: According to HKSFC regulations...",
    generalResponse: "Thank you for your question. Regarding \"{question}\", here is the information I found...",
    
    // File Parser
    fileParserTitle: "Intelligent File Parser",
    uploadStep: "Upload",
    previewStep: "Preview",
    downloadStep: "Download",
    completeStep: "Complete",
    uploadFiles: "Upload",
    preview: "Preview & Edit",
    parsing: "Parsing",
    confirm: "Confirm",
    dragDropText: "Drag and drop files here or click to upload",
    supportFormats: "Supports PDF, JPG, PNG formats, max 10MB",
    selectFiles: "Select Files",
    filesSelected: "{count} files selected",
    confirmParse: "Confirm Parse",
    parsingComplete: "Parsing Complete:",
    successCount: "{count} successful",
    failureCount: "{count} failed",
    reupload: "Re-upload",
    viewResults: "View Results",
    fileName: "File Name",
    parsingStatus: "Parsing",
    parseSuccess: "Parse Successful",
    parseError: "Unsupported file format or parsing failed",
    parseResult: "Parse Result",
    edit: "Edit",
    done: "Done",
    previous: "Previous",
    next: "Next",
    confirmCorrect: "Confirm",
    parseComplete: "Parse Complete",
    fileSavedSuccess: "File parsed and saved successfully",
    downloadResult: "Download Result",
    downloaded: "Downloaded",
    selectDownloadSettings: "Download Settings",
    confirmFileNamePath: "Please confirm file name and save location",
    savePath: "Save Location",
    fileNameLabel: "File Name",
    fileNamePlaceholder: "Enter file name",
    fullPath: "Full Path",
    cancel: "Cancel",
    confirmDownload: "Confirm Download",
    downloads: "Downloads",
    desktop: "Desktop",
    documents: "Documents",
    myDocuments: "My Documents",
    filesDeleted: "All files deleted, please select again",
    clientName: "Client Name",
    idNumber: "ID Number",
    address: "Address",
    phone: "Phone",
    email: "Email",
    accountType: "Account Type",
    riskLevel: "Risk Level",
    
    // Error Messages
    errorNoInformation: "Sorry, I currently cannot answer this question",
  },
};

export const hotQuestionsTranslations = {
  "zh-CN": {
    "Account Opening": [
      "My prospective client is a Fund. What are the required onboarding documents?",
      "What are the supporting document requirements for a fully exempted Corporate PI qualification?",
      "What is covered entity?",
    ],
    "Regulations & Compliance": [
      "反洗钱审查需要多久？",
      "如何进行KYC认证？",
      "合规审核的具体步骤是什么？",
      "尽职调查包括哪些内容？",
    ],
    "SOP Assistant": [
      "新员工如何快速了解开户流程SOP？",
      "客户投诉处理的标准流程是什么？",
      "交易异常情况的应急处理步骤？",
      "如何查询某个业务的操作手册？",
    ],
    "个人开户材料清单": [
      "需要准备哪些身份证明文件？",
      "地址证明有哪些要求？",
      "开户需要多长时间？",
      "非香港居民可以开户吗？",
    ],
    "公司开户材料清单": [
      "需要哪些公司注册文件？",
      "董事会决议如何准备？",
      "是否需要提供财务报表？",
      "实益拥有人声明如何填写？",
    ],
    "美股交易规则": [
      "开通美股需要什么资格？",
      "美股最低入金要求是多少？",
      "美股交易手续费如何计算？",
      "Pattern Day Trader规则是什么？",
    ],
    "港股通规则": [
      "港股通开户需要什么条件？",
      "哪些股票可以通过港股通交易？",
      "港股通交易额度是多少？",
      "港股通结算周期是怎样的？",
    ],
    "业务办理流程查询": [
      "线上开户和线下开户有什么区别？",
      "开户申请被拒的常见原因？",
      "如何查询开户进度？",
      "开户后多久可以开始交易？",
    ],
    "default": [
      "My prospective client is a Fund. What are the required onboarding documents?",
      "What are the supporting document requirements for a fully exempted Corporate PI qualification?",
      "What is covered entity?",
    ],
  },
  "zh-TW": {
    "Account Opening": [
      "My prospective client is a Fund. What are the required onboarding documents?",
      "What are the supporting document requirements for a fully exempted Corporate PI qualification?",
      "What is covered entity?",
    ],
    "Regulations & Compliance": [
      "反洗錢審查需要多久？",
      "如何進行KYC認證？",
      "合規審核的具體步驟是什麼？",
      "盡職調查包括哪些內容？",
    ],
    "SOP Assistant": [
      "新員工如何快速了解開戶流程SOP？",
      "客戶投訴處理的標準流程是什麼？",
      "交易異常情況的應急處理步驟？",
      "如何查詢某個業務的操作手冊？",
    ],
    "個人開戶材料清單": [
      "需要準備哪些身份證明文件？",
      "地址證明有哪些要求？",
      "開戶需要多長時間？",
      "非香港居民可以開戶嗎？",
    ],
    "公司開戶材料清單": [
      "需要哪些公司註冊文件？",
      "董事會決議如何準備？",
      "是否需要提供財務報表？",
      "實益擁有人聲明如何填寫？",
    ],
    "美股交易規則": [
      "開通美股需要什麼資格？",
      "美股最低入金要求是多少？",
      "美股交易手續費如何計算？",
      "Pattern Day Trader規則是什麼？",
    ],
    "港股通規則": [
      "港股通開戶需要什麼條件？",
      "哪些股票可以通過港股通交易？",
      "港股通交易額度是多少？",
      "港股通結算週期是怎樣的？",
    ],
    "業務辦理流程查詢": [
      "線上開戶和線下開戶有什麼區別？",
      "開戶申請被拒的常見原因？",
      "如何查詢開戶進度？",
      "開戶後多久可以開始交易？",
    ],
    "default": [
      "My prospective client is a Fund. What are the required onboarding documents?",
      "What are the supporting document requirements for a fully exempted Corporate PI qualification?",
      "What is covered entity?",
    ],
  },
  "en": {
    "Account Opening": [
      "My prospective client is a Fund. What are the required onboarding documents?",
      "What are the supporting document requirements for a fully exempted Corporate PI qualification?",
      "What is covered entity?",
    ],
    "Regulations & Compliance": [
      "How long does AML review take?",
      "How to complete KYC verification?",
      "What are the specific compliance steps?",
      "What does due diligence include?",
    ],
    "SOP Assistant": [
      "How do new employees quickly understand the account opening SOP?",
      "What is the standard process for handling customer complaints?",
      "What are the emergency response steps for transaction anomalies?",
      "How to query the operation manual for a specific business?",
    ],
    "Personal Account Documents": [
      "What ID documents are required?",
      "What are the address proof requirements?",
      "How long does account opening take?",
      "Can non-HK residents open an account?",
    ],
    "Corporate Account Documents": [
      "What company registration documents are needed?",
      "How to prepare board resolutions?",
      "Are financial statements required?",
      "How to complete beneficial owner declaration?",
    ],
    "US Stock Trading Rules": [
      "What are the requirements to trade US stocks?",
      "What is the minimum deposit for US stocks?",
      "How are US stock commissions calculated?",
      "What is the Pattern Day Trader rule?",
    ],
    "HK Stock Connect Rules": [
      "What are the requirements for Stock Connect?",
      "Which stocks are tradable via Stock Connect?",
      "What are the Stock Connect trading quotas?",
      "What is the Stock Connect settlement cycle?",
    ],
    "Business Process Inquiry": [
      "What's the difference between online and offline account opening?",
      "What are common reasons for account rejection?",
      "How to check account opening progress?",
      "How soon can I trade after opening account?",
    ],
    "default": [
      "My prospective client is a Fund. What are the required onboarding documents?",
      "What are the supporting document requirements for a fully exempted Corporate PI qualification?",
      "What is covered entity?",
    ],
  },
};

export function getTranslation(lang: Language, key: keyof typeof translations.en): string {
  return translations[lang][key];
}

export function getHotQuestions(language: Language, scenario: string): string[] {
  // Agreement Assistant scenario
  if (scenario === "Agreement Assistant") {
    return [
      "What is the minimum transfer amount under the ISDA between Huatai and HSBC?",
      "What is the rounding under the ISDA between Huatai and HSBC?",
      "What is the eligible collateral under the ISDA between Huatai and HSBC?",
      "What is the regular settlement day under the ISDA between Huatai and HSBC?"
    ];
  }
  
  // EDD scenario - add this question
  if (scenario.includes("Due Diligence") || scenario.includes("尽调") || scenario.includes("盡調")) {
    if (language === "zh-CN") {
      return [
        "客户被归类为高风险需要增强尽职调查，有哪些额外要求？",
        "如何进行 AML 反洗钱筛查？",
        "PEP 政要身份审查流程是什么？",
        "如何核实客户的资金来源？"
      ];
    } else if (language === "zh-TW") {
      return [
        "客戶被歸類為高風險需要增強盡職調查，有哪些額外要求？",
        "如何進行 AML 反洗錢篩查？",
        "PEP 政要身份審查流程是什麼？",
        "如何核實客戶的資金來源？"
      ];
    } else {
      return [
        "My client has been classified as high risk and requires Enhanced Due Diligence. What additional requirements apply?",
        "How do I conduct AML screening?",
        "What is the PEP screening process?",
        "How do I verify the source of client funds?"
      ];
    }
  }
  
  // Default questions for other scenarios
  const questions = hotQuestionsTranslations[language][scenario] || hotQuestionsTranslations[language]["default"];
  return questions;
}