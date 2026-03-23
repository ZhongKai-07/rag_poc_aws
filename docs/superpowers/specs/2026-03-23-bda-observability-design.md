# BDA 解析可观测性设计

**日期：** 2026-03-23
**状态：** 已审核
**作者：** Claude Code

---

## 背景与目标

当前 BDA 解析链路（`BdaClient` → `BdaResultMapper` → `BdaDocumentParserAdapter`）完成后，日志只输出一行 `"Parsed document X into N chunks"`，无法判断：

- 分块是否合理（段落太长/太短、section 标签是否正确）
- 某文档问答效果差时，是 BDA 解析阶段就出问题还是 embedding/rerank 的问题
- 不同上传版本的解析结果差异

**目标：** 新增独立 Admin 页面，持久化 BDA 解析元数据，支持查看原始 BDA JSON 和 OpenSearch 已索引 chunks，用于解析质量验证和故障排查。

---

## 范围

**在范围内：**
- 新增 `bda_parse_result` PG 表，ingestion 解析成功后写入元数据
- 新增 3 个 `/admin/parse_results` REST 接口
- React 新增 `/admin` 路由页面（两栏布局，三 Tab 详情面板）

**不在范围内：**
- 修改现有 `/upload_files`、`/rag_answer` 等前端契约接口
- 生产环境权限控制（Auth）
- 现有文件列表页的"解析详情"弹窗（已记录为后续产品形态）

---

## 域模型变更

### `ParsedDocument` 扩展

`BdaResultMapper` 内部已分别计算 `parserType` 和 `parserVersion`，再组合为 `parserProvenance()`。本次将这两个值**提升为 `ParsedDocument` 的顶层字段**（不是拆分，而是透传），同时新增 `s3OutputPath` 字段：

```java
// 新增三个字段（与现有 record 风格一致）
String s3OutputPath()   // BDA result.json 的完整 S3 URI
String parserType()     // 解析器类型，如 "aws-bda"（原已在 BdaResultMapper 内部计算）
String parserVersion()  // 解析器版本，如 "2025-03-01"（原已在 BdaResultMapper 内部计算）
```

`parserProvenance()` 保留，实现改为 `parserType + ":" + parserVersion`，不破坏现有调用。

**`s3OutputPath` 的传递路径：**

`BdaDocumentParserAdapter.parse()` 中已有 `outputUri` 局部变量（由 `defaultOutputUri()` 计算）。修改 `BdaResultMapper.map()` 签名，增加第四个参数 `String s3OutputPath`，在构造 `ParsedDocument` 时直接传入。`BdaDocumentParserAdapter` 在调用 `resultMapper.map(payload, fileName, indexName, outputUri)` 时传递该值。

`page_count` = `parsedDocument.pages().size()`。

---

## 数据模型

新增 Flyway migration `V4__bda_parse_result.sql`：

```sql
CREATE TABLE bda_parse_result (
    id               UUID PRIMARY KEY,
    document_file_id UUID NOT NULL REFERENCES document_file(id),
    index_name       VARCHAR(128) NOT NULL,
    s3_output_path   VARCHAR(1024) NOT NULL,
    chunk_count      INTEGER NOT NULL DEFAULT 0,
    page_count       INTEGER NOT NULL DEFAULT 0,
    parser_type      VARCHAR(64),
    parser_version   VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ON bda_parse_result (document_file_id);
CREATE INDEX ON bda_parse_result (index_name);
```

**关键决策：**
- 不加 `UNIQUE` 约束，允许同一文档多次上传产生多条记录，支持版本对比
- 写入时机：`DocumentIngestionApplicationService.Default` 在 `documentParser.parse()` 返回 `ParsedDocument` 之后**立即写入**，不依赖 `writeChunks` 是否被调用。这样即使 chunks 为空（如空 PDF），仍能记录解析元数据，不产生观测盲区
- `bda_parse_result` 的写入失败**不阻断** ingestion 主流程（捕获异常后记录 warning 日志，继续执行）

**与现有表关系：**
```
document_file (1) ──< bda_parse_result (N)
     id                  document_file_id
     index_name          index_name（冗余，方便按 index 查）
```

---

## 分层架构

| 组件 | 层 | 说明 |
|------|----|------|
| `AdminController` | `api/` | HTTP 路由，DTO 映射 |
| `ParseResultQueryApplicationService` | `application/` | 编排查询，依赖 `BdaParseResultPort` 接口 |
| `BdaParseResultPort` | `domain/` 内接口（或 application service 内部接口） | 隔离 application 与 JPA；方法：`save(BdaParseResultRecord)`、`findAll()`、`findLatestByIndexName(String)` |
| `BdaParseResultRepository` | `infrastructure/persistence/` | JPA repository，实现 `BdaParseResultPort` |
| `BdaParseResultEntity` | `infrastructure/persistence/entity/` | JPA 实体 |
| S3 raw 拉取 | `infrastructure/storage/` | 复用现有 `S3Client` bean，新增 `fetchAsJson(String s3Uri): JsonNode` |

**`DocumentIngestionApplicationService.Default` 构造器变更：**

新增第六个依赖 `BdaParseResultPort`。对应地，`ApplicationWiringConfig` 中的 `@Bean` 方法需注入该端口并传入构造器。

---

## 后端 API

### `GET /admin/parse_results`

返回所有文档的解析历史列表，按 `created_at` 降序。PG 查询 join `document_file` 取 `filename`。

响应：
```json
[
  {
    "index_name": "ced4c5ef",
    "filename": "sample.pdf",
    "chunk_count": 29,
    "page_count": 8,
    "parser_type": "aws-bda",
    "parser_version": "2025-03-01",
    "created_at": "2026-03-23T10:00:00Z"
  }
]
```

---

### `GET /admin/parse_results/{index_name}/raw`

返回该 index 最新一次解析的原始 BDA `result.json`。

流程：
1. `BdaParseResultPort.findLatestByIndexName(indexName)` → 取 `s3_output_path`（按 `created_at` 最新）
2. 若无记录 → HTTP 404
3. 从 S3 拉取 JSON → 若 S3 失败 → HTTP 502
4. 以 `Content-Type: application/json` 透传给前端，不做字段映射

---

### `GET /admin/parse_results/{index_name}/chunks`

返回 OpenSearch 中该 index 的所有已索引 chunks。

流程：
- 向 OpenSearch 发送 `POST /{index_name}/_search`（`index_name` 直接作为 URL 路径段，不作为过滤字段）
- Body：`{ "query": { "match_all": {} }, "size": 200, "_source": { "excludes": ["sentence_vector"] } }`
- 若 index 不存在（OpenSearch 返回 404）→ HTTP 404

响应（`chunk_id` 从 `_source.metadata.chunk_id` 重映射到顶层字段）：
```json
[
  {
    "chunk_id": "chunk-1",
    "page_number": 1,
    "section_path": ["Executive Summary"],
    "paragraph": "The client agreement sets out...",
    "sentence": "The client agreement sets out onboarding steps.",
    "asset_references": []
  }
]
```

---

## 前端页面

**路由：** `/admin` 路由在 React Router 中**无条件注册**（生产环境也可通过直接输入 URL 访问，security-by-obscurity 对本 POC 可接受）。顶部导航栏的 `<NavLink>` 仅在 `import.meta.env.DEV === true` 时渲染，两者独立控制。

### 页面布局

两栏布局：左侧文档列表，右侧详情面板。

```
┌─────────────────────────────────────────────────────┐
│  BDA 解析观测                                        │
├──────────────┬──────────────────────────────────────┤
│ 文档列表     │  详情面板                             │
│              │                                       │
│ □ 文件A      │  [Tab: 摘要] [Tab: 原始JSON] [Tab: Chunks]
│   29块 2页   │                                       │
│   2026-03-22 │  ── 摘要 Tab ──                      │
│              │  chunk数: 29  页数: 8                 │
│ □ 文件B      │  解析器: aws-bda:2025-03-01           │
│   15块 5页   │  S3路径: s3://...                     │
│              │                                       │
│              │  ── 原始JSON Tab ──                   │
│              │  { "job": {...},                       │
│              │    "document": {...} }  （可折叠树）  │
│              │                                       │
│              │  ── Chunks Tab ──                     │
│              │  chunk-1 | p.1 | Executive Summary    │
│              │  > 段落: The client agreement...      │
│              │  > 摘要句: The client agreement...    │
│              │  > Assets: []                         │
└──────────────┴──────────────────────────────────────┘
```

### Tab 数据来源

| Tab | API 调用 | 触发时机 |
|-----|---------|---------|
| 摘要 | `/admin/parse_results`（列表已有数据） | 点击文档立即显示 |
| 原始 JSON | `/admin/parse_results/{index}/raw` | 点击 Tab 时懒加载 |
| Chunks | `/admin/parse_results/{index}/chunks` | 点击 Tab 时懒加载 |

### 交互细节

- 原始 JSON 用可折叠树展示（利用现有 `lucide-react` 图标 + 递归组件）
- Chunks Tab 每条默认折叠，展开显示完整段落 + 摘要句 + assets
- 懒加载时显示 loading 状态；HTTP 404/502 显示对应错误提示

---

## 测试策略

**后端：**
- `ParsedDocumentTest`：验证 `s3OutputPath`、`parserType`、`parserVersion` 新字段的构造和访问
- `BdaResultMapperTest`：验证 `map(payload, fileName, indexName, s3OutputPath)` 新签名，`s3OutputPath` 正确传入 `ParsedDocument`
- `BdaParseResultRepositoryTest`：验证 `save`、`findAll`、`findLatestByIndexName`
- `AdminControllerContractTest`：验证 3 个 Admin 接口的 HTTP 契约，覆盖 404/502 错误路径（mock `BdaParseResultPort` 和 OpenSearch）
- `DocumentIngestionApplicationServiceTest`：验证 `BdaParseResultPort.save()` 在 `documentParser.parse()` 返回后被调用，入参包含正确的 `s3OutputPath`、`chunkCount`、`pageCount`、`parserType`、`parserVersion`

**前端：**
- 现有前端无测试框架，不新增

---

## 后续产品形态（不在本期范围）

- 现有文件列表页（`/processed_files`）加"解析详情"弹窗，点击跳转 Admin 面板
- Admin 页面支持多版本 diff（同一文档多次上传的 chunk 对比）
- 生产环境 Basic Auth 保护 `/admin` 路由
