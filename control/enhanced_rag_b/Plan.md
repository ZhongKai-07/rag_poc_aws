# Enhanced RAG Batch B Plan

## 详细实施计划

见 `docs/superpowers/plans/2026-03-28-enhanced-rag-batch-b-plan.md`（19个Task，6个Chunk）。

## 里程碑

### Milestone 1: Database + Domain（对话/反馈数据模型）

- [x] Flyway迁移：`chat_session`、`chat_message`、`chat_feedback`
- [x] Domain值对象：`ChatSession`、`ChatMessage`、`ChatFeedback`、`ConversationContext`、`ConfidenceLevel`
- [x] Domain Ports：`ChatSessionPort`、`ChatMessagePort`、`ChatFeedbackPort`、`HistoryCompressorPort`
- [x] JPA Entity + Repository + Persistence Adapter

**验证:** `mvn test` — 98 tests passing

---

### Milestone 2: Session管理 + 对话记忆

- [x] `ChatSessionApplicationService` — Session CRUD
- [x] `ConversationMemoryService` — 滑动窗口 + 压缩编排
- [x] `BedrockConversationMemoryAdapter` — LLM压缩实现
- [x] `ChatSessionController` — REST API
- [x] 管线集成：`RagQueryApplicationService` 注入对话历史

**验证:** `mvn test` — 109 tests passing

---

### Milestone 3: 流式输出

- [x] `AnswerGenerationPort.generateAnswerStream()` 接口
- [x] `BedrockAnswerGenerationAdapter` 同步分块回退（async client升级延后）
- [x] `POST /rag_answer/stream` SSE端点
- [x] 流式专用线程池配置（StreamingConfig）

**验证:** `mvn test` — 109 tests passing

---

### Milestone 4: 推荐追问 + 置信度 + Citation优化

- [x] Prompt模板改造（JSON输出 + 追问指令）
- [x] `CitationAssemblyService.parseLlmOutput()` JSON解析
- [x] `CitedAnswer` 扩展 `suggestedQuestions`
- [x] `ConfidenceLevel` 计算
- [x] Citation文件名PostgreSQL回退
- [x] Citation OCR噪声过滤 + 截断

**验证:** `mvn test` — 115 tests passing

---

### Milestone 5: 用户反馈 + 管线集成

- [x] `ChatFeedbackPort` 实现
- [x] 反馈API：提交、列表、统计
- [x] 消息详情包含反馈状态
- [x] RagRequest @NotBlank 移除，sessionId 可空
- [x] RagResponse 扩展 4 个新字段
- [x] QueryResult 扩展到 9 字段
- [x] handle() 集成对话记忆 + 置信度 + 推荐追问 + 消息持久化
- [ ] 前端最小对接（👍👎按钮）— 延后

**验证:** `mvn test` — 115 tests passing

---

### Milestone 6: Final Verification + Cleanup

- [x] 全量测试通过（115 tests）
- [ ] `spring-boot:run` + `/health` 正常（需 AWS 凭证）
- [x] 更新 CLAUDE.md + control文档

## 决策备注

| 决策 | 结论 | 日期 |
|------|------|------|
| Spring AI | 不引入，等AWS切换 + 2.0 GA | 2026-03-28 |
| Langfuse | 不引入，等上线前 | 2026-03-28 |
| 对话记忆 | 滑动窗口5轮 + LLM压缩 | 2026-03-28 |
| 对话存储 | PostgreSQL持久化 | 2026-03-28 |
| 流式技术 | SSE (SseEmitter + 同步分块回退) | 2026-03-28 |
| 推荐追问 | 同Prompt一次生成JSON | 2026-03-28 |
| 置信度 | rerank分数三档 | 2026-03-28 |
| Citation优化 | PG回退 + 噪声过滤 + 截断 | 2026-03-28 |
| 流式真正streaming | 延后到引入 BedrockRuntimeAsyncClient 时 | 2026-03-28 |
