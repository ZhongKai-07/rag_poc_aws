# BDA 解析可观测性 — 开发状态日志（2026-03-23）

## 已完成任务（Chunk 1 + Chunk 2 全部完成）

| Task | 内容 | Commit SHA | 状态 |
|------|------|-----------|------|
| Task 1 | ParsedDocument record 扩展：`parserProvenance` → `s3OutputPath` / `parserType` / `parserVersion`；新增 `parserProvenance()` 实例方法 | `74fea89f` + `ec9531e6`（质量修复） | ✅ 完成 |
| Task 2 | 新增域类型：`BdaParseResultRecord` + `BdaParseResultPort` | `9bcd9acf` | ✅ 完成 |
| Task 3 | `BdaResultMapper.map()` 增加第 4 参数 `s3OutputPath`；`BdaDocumentParserAdapter` 传递 `outputUri` | `690a0251` | ✅ 完成 |
| Task 4 | Flyway V4 migration（`bda_parse_result` 表）+ `BdaParseResultEntity` + `BdaParseResultJpaRepository` | `6ed0c759`（V3）+ `262daf9d` | ✅ 完成 |
| Task 5 | `BdaParseResultPersistenceAdapter` 实现 `BdaParseResultPort`（TDD，4 个单元测试） | `7c5a8b18` + `15bf0f85`（断言补全） | ✅ 完成 |

---

## 待完成任务（Chunk 3 + Chunk 4）

### Chunk 3: 应用服务 + Admin API

**Task 6** — `DocumentIngestionApplicationService.Default` 更新
- 新增第 6 构造参数 `BdaParseResultPort bdaParseResultPort`
- 在 `handle()` 的外层 `try` 块内，`log.info("Parsed document...")` 之后、`List<String> sentences = ...` 之前插入非阻断写入块（内层 `try/catch(Exception e)`）
- 同步更新 `DocumentIngestionApplicationServiceTest`：添加 `FakeBdaParseResultPort` 内部类，新增 6 个断言
- 参考：计划文件 Task 6，`docs/superpowers/plans/2026-03-23-bda-observability.md` 第 843-991 行

**Task 7** — `ParseResultQueryApplicationService` + `ApplicationWiringConfig` 更新
- 新建 `application/admin/ParseResultQueryApplicationService.java`（listAll / fetchRawBdaJson / fetchIndexedChunks）
- 在 `ApplicationWiringConfig.java` 新增 `bdaParseResultPort` bean 和 `parseResultQueryApplicationService` bean
- 更新现有 `documentIngestionApplicationService` bean 注入第 6 参数
- 参考：计划文件 Task 7，第 995-1240 行

**Task 8** — `AdminController` + `ApiExceptionHandler` 扩展
- TDD：先写 `AdminControllerContractTest`（6 个测试）
- 新建 `api/admin/AdminController.java`（3 个端点：`/admin/parse_results`、`/admin/parse_results/{indexName}/raw`、`/admin/parse_results/{indexName}/chunks`）
- 在 `ApiExceptionHandler` 新增 3 个 handler：`S3FetchException` → 502、`S3ObjectNotFoundException` → 502、`IndexNotFoundException` → 404
- 参考：计划文件 Task 8，第 1244-1486 行

### Chunk 4: 前端

**Task 9** — 前端 Admin API 客户端 + 路由
- 新建 `frontend/src/api/adminApi.ts`（3 个 fetch 函数）
- `App.tsx` 新增 `/admin` 路由
- `Layout.tsx` 新增 DEV-only Admin 导航（`Settings` 图标）

**Task 10** — `AdminPage.tsx` 组件
- 两栏布局：左侧文档列表 + 右侧三 Tab（摘要 / 原始 JSON / Chunks）
- 含 `JsonTree` 可折叠组件和 `ChunkItem` 组件

---

## 当前代码库状态

- **全量测试**：42 中 41 通过（1 个预存失败：`PromptTemplateFactoryTest.systemPromptKeepsExpertRoleAndDirectAnswerStyle` — Windows 编码问题，与本次任务无关）
- **域层边界**：`BdaParseResultRecord` / `BdaParseResultPort` 纯 Java，无基础设施依赖 ✅
- **数据流**：`BdaDocumentParserAdapter` → `BdaResultMapper` → `ParsedDocument`（含 `s3OutputPath`）→ 待 Task 6 写入 PG
- **Bean 注册**：`BdaParseResultPersistenceAdapter` 尚未注册为 Spring bean，将在 Task 7 的 `ApplicationWiringConfig` 更新中完成（符合计划设计）

---

## 接力开发指引

接手时执行以下命令确认基础状态：
```bash
# 确认当前 HEAD
cd C:\Users\zhong kai\.codex\worktrees\ff34\huatai_rag_github_share
git log --oneline -5

# 确认测试状态（预期 41/42 通过）
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" test -q

# 从 Task 6 继续，参考计划文件
# docs/superpowers/plans/2026-03-23-bda-observability.md（Task 6 从第 835 行开始）
```

**下一步：Task 6** — `DocumentIngestionApplicationService` 非阻断写入。
