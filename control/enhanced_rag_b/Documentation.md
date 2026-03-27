# Enhanced RAG Batch B — 状态与决策记录

## 当前状态

**阶段:** 设计完成，待生成实施计划
**日期:** 2026-03-28

### 里程碑进度

| # | 里程碑 | 状态 | 完成日期 |
|---|--------|------|---------|
| 1 | Database + Domain | 待开始 | — |
| 2 | Session管理 + 对话记忆 | 待开始 | — |
| 3 | 流式输出 | 待开始 | — |
| 4 | 推荐追问 + 置信度 + Citation优化 | 待开始 | — |
| 5 | 用户反馈 | 待开始 | — |
| 6 | Final Verification | 待开始 | — |

### 测试统计

- Batch A测试: 86 通过
- Batch B新增: 0 / ~TBD
- 总计: 86

## 已做出的决策

| 日期 | 决策 | 原因 |
|------|------|------|
| 2026-03-28 | 不引入Spring AI | AWS账号即将切换，连接配置不稳定；Spring AI 2.0仍在M3 |
| 2026-03-28 | 不引入Langfuse | POC查询量小(~120/天)，RAGAS已覆盖离线评估 |
| 2026-03-28 | 对话记忆用滑动窗口+压缩 | 短对话零开销，长对话不丢上下文 |
| 2026-03-28 | 流式用SSE而非WebSocket | 单向推送够用，不需要引入WebFlux |
| 2026-03-28 | 推荐追问同Prompt生成 | 零额外LLM调用，和流式天然配合 |
| 2026-03-28 | 答案置信度基于rerank分数 | 零LLM成本，已有rerank分数 |
| 2026-03-28 | Citation文件名用PG回退 | 存量文档无metadata.filename，PG有全量记录 |

## 已知问题 / 后续跟进

- 单用户设计，无user_id/租户隔离 — 等认证体系引入后补
- Session无TTL清理 — POC阶段可接受，上线前加定期清理
- OCR噪声过滤为正则粗清理 — 根因解决靠Docling集成
- 流式中断生成（用户取消）— 当前不支持，后续可升级WebSocket

## 参考文档

| 文档 | 路径 |
|------|------|
| 设计规格 | `docs/superpowers/specs/2026-03-28-enhanced-rag-batch-b-design.md` |
| Batch A设计 | `docs/superpowers/specs/2026-03-26-enhanced-rag-batch-a-design.md` |
| Batch A控制 | `control/enhanced_rag/` |
| Spring AI决策 | Memory: `project_spring_ai_migration.md` |
