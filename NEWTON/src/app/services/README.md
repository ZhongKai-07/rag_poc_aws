# RAG API 集成指南

## 📁 文件结构

```
/src/app/
├── services/
│   ├── ragApi.ts          # API 服务主文件
│   ├── types.ts           # TypeScript 类型定义
│   ├── config.ts          # API 配置文件
│   └── README.md          # 本文档
├── hooks/
│   └── useRagApi.ts       # React Hook 封装
└── components/
    └── MaximizedChat.tsx  # 聊天组件（需要更新）
```

## 🔧 配置步骤

### 1. 环境变量配置

在项目根目录创建 `.env` 文件：

```bash
# RAG API 基础 URL
REACT_APP_RAG_API_URL=https://your-rag-api.com/api

# API 超时时间（毫秒）
REACT_APP_API_TIMEOUT=30000

# 重试次数
REACT_APP_MAX_RETRIES=2

# 开发模式：使用 Mock 数据
REACT_APP_USE_MOCK=true
```

### 2. 后端 API 接口规范

#### 2.1 聊天接口

**端点**: `POST /api/chat`

**请求体**:
```json
{
  "question": "开户需要什么材料？",
  "scenario": "Account Opening",
  "language": "zh-CN",
  "conversationHistory": [
    {
      "role": "user",
      "content": "之前的问题",
      "timestamp": "2024-01-01T00:00:00Z"
    },
    {
      "role": "assistant",
      "content": "之前的回答",
      "timestamp": "2024-01-01T00:00:01Z"
    }
  ]
}
```

**成功响应** (200):
```json
{
  "answer": "开户需要以下材料：\n1. 身份证明文件{cite:1}\n2. 地址证明{cite:2}",
  "citations": [
    {
      "id": "cite-1",
      "source": "HK SFC | Regulatory Guidelines",
      "content": "详细的引用内容...",
      "highlight": "关键摘要文本"
    }
  ],
  "metadata": {
    "modelUsed": "gpt-4",
    "processingTime": 1250,
    "confidence": 0.95
  }
}
```

**错误响应** (4xx/5xx):
```json
{
  "error": {
    "code": "INSUFFICIENT_CONTEXT",
    "message": "无法根据当前上下文生成回答",
    "details": {}
  }
}
```

#### 2.2 协议助手接口

**端点**: `POST /api/agreement/query`

**请求体**:
```json
{
  "question": "What is the minimum transfer amount?",
  "agreementType": "ISDA",
  "counterparty": "HSBC",
  "language": "en"
}
```

**响应格式**: 与聊天接口相同

#### 2.3 文件解析接口

**端点**: `POST /api/file/parse`

**请求体**: `multipart/form-data`
- `file`: 文件对象
- `fileType`: "pdf" | "jpg" | "png"

**响应**:
```json
{
  "parsedData": {
    "clientName": "张三",
    "idNumber": "123456789",
    "address": "香港中环...",
    // ...其他解析字段
  }
}
```

## 💻 在组件中使用

### 方式一：使用 Hook（推荐）

```typescript
import { useRagApi } from '../hooks/useRagApi';
import { Language } from '../i18n/translations';

function MyComponent() {
  const language: Language = 'zh-CN';
  
  const { sendMessage, isLoading, error } = useRagApi({
    language,
    onSuccess: (response) => {
      console.log('成功:', response.answer);
    },
    onError: (error) => {
      console.error('失败:', error);
    }
  });

  const handleAsk = async () => {
    const response = await sendMessage({
      question: "开户需要什么材料？",
      scenario: "Account Opening",
      language: "zh-CN"
    });

    if (response.success) {
      // 处理成功响应
      console.log(response.answer);
      console.log(response.citations);
    } else {
      // 处理错误
      console.error(response.error.message);
    }
  };

  return (
    <div>
      <button onClick={handleAsk} disabled={isLoading}>
        {isLoading ? '加载中...' : '提问'}
      </button>
      {error && <p>错误: {error}</p>}
    </div>
  );
}
```

### 方式二：直接调用 API

```typescript
import { sendChatMessage } from '../services/ragApi';

async function askQuestion() {
  const response = await sendChatMessage({
    question: "What are KYC requirements?",
    scenario: "Regulations & Compliance",
    language: "en"
  });

  if (response.success) {
    return response.answer;
  } else {
    throw new Error(response.error.message);
  }
}
```

## 🎯 协议助手专用接口

对于协议助手场景，传递 `agreementType` 和 `counterparty` 参数：

```typescript
import { useRagApi } from '../hooks/useRagApi';

const { sendAgreementQuery } = useRagApi({ language: 'en' });

const response = await sendAgreementQuery({
  question: "What is the minimum transfer amount?",
  agreementType: "ISDA",      // ← 协议类型
  counterparty: "HSBC",        // ← 交易对手
  language: "en"
});
```

## 🔄 引用标记格式

前端返回的答案中使用 `{cite:N}` 标记来标识引用位置：

```typescript
const answer = "根据规定，需要提交身份证明{cite:1}和地址证明{cite:2}。";

const citations = [
  { id: "cite-1", source: "...", content: "...", highlight: "..." },
  { id: "cite-2", source: "...", content: "...", highlight: "..." }
];
```

前端会自动解析这些标记并显示为可点击的引用按钮。

## 🐛 错误处理

### 错误类型

1. **网络错误** (`NETWORK_ERROR`)
   - 无法连接到服务器
   - 请求超时
   
2. **HTTP 错误** (`HTTP_4xx`, `HTTP_5xx`)
   - 400: 请求参数错误
   - 401: 未授权
   - 500: 服务器内部错误

3. **业务错误** (自定义错误码)
   - `INSUFFICIENT_CONTEXT`: 上下文不足
   - `MODEL_ERROR`: 模型生成失败

### 错误处理示例

```typescript
const response = await sendMessage({ question: "..." });

if (!response.success) {
  switch (response.error.code) {
    case 'NETWORK_ERROR':
      // 显示网络错误提示
      showToast('网络连接失败，请检查网络');
      break;
    case 'HTTP_401':
      // 重新登录
      redirectToLogin();
      break;
    default:
      // 使用系统语言显示错误
      showToast(getTranslation(language, 'errorNoInformation'));
  }
}
```

## 🧪 测试模式

### 启用 Mock 模式

在 `.env` 文件中设置：
```bash
REACT_APP_USE_MOCK=true
```

或在代码中直接使用：
```typescript
import { sendChatMessageMock } from '../services/ragApi';

const response = await sendChatMessageMock({
  question: "测试问题"
});
```

## 📊 集成检查清单

- [ ] 配置 `.env` 文件，设置 `REACT_APP_RAG_API_URL`
- [ ] 确认后端 API 端点符合接口规范
- [ ] 测试聊天接口 (`POST /api/chat`)
- [ ] 测试协议助手接口 (`POST /api/agreement/query`)
- [ ] 测试文件解析接口 (`POST /api/file/parse`)
- [ ] 确认引用标记格式 `{cite:N}` 正确返回
- [ ] 测试错误场景和超时处理
- [ ] 更新 `MaximizedChat.tsx` 使用新的 API
- [ ] 添加日志和监控

## 🚀 下一步

查看 `INTEGRATION_EXAMPLE.md` 了解如何更新 `MaximizedChat.tsx` 组件。
