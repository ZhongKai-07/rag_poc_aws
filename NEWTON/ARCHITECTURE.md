# 🏗️ NEWTON 智能助手 - RAG 集成架构

## 📐 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        前端应用                              │
│                   (React + TypeScript)                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ HTTPS
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                      RAG 后端 API                            │
│         (您的 Python/Node.js 服务 + 向量数据库)              │
└─────────────────────────────────────────────────────────────┘
                              │
                              │
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     大语言模型 (LLM)                         │
│              (GPT-4, Claude, 自建模型等)                     │
└─────────────────────────────────────────────────────────────┘
```

## 📂 前端文件结构

```
/src/app/
├── services/                    # API 服务层 ⭐
│   ├── ragApi.ts               # RAG API 主文件（核心）
│   ├── types.ts                # TypeScript 类型定义
│   ├── config.ts               # API 配置
│   ├── README.md               # 集成指南
│   ├── INTEGRATION_EXAMPLE.md  # 代码示例
│   └── API_REFERENCE.md        # API 接口文档
│
├── hooks/                       # React Hooks
│   └── useRagApi.ts            # RAG API Hook（推荐使用）
│
├── components/                  # React 组件
│   ├── MaximizedChat.tsx       # 聊天主组件 ⭐（需要更新）
│   ├── ScenarioButtonsWeb.tsx  # 场景按钮
│   ├── HotQuestions.tsx        # 热门问题
│   ├── MessageWithCitations.tsx # 消息 + 引用显示
│   ├── CitationModal.tsx       # 引用详情弹窗
│   ├── AgreementAssistantModal.tsx # 协议助手参数输入
│   └── ...
│
├── i18n/                        # 国际化
│   └── translations.ts         # 多语言翻译
│
└── App.tsx                      # 应用入口

/.env.example                    # 环境变量模板
```

## 🎯 三大接口集成点

### 1. 模型返回的回答数据接口

**位置**: `MaximizedChat.tsx` 的三个函数

| 函数名 | 触发时机 | 说明 |
|-------|---------|------|
| `handleQuestionClick` | 用户点击热门问题 | 预设问题的问答 |
| `handleSend` | 用户输入并发送 | 自由输入的问答 |
| `handleRegenerate` | 点击重新生成 | 重新请求回答 |

**实现方式**:
```typescript
// 使用 Hook（推荐）
const { sendMessage } = useRagApi({ language });

const response = await sendMessage({
  question: "用户的问题",
  scenario: "Account Opening",
  language: "zh-CN"
});

if (response.success) {
  // 显示回答
  const answer = response.answer;
  const citations = response.citations;
}
```

**API 调用**:
```
POST /api/chat
Body: { question, scenario, language, conversationHistory }
Response: { answer, citations, metadata }
```

---

### 2. 模型无法生成答案的错误处理接口

**位置**: 同样在 `handleQuestionClick`、`handleSend`、`handleRegenerate`

**错误来源**:
1. **网络错误**: 无法连接到服务器
2. **超时错误**: API 响应时间超过 30 秒
3. **业务错误**: 模型返回 `success: false`

**实现方式**:
```typescript
const response = await sendMessage({ question: "..." });

if (!response.success) {
  // 错误处理
  const errorMessage = getTranslation(language, 'errorNoInformation');
  
  // 显示错误消息给用户
  const errorMsg: Message = {
    id: `msg-${Date.now()}`,
    type: "bot",
    content: errorMessage,  // ← 根据系统语言显示错误
    timestamp: new Date(),
    feedback: null,
  };
  
  setMessages(prev => [...prev, errorMsg]);
}
```

**错误提示多语言**:
- 简体中文: `抱歉，我目前无法回答这个问题`
- 繁体中文: `抱歉，我目前無法回答這個問題`
- 英文: `Sorry, I currently cannot answer this question`

---

### 3. 协议助手：传递 agreementType 和 counterparty

**位置**: `MaximizedChat.tsx` 的 `handleSend` 函数

**目的**: 锁定特定协议文件，只在该文件范围内检索

**实现流程**:

```typescript
// 1. 用户在协议助手界面输入参数
const [fileParsingData, setFileParsingData] = useState({
  agreementType: '',   // 例如: "ISDA"
  counterparty: ''     // 例如: "HSBC"
});

// 2. 发送问题时，判断是否为协议助手场景
const handleSend = async () => {
  if (currentScenario === "Agreement Assistant") {
    // 使用协议助手专用接口
    const response = await sendAgreementQuery({
      question: "What is the minimum transfer amount?",
      agreementType: fileParsingData.agreementType,  // ← 传递给后端
      counterparty: fileParsingData.counterparty,     // ← 传递给后端
      language: language
    });
  } else {
    // 使用普通聊天接口
    const response = await sendMessage({
      question: "...",
      scenario: currentScenario
    });
  }
};
```

**API 调用**:
```
POST /api/agreement/query
Body: { 
  question: "...",
  agreementType: "ISDA",
  counterparty: "HSBC",
  language: "en"
}
```

**后端应该做什么**:
1. 接收 `agreementType` 和 `counterparty` 参数
2. 在向量数据库中过滤：只检索该协议文件的内容
3. 生成回答时，只参考该特定协议
4. 返回的 citations 也只来自该协议

---

## 🔄 数据流图

### 普通聊天场景

```
用户输入问题
    ↓
MaximizedChat.handleSend()
    ↓
useRagApi.sendMessage()
    ↓
ragApi.sendChatMessage()
    ↓
POST /api/chat {
  question: "...",
  scenario: "Account Opening",
  language: "zh-CN"
}
    ↓
后端 RAG 处理
    ↓
返回 { answer, citations }
    ↓
显示在聊天界面
```

### 协议助手场景

```
用户选择协议类型和交易对手
    ↓
setFileParsingData({ 
  agreementType: "ISDA",
  counterparty: "HSBC" 
})
    ↓
用户输入问题
    ↓
MaximizedChat.handleSend()
    ↓
检测到 currentScenario === "Agreement Assistant"
    ↓
useRagApi.sendAgreementQuery()
    ↓
POST /api/agreement/query {
  question: "...",
  agreementType: "ISDA",    ← 锁定文件
  counterparty: "HSBC",     ← 锁定文件
  language: "en"
}
    ↓
后端只在 ISDA-HSBC 协议中检索
    ↓
返回 { answer, citations }
    ↓
显示在聊天界面
```

---

## 🛠️ 集成步骤速查

### 步骤 1: 配置环境变量

```bash
# 复制模板
cp .env.example .env

# 编辑 .env
REACT_APP_RAG_API_URL=https://your-api.com/api
REACT_APP_USE_MOCK=false
```

### 步骤 2: 更新 MaximizedChat.tsx

```typescript
// 1. 引入 Hook
import { useRagApi } from '../hooks/useRagApi';

// 2. 在组件中初始化
const { sendMessage, sendAgreementQuery } = useRagApi({ language });

// 3. 更新三个函数
// - handleQuestionClick
// - handleSend  
// - handleRegenerate
```

详细代码见: `/src/app/services/INTEGRATION_EXAMPLE.md`

### 步骤 3: 测试

```bash
# 1. 开发模式（使用 mock）
REACT_APP_USE_MOCK=true npm start

# 2. 集成测试（连接本地后端）
REACT_APP_USE_MOCK=false
REACT_APP_RAG_API_URL=http://localhost:8000/api
npm start

# 3. 生产部署
REACT_APP_RAG_API_URL=https://api.production.com/api
npm run build
```

---

## 📋 接口规范总结

| 功能 | 端点 | 参数 |
|-----|------|------|
| **普通问答** | `POST /api/chat` | `question`, `scenario`, `language`, `conversationHistory` |
| **协议助手** | `POST /api/agreement/query` | `question`, `agreementType`, `counterparty`, `language` |
| **文件解析** | `POST /api/file/parse` | `file`, `fileType` |

**响应格式**:
```json
{
  "answer": "回答内容{cite:1}{cite:2}",
  "citations": [
    { "id": "cite-1", "source": "...", "content": "...", "highlight": "..." }
  ],
  "metadata": { ... }
}
```

---

## 🎨 UI 交互说明

### 用户流程 1: 普通问答

1. 用户选择场景（例如: Account Opening）
2. 点击热门问题 或 输入自己的问题
3. 前端调用 `/api/chat`
4. 显示回答（带引用标记 `{cite:N}`）
5. 用户可点击引用查看详情

### 用户流程 2: 协议助手

1. 用户选择 "Agreement Assistant" 场景
2. 输入协议类型（ISDA）和交易对手（HSBC）
3. 系统保存: `fileParsingData = { agreementType: "ISDA", counterparty: "HSBC" }`
4. 用户输入问题
5. 前端调用 `/api/agreement/query`（带上 agreementType 和 counterparty）
6. 后端锁定特定协议文件进行检索
7. 显示回答和引用

---

## 🔍 关键代码位置

| 功能 | 文件 | 行号/函数 |
|-----|------|----------|
| 热门问题点击 | `MaximizedChat.tsx` | `handleQuestionClick` |
| 用户输入发送 | `MaximizedChat.tsx` | `handleSend` |
| 重新生成 | `MaximizedChat.tsx` | `handleRegenerate` |
| 协议参数存储 | `MaximizedChat.tsx` | `fileParsingData` state |
| API Hook | `useRagApi.ts` | `sendMessage`, `sendAgreementQuery` |
| API 服务 | `ragApi.ts` | `sendChatMessage`, `queryAgreement` |
| 错误翻译 | `translations.ts` | `errorNoInformation` |

---

## 📚 文档导航

- **集成指南**: `/src/app/services/README.md`
- **代码示例**: `/src/app/services/INTEGRATION_EXAMPLE.md`
- **API 文档**: `/src/app/services/API_REFERENCE.md`
- **本文档**: `/ARCHITECTURE.md`

---

## ✅ 验收检查清单

- [ ] `.env` 文件已配置
- [ ] `handleQuestionClick` 已更新为调用真实 API
- [ ] `handleSend` 已更新为调用真实 API
- [ ] `handleRegenerate` 已更新为调用真实 API
- [ ] 协议助手能正确传递 `agreementType` 和 `counterparty`
- [ ] 错误提示使用多语言翻译
- [ ] 引用标记 `{cite:N}` 正确显示和可点击
- [ ] 所有三个场景都已测试：
  - [ ] Account Opening
  - [ ] Agreement Assistant
  - [ ] Regulations & Compliance
- [ ] 网络错误处理正常
- [ ] 超时处理正常
- [ ] 删除所有 mock/setTimeout 代码

---

**如有疑问，请参考各个文档或联系技术支持。**
