# RAG API 接口快速参考

## 📡 接口总览

| 接口名称 | 端点 | 方法 | 用途 |
|---------|------|------|------|
| 聊天问答 | `/api/chat` | POST | 普通场景的问答 |
| 协议助手 | `/api/agreement/query` | POST | 协议相关问答（需要 agreementType + counterparty） |
| 文件解析 | `/api/file/parse` | POST | 上传文件解析 |
| 健康检查 | `/api/health` | GET | 检查 API 状态 |

---

## 1️⃣ 聊天问答接口

### 请求

```http
POST /api/chat
Content-Type: application/json
```

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
      "content": "之前的回答"
    }
  ]
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|-----|------|------|------|
| `question` | string | ✅ | 用户的问题 |
| `scenario` | string | ❌ | 当前场景（Account Opening, Regulations & Compliance 等） |
| `language` | string | ❌ | 语言代码（zh-CN, zh-TW, en） |
| `conversationHistory` | array | ❌ | 对话历史，用于上下文理解 |

### 成功响应 (200 OK)

```json
{
  "answer": "开户需要以下材料：\n1. 身份证明文件{cite:1}\n2. 地址证明{cite:2}",
  "citations": [
    {
      "id": "cite-1",
      "source": "HK SFC | Regulatory Guidelines",
      "content": "根据香港证监会第123号规定...",
      "highlight": "所有持牌机构必须..."
    },
    {
      "id": "cite-2",
      "source": "HKEX | Trading Rules Manual",
      "content": "香港联合交易所有限公司...",
      "highlight": "交易所参与者必须确保..."
    }
  ],
  "metadata": {
    "modelUsed": "gpt-4",
    "processingTime": 1250,
    "confidence": 0.95
  }
}
```

### 错误响应 (4xx/5xx)

```json
{
  "error": {
    "code": "INSUFFICIENT_CONTEXT",
    "message": "无法根据当前上下文生成回答",
    "details": {
      "reason": "缺少必要的背景信息"
    }
  }
}
```

---

## 2️⃣ 协议助手接口

### 请求

```http
POST /api/agreement/query
Content-Type: application/json
```

```json
{
  "question": "What is the minimum transfer amount?",
  "agreementType": "ISDA",
  "counterparty": "HSBC",
  "language": "en"
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|-----|------|------|------|
| `question` | string | ✅ | 用户的问题 |
| `agreementType` | string | ✅ | 协议类型（ISDA, CSA 等） |
| `counterparty` | string | ✅ | 交易对手名称（HSBC, JPMorgan 等） |
| `language` | string | ❌ | 语言代码 |

### 🔑 关键点：锁定文件

后端应根据 `agreementType` 和 `counterparty` 参数：
1. 在 RAG 数据库中定位对应的协议文件
2. 仅在该文件范围内进行检索和问答
3. 返回的引用（citations）应来自该特定文件

**示例场景**：
- `agreementType: "ISDA"` + `counterparty: "HSBC"` 
- → 后端锁定文件：`ISDA_Huatai_HSBC_2023.pdf`
- → 所有回答和引用仅来自这份协议

### 响应格式

与聊天接口相同，返回 `answer` + `citations`。

---

## 3️⃣ 文件解析接口

### 请求

```http
POST /api/file/parse
Content-Type: multipart/form-data
```

```
--boundary
Content-Disposition: form-data; name="file"; filename="kyc_doc.pdf"
Content-Type: application/pdf

[binary file data]
--boundary
Content-Disposition: form-data; name="fileType"

pdf
--boundary--
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|-----|------|------|------|
| `file` | File | ✅ | 上传的文件对象 |
| `fileType` | string | ✅ | 文件类型（pdf, jpg, png） |

### 成功响应 (200 OK)

```json
{
  "parsedData": {
    "clientName": "张三",
    "idNumber": "123456789",
    "address": "香港中环...",
    "phone": "+852 1234 5678",
    "email": "example@email.com",
    "accountType": "个人账户",
    "riskLevel": "中风险"
  }
}
```

### 错误响应

```json
{
  "error": "Unsupported file format or parsing failed"
}
```

---

## 4️⃣ 健康检查接口

### 请求

```http
GET /api/health
```

### 成功响应 (200 OK)

```json
{
  "status": "healthy",
  "timestamp": "2024-01-01T00:00:00Z",
  "version": "1.0.0"
}
```

---

## 🎯 引用标记格式

### 后端返回格式

在 `answer` 字段中使用 `{cite:N}` 标记引用位置：

```json
{
  "answer": "根据规定，需要提交身份证明{cite:1}和地址证明{cite:2}。详细要求请参考监管文件{cite:1}。",
  "citations": [
    {
      "id": "cite-1",
      "source": "HK SFC | Guidelines",
      "content": "...",
      "highlight": "..."
    },
    {
      "id": "cite-2",
      "source": "HKEX | Rules",
      "content": "...",
      "highlight": "..."
    }
  ]
}
```

### 前端解析规则

1. 找到所有 `{cite:N}` 标记
2. 用可点击的上标按钮替换
3. 点击按钮显示对应 citation 的详细内容

**示例渲染结果**：
```
根据规定，需要提交身份证明[1]和地址证明[2]。详细要求请参考监管文件[1]。
                              ↑点击显示 citation 详情
```

---

## 🚨 错误代码表

| 错误代码 | HTTP 状态码 | 说明 | 建议处理 |
|---------|-----------|------|---------|
| `NETWORK_ERROR` | - | 网络连接失败 | 提示用户检查网络 |
| `HTTP_400` | 400 | 请求参数错误 | 检查请求参数 |
| `HTTP_401` | 401 | 未授权 | 重新登录 |
| `HTTP_404` | 404 | 接口不存在 | 检查 API 端点 |
| `HTTP_500` | 500 | 服务器错误 | 稍后重试 |
| `TIMEOUT` | - | 请求超时 | 显示超时提示 |
| `INSUFFICIENT_CONTEXT` | 200 | 上下文不足 | 提示用户提供更多信息 |
| `MODEL_ERROR` | 200 | 模型生成失败 | 显示通用错误提示 |

---

## 📋 前端调用示例

### 示例 1: 普通聊天

```typescript
import { sendChatMessage } from '../services/ragApi';

const response = await sendChatMessage({
  question: "开户需要什么材料？",
  scenario: "Account Opening",
  language: "zh-CN"
});

if (response.success) {
  console.log('回答:', response.answer);
  console.log('引用:', response.citations);
}
```

### 示例 2: 协议助手

```typescript
import { queryAgreement } from '../services/ragApi';

const response = await queryAgreement({
  question: "What is the minimum transfer amount?",
  agreementType: "ISDA",      // ← 锁定协议类型
  counterparty: "HSBC",        // ← 锁定交易对手
  language: "en"
});
```

### 示例 3: 使用 Hook

```typescript
import { useRagApi } from '../hooks/useRagApi';

function MyComponent() {
  const { sendMessage, sendAgreementQuery } = useRagApi({
    language: 'zh-CN'
  });

  // 普通聊天
  const response1 = await sendMessage({
    question: "...",
    scenario: "..."
  });

  // 协议助手
  const response2 = await sendAgreementQuery({
    question: "...",
    agreementType: "ISDA",
    counterparty: "HSBC"
  });
}
```

---

## 🔐 认证（如需要）

如果 API 需要认证，在请求头中添加：

```typescript
headers: {
  'Authorization': `Bearer ${accessToken}`,
  'Content-Type': 'application/json'
}
```

在 `config.ts` 中已提供 `getHeaders()` 辅助函数。

---

## 📊 性能建议

| 指标 | 建议值 |
|-----|-------|
| API 响应时间 | < 3 秒 |
| 超时设置 | 30 秒 |
| 重试次数 | 2 次 |
| 并发请求限制 | < 5 |

---

## 📞 技术支持

如有问题，请查看：
- `README.md` - 集成指南
- `INTEGRATION_EXAMPLE.md` - 代码示例
- 或联系后端团队
