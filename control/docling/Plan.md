# Docling-Java 集成计划

## Source Of Truth

本文件是 docling-java 集成的执行真理源，定义里程碑边界、验收标准和验证命令。

配套文档：

- `control/docling/Prompt.md` — 目标、约束、规格
- `control/docling/Implement.md` — 运行手册
- `control/docling/Documentation.md` — 进度和决策记录

## Target Architecture

在现有六边形架构中，仅在 Infrastructure 层插入新的解析器适配器分支：

```
infrastructure/
├── bda/                          ← 现有，保持不变
│   ├── BdaClient.java
│   ├── BdaDocumentParserAdapter.java
│   └── BdaResultMapper.java
├── docling/                      ← 新增
│   ├── DoclingDocumentParserAdapter.java
│   └── MarkdownHeaderChunker.java
├── config/
│   ├── ApplicationWiringConfig.java  ← 修改：条件装配
│   ├── ClientConfig.java             ← 修改：BDA client 条件化
│   ├── ParserProperties.java         ← 新增
│   └── DoclingProperties.java        ← 新增
```

切换逻辑：

```
huatai.parser.type=bda (default)      huatai.parser.type=docling
├─ S3DocumentStorageAdapter           ├─ LocalFileStorageAdapter
├─ BdaDocumentParserAdapter           ├─ DoclingDocumentParserAdapter
└─ BedrockDataAutomationClient        └─ DoclingServeApi (HTTP)
```

## Milestones

### M1: 配置基础设施

- Status: not started
- Scope:
  - 新建 `ParserProperties.java`（`huatai.parser.type`，默认 `bda`）
  - 新建 `DoclingProperties.java`（`huatai.docling.endpoint`、`connect-timeout`、`read-timeout`）
  - `application.yml` 添加 `huatai.parser` 和 `huatai.docling` 配置块
  - `ClientConfig.java` 注册新属性类
- Acceptance:
  - 编译通过
  - 现有 42 个测试全部通过（默认 bda，零影响）
- Verification:
  ```bash
  mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" compile
  mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test
  ```
- Files touched:
  - NEW: `infrastructure/config/ParserProperties.java`
  - NEW: `infrastructure/config/DoclingProperties.java`
  - MOD: `infrastructure/config/ClientConfig.java`
  - MOD: `src/main/resources/application.yml`

### M2: Maven 依赖

- Status: not started
- Scope:
  - `pom.xml` 添加 `docling-serve-client` 和 `docling-testcontainers`（test scope）
  - 确认无版本冲突（Jackson、HttpClient）
- Acceptance:
  - 依赖解析成功
  - 编译通过
  - 现有测试全部通过
- Verification:
  ```bash
  mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" dependency:resolve
  mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test
  ```
- Files touched:
  - MOD: `backend-java/pom.xml`
- Risk: docling-java 的 Maven 坐标需从 Maven Central 确认（预期 groupId `ai.docling`）。如果坐标不对，停止修复后再继续。

### M3: MarkdownHeaderChunker

- Status: not started
- Scope:
  - 新建 `MarkdownHeaderChunker.java` — 纯 Java，无外部依赖
  - 按 `#`/`##`/`###` 标题拆分 Markdown
  - 追踪 sectionPath 层级
  - 短块合并（< 100 chars）
  - 返回 `List<Section>` 其中 `record Section(List<String> sectionPath, String content)`
  - 新建 `MarkdownHeaderChunkerTest.java` — 全覆盖单元测试
- Acceptance:
  - 无标题文档 → 单块
  - 多级标题 → 正确 sectionPath（如 `["第一章", "1.1 概述"]`）
  - 短块合并到下一块
  - 空内容不崩溃
  - 中文标题正确处理
- Verification:
  ```bash
  mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" \
    "-Dspring.profiles.active=test" "-Dtest=MarkdownHeaderChunkerTest" test
  ```
- Files touched:
  - NEW: `infrastructure/docling/MarkdownHeaderChunker.java`
  - NEW: `test/.../docling/MarkdownHeaderChunkerTest.java`

### M4: DoclingDocumentParserAdapter

- Status: not started
- Scope:
  - 新建 `DoclingDocumentParserAdapter.java` 实现 `DocumentParser` 端口
  - 从 `ParserRequest.storagePath()`（本地路径）读取文件字节
  - 调用 `DoclingServeApi.convertSource()` 获取 Markdown
  - 用 `MarkdownHeaderChunker` 分块
  - 每块按 `\n` 拆句，取首行作为 `sentenceText`
  - 构建 `ParsedChunk`（含 metadata: source, chunk_id, parser_type, parser_version）
  - 构建 `ParsedPage`
  - 返回 `ParsedDocument(parserType="docling", s3OutputPath="")`
  - 新建 `DoclingDocumentParserAdapterTest.java` — Mock DoclingServeApi
- Acceptance:
  - Mock 输入已知 Markdown → 输出正确的 ParsedDocument
  - `parserType` = `"docling"`
  - `s3OutputPath` = `""`
  - `sentenceText` 为每个 chunk content 的首行
  - `metadata` 含 `source`、`chunk_id`、`parser_type`、`parser_version`
  - chunk 数量与 Markdown 标题数匹配
- Verification:
  ```bash
  mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" \
    "-Dspring.profiles.active=test" "-Dtest=DoclingDocumentParserAdapterTest" test
  ```
- Files touched:
  - NEW: `infrastructure/docling/DoclingDocumentParserAdapter.java`
  - NEW: `test/.../docling/DoclingDocumentParserAdapterTest.java`
  - NEW: `test/resources/fixtures/parser/docling-sample-markdown.md`

### M5: 条件装配

- Status: not started
- Scope:
  - `ApplicationWiringConfig.java`：
    - `documentParser` bean → 拆为两个 `@ConditionalOnProperty` bean
    - `documentStorage` bean → 同理拆为 S3 和 Local 两个条件 bean
    - 新增 `DoclingServeApi` bean（仅 docling 模式）
    - BDA 专属 bean 加条件注解（`matchIfMissing=true`）
  - `ClientConfig.java`：
    - `bedrockDataAutomationRuntimeClient` bean 加条件注解
  - `BdaParseResultPort` 保持无条件注入（两种模式都记录解析元数据）
- Acceptance:
  - `PARSER_TYPE=bda`（默认）：全部 42 个现有测试通过
  - `PARSER_TYPE=docling`：Spring context 可启动（需 mock docling endpoint 或 test 配置）
  - 无 bean 定义冲突或缺失
- Verification:
  ```bash
  # 默认 bda 模式 — 全部测试通过
  mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test

  # docling 模式 — 新增 wiring 测试
  mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" \
    "-Dspring.profiles.active=test" "-Dtest=ApplicationWiringDoclingTest" test
  ```
- Files touched:
  - MOD: `infrastructure/config/ApplicationWiringConfig.java`
  - MOD: `infrastructure/config/ClientConfig.java`
  - NEW: `test/.../config/ApplicationWiringDoclingTest.java`

### M6: 集成测试 + 运行配置

- Status: not started
- Scope:
  - 新建 `DoclingIntegrationTest.java`（`@Tag("docker")`）— Testcontainers 启动 docling-serve 容器，转换样本 PDF
  - 新建 `application-docling.yml` profile
  - 新建 `.env.docling.example` 环境变量模板
  - 更新 `CLAUDE.md` 引用新的控制文档
- Acceptance:
  - Testcontainers 测试在 Docker 可用时通过
  - `SPRING_PROFILES_ACTIVE=docling` 启动成功
  - 端到端验证：上传 PDF → 解析 → 索引 → 问答
- Verification:
  ```bash
  # 集成测试（需 Docker）
  mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" \
    "-Dspring.profiles.active=test" "-Dtest=DoclingIntegrationTest" test

  # 端到端验证（需 docling-serve 运行）
  # docker run -p 5001:5001 quay.io/docling-project/docling-serve
  PARSER_TYPE=docling DOCLING_ENDPOINT=http://localhost:5001 \
    mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" spring-boot:run
  curl http://localhost:8001/health
  curl -X POST http://localhost:8001/upload_files -F "file=@test.pdf" -F "directory_path=test"
  ```
- Files touched:
  - NEW: `test/.../docling/DoclingIntegrationTest.java`
  - NEW: `src/main/resources/application-docling.yml`
  - NEW: `backend-java/.env.docling.example`
  - MOD: `CLAUDE.md`

## Stop And Fix Rules

以下情况必须停下来修复，再继续下一个里程碑：

- 当前里程碑的验证命令失败。
- 新改动导致之前已通过的里程碑验证失败（回归）。
- 新改动修改了前端 API 端点名称、字段名、响应结构。
- 新改动引入了 Domain 层对 Spring/AWS/Docling 的依赖。
- 新改动破坏了 BDA 解析路径的行为。
- `PARSER_TYPE=bda`（默认）时现有 42 个测试不再全部通过。
- Maven 依赖解析失败或引入版本冲突。
- 里程碑状态与控制文档不一致。

## Decision Notes

（实现过程中积累，初始为空）

1. 双解析器共存而非替换
   - Reason: 用户明确要求 `parser.type=docling` 用本地存储，`parser.type=bda` 用 S3 存储，两者通过配置切换。

2. MarkdownHeaderChunker 作为独立类
   - Reason: 纯 Java 无依赖，可独立单元测试，不耦合 Docling API。复刻 Python 基线的 `MarkdownHeaderTextSplitter` 行为。

3. 复用 `bda_parse_result` 表
   - Reason: 表结构通用（chunk_count, page_count, parser_type, parser_version），无需为 Docling 新建表。`s3OutputPath` 在 Docling 模式下为空字符串。

4. BDA 条件 bean 使用 `matchIfMissing=true`
   - Reason: 确保默认行为（bda）零变化，不影响现有 42 个测试。

5. docling-serve 通过 Docker 运行
   - Reason: docling-java 是客户端-服务端架构，docling-serve 是 Python 服务，需容器化运行。Testcontainers 用于测试。

## Documentation Update Rule

每个里程碑完成后，更新：

- `Plan.md` — 里程碑状态
- `Documentation.md` — 进度、决策、已知问题
- `Prompt.md` — 仅当约束或范围发生变化时
- `Implement.md` — 仅当工作规则发生变化时
