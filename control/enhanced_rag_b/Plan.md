# Enhanced RAG Batch B Plan

## 详细实施计划

见 `docs/superpowers/plans/2026-03-28-enhanced-rag-batch-b-plan.md`（待生成）。

## 里程碑

### Milestone 1: Database + Domain（对话/反馈数据模型）

- [ ] Flyway迁移：`chat_session`、`chat_message`、`chat_feedback`
- [ ] Domain值对象：`ChatSession`、`ChatMessage`、`ChatFeedback`、`ConversationContext`、`ConfidenceLevel`
- [ ] Domain Ports：`ChatSessionPort`、`ChatMessagePort`、`ChatFeedbackPort`、`HistoryCompressorPort`
- [ ] JPA Entity + Repository + Persistence Adapter

**验证:** `mvn test`

---

### Milestone 2: Session管理 + 对话记忆

- [ ] `ChatSessionApplicationService` — Session CRUD
- [ ] `ConversationMemoryService` — 滑动窗口 + 压缩编排
- [ ] `BedrockConversationMemoryAdapter` — LLM压缩实现
- [ ] `ChatSessionController` — REST API
- [ ] 管线集成：`RagQueryApplicationService` 注入对话历史

**验证:** `mvn test` + 手动测试session创建和历史加载

---

### Milestone 3: 流式输出

- [ ] `AnswerGenerationPort.generateAnswerStream()` 接口
- [ ] `BedrockAnswerGenerationAdapter` converseStream实现
- [ ] `POST /rag_answer/stream` SSE端点
- [ ] 流式专用线程池配置

**验证:** `mvn test` + curl测试SSE端点

---

### Milestone 4: 推荐追问 + 置信度 + Citation优化

- [ ] Prompt模板改造（JSON输出 + 追问指令）
- [ ] `CitationAssemblyService.parseLlmOutput()` JSON解析
- [ ] `CitedAnswer` 扩展 `suggestedQuestions`
- [ ] `ConfidenceLevel` 计算
- [ ] Citation文件名PostgreSQL回退
- [ ] Citation OCR噪声过滤 + 截断

**验证:** `mvn test`

---

### Milestone 5: 用户反馈

- [ ] `ChatFeedbackPort` 实现
- [ ] 反馈API：提交、列表、统计
- [ ] 消息详情包含反馈状态
- [ ] 前端最小对接（👍👎按钮）

**验证:** `mvn test` + 前端手动测试

---

### Milestone 6: Final Verification + Cleanup

- [ ] 全量测试通过
- [ ] `spring-boot:run` + `/health` 正常
- [ ] 更新 CLAUDE.md + control文档

## 决策备注

| 决策 | 结论 | 日期 |
|------|------|------|
| Spring AI | 不引入，等AWS切换 + 2.0 GA | 2026-03-28 |
| Langfuse | 不引入，等上线前 | 2026-03-28 |
| 对话记忆 | 滑动窗口5轮 + LLM压缩 | 2026-03-28 |
| 对话存储 | PostgreSQL持久化 | 2026-03-28 |
| 流式技术 | SSE (SseEmitter + converseStream) | 2026-03-28 |
| 推荐追问 | 同Prompt一次生成JSON | 2026-03-28 |
| 置信度 | rerank分数三档 | 2026-03-28 |
| Citation优化 | PG回退 + 噪声过滤 + 截断 | 2026-03-28 |
