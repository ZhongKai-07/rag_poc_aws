
  ## 任务说明
  继续实现 BDA 解析可观测性功能。规格说明和实现计划已完成审核，可直接进入执行阶段。

  ## 关键文档（必读）
  - **实现计划**：`docs/superpowers/plans/2026-03-23-bda-observability.md`（10 个任务，完整代码）
  - **设计规格**：`docs/superpowers/specs/2026-03-23-bda-observability-design.md`
  - **项目背景**：`CLAUDE.md`（仓库级指引，必读）

  ## 当前状态
  - 规格和计划均已通过多轮 reviewer 审核，已 commit
  - 代码库处于 Java 后端迁移完成后的稳定状态（Tasks 1-12 已完成）
  - **尚未执行任何实现代码**，从计划 Task 1 开始全新实现

  ## 执行要求
  使用 `superpowers:subagent-driven-development` skill 执行计划。

  计划分 4 个 Chunk，每个 Chunk 完成后做代码审查：
  - **Chunk 1**（Tasks 1-2）：ParsedDocument 扩展 + 域类型
  - **Chunk 2**（Tasks 3-5）：BDA 适配器 + Flyway V4 + JPA + 持久化适配器
  - **Chunk 3**（Tasks 6-8）：应用服务 + AdminController + 异常处理
  - **Chunk 4**（Tasks 9-10）：前端 API 客户端 + AdminPage

  ## 重要执行注意事项

  ### 环境
  - Java 17，Maven：`mvn -f backend-java/pom.xml "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository"`
  - 测试命令加 `-Dspring.profiles.active=test`

  ### Task 1 特别说明
  - `ParsedDocument` record 从 6 组件变为 8 组件，会有编译错误——这是预期的 TDD 中间状态
  - Step 2 预期**两处**都报构造器错误：`BdaResultMapper.java` 和 `DocumentIngestionApplicationServiceTest.java`

  ### Task 3 特别说明
  - 先改测试文件（Step 1），再改 `BdaResultMapper.map()` 签名（Step 3），再改 `BdaDocumentParserAdapter`（Step 4）
  - Step 3 后，`BdaDocumentParserAdapter` 的相关适配器测试也会暂时无法编译——Step 4 修复

  ### Task 4 特别说明
  - **Step 1 先做 V3 migration 前置检查**（确认 `V3__filename_unique_constraint.sql` 已 commit）
  - V4 migration 用 `timestamp with time zone`（不用 `TIMESTAMPTZ`），与 V1 保持一致

  ### Task 6 特别说明
  - 非阻断写入插入在外层 `try` 块内（`log.info("Parsed document...")` 之后、`List<String> sentences = ...` 之前），用内层 `try/catch(Exception e)` 包裹

  ## 验收
  全部任务完成后：
  ```bash
  mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" test -q
  mvn -f backend-java/pom.xml spring-boot:run
  curl http://localhost:8001/admin/parse_results
  上传一个 PDF 后，/admin/parse_results 应返回包含该文档的列表，/admin/parse_results/{index}/chunks 应返回已索引的 chunk 列表。
  ```