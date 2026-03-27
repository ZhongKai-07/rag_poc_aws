# Enhanced RAG Batch A Plan

## 详细实施计划

见 `docs/superpowers/plans/2026-03-26-enhanced-rag-batch-a-plan.md`（20 个 Task，6 个 Chunk）。

## 里程碑

### Milestone 1: Foundation（Task 1-4）

**范围:** 域值对象 + 配置 + RetrievalPort 重构

**交付物:**
- [ ] `RewriteResult`, `StructuredQuery`, `QueryRewriteStrategy` 域对象
- [ ] `Citation`, `CitedAnswer` 域对象
- [ ] `RetrievalRequest` 值对象 + `RetrievalPort` 单参数签名
- [ ] `RagProperties` 新增 feature flags + rewrite model ID
- [ ] `application.yml` 新增配置项

**验证:** `mvn test` — 所有现有测试 + 新增值对象测试通过

**停止修复规则:** 如果 `RetrievalPort` 签名变更导致现有测试编译失败，必须在本里程碑内修复所有调用方。

---

### Milestone 2: Query Rewriting（Task 5-8）

**范围:** 路由器 + 双路径策略 + OpenSearch metadata filter

**交付物:**
- [ ] `QueryRewriteRouter`（application 层）
- [ ] `CobKeywordRewriteStrategy`（infrastructure 层）
- [ ] `CollateralStructuredRewriteStrategy`（infrastructure 层）
- [ ] `OpenSearchQueryBuilder` + metadata filter 查询
- [ ] `OpenSearchIndexManager` mapping 更新（`keyword` 类型）

**验证:** `mvn test` — Router 路由正确、策略解析 JSON 正确、降级正确、filter 查询构建正确

**停止修复规则:** 如果 LLM 调用 mock 测试失败（JSON 解析、超时降级），必须在本里程碑内修复。

---

### Milestone 3: Answer Citation（Task 9-12）

**范围:** Citation 组装 + Port 签名变更 + DTO + 文件名元数据注入

**交付物:**
- [ ] `CitationAssemblyService` + `PromptWithCitations`
- [ ] `AnswerGenerationPort` 签名变更 `(String, String)`
- [ ] `BedrockAnswerGenerationAdapter` 简化
- [ ] `CitationDto` + `RagResponse.citations` 字段
- [ ] `OpenSearchDocumentWriter` 注入 filename 到 metadata

**验证:** `mvn test` — Citation 编号正确、正则提取 `[n]` 正确、null filename 降级正确

**停止修复规则:** 如果 `AnswerGenerationPort` 签名变更破坏现有测试，必须在本里程碑内修复所有 fake/mock。

---

### Milestone 4: Pipeline Integration（Task 13-14）

**范围:** 管线串联 + 端到端验证

**交付物:**
- [ ] `RagQueryApplicationService.Default.handle()` 集成 query rewrite + citation
- [ ] `QueryResult` 新增 `citations` 字段
- [ ] `ApplicationWiringConfig` 装配新 bean
- [ ] `RagController` 映射 citations 到 DTO

**验证:**
1. `mvn test` — 全量通过
2. `mvn spring-boot:run` — 启动无报错，`/health` 200

**停止修复规则:** 如果集成后有任何测试回归，必须在本里程碑内修复后才能继续。

---

### Milestone 5: Offline Evaluation（Task 15-18）

**范围:** 评估数据模型 + Runner + RAGAS sidecar + 测试集

**交付物:**
- [ ] `evaluation/` 包下所有模型和服务类
- [ ] `RagasClient` HTTP 客户端
- [ ] `ragas-evaluator/` Python 项目（app.py + Dockerfile + docker-compose.yml）
- [ ] 测试集骨架 JSON 文件
- [ ] `RagEvaluationTest` 评估测试类

**验证:**
1. `mvn test` — 评估相关单测通过
2. `docker-compose up -d && curl http://localhost:8002/health` — RAGAS 服务就绪

---

### Milestone 6: Final Verification + Cleanup（Task 19-20）

**范围:** 全量验证 + 控制文档更新

**验证:**
1. `mvn test` — 全部通过（57-62 个测试）
2. `mvn spring-boot:run` + `curl /health` — 正常
3. RAGAS sidecar 正常
4. 控制文档同步更新

## 决策备注

| 决策 | 结论 | 日期 |
|------|------|------|
| AI 框架选择 | Batch A 手写，Batch B 引入 Spring AI | 2026-03-26 |
| 评估框架 | RAGAS (Python sidecar) + Bedrock Evaluations | 2026-03-26 |
| Query 改写模式 | Strategy + Registry，COB/Collateral 双路径 | 2026-03-26 |
| Citation 方式 | Prompt 注入 `[n]` 标记 + 响应解析 | 2026-03-26 |
| 测试集来源 | 业务核心 case + RAGAS 自动生成 + 人工审核 | 2026-03-26 |

## 目标架构

```
RagRequest → QueryRewriteRouter (Strategy) → RetrievalPort (RetrievalRequest) →
  Rerank → CitationAssemblyService → AnswerGenerationPort → CitationParser → RagResponse + citations
```

评估模块独立于主管线，通过调用 `RagQueryApplicationService` 收集 trace 数据。
