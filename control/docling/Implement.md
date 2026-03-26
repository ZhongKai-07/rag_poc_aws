# Docling-Java 集成运行手册

## Working Rules

- `control/docling/Plan.md` 是唯一执行真理源。
- 按里程碑顺序执行（M1 → M2 → M3 → M4 → M5 → M6）。
- 不跳过里程碑。
- 不擅自扩大范围。
- 如果需要调整计划，做最小调整并先记录到 `Plan.md` 的 Decision Notes 中。

## Milestone Workflow

每个里程碑：

1. 先写或更新测试（如适用）。
2. 运行验证命令，确认预期失败。
3. 实现最小代码变更。
4. 重新运行验证命令，直到通过。
5. 运行全量测试确认无回归：
   ```bash
   mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test
   ```
6. 检查 `git status`。
7. 只提交该里程碑相关的文件。
8. 更新 `Documentation.md`。

## Verification Rules

- 每个已完成里程碑必须有已执行的验证命令记录在 `Plan.md`。
- 绝不声称任务完成而不跑验证。
- 验证失败则停止修复，再开始下一个里程碑。
- Maven 需要 `-Dmaven.repo.local=$env:USERPROFILE\.m2\repository`（PowerShell）或 `-Dmaven.repo.local=$HOME/.m2/repository`（bash）。

## Diff Control

- 每个里程碑只改 Plan 中列出的文件。
- 不重构计划外的代码。
- 不修改前端。
- 不修改 Domain 层（`domain/parser/`、`domain/document/` 等）。
- 不修改 Application 层（`application/ingestion/` 等）。
- 不修改现有 BDA 基础设施代码（`infrastructure/bda/`）。
- 不修改 OpenSearch 基础设施代码（`infrastructure/opensearch/`）。

## Documentation Rules

- `Prompt.md`、`Plan.md`、`Implement.md`、`Documentation.md` 随进度持续更新。
- 文档更新是实现工作的一部分，不是可选的后续工作。
- 立即记录任何临时解决方法或环境约束。
- 结束工作前确保里程碑状态和下一步记录是最新的。

## Subagent Usage

- 复杂的代码探索（跨多文件理解数据流）→ 用 Explore subagent。
- 需要确认 docling-java API 用法 → 用 general-purpose subagent 查文档。
- 里程碑验证和测试运行 → 在主上下文中执行。
- 每个里程碑开始前，如有大量上下文需要加载 → 考虑用 subagent 保持主上下文干净。

## Repository Hygiene

- 不提交 `backend-java/target/`。
- 不提交 `.env` 文件中的真实密钥。
- 保持 `.gitignore` 保护构建输出。
- 保持 Python `api/` 后端不变。

## Runtime Notes

- Java 17 目标。
- Spring Boot 3.4.4。
- docling-serve 默认端口 5001。
- 后端端口 8001。
- 测试 profile 使用 H2 内存数据库。
- Testcontainers 集成测试需要 Docker（用 `@Tag("docker")` 标记，CI 无 Docker 时可跳过）。
- docling-java API 关键入口：`DoclingServeApi.convertSource(ConvertDocumentRequest)` → `ConvertDocumentResponse`。
