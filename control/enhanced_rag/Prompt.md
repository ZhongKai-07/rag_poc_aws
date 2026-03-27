# Enhanced RAG Batch A Prompt — Query Rewriting, Citation, Offline Evaluation

## Goal

在 `backend-java/` 中增强现有 RAG 管线，添加三项核心能力：
1. **Query 改写** — 双路径策略（COB 关键词提取 + Collateral 结构化三元组解析），提升检索质量
2. **答案溯源** — Prompt 注入引用标记 `[n]`，返回文档名+页码+引用片段
3. **离线评估** — RAGAS Python sidecar + Bedrock Evaluations，标准化度量 RAG 效果

## Non-Goals

- 不实现多轮对话（Batch B）
- 不实现流式输出 SSE（Batch B）
- 不实现用户反馈机制（Batch B）
- 不引入 Langfuse / 在线可观测性（Batch B）
- 不引入 Spring AI 框架（Batch B）
- 不实现 Agent / tool-use 能力（Phase 2）
- 不修改前端代码（前端消费 `citations` 字段留到后续）
- 不删除或破坏 Python 后端 `api/`
- 不改变现有 PostgreSQL schema

## Hard Constraints

### 架构边界

- 遵循现有六边形架构（domain/application/infrastructure 分层）
- Domain 层保持零外部依赖 — 新增的 `QueryRewriteStrategy`、`RewriteResult`、`Citation` 等均为纯接口/值对象
- Query 改写策略实现放在 `infrastructure/bedrock/`，直接使用 `BedrockRuntimeClient`
- 评估模块作为独立顶层包 `evaluation/`，不侵入主管线

### API 兼容性

- 现有 10 个 REST 端点签名不变
- `RagResponse` 新增 `citations` 字段，`source_documents` 保留向后兼容
- `RagRequest.module` 字段已存在，默认 `"RAG"` 映射到 COB 策略（前端无需变更）

### 技术约束

- Java 17，Spring Boot 3.4
- Query 改写使用轻量模型（Nova Lite），通过 `RAG_REWRITE_MODEL_ID` 配置
- 改写超时硬限 3 秒，失败降级到原始 query（`CompletableFuture.orTimeout()`）
- OpenSearch metadata 字段（`counterparty`、`agreement_type`）必须定义为 `keyword` 类型
- RAGAS sidecar 运行在端口 8002，AWS 凭证从宿主环境透传

### Feature Flags

- `rag.query-rewrite.enabled=true` — Query 改写开关
- `rag.citation.enabled=true` — Citation 开关
- 任一功能关闭时，管线降级到 Batch A 前的行为

## Deliverables

1. Query 改写模块：`QueryRewriteStrategy` 接口 + `QueryRewriteRouter` + COB/Collateral 两个策略实现
2. Citation 模块：`CitationAssemblyService` + `PromptWithCitations` + `CitationDto`
3. `RetrievalPort` 重构为 `RetrievalRequest` 值对象，支持 metadata filter
4. OpenSearch metadata filter 查询（`bool` + `knn` + `filter`）
5. `AnswerGenerationPort` 签名简化为 `(String, String)`
6. 评估模块：`EvaluationRunner` + `RagasClient` + 数据模型
7. RAGAS Python sidecar：`ragas-evaluator/`（FastAPI + Docker）
8. 测试集骨架 + 评估测试类
9. ~15-20 个新测试方法

## Done When

- [ ] 所有现有测试通过（42+ 原有测试）
- [ ] 新增测试通过（~15-20 个）
- [ ] `POST /rag_answer` 返回 `citations` 字段（当 `rag.citation.enabled=true`）
- [ ] COB 场景 query 改写：提取业务关键词 + 改写检索 query
- [ ] Collateral 场景 query 改写：解析 `[counterparty]+[agreement_type]+[business_field]` 三元组
- [ ] 改写失败时优雅降级到原始 query，不阻塞管线
- [ ] Feature flags 可独立关闭 Query 改写和 Citation
- [ ] RAGAS sidecar 启动并响应 `/health`
- [ ] `EvaluationRunner` 可跑测试集并输出 trace 数据
- [ ] `mvn test` 全量通过
- [ ] `spring-boot:run` 启动无报错，`/health` 返回 200
