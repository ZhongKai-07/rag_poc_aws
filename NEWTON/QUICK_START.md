# 🚀 快速开始 - RAG 集成

## 1️⃣ 配置 (1 分钟)

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env，填入你的 API 地址
# REACT_APP_RAG_API_URL=https://your-rag-api.com/api
# REACT_APP_USE_MOCK=false
```

## 2️⃣ 三个关键接口位置

在 `/src/app/components/MaximizedChat.tsx` 中找到这三个函数并更新：

### ① 热门问题点击

```typescript
const handleQuestionClick = async (question: string) => {
  // 原来: setTimeout 模拟延迟
  // 现在: 调用真实 API ⬇️
  
  const response = await sendMessage({
    question: question,
    scenario: currentScenario,
    language: language
  });
};
```

### ② 用户输入发送

```typescript
const handleSend = async () => {
  // 检查是否为协议助手
  if (currentScenario === "Agreement Assistant") {
    // 传递 agreementType 和 counterparty
    const response = await sendAgreementQuery({
      question: inputValue,
      agreementType: fileParsingData.agreementType,  // ← 锁定文件
      counterparty: fileParsingData.counterparty,     // ← 锁定文件
      language: language
    });
  } else {
    // 普通聊天
    const response = await sendMessage({
      question: inputValue,
      scenario: currentScenario,
      language: language
    });
  }
};
```

### ③ 重新生成回答

```typescript
const handleRegenerate = async (messageId: string) => {
  // 找到对应的用户消息
  // 调用 API 重新生成
  const response = await sendMessage({
    question: userMessage.content,
    scenario: currentScenario,
    language: language
  });
};
```

## 3️⃣ 后端 API 需要实现的接口

### 接口 1: 普通聊天

```http
POST /api/chat
Content-Type: application/json

{
  "question": "开户需要什么材料？",
  "scenario": "Account Opening",
  "language": "zh-CN"
}
```

**响应**:
```json
{
  "answer": "需要以下材料：\n1. 身份证{cite:1}\n2. 地址证明{cite:2}",
  "citations": [
    {
      "id": "cite-1",
      "source": "HK SFC Guidelines",
      "content": "详细内容...",
      "highlight": "关键摘要"
    }
  ]
}
```

### 接口 2: 协议助手（带文件锁定）

```http
POST /api/agreement/query
Content-Type: application/json

{
  "question": "What is the minimum transfer amount?",
  "agreementType": "ISDA",     ← 锁定协议类型
  "counterparty": "HSBC",       ← 锁定交易对手
  "language": "en"
}
```

**后端逻辑**:
1. 根据 `agreementType` + `counterparty` 定位文件
2. **只在这个文件中检索**（RAG 向量搜索时添加过滤条件）
3. 生成回答时，只参考这个文件的内容
4. 返回的 citations 也只来自这个文件

### 接口 3: 错误处理

当无法生成答案时，返回：
```json
{
  "error": {
    "code": "INSUFFICIENT_CONTEXT",
    "message": "无法根据当前上下文生成回答"
  }
}
```

前端会显示多语言错误提示：
- 中文: `抱歉，我目前无法回答这个问题`
- 英文: `Sorry, I currently cannot answer this question`

## 4️⃣ 文件结构

```
你需要关注的文件：

/src/app/
├── services/
│   ├── ragApi.ts              ← API 调用实现
│   ├── types.ts               ← TypeScript 类型
│   └── README.md              ← 详细文档
│
├── hooks/
│   └── useRagApi.ts           ← React Hook（推荐使用）
│
└── components/
    └── MaximizedChat.tsx      ← 需要更新的主文件 ⭐

/.env                           ← 配置 API 地址
```

## 5️⃣ 代码示例

### 在 MaximizedChat.tsx 顶部添加

```typescript
import { useRagApi } from '../hooks/useRagApi';

export function MaximizedChat({ language, ... }: MaximizedChatProps) {
  // 初始化 API Hook
  const { sendMessage, sendAgreementQuery } = useRagApi({ language });
  
  // ... 其他代码 ...
}
```

### 替换 mock 代码

**之前**:
```typescript
setTimeout(() => {
  let botContent = `关于"${question}"的回答...`;
  resolve(botContent);
}, 2000);
```

**之后**:
```typescript
const response = await sendMessage({
  question: question,
  scenario: currentScenario,
  language: language
});

if (response.success) {
  const botMessage = {
    content: response.answer,
    citations: response.citations
  };
}
```

## 6️⃣ 测试

### 开发模式（使用 mock）
```bash
# .env
REACT_APP_USE_MOCK=true

npm start
```

### 连接真实后端
```bash
# .env
REACT_APP_USE_MOCK=false
REACT_APP_RAG_API_URL=http://localhost:8000/api

npm start
```

### 测试场景
- [ ] Account Opening - 普通问答
- [ ] Agreement Assistant - 协议问答（带 agreementType + counterparty）
- [ ] Regulations & Compliance - 法规问答
- [ ] 测试网络错误
- [ ] 测试超时（问题包含 "timeout test"）

## 7️⃣ 引用标记

后端返回的 answer 中使用 `{cite:N}` 标记：

```json
{
  "answer": "根据规定{cite:1}，需要提交材料{cite:2}。",
  "citations": [
    { "id": "cite-1", "source": "...", "content": "...", "highlight": "..." },
    { "id": "cite-2", "source": "...", "content": "...", "highlight": "..." }
  ]
}
```

前端会自动渲染为可点击的引用按钮。

## 📚 详细文档

- **完整集成指南**: `/src/app/services/README.md`
- **代码示例**: `/src/app/services/INTEGRATION_EXAMPLE.md`
- **API 文档**: `/src/app/services/API_REFERENCE.md`
- **架构说明**: `/ARCHITECTURE.md`

## ❓ 常见问题

### Q1: agreementType 和 counterparty 从哪里来？

A: 用户在协议助手界面输入，存储在 `fileParsingData` state 中：
```typescript
const [fileParsingData, setFileParsingData] = useState({
  agreementType: '',
  counterparty: ''
});
```

### Q2: 如何锁定特定协议文件？

A: 后端在 RAG 检索时，使用 agreementType + counterparty 作为过滤条件，只在匹配的文件中搜索。

### Q3: 错误提示为什么用系统语言？

A: 根据需求，AI 回答使用用户提问的语言，但系统错误提示使用界面语言设置。

```typescript
// AI 回答: 保持用户提问的语言（由模型自动处理）
// 错误提示: 使用系统语言
const errorMsg = getTranslation(language, 'errorNoInformation');
```

## ✅ 快速检查清单

- [ ] `.env` 已配置 API URL
- [ ] `MaximizedChat.tsx` 引入了 `useRagApi`
- [ ] `handleQuestionClick` 已更新
- [ ] `handleSend` 已更新（包括协议助手逻辑）
- [ ] `handleRegenerate` 已更新
- [ ] 后端 API 已实现 `/api/chat` 和 `/api/agreement/query`
- [ ] 引用格式 `{cite:N}` 正确返回
- [ ] 测试通过

**准备好了？开始集成！** 🚀
