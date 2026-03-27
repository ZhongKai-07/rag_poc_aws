# Enhanced RAG Batch B — 执行说明

## 唯一事实来源

- **设计规格:** `docs/superpowers/specs/2026-03-28-enhanced-rag-batch-b-design.md`
- **实施计划:** `docs/superpowers/plans/2026-03-28-enhanced-rag-batch-b-plan.md`（待生成）
- **里程碑进度:** `control/enhanced_rag_b/Plan.md`
- **状态与决策:** `control/enhanced_rag_b/Documentation.md`

## 执行规则

### 验证纪律

每个里程碑完成后运行：

```bash
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test
```

Milestone 3+ 额外验证：
```bash
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" spring-boot:run
curl http://localhost:8001/health
# 流式测试
curl -N -X POST http://localhost:8001/rag_answer/stream -H "Content-Type: application/json" -d '{"query":"test","index_names":["test"]}'
```

### 控制 diff 范围

- 不引入新Maven依赖（除非SSE需要额外配置）
- 不修改Batch A的Query改写/评估模块
- 不修改OpenSearch索引结构
- 前端改动限于反馈按钮最小对接

### 持续更新文档

每个里程碑完成后同步更新：
1. `control/enhanced_rag_b/Plan.md` — 勾选已完成项
2. `control/enhanced_rag_b/Documentation.md` — 更新状态
3. `CLAUDE.md` — 如有架构变更

### 关键注意事项

1. **`RagRequest.sessionId`:** 必须移除 `@NotBlank` 注解，改为nullable
2. **`RagQueryApplicationService`:** 是interface + inner class `Default` 结构
3. **`CitationAssemblyService`:** 注入 `DocumentRegistryPort`（用已有的 `findByIndexName()`）
4. **JSON输出解析:** `parseLlmOutput()` 在 citation解析之前，提取answer文本再做 `[n]` 解析
5. **流式不重试:** 首个token发出后不重试，用error事件降级
6. **线程池:** SSE专用 `TaskExecutor`（core=4, max=8, queue=50）
