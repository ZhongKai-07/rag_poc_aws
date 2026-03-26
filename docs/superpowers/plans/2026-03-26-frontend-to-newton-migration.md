# Frontend Migration Plan: frontend/ → NEWTON Design

**Date:** 2026-03-26
**Status:** Planning (未开始实施)
**Supersedes:** `2026-03-23-newton-ui-refactor.md` (仅针对 NEWTON 内部重构，本文档覆盖完整迁移)

---

## Context

产品部门提供了 `NEWTON/` 文件夹下的前端交互演示页面，展示了知识库问答系统的目标 UI 效果——以聊天界面为核心的单页应用，取代现有的多页面导航式 `frontend/`。现有 `frontend/` 需要迁移到 NEWTON 的设计风格和交互模式，同时保持与 Spring Boot 后端 (`backend-java/`) 的 API 对接不变。

---

## 两个前端对比

| 维度 | 现有 frontend/ | NEWTON/ (目标) |
|------|---------------|----------------|
| UI 模式 | 多页面导航 (Home/Upload/QA/Admin) | 聊天式单页应用 (浮动按钮 → 全屏聊天) |
| 路由 | React Router v6 (`/`, `/upload`, `/qa`, `/admin`) | 无路由，状态驱动 UI 切换 |
| 样式 | Tailwind v3 + Shadcn/ui | Tailwind v4 + Radix UI + Emotion + Framer Motion |
| 构建 | Vite 5 + SWC + PostCSS | Vite 6 + Babel + @tailwindcss/vite 插件 |
| 状态管理 | TanStack React Query | React useState (无外部库) |
| 特色功能 | Admin 面板 (BDA 观测) | 场景选择、引用系统、i18n、历史面板 |
| API 调用 | 相同 4 个核心端点 + 3 个 admin 端点 | 相同 4 个核心端点 + health |

---

## API 兼容性分析

### 已有端点（两端完全一致，无需后端改动）

| 端点 | 方法 | 用途 | 请求格式 | 响应格式 |
|------|------|------|----------|----------|
| `/processed_files` | GET | 获取已处理文件列表 | — | `{ status, files: [{filename, index_name}] }` |
| `/upload_files` | POST | 上传文件 | FormData: `file` + `directory_path` | HTTP status |
| `/rag_answer` | POST | RAG 问答 | `{ session_id, index_names, query, module, vec_docs_num, txt_docs_num, vec_score_threshold, text_score_threshold, rerank_score_threshold, search_method }` | `{ answer, source_documents, recall_documents, rerank_documents }` |
| `/top_questions_multi` | GET | 获取热门问题 | `?index_names=idx1,idx2` | `{ status, questions: [{question, count}] }` |
| `/health` | GET | 健康检查 | — | boolean |

请求/响应格式完全相同，NEWTON 的 `ragApi.ts` 已正确对接这些端点。

### 需要保留的 Admin 端点（后端已实现）

| 端点 | 方法 | 用途 |
|------|------|------|
| `/admin/parse_results` | GET | BDA 解析结果列表 |
| `/admin/parse_results/{indexName}/raw` | GET | 原始 BDA JSON |
| `/admin/parse_results/{indexName}/chunks` | GET | 索引分块数据 |

### 需要后端新增的接口：聊天历史 API

| 端点 | 方法 | 用途 | 请求/响应 |
|------|------|------|-----------|
| `/chat/sessions` | GET | 获取会话列表 | → `{ sessions: [{ session_id, title, created_at, updated_at }] }` |
| `/chat/sessions/{sessionId}` | GET | 获取单个会话消息 | → `{ session_id, messages: [{ role, content, timestamp, citations? }] }` |
| `/chat/sessions` | POST | 创建新会话 | `{ title }` → `{ session_id, title, created_at }` |
| `/chat/sessions/{sessionId}/messages` | POST | 保存消息 | `{ role, content, citations? }` → `{ message_id, timestamp }` |
| `/chat/sessions/{sessionId}` | DELETE | 删除会话 | → `204 No Content` |

存储：PostgreSQL（已有连接），新建 `chat_sessions` + `chat_messages` 表。

### NEWTON 中的 Mock/占位功能（保留 UI，暂不对接后端）

| 功能 | 端点 | 状态 | 处理方式 |
|------|------|------|----------|
| 协议助手 (ISDA/GMRA) | `POST /agreement/query` | Mock | 保留 UI 占位，后续对接 |
| 场景选择 (开户/合规/SOP) | — | Mock 热门问题 | 保留 UI，后续可调整 |
| 文件解析 | `POST /file/parse` | Mock | 不使用，上传走 `/upload_files` |

### 未来可选（当前不需要）
- `POST /agreement/query` — 协议助手真实后端
- `POST /feedback` — 点赞/点踩持久化

---

## 已确认决策

1. **Admin 面板：** 保留为独立路由 `/admin`，App.tsx 使用最小路由（`/` → 聊天界面，`/admin` → Admin 面板）
2. **聊天历史：** 需要后端 API 支持，新增 chat sessions/messages 端点
3. **Mock 功能：** 保留 UI 占位（协议助手、场景选择、热门问题照常展示），后续对接真实后端
4. **场景名称：** 保持 NEWTON 原有场景（开户/合规/SOP），后续可根据业务调整

---

## 迁移步骤

### Phase 1: 项目基础迁移（构建系统 + 依赖）

**目标：** 将 frontend/ 的构建系统从 Tailwind v3 + Vite 5 升级到 Tailwind v4 + Vite 6，匹配 NEWTON 的技术栈。

1. **备份** — `git branch frontend-backup` 创建备份分支

2. **替换 package.json**
   - 合并 NEWTON 的依赖到 `frontend/package.json`
   - 添加 `react`, `react-dom` 为直接依赖（NEWTON 仅列为 peerDependencies）
   - 保留 TypeScript, ESLint, Vitest 等开发依赖
   - 新增: `@emotion/react`, `@emotion/styled`, `motion`, `react-markdown`, `react-slick`, `@mui/icons-material`
   - 替换: `@vitejs/plugin-react-swc` → `@vitejs/plugin-react`
   - 升级: `tailwindcss` 3→4, `vite` 5→6, 新增 `@tailwindcss/vite` 插件

3. **替换 vite.config.ts**
   - 使用 `@tailwindcss/vite` 插件替代 PostCSS + autoprefixer
   - 保持 dev server port 8080, host `::`
   - 保持 `@` 路径别名

4. **删除废弃配置文件**
   - `postcss.config.js` (Tailwind v4 不需要)
   - `tailwind.config.ts` (Tailwind v4 用 CSS 配置)
   - `components.json` (Shadcn/ui 配置)

5. **验证** — `npm install` + `npm run build`

**关键文件：** `frontend/package.json`, `frontend/vite.config.ts`, `NEWTON/package.json` (参考), `NEWTON/vite.config.ts` (参考)

### Phase 2: 复制 NEWTON 核心代码

**目标：** 将 NEWTON 的组件、服务、样式复制到 frontend/src/。

1. **复制目录结构**
   - `NEWTON/src/app/` → `frontend/src/app/`
     - `components/` — MaximizedChat, FileParseModal, FloatingButton, HistoryPanel, HotQuestions, ScenarioButtonsWeb, CitationModal, MessageWithCitations, AgreementAssistantModal, DownloadModal, Tooltip, figma/, ui/
     - `hooks/` — useRagApi.ts
     - `services/` — ragApi.ts, types.ts
     - `i18n/` — translations.ts
   - `NEWTON/src/styles/` → `frontend/src/styles/`
   - `NEWTON/src/assets/` → `frontend/src/assets/` (合并)

2. **替换入口文件**
   - `frontend/src/App.tsx` ← 基于 NEWTON 的 App.tsx 重写（添加最小路由支持 `/admin`）
   - `frontend/src/main.tsx` ← 适配 NEWTON 入口
   - `frontend/src/index.css` ← 使用 NEWTON 的全局样式

3. **保留 Admin 功能**
   - 将 `frontend/src/pages/AdminPage.tsx` → `frontend/src/app/components/AdminPanel.tsx`
   - 将 `frontend/src/api/adminApi.ts` → `frontend/src/app/services/adminApi.ts`
   - 在 App.tsx 路由中添加 `/admin` → AdminPanel

**关键文件：** `NEWTON/src/app/` (所有组件源), `frontend/src/App.tsx`, `frontend/src/pages/AdminPage.tsx`, `frontend/src/api/adminApi.ts`

### Phase 3: 清理旧代码

**目标：** 移除不再需要的旧前端代码。

**删除：**
- `frontend/src/pages/Index.tsx` — 被聊天界面替代
- `frontend/src/pages/Upload.tsx` — 被 FileParseModal 替代
- `frontend/src/pages/QA.tsx` — 被 MaximizedChat 替代
- `frontend/src/pages/NotFound.tsx` — 无路由不需要
- `frontend/src/components/Layout.tsx` — 导航栏不需要
- `frontend/src/components/NavLink.tsx`
- `frontend/src/lib/api.ts` — 旧 email 分析 API（已废弃）
- `frontend/src/hooks/use-toast.ts` — 被 sonner 替代
- `frontend/src/hooks/use-mobile.tsx`
- 旧的 `frontend/src/components/ui/` — 被 NEWTON 的 ui/ 替代

### Phase 4: 适配与集成

**目标：** 确保 NEWTON 代码在 frontend/ 环境下正确运行。

1. **修复导入路径**
   - NEWTON 中的 `@/` 别名需与 frontend 的 tsconfig 路径映射一致
   - 确保 `@/app/services/ragApi` 等路径正确

2. **环境变量**
   - 确认 `frontend/.env` 中 `VITE_API_BASE_URL=http://localhost:8001`
   - 删除 NEWTON 的 `config.ts`（使用 `process.env`，与 Vite 不兼容），ragApi.ts 已正确使用 `import.meta.env`

3. **Admin 面板路由集成**
   - App.tsx 保留最小路由：`/` 渲染聊天界面，`/admin` 渲染 AdminPanel
   - 开发环境可直接访问 `/admin`

4. **聊天历史持久化（前端 + 后端）**
   - **后端新增：**
     - `domain/model/ChatSession.java`, `domain/model/ChatMessage.java`
     - `domain/port/ChatHistoryRepository.java`
     - `infrastructure/persistence/ChatSessionEntity.java`, `ChatMessageEntity.java`
     - `infrastructure/persistence/JpaChatHistoryRepository.java`
     - `api/ChatHistoryController.java` — REST 端点
   - **前端对接：**
     - `ragApi.ts` 新增 `fetchChatSessions()`, `fetchSessionMessages()`, `createSession()`, `saveMessage()`, `deleteSession()`
     - MaximizedChat 在发送/接收消息时调用 `saveMessage()`
     - HistoryPanel 加载时调用 `fetchChatSessions()`

5. **TypeScript 适配**
   - NEWTON 的类型定义在 `services/types.ts`，确保与后端响应一致
   - 补充 AdminPanel 相关类型

### Phase 5: 构建验证

1. `npm run build` — 零 TypeScript 错误，零构建警告
2. `npm run dev` — 启动 dev server on port 8080
3. 启动后端 `mvn spring-boot:run` on port 8001
4. **手动测试清单：**
   - [ ] 聊天界面加载，场景选择正常
   - [ ] 文件上传 → 解析 → 出现在已处理列表
   - [ ] 选择文档 → 热门问题加载
   - [ ] 提问 → 收到 RAG 回答 + 引用
   - [ ] 语言切换 (zh-CN/zh-TW/en)
   - [ ] Admin 面板可通过 `/admin` 访问
   - [ ] 聊天历史保存/加载/删除
   - [ ] 浮动按钮模式切换

---

## NEWTON 组件架构（参考）

```
App.tsx (root, 最小路由)
├── / → ChatApp
│   ├── FloatingButton.tsx (可拖拽, 贴底居中)
│   ├── FileParseModal.tsx (文件上传弹窗)
│   └── MaximizedChat.tsx (主聊天界面)
│       ├── ScenarioButtonsWeb.tsx (场景轮播)
│       ├── HotQuestions.tsx (热门问题)
│       ├── HistoryPanel.tsx (侧边栏历史)
│       ├── MessageWithCitations.tsx
│       │   └── CitationModal.tsx (引用详情)
│       ├── AgreementAssistantModal.tsx (协议助手, Mock)
│       └── DownloadModal.tsx
└── /admin → AdminPanel.tsx (BDA 观测面板)
```

## NEWTON API 服务层

**文件：** `NEWTON/src/app/services/ragApi.ts`

已实现的真实 API 调用（可直接复用）：
- `fetchProcessedFiles()` → `GET /processed_files`
- `uploadRealFiles(file, path)` → `POST /upload_files`
- `askRealQuestion(request)` → `POST /rag_answer`
- `fetchTopQuestionsMulti(indexNames)` → `GET /top_questions_multi`
- `checkApiHealth()` → `GET /health`

需要新增：
- `fetchChatSessions()` → `GET /chat/sessions`
- `fetchSessionMessages(sessionId)` → `GET /chat/sessions/{sessionId}`
- `createSession(title)` → `POST /chat/sessions`
- `saveMessage(sessionId, message)` → `POST /chat/sessions/{sessionId}/messages`
- `deleteSession(sessionId)` → `DELETE /chat/sessions/{sessionId}`
