# Enhanced RAG Batch A — 状态与决策记录

## 当前状态

**阶段:** 设计完成，待开始实施
**日期:** 2026-03-27

### 里程碑进度

| # | 里程碑 | 状态 | 完成日期 |
|---|--------|------|---------|
| 1 | Foundation — 域值对象 + 配置 | **完成** | 2026-03-27 |
| 2 | Query Rewriting — 路由器 + 策略 + filter | **完成** | 2026-03-27 |
| 3 | Answer Citation — 组装 + Port 变更 + DTO | 待开始 | — |
| 4 | Pipeline Integration — 管线串联 + E2E | 待开始 | — |
| 5 | Offline Evaluation — Runner + RAGAS sidecar | 待开始 | — |
| 6 | Final Verification + Cleanup | 待开始 | — |

### 测试统计

- 现有测试: 42 个通过（含重构后更新的 4 个测试文件）
- 新增测试: 22（M1: 9 + M2: 13）
- 总计: 77 通过 / ~85-90（预估最终）

## 已做出的决策

### 架构决策

| 日期 | 决策 | 原因 |
|------|------|------|
| 2026-03-26 | Batch A 不引入 Spring AI，手写 Port/Adapter | Spring AI 对 Batch A 的三项功能帮助有限（BDA/Rerank 无法替换），Batch B 时 ChatMemory/Streaming 价值更大 |
| 2026-03-26 | Query 改写用 Strategy + Registry 模式 | COB 和 Collateral 场景的改写逻辑本质不同，Strategy 模式支持开闭原则 |
| 2026-03-26 | Citation 用 Prompt 注入而非后处理匹配 | LLM 理解上下文关系，标注更准确 |
| 2026-03-26 | 评估用 RAGAS Python sidecar 而非 Java 自实现 | RAGAS 是 RAG 评估事实标准，指标定义成熟，且内置 Bedrock 支持 |
| 2026-03-26 | `RetrievalPort` 重构为 `RetrievalRequest` 值对象 | 原 7 参数签名难以扩展，值对象支持可选 metadataFilters |
| 2026-03-26 | `AnswerGenerationPort` 签名改为 `(String, String)` | Citation 格式化职责移到 `CitationAssemblyService`，Adapter 变为薄调用器 |

### 评估框架选型

| 工具 | 角色 | 集成方式 |
|------|------|---------|
| RAGAS | 离线评估（4 项标准指标） | Python sidecar via HTTP |
| Bedrock Evaluations | 补充 Correctness 评估 | AWS SDK 原生调用 |
| Langfuse | 在线可观测（推迟到 Batch B） | OpenTelemetry |

### 批次规划

| 批次 | 内容 | 状态 |
|------|------|------|
| Batch A | Query 改写 + Citation + 离线评估 | **当前** |
| Batch B | 多轮对话 + 流式输出 + 用户反馈 + Spring AI + Langfuse | 待规划 |

## 运行和验证命令

```bash
# 编译 + 测试
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test

# 启动后端
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" spring-boot:run

# 健康检查
curl http://localhost:8001/health

# RAGAS sidecar
cd ragas-evaluator && docker-compose up -d
curl http://localhost:8002/health

# 运行评估（需要 live services）
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test -Dtest=RagEvaluationTest
```

## 已知问题 / 后续跟进

- 存量已索引文档缺少 `metadata.filename` 字段 — 新文档入库后自动包含，旧文档需重新索引
- Collateral 场景的 metadata（counterparty、agreement_type）依赖目录结构约定 + LLM 提取 — 具体目录命名规范待与业务确认
- 测试集内容需要业务部门提供核心 case（~20-30 条）— 待协调
- RAGAS TestsetGenerator 自动生成质量待验证 — 需人工审核

## 参考文档

| 文档 | 路径 |
|------|------|
| 设计规格 | `docs/superpowers/specs/2026-03-26-enhanced-rag-batch-a-design.md` |
| 实施计划 | `docs/superpowers/plans/2026-03-26-enhanced-rag-batch-a-plan.md` |
| 用户评审反馈 | `docs/prompts/prompt_phaseA_evaluation.md` |
| 评估工具研究 | Memory: `reference_rag_evaluation_tools.md` |
| Spring AI 迁移计划 | Memory: `project_spring_ai_migration.md` |
