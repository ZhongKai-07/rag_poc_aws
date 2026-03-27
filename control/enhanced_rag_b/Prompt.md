# Enhanced RAG Batch B Prompt — Multi-turn Chat, Streaming, Feedback, UX

## Goal

在 `backend-java/` 中增强 RAG 管线的用户体验，添加六项能力：
1. **多轮对话** — 持久化Session + 滑动窗口(5轮) + LLM压缩摘要
2. **流式输出** — SSE (`SseEmitter` + Bedrock `converseStream()`)
3. **用户反馈** — 消息级 👍👎 + PostgreSQL持久化 + admin查看
4. **推荐追问** — 同Prompt一次生成2-3个追问建议
5. **答案置信度** — 基于rerank分数的HIGH/MEDIUM/LOW三档
6. **Citation展示优化** — 文件名PostgreSQL回退 + OCR噪声过滤 + 150字截断

## Non-Goals

- 不引入 Spring AI 框架（等AWS账号切换完成 + 2.0 GA）
- 不引入 Langfuse（等正式上线前）
- 不修复文档解析质量根因（Docling集成单独做）
- 不做前端完整重构（仅最小对接反馈按钮）
- 不实现多用户/租户隔离（当前无认证体系）
- 不实现 Agent / tool-use（Phase 2）

## Hard Constraints

### 架构边界

- 继续手写六边形架构，不引入新框架
- Domain层保持零外部依赖
- `HistoryCompressorPort`(domain) → `BedrockConversationMemoryAdapter`(infrastructure)
- `ConversationMemoryService`(application) 编排滑动窗口逻辑
- 流式和同步共享管线前半段，最后一步分叉

### API兼容性

- 现有 `POST /rag_answer` 保留，向后兼容
- 新增 `POST /rag_answer/stream`（SSE）
- 新增 Session/Feedback CRUD API
- `RagResponse` 新增字段全部为additive（`suggested_questions`、`confidence`、`history_compressed`、`session_id`）
- `RagRequest.session_id` 的 `@NotBlank` 注解必须移除，改为nullable处理

### 技术约束

- Java 17，Spring Boot 3.4
- SSE用 `SseEmitter`（Servlet架构，不引入WebFlux）
- 流式专用线程池（core=4, max=8, queue=50）
- 对话压缩超时复用现有3秒LLM超时机制
- 流式调用在首个token发出后不重试，通过error事件降级
- Flyway迁移版本号以实施时最高版本为准

## Deliverables

1. 对话管理：`chat_session` + `chat_message` 表 + Session CRUD API
2. 对话记忆：`ConversationMemoryService` + `HistoryCompressorPort` + 滑动窗口/压缩逻辑
3. 流式输出：`POST /rag_answer/stream` + `AnswerGenerationPort.generateAnswerStream()` + `converseStream()`
4. 用户反馈：`chat_feedback` 表 + 反馈API + admin查看/统计
5. 推荐追问：Prompt模板改造 + JSON输出解析 + `parseLlmOutput()`
6. 答案置信度：`ConfidenceLevel` 枚举 + rerank分数计算
7. Citation优化：文件名回退 + 噪声过滤 + 截断
8. 前端最小对接：👍👎按钮 + 反馈状态显示

## Done When

- [ ] 所有现有测试通过（86+ Batch A测试）
- [ ] 新增测试通过
- [ ] `POST /sessions` 创建对话，`GET /sessions` 列出历史
- [ ] `POST /rag_answer` 支持session_id关联对话历史
- [ ] 超过5轮对话自动压缩，响应包含 `history_compressed: true`
- [ ] `POST /rag_answer/stream` 流式返回token + done事件
- [ ] `POST .../feedback` 提交反馈，消息详情包含反馈状态
- [ ] 响应包含 `suggested_questions` 列表
- [ ] 响应包含 `confidence` 标签
- [ ] Citation显示正确文件名（含存量文档回退）
- [ ] Citation excerpt已过滤OCR噪声并截断
- [ ] `mvn test` 全量通过
- [ ] `spring-boot:run` 启动无报错，`/health` 200
