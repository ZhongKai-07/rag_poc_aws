# Enhanced RAG Batch A — 开发日志（2026-03-27）

## 概述

本次会话完成了 Enhanced RAG Batch A 的全量实施（6 个里程碑、20 个 Task），以及后续的管线优化和前端场景选择器。共 32 个 commits，87 个测试全部通过。

分支：`feature/enhanced-rag-batch-a`

---

## 一、Batch A 全量实施

### Milestone 1: Foundation（Task 1-4）

| Commit | 内容 |
|--------|------|
| `02b198db` | 添加 `RewriteResult`, `StructuredQuery`, `QueryRewriteStrategy` 域值对象 + 测试 |
| `e7b3ba4b` | 添加 `Citation`, `CitedAnswer` 域值对象 + 测试 |
| `9306e4be` | `RetrievalPort` 7-param 签名重构为 `RetrievalRequest` 值对象，更新所有调用方（4 个测试文件） |
| `1b73e736` | 添加 `queryRewriteEnabled`, `citationEnabled`, `rewriteModelId` 到 `RagProperties` + `application.yml` |
| `b20dd418` | 里程碑文档更新 — 64 tests pass |

### Milestone 2: Query Rewriting（Task 5-8）

| Commit | 内容 |
|--------|------|
| `9817ed72` | `QueryRewriteRouter`：Strategy 模式路由 + feature flag 控制 + 异常降级 |
| `53415e38` | `CobKeywordRewriteStrategy`：Bedrock Converse API 调用 + JSON 解析 + 3s 超时 |
| `efdbd9d9` | `CollateralStructuredRewriteStrategy`：三元组解析（counterparty/agreementType/businessField） |
| `06c987c5` | `OpenSearchQueryBuilder` + metadata filter（bool+knn+filter）+ `OpenSearchIndexManager` keyword 类型映射 |
| `77d51379` | 里程碑文档更新 — 77 tests pass |

### Milestone 3: Answer Citation（Task 9-12）

| Commit | 内容 |
|--------|------|
| `36f68400` | `CitationAssemblyService`：编号引用组装 + `[n]` 正则提取 + few-shot prompt + null filename 降级 |
| `9274f1e5` | `AnswerGenerationPort` 签名从 `(String, List<RetrievedDocument>)` 简化为 `(String, String)`，更新全部调用方 |
| `d2cdf254` | `CitationDto` + `RagResponse.citations` 字段（向后兼容，空数组） |
| `6d35d6d8` | `DocumentIngestionApplicationService` 注入 filename 到 chunk metadata |
| `2a409ff9` | 里程碑文档更新 — 82 tests pass |

### Milestone 4: Pipeline Integration（Task 13-14）

| Commit | 内容 |
|--------|------|
| `b42d8784` | 核心集成：`RagQueryApplicationService.Default.handle()` 串联 rewrite → retrieval(+metadataFilters) → rerank → citation → answer。`QueryResult` 新增 `citations` 字段。`ApplicationWiringConfig` 装配所有新 bean。`RagController` 映射 citations 到 DTO。更新 5 个测试文件。 |
| `1d2f1045` | 里程碑文档更新 — 82 tests pass |

### Milestone 5: Offline Evaluation（Task 15-18）

| Commit | 内容 |
|--------|------|
| `a92c4903` | 评估数据模型：`TestCase`, `TestDataset`, `ExpectedSource`, `TraceRecord`, `EvaluationReport` |
| `ba5e364f` | `EvaluationRunner`（批量执行+自定义指标）、`RagasClient`（HTTP 客户端+指标解析）、`ReportGenerator`、`TestDatasetLoader` |
| `2a9bb36a` | `ragas-evaluator/` Python sidecar：FastAPI app + Dockerfile + docker-compose.yml |
| `6c9c5701` | 骨架测试集 JSON（cob_testset + collateral_testset）+ `EvaluationDataModelTest` |
| `d2ebf036` | 里程碑文档更新 — 86 tests pass |

### Milestone 6: Final Verification + Cleanup

| Commit | 内容 |
|--------|------|
| `d1333f51` | `CLAUDE.md` + `control/enhanced_rag/Documentation.md` 更新 Batch A 完成状态 |

---

## 二、管线日志增强

| Commit | 内容 |
|--------|------|
| `2c15b122` | 7 个文件添加结构化 `[TAG]` 日志：`[API]` 请求响应、`[RAG]` 管线各阶段耗时分解、`[Rewrite]` 路由决策、`[COB]`/`[Collateral]` LLM 调用详情、`[Citation]` 组装解析、`[Retrieval]` 搜索参数与结果 |

---

## 三、生产日志分析与优化

### 第一次分析（优化前 log）

- 总耗时 14.3s，Retrieval 占 9.3s (65%)
- rerank threshold=0.0 导致无过滤
- 发现 answer model 用了 VL 版本（qwen3-vl）

### 优化实施

| Commit | 内容 |
|--------|------|
| `b712b965` | **Retrieval 计时细分**：`BedrockEmbeddingAdapter` 添加 `[Embedding]` 计时；`RestClientSearchGateway` vectorSearch/textSearch 分别计时 embed vs opensearch；`BedrockRerankAdapter` API 计时 + per-doc score。**Rerank Threshold Floor**：`Math.max(client, serverFloor)` 防止客户端 0.0 绕过配置。新增 floor 行为测试。 |

### 第二次分析（优化后 log）

- Embedding 370ms, vectorSearch opensearch 2476ms, textSearch opensearch 2864ms — **瓶颈在 OpenSearch 而非 Embedding**
- 但 rerank 0.5 threshold 过高，所有文档被过滤（0 passed）

| Commit | 内容 |
|--------|------|
| `643a0c18` | 将 rerank threshold floor 从 0.5 降到 0.01；per-doc score 从 DEBUG 提升到 INFO 便于校准 |

---

## 四、前端场景选择器

| Commit | 内容 |
|--------|------|
| `188ca198` | `frontend/src/pages/QA.tsx` Configuration 面板添加 Scene 下拉框：COB 知识问答 (module=RAG) / Collateral 协议查询 (module=collateral)，打通后端双路径 Query Rewrite |

---

## 统计

| 指标 | 数值 |
|------|------|
| 总 Commits | 32 |
| 测试数量 | 87（原有 42 + 新增 45） |
| 新增 Java 文件 | ~25 |
| 新增 Python 文件 | 1 (app.py) |
| 修改前端文件 | 1 (QA.tsx) |
| 涉及包 | domain/rag, application/rag, infrastructure/bedrock, infrastructure/opensearch, evaluation/*, api/rag, ragas-evaluator/ |

## 关键架构决策

1. **Strategy + Registry 模式** 实现 Query Rewrite 路由，COB/Collateral 策略可独立开关
2. **Citation 用 Prompt 注入** 而非后处理匹配，LLM 标注 `[n]` 引用更准确
3. **AnswerGenerationPort 签名简化** 为 `(String, String)`，context 格式化职责移到 CitationAssemblyService
4. **RetrievalPort 重构** 为 RetrievalRequest 值对象，支持 metadataFilters 扩展
5. **RAGAS Python sidecar** 独立部署，Java 端通过 HTTP 调用，评估不侵入主管线
6. **Rerank threshold floor** 用 `Math.max()` 确保服务端最低阈值生效

## 待后续处理

- 观察 per-doc rerank score 分布，校准合适的阈值（当前 0.01 基本 passthrough）
- OpenSearch 查询耗时较高（2.5-6.8s），可能需要优化索引或调整查询参数
- Collateral 场景端到端验证（需上传带 counterparty/agreement_type metadata 的文档）
- RAGAS sidecar Docker 构建与本地启动验证
