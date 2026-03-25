# Code Stability Audit — Java Backend (2026-03-22)

Scope: `backend-java/src/` 全量代码审查，端到端 RAG 链路已验证通过后的稳定性审计。

**修复状态**: C1/C2/C3/C4 全部已修复（2026-03-22），42 测试通过。

## Critical（必须修） — 已全部修复

### ~~C1. `ApiExceptionHandler.handleGeneric` NPE~~ FIXED

### C1. `ApiExceptionHandler.handleGeneric` NPE

**文件**: `api/common/ApiExceptionHandler.java:42`

`Map.of(exception.getMessage())` 不接受 null 值。当 `NullPointerException` 等无 message 的异常到达 handler 时，会再抛 NPE，返回 Spring 默认错误页而非 JSON。`handleIllegalArgument`（line 28）同理。

**修复**: 使用 null-safe 回退值。

### ~~C2. `BedrockRerankAdapter` 是空壳，合成分数导致文档被过滤~~ FIXED

**文件**: `infrastructure/bedrock/BedrockRerankAdapter.java:30-39`

- 未调用 `bedrockAgentRuntimeClient`，三个依赖标记 `@SuppressWarnings("unused")`
- 合成分数 `score / 100.0`，对于余弦相似度 0.5~1.0 的结果，合成后 < 0.01，远低于 `rerankScoreThreshold` 默认值 0.5
- 结果：大部分查询的文档会被全部过滤，返回 `NO_DOCS_FALLBACK`

**修复**: 实现真实 rerank 调用，或调整合成分数逻辑使其不触发过滤。

### ~~C3. `index_name` unique 约束 + md5[:8] 碰撞无友好处理~~ FIXED

**文件**: `infrastructure/persistence/entity/DocumentFileEntity.java:19-24`

`index_name` 列有 `unique = true` 约束，但 md5(filename)[:8] 不同文件名可能碰撞到同一个值。碰撞时抛 DB 异常，无用户友好的错误信息。`filename` 列缺少 unique 约束。

**修复**: 捕获唯一约束异常并返回友好提示，或给 filename 加 unique 约束。

### ~~C4. FAILED 状态文件重新上传触发唯一约束冲突~~ FIXED

**文件**: `application/ingestion/DocumentIngestionApplicationService.java:71-79`

FAILED 文件不被视为"已处理"，重新上传时会创建新记录（新 UUID），但 `index_name` 相同导致 DB unique 约束冲突。

**修复**: 对 FAILED 状态的文件做 upsert 或删除旧记录后重建。

---

## Important（应该修）

### I1. 文件内容双倍缓存

**文件**: `api/upload/UploadController.java:37`

`file.getBytes()` 加 `IngestionCommand` 的 `content.clone()` 导致双倍内存。20MB 上限下并发上传可能 OOM。

**建议**: 考虑 `file.getInputStream()` 流式传输到 S3。

### I2. Bulk 写入不检查单条错误

**文件**: `infrastructure/opensearch/OpenSearchDocumentWriter.java:49-56`

OpenSearch `_bulk` API 即使部分文档失败也返回 200。当前代码丢弃 response body，部分索引失败静默通过。

**建议**: 检查 response 中 `errors` 字段和 `items` 数组的 per-item status。

### I3. Embedding 逐条串行调用

**文件**: `infrastructure/bedrock/BedrockEmbeddingAdapter.java:35`

100+ chunks 的文档会发 100+ 个串行 HTTP 请求，每个有独立重试。大文档处理极慢。

**建议**: 并行化或批量请求。

### I4. OpenSearch URL 路径注入风险

**文件**: `infrastructure/opensearch/OpenSearchRetrievalAdapter.java:119`

`indexNames` 来自前端输入，直接拼接到 URL 路径。应校验 index name 匹配 `[a-f0-9]{8}` 模式。

### I5. `RetryUtils` 对所有 RuntimeException 重试

**文件**: `infrastructure/support/RetryUtils.java:12-13`

非瞬态错误（`IllegalArgumentException`、`AccessDeniedException`）也被重试。应限制为特定瞬态异常类型。

### I6. `AwsProperties` Java 默认值与 `application.yml` 不一致

**文件**: `infrastructure/config/AwsProperties.java:9`

Java 默认 `ap-northeast-1`，YAML 默认 `us-east-1` / `us-west-2`。配置绑定失败时会静默使用错误 region。

### I7. `topN` 参数无边界校验

**文件**: `api/question/QuestionController.java:23`

0 或负值静默返回空结果，极大值可能造成重查询。

### I8. `getOriginalFilename()` 可能返回 null

**文件**: `api/upload/UploadController.java:35`

Spring API 允许返回 null，会导致 `IndexNamingPolicy.indexNameFor(null)` NPE。

---

## Suggestion（建议改）

| ID | 问题 | 文件 |
|---|---|---|
| S1 | `RagController` 重复 import `RagResponse` | `api/rag/RagController.java:7` |
| S3 | CORS `allowedOriginPatterns("*")` + `allowCredentials(true)` 生产需收紧 | `infrastructure/config/CorsConfig.java` |
| S4 | `restClient == null` 时静默 return，应 fail-fast 或 warn | `OpenSearchDocumentWriter.java:34`, `OpenSearchIndexManager.java:57` |
| S5 | `LocalFileStorageAdapter` 文件名未校验，路径穿越风险 | `infrastructure/storage/LocalFileStorageAdapter.java:24` |
| S6 | AWS SDK 客户端未注册 `destroyMethod="close"`，关闭时可能泄漏连接 | `infrastructure/config/ClientConfig.java` |

---

## 测试覆盖盲区

- 无 ingestion 失败路径测试（`catch` 分支标记 FAILED）
- 无 rerank 过滤逻辑测试
- 无 `OpenSearchRetrievalAdapter.mergeUnique` 去重测试
- 无 `OpenSearchDocumentWriter` bulk payload 构造测试
- 无 `S3DocumentStorageAdapter` key 构造边界测试
- 无 `ApiExceptionHandler` 测试（C1 的 NPE 会被测试捕获）
- 无并发上传同文件的竞态测试

---

## 总体评价

架构层面很干净：分层边界清晰，domain 层零框架依赖，port/adapter 模式一致使用，不可变 record + 防御拷贝。

主要风险集中在 **C2（rerank 空壳）** 和 **C4（失败重传）**，前者影响 RAG 答案质量，后者影响用户体验。其余 Critical 和 Important 问题在 POC 阶段可容忍，但正式开发前必须处理。
