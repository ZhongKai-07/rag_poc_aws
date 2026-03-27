# Enhanced RAG Batch A — 执行说明

## 唯一事实来源

- **设计规格:** `docs/superpowers/specs/2026-03-26-enhanced-rag-batch-a-design.md`
- **实施计划:** `docs/superpowers/plans/2026-03-26-enhanced-rag-batch-a-plan.md`
- **里程碑进度:** `control/enhanced_rag/Plan.md`
- **状态与决策:** `control/enhanced_rag/Documentation.md`

开发过程中以 Plan.md 中的里程碑检查项为准。

## 执行规则

### 验证纪律

每个里程碑完成后必须运行验证命令：

```bash
# 基本验证链
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test

# 集成验证（Milestone 4+）
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" spring-boot:run
curl http://localhost:8001/health

# 评估验证（Milestone 5）
cd ragas-evaluator && docker-compose up -d
curl http://localhost:8002/health
```

验证失败必须修复后才能进入下一个里程碑。

### 控制 diff 范围

- 每个里程碑只改该改的代码，不顺手动计划外的东西
- 不引入额外依赖（Batch A 无新 Maven 依赖，除非评估模块需要 HTTP 客户端）
- 不修改前端代码
- 不修改 PostgreSQL schema

### 持续更新文档

每个里程碑完成后，同步更新：
1. `control/enhanced_rag/Plan.md` — 勾选已完成检查项
2. `control/enhanced_rag/Documentation.md` — 更新状态、记录决策
3. `CLAUDE.md` — 如有架构变更，同步更新 Project Map

### TDD 流程

遵循 test-first 纪律：
1. 写失败测试
2. 确认测试失败
3. 写最小实现
4. 确认测试通过
5. 提交

### Git 提交规范

- 每个 Task 一次提交
- 提交信息格式：`feat:` / `refactor:` / `docs:` 前缀
- 不合并多个 Task 到一次提交

## 实现注意事项（来自用户评审）

1. **OpenSearch mapping:** `counterparty`、`agreement_type` 必须显式定义为 `keyword` 类型，在 `ensureIndex()` mapping 中
2. **Citation 防御性编程:** `filename` 为 null 时降级显示 `"未知源文档"` 或 indexName
3. **Citation Prompt:** 加 1-2 个 Few-Shot 示例；正则兼容 `[1,2]`、`[1][2]` 等变体
4. **改写超时:** 用 `CompletableFuture.orTimeout(3, SECONDS)` 实现硬超时
5. **RAGAS URL 配置:** `RagasClient` base URL 通过 `RAGAS_EVALUATOR_URL` 环境变量配置
6. **Citation Feature Flag:** `rag.citation.enabled` 独立于 `rag.query-rewrite.enabled`

## 关键类引用

| 需修改的类 | 位置 | 变更内容 |
|-----------|------|---------|
| `RagQueryApplicationService.Default` | `application/rag/` (line 51) | 集成 rewrite + citation（**注意：是 interface 内部的 inner class**） |
| `RetrievalPort` | `domain/retrieval/` (line 7) | 7-param → `RetrievalRequest` 单参数 |
| `AnswerGenerationPort` | `domain/rag/` (line 8) | `(String, List<RetrievedDocument>)` → `(String, String)` |
| `OpenSearchRetrievalAdapter` | `infrastructure/opensearch/` (line 40) | 解包 `RetrievalRequest` + metadata filter |
| `BedrockAnswerGenerationAdapter` | `infrastructure/bedrock/` (line 35) | 简化为薄 LLM 调用器 |
| `OpenSearchChunkMapper` | `infrastructure/opensearch/` (line 11) | filename 从 chunk.metadata() 读取 |
| `ApplicationWiringConfig` | `infrastructure/config/` | 装配新 bean |
| `RagProperties` | `infrastructure/config/` | 新增 feature flags + rewrite model ID |
