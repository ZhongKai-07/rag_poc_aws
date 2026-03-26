# Docling-Java 文档解析集成 Prompt

## Goal

在 `backend-java/` 中添加 docling-java 作为可切换的文档解析器，与现有 AWS BDA 解析器共存，通过配置一键切换。

## Non-Goals

- 不删除或破坏现有 BDA 解析代码。
- 不修改 Domain 层接口（`DocumentParser`、`ParsedDocument` 等）。
- 不修改 Application 层编排逻辑（`DocumentIngestionApplicationService`）。
- 不修改前端 API 契约。
- 不改变 OpenSearch 索引结构（`sentence_vector`、`paragraph`、`sentence`、`metadata`）。
- 不在本次任务中删除 S3 存储能力。

## Hard Constraints

### 切换机制

- 单一配置项 `huatai.parser.type` 控制解析器和存储方式的联动切换：
  - `bda`（默认）→ `S3DocumentStorageAdapter` + `BdaDocumentParserAdapter`
  - `docling` → `LocalFileStorageAdapter` + `DoclingDocumentParserAdapter`
- 使用 Spring `@ConditionalOnProperty` 实现条件装配。
- 默认值为 `bda`，所有条件 bean 使用 `matchIfMissing = true`，确保现有行为零变化。

### 架构边界

- 新代码仅在 `infrastructure/docling/` 和 `infrastructure/config/` 中。
- Domain 层保持零外部依赖。
- Application 层不感知具体解析器实现。
- 遵循现有六边形架构模式。

### docling-java 技术约束

- docling-java 是客户端-服务端架构，Java 端是 HTTP 客户端，需要 docling-serve 服务运行（通常在 Docker 中）。
- Maven 模块：`docling-serve-client`（含 `docling-core` + `docling-serve-api` 传递依赖）。
- 测试用 `docling-testcontainers` 模块。
- 输入方式：HTTP 上传文件字节（非 S3 路径）。
- 输出格式：结构化文档，可导出为 Markdown。

### 分块策略

- BDA 返回预分块的 `chunks[]`。
- Docling 返回完整 Markdown 文档，需要 Java 端实现分块：
  - 按 `#`/`##`/`###` 标题分割（复刻 Python 基线 `MarkdownHeaderTextSplitter`）。
  - 短块合并（< 100 chars，复刻 Python `CHUNK_SIZE_THRESHOLD`）。
  - 句子提取：按 `\n` 分割，取首行作为 `sentenceText`（复刻 Python `_process_sentences`）。

### 领域模型复用

- `ParsedDocument.s3OutputPath` 在 Docling 模式下为空字符串。
- `ParsedDocument.parserType` 为 `"docling"`。
- `bda_parse_result` 表复用，无需 schema 变更。

### 环境约束

- Java 17 目标。
- Maven 构建需 `-Dmaven.repo.local=$env:USERPROFILE\.m2\repository`。
- docling-serve 默认端口 `5001`。
- 后端服务端口 `8001` 不变。

## Deliverables

- `DoclingDocumentParserAdapter` 实现 `DocumentParser` 端口。
- `MarkdownHeaderChunker` 纯 Java Markdown 标题分块器。
- `ParserProperties` + `DoclingProperties` 配置类。
- `ApplicationWiringConfig` 条件装配，`bda`/`docling` 一键切换。
- `application-docling.yml` 运行 profile。
- 单元测试（分块器 + 适配器）+ Testcontainers 集成测试。
- 现有 42 个测试全部通过不受影响。

## Done When

- `PARSER_TYPE=bda`：全部现有测试通过，行为零变化。
- `PARSER_TYPE=docling` + 运行中的 docling-serve：
  - `POST /upload_files` 上传 PDF 成功。
  - `GET /processed_files` 返回已处理文件。
  - `POST /rag_answer` 对已索引文档返回 AI 答案。
  - OpenSearch 索引结构与 BDA 模式一致。
- 新增单元测试和集成测试全部通过。
- 四个控制文档状态准确反映当前进度。
