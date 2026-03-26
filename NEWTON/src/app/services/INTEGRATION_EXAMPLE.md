# MaximizedChat 组件集成示例

## 📝 集成步骤

### 1. 在 MaximizedChat 组件中引入 API Hook

```typescript
// 在 MaximizedChat.tsx 顶部添加
import { useRagApi } from '../hooks/useRagApi';
import type { ChatRequest, AgreementQueryRequest } from '../services/types';
```

### 2. 在组件中初始化 Hook

```typescript
export function MaximizedChat({ isOpen, onMinimize, onOpenFileParser, language, onLanguageChange }: MaximizedChatProps) {
  // ... 现有的 state 声明 ...

  // 添加 RAG API hook
  const { sendMessage, sendAgreementQuery, isLoading: apiIsLoading, error: apiError } = useRagApi({
    language,
    onSuccess: (response) => {
      console.log('RAG 响应成功:', response);
    },
    onError: (error) => {
      console.error('RAG 错误:', error);
    }
  });

  // ... 其余代码 ...
}
```

### 3. 更新 `handleQuestionClick` 函数

**原来的代码** (使用 mock 数据):
```typescript
const handleQuestionClick = (question: string) => {
  const userMessage: Message = {
    id: `msg-${Date.now()}`,
    type: "user",
    content: question,
    timestamp: new Date(),
  };

  setMessages(prev => [...prev, userMessage]);
  setIsLoading(true);

  // Mock 响应逻辑
  const generateResponse = new Promise<string>((resolve) => {
    setTimeout(() => {
      let botContent = `关于"${question}"，这是详细解答...`;
      resolve(botContent);
    }, 2000);
  });
  
  // ... 处理响应 ...
};
```

**新的代码** (连接真实 RAG API):
```typescript
const handleQuestionClick = async (question: string) => {
  // 1. 添加用户消息到对话
  const userMessage: Message = {
    id: `msg-${Date.now()}`,
    type: "user",
    content: question,
    timestamp: new Date(),
  };

  setMessages(prev => [...prev, userMessage]);
  setIsLoading(true);

  try {
    // 2. 准备请求数据
    const request: ChatRequest = {
      question,
      scenario: currentScenario,
      language,
      conversationHistory: messages.map(msg => ({
        role: msg.type === 'user' ? 'user' : 'assistant',
        content: msg.content,
        timestamp: msg.timestamp.toISOString(),
      })),
    };

    // 3. 调用 RAG API
    const response = await sendMessage(request);

    // 4. 处理响应
    if (response.success) {
      const botMessage: Message = {
        id: `msg-${Date.now() + 1}`,
        type: "bot",
        content: response.answer,
        timestamp: new Date(),
        feedback: null,
        citations: response.citations,
      };
      setMessages(prev => [...prev, botMessage]);
    } else {
      // 显示错误消息
      const errorMessage: Message = {
        id: `msg-${Date.now() + 1}`,
        type: "bot",
        content: getTranslation(language, 'errorNoInformation'),
        timestamp: new Date(),
        feedback: null,
      };
      setMessages(prev => [...prev, errorMessage]);
    }
  } catch (error) {
    console.error('发送消息失败:', error);
    // 显示错误消息
    const errorMessage: Message = {
      id: `msg-${Date.now() + 1}`,
      type: "bot",
      content: getTranslation(language, 'errorNoInformation'),
      timestamp: new Date(),
      feedback: null,
    };
    setMessages(prev => [...prev, errorMessage]);
  } finally {
    setIsLoading(false);
  }
};
```

### 4. 更新 `handleSend` 函数（用户输入发送）

```typescript
const handleSend = async () => {
  if (!inputValue.trim()) return;

  const userMessage: Message = {
    id: `msg-${Date.now()}`,
    type: "user",
    content: inputValue,
    timestamp: new Date(),
  };

  const savedInputValue = inputValue;
  setMessages(prev => [...prev, userMessage]);
  setInputValue("");
  setIsSending(true);
  setIsLoading(true);

  setTimeout(() => setIsSending(false), 600);

  try {
    // 根据当前场景选择不同的 API
    let response;

    if (currentScenario === "Agreement Assistant") {
      // 协议助手：需要传递 agreementType 和 counterparty
      const agreementRequest: AgreementQueryRequest = {
        question: savedInputValue,
        agreementType: fileParsingData.agreementType || "ISDA",
        counterparty: fileParsingData.counterparty || "",
        language,
      };
      response = await sendAgreementQuery(agreementRequest);
    } else {
      // 普通聊天
      const chatRequest: ChatRequest = {
        question: savedInputValue,
        scenario: currentScenario,
        language,
        conversationHistory: messages.map(msg => ({
          role: msg.type === 'user' ? 'user' : 'assistant',
          content: msg.content,
        })),
      };
      response = await sendMessage(chatRequest);
    }

    // 处理响应
    if (response.success) {
      const botMessage: Message = {
        id: `msg-${Date.now() + 1}`,
        type: "bot",
        content: response.answer,
        timestamp: new Date(),
        feedback: null,
        citations: response.citations,
      };
      setMessages(prev => [...prev, botMessage]);
    } else {
      // 错误处理
      const errorMessage: Message = {
        id: `msg-${Date.now() + 1}`,
        type: "bot",
        content: getTranslation(language, 'errorNoInformation'),
        timestamp: new Date(),
        feedback: null,
      };
      setMessages(prev => [...prev, errorMessage]);
    }
  } catch (error) {
    console.error('发送消息失败:', error);
    const errorMessage: Message = {
      id: `msg-${Date.now() + 1}`,
      type: "bot",
      content: getTranslation(language, 'errorNoInformation'),
      timestamp: new Date(),
      feedback: null,
    };
    setMessages(prev => [...prev, errorMessage]);
  } finally {
    setIsLoading(false);
  }
};
```

### 5. 协议助手：传递 agreementType 和 counterparty

当用户在协议助手场景中上传文件或选择参数后，更新 state：

```typescript
// 在文件解析完成后
const handleFileParseComplete = (parsedData: { agreementType: string; counterparty: string }) => {
  setFileParsingData({
    agreementType: parsedData.agreementType,  // 例如: "ISDA"
    counterparty: parsedData.counterparty,     // 例如: "HSBC"
  });
  
  // 这些参数会在 handleSend 中传递给 RAG API
};
```

### 6. 更新 `handleRegenerate` 函数

```typescript
const handleRegenerate = async (messageId: string) => {
  const messageIndex = messages.findIndex(msg => msg.id === messageId);
  if (messageIndex <= 0) return;
  
  const userMessage = messages[messageIndex - 1];
  if (userMessage.type !== "user") return;
  
  // 移除旧的 bot 消息
  setMessages(prev => prev.filter(msg => msg.id !== messageId));
  setIsLoading(true);
  
  try {
    // 重新发送请求
    const request: ChatRequest = {
      question: userMessage.content,
      scenario: currentScenario,
      language,
      conversationHistory: messages.slice(0, messageIndex).map(msg => ({
        role: msg.type === 'user' ? 'user' : 'assistant',
        content: msg.content,
      })),
    };

    const response = await sendMessage(request);

    if (response.success) {
      const botMessage: Message = {
        id: `msg-${Date.now() + 1}`,
        type: "bot",
        content: response.answer,
        timestamp: new Date(),
        feedback: null,
        citations: response.citations,
      };
      setMessages(prev => [...prev, botMessage]);
    } else {
      const errorMessage: Message = {
        id: `msg-${Date.now() + 1}`,
        type: "bot",
        content: getTranslation(language, 'errorNoInformation'),
        timestamp: new Date(),
        feedback: null,
      };
      setMessages(prev => [...prev, errorMessage]);
    }
  } catch (error) {
    console.error('重新生成失败:', error);
    const errorMessage: Message = {
      id: `msg-${Date.now() + 1}`,
      type: "bot",
      content: getTranslation(language, 'errorNoInformation'),
      timestamp: new Date(),
      feedback: null,
    };
    setMessages(prev => [...prev, errorMessage]);
  } finally {
    setIsLoading(false);
  }
};
```

## 🔑 关键要点

### 1. 三个主要接口位置

| 接口用途 | 函数名 | 说明 |
|---------|--------|------|
| **热门问题点击** | `handleQuestionClick` | 用户点击预设问题时调用 |
| **用户输入发送** | `handleSend` | 用户手动输入问题后点击发送 |
| **重新生成回答** | `handleRegenerate` | 用户对回答不满意，点击重新生成 |

### 2. 协议助手特殊处理

```typescript
if (currentScenario === "Agreement Assistant") {
  // 使用 sendAgreementQuery 并传递 agreementType 和 counterparty
  const request: AgreementQueryRequest = {
    question: question,
    agreementType: fileParsingData.agreementType,  // ← 关键参数
    counterparty: fileParsingData.counterparty,     // ← 关键参数
    language: language,
  };
  response = await sendAgreementQuery(request);
}
```

### 3. 错误处理

```typescript
if (!response.success) {
  // 方式1: 使用 API 返回的错误消息
  const errorMsg = response.error.message;
  
  // 方式2: 使用多语言错误提示
  const errorMsg = getTranslation(language, 'errorNoInformation');
  
  // 显示错误消息
  showErrorMessage(errorMsg);
}
```

### 4. Loading 状态

```typescript
// 组件已有的 isLoading state 用于 UI
setIsLoading(true);

// Hook 的 apiIsLoading 可用于额外的状态检查
if (apiIsLoading) {
  // API 正在请求中
}

// 请求完成后
setIsLoading(false);
```

## 📋 完整的迁移检查清单

- [ ] 引入 `useRagApi` hook
- [ ] 更新 `handleQuestionClick` 函数
- [ ] 更新 `handleSend` 函数
- [ ] 更新 `handleRegenerate` 函数
- [ ] 为协议助手添加特殊处理（agreementType, counterparty）
- [ ] 添加错误处理和用户提示
- [ ] 测试所有场景：
  - [ ] Account Opening
  - [ ] Agreement Assistant
  - [ ] Regulations & Compliance
- [ ] 测试错误场景（网络错误、超时等）
- [ ] 删除所有 mock 代码（setTimeout, 假数据等）
- [ ] 验证引用标记 `{cite:N}` 正确显示

## 🧪 测试建议

### 1. 本地测试（Mock 模式）
```bash
# .env
REACT_APP_USE_MOCK=true
```

### 2. 集成测试（真实 API）
```bash
# .env
REACT_APP_USE_MOCK=false
REACT_APP_RAG_API_URL=http://localhost:8000/api
```

### 3. 生产环境
```bash
# .env.production
REACT_APP_USE_MOCK=false
REACT_APP_RAG_API_URL=https://api.your-domain.com/api
```

## 💡 最佳实践

1. **错误日志**: 在 catch 块中添加详细日志
2. **用户反馈**: 使用 Toast 或 Alert 提示用户
3. **超时处理**: 设置合理的超时时间（建议 30 秒）
4. **重试机制**: 自动重试 2-3 次
5. **离线处理**: 检测网络状态，提供离线提示
