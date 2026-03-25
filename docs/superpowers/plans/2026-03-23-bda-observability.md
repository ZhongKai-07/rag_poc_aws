# BDA 解析可观测性 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增独立 Admin 页面，持久化 BDA 解析元数据，支持查看原始 BDA JSON 和 OpenSearch 已索引 chunks，用于解析质量验证和故障排查。

**Architecture:** `ParsedDocument` record 新增 `s3OutputPath`、`parserType`、`parserVersion` 字段，由 `BdaDocumentParserAdapter` 填充。ingestion 成功后将元数据写入新的 `bda_parse_result` PG 表（非阻断写入）。新增三个 `/admin/parse_results` 接口和 React `/admin` 页面（两栏 + 三 Tab）。

**Tech Stack:** Java 17, Spring Boot, JPA/Hibernate, Flyway, PostgreSQL, OpenSearch REST client, AWS S3 SDK, React + TypeScript, TanStack Query, Tailwind CSS, shadcn/ui

**Spec:** `docs/superpowers/specs/2026-03-23-bda-observability-design.md`

***

## File Map

### 新建文件

| 文件                                                                                                                 | 职责                                            |
| ------------------------------------------------------------------------------------------------------------------ | --------------------------------------------- |
| `backend-java/src/main/java/com/huatai/rag/domain/bda/BdaParseResultRecord.java`                                   | 域记录对象                                         |
| `backend-java/src/main/java/com/huatai/rag/domain/bda/BdaParseResultPort.java`                                     | 域端口接口（save / findAll / findLatestByIndexName） |
| `backend-java/src/main/resources/db/migration/V4__bda_parse_result.sql`                                            | Flyway 建表 migration                           |
| `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/entity/BdaParseResultEntity.java`            | JPA 实体                                        |
| `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/repository/BdaParseResultJpaRepository.java` | Spring Data JPA repository                    |
| `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/BdaParseResultPersistenceAdapter.java`       | 实现 BdaParseResultPort                         |
| `backend-java/src/main/java/com/huatai/rag/application/admin/ParseResultQueryApplicationService.java`              | Admin 查询编排（PG + OpenSearch + S3）              |
| `backend-java/src/main/java/com/huatai/rag/api/admin/AdminController.java`                                         | 3 个 admin 接口                                  |
| `frontend/src/api/adminApi.ts`                                                                                     | Admin API 客户端函数                               |
| `frontend/src/pages/AdminPage.tsx`                                                                                 | Admin 主页面（两栏布局）                               |
| `backend-java/src/test/java/com/huatai/rag/infrastructure/persistence/BdaParseResultRepositoryTest.java`           | JPA 读写测试（含 FK parent 行插入）                     |
| `backend-java/src/test/java/com/huatai/rag/infrastructure/persistence/BdaParseResultPersistenceAdapterTest.java`   | Adapter record↔entity 映射单元测试                  |
| `backend-java/src/test/java/com/huatai/rag/api/AdminControllerContractTest.java`                                   | Admin HTTP 契约测试                               |

### 修改文件

| 文件                                                                                                         | 变更                                                                                                 |
| ---------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `backend-java/src/main/java/com/huatai/rag/domain/parser/ParsedDocument.java`                              | 移除 `parserProvenance` 组件，新增 `s3OutputPath`、`parserType`、`parserVersion`，保留 `parserProvenance()` 方法 |
| `backend-java/src/main/java/com/huatai/rag/infrastructure/bda/BdaResultMapper.java`                        | `map()` 增加第 4 参数 `String s3OutputPath`                                                             |
| `backend-java/src/main/java/com/huatai/rag/infrastructure/bda/BdaDocumentParserAdapter.java`               | 传递 `outputUri` 给 `resultMapper.map()`                                                              |
| `backend-java/src/main/java/com/huatai/rag/application/ingestion/DocumentIngestionApplicationService.java` | 新增第 6 构造参数 `BdaParseResultPort`，parse 后非阻断写入                                                       |
| `backend-java/src/main/java/com/huatai/rag/infrastructure/config/ApplicationWiringConfig.java`             | 注入 `BdaParseResultPersistenceAdapter` 和新 bean                                                      |
| `backend-java/src/test/java/com/huatai/rag/infrastructure/bda/BdaResultMapperTest.java`                    | 更新 `map()` 调用为 4 参数                                                                                |
| `backend-java/src/test/java/com/huatai/rag/application/DocumentIngestionApplicationServiceTest.java`       | 更新 `ParsedDocument` 构造 + 新增 `BdaParseResultPort` mock                                              |
| `frontend/src/App.tsx`                                                                                     | 新增 `/admin` 路由                                                                                     |
| `frontend/src/components/Layout.tsx`                                                                       | 新增 DEV-only Admin 导航链接                                                                             |

***

## Chunk 1: Domain Layer

### Task 1: 扩展 ParsedDocument record

**Files:**

- Modify: `backend-java/src/main/java/com/huatai/rag/domain/parser/ParsedDocument.java`
- Modify: `backend-java/src/test/java/com/huatai/rag/application/DocumentIngestionApplicationServiceTest.java`（更新构造调用）
- [ ] **Step 1: 修改 ParsedDocument record**

将 `parserProvenance` 组件替换为三个新组件，保留 `parserProvenance()` 实例方法：

```java
// backend-java/src/main/java/com/huatai/rag/domain/parser/ParsedDocument.java
package com.huatai.rag.domain.parser;

import java.util.List;

public record ParsedDocument(
        String fileName,
        String indexName,
        List<ParsedPage> pages,
        List<ParsedChunk> chunks,
        List<ParsedAsset> assets,
        String s3OutputPath,
        String parserType,
        String parserVersion) {

    public ParsedDocument {
        pages = List.copyOf(pages);
        chunks = List.copyOf(chunks);
        assets = List.copyOf(assets);
    }

    public String parserProvenance() {
        return parserType + ":" + parserVersion;
    }
}
```

> **行为变更说明（已知并接受）：** 旧的 `BdaResultMapper.parserProvenance(payload)` 私有方法在 version 为空白字符串时返回单独的 `type`（如 `"aws-bda"`）。新的 `parserProvenance()` 实例方法始终返回 `type + ":" + version`。由于 `BdaResultMapper.parserVersion()` 的 fallback 返回 `"unknown"`（非空白），实际上 `parserVersion` 字段永远不会是空白字符串，因此输出格式从 `"aws-bda"` 变为 `"aws-bda:unknown"`（仅对无版本信息的 BDA payload）。这是与规范一致的有意变更。

- [ ] **Step 2: 编译确认有哪些调用点报错**

```bash
mvn -f backend-java/pom.xml compile -q 2>&1 | grep "error:"
```

预期：**两处均报构造器参数错误**：

1. `BdaResultMapper.java`：`new ParsedDocument(...)` 仍传 6 参数，第 6 参数 `parserProvenance(payload)` 已无对应 record 组件
2. `DocumentIngestionApplicationServiceTest.java`：同样以 6 参数构造 `ParsedDocument`，8 参数 record 不匹配

Step 3 修复第一处（仅改构造调用），Step 4 修复第二处，两处均需在本 Task 内完成。

- [ ] **Step 3: 暂时修复 BdaResultMapper 以让编译通过（只改构造调用，不改方法签名）**

注意：此步骤**只修改** **`map()`** **方法体内的** **`new ParsedDocument(...)`** **一行**，不修改 `map()` 的方法签名（仍保持 3 参数）。Task 3 才会修改签名。

在 `BdaResultMapper.map()` 中将：

```java
return new ParsedDocument(
        fileName,
        indexName,
        pages,
        chunks,
        new ArrayList<>(assetsById.values()),
        parserProvenance(payload));
```

改为：

```java
return new ParsedDocument(
        fileName,
        indexName,
        pages,
        chunks,
        new ArrayList<>(assetsById.values()),
        "",                      // s3OutputPath placeholder — filled in Task 3
        parserType(payload),
        parserVersion(payload));
```

- [ ] **Step 4: 修复 DocumentIngestionApplicationServiceTest 的构造调用**

找到测试中 `new ParsedDocument(...)` 的 6 参数构造，改为 8 参数。原来最后一个参数是 `"aws-bda:2025-03-01"`（合并的 parserProvenance 字符串），现在需要拆分为三个独立参数：

```java
// 在 DocumentIngestionApplicationServiceTest.java 中
ParsedDocument parsedDocument = new ParsedDocument(
        "PRC Client.pdf",
        IndexNamingPolicy.indexNameFor("PRC Client.pdf"),
        List.of(new ParsedPage(1, "Executive Summary", List.of("Executive Summary"))),
        List.of(chunk),
        List.of(),
        "s3://huatai-rag/_bda_output/2374dcf7.json",  // s3OutputPath（测试用任意合法 S3 URI）
        "aws-bda",                                      // parserType（原字符串 ":" 前部分）
        "2025-03-01");                                  // parserVersion（原字符串 ":" 后部分）
```

`parserProvenance()` 方法现在是实例方法，调用方式不变：`parsedDocument.parserProvenance()` 仍返回 `"aws-bda:2025-03-01"`，所有现有断言无需修改。

- [ ] **Step 5: 运行相关测试确认编译通过且测试不因 record 变化而失败**

```bash
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" \
  "-Dtest=DocumentIngestionApplicationServiceTest,BdaResultMapperTest" test -q
```

预期：

- 两个测试类均编译通过
- `BdaResultMapperTest` 中 `assertThat(parsedDocument.parserProvenance()).isEqualTo("aws-bda:2025-03-01")` 仍然 PASS（`parserType="aws-bda"`, `parserVersion="2025-03-01"` → 方法返回 `"aws-bda:2025-03-01"`）
- [ ] **Step 6: Commit**

> **注意（中间状态提交）：** 此提交中的 `BdaResultMapper.java` 包含 `s3OutputPath = ""` 占位符，这是有意为之的中间状态——Task 3 才会填入真实值。此 commit **不可发布到生产环境**，仅用于保持编译通过，便于后续 Task 的原子提交。

```bash
git add backend-java/src/main/java/com/huatai/rag/domain/parser/ParsedDocument.java \
        backend-java/src/main/java/com/huatai/rag/infrastructure/bda/BdaResultMapper.java \
        backend-java/src/test/java/com/huatai/rag/application/DocumentIngestionApplicationServiceTest.java
git commit -m "refactor: split parserProvenance into parserType/parserVersion on ParsedDocument, add s3OutputPath"
```

***

### Task 2: 新增 BdaParseResultRecord 和 BdaParseResultPort

**Files:**

- Create: `backend-java/src/main/java/com/huatai/rag/domain/bda/BdaParseResultRecord.java`
- Create: `backend-java/src/main/java/com/huatai/rag/domain/bda/BdaParseResultPort.java`
- [ ] **Step 1: 创建 BdaParseResultRecord**

```java
// backend-java/src/main/java/com/huatai/rag/domain/bda/BdaParseResultRecord.java
package com.huatai.rag.domain.bda;

import java.time.Instant;
import java.util.UUID;

public record BdaParseResultRecord(
        UUID id,
        UUID documentFileId,
        String indexName,
        String s3OutputPath,
        int chunkCount,
        int pageCount,
        String parserType,
        String parserVersion,
        Instant createdAt) {
}
```

- [ ] **Step 2: 创建 BdaParseResultPort**

```java
// backend-java/src/main/java/com/huatai/rag/domain/bda/BdaParseResultPort.java
package com.huatai.rag.domain.bda;

import java.util.List;
import java.util.Optional;

public interface BdaParseResultPort {

    BdaParseResultRecord save(BdaParseResultRecord record);

    List<BdaParseResultRecord> findAll();

    Optional<BdaParseResultRecord> findLatestByIndexName(String indexName);
}
```

- [ ] **Step 3: 编译确认**

```bash
mvn -f backend-java/pom.xml compile -q
```

预期：无错误。

- [ ] **Step 4: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/domain/bda/
git commit -m "feat: add BdaParseResultRecord and BdaParseResultPort domain types"
```

***

## Chunk 2: Infrastructure — BDA Adapter + Persistence

### Task 3: 更新 BdaResultMapper 和 BdaDocumentParserAdapter

**Files:**

- Modify: `backend-java/src/main/java/com/huatai/rag/infrastructure/bda/BdaResultMapper.java`
- Modify: `backend-java/src/main/java/com/huatai/rag/infrastructure/bda/BdaDocumentParserAdapter.java`
- Modify: `backend-java/src/test/java/com/huatai/rag/infrastructure/bda/BdaResultMapperTest.java`
- [ ] **Step 1: 写失败测试 — 验证 map() 正确传递 s3OutputPath**

在 `BdaResultMapperTest.java` 的 `mapsBdaPayloadIntoNormalizedParsedDocument` 测试中，在 `map()` 调用处加入 s3OutputPath 参数，并断言新字段：

```java
// 修改现有测试调用（当前是 3 参数，改为 4 参数）
ParsedDocument parsedDocument = new BdaResultMapper()
        .map(payload, "sample-financing.pdf", "c0ffee12",
             "s3://huatai-rag/_bda_output/c0ffee12.json");

// 在现有断言后新增
assertThat(parsedDocument.s3OutputPath())
        .isEqualTo("s3://huatai-rag/_bda_output/c0ffee12.json");
assertThat(parsedDocument.parserType()).isEqualTo("aws-bda");
assertThat(parsedDocument.parserVersion()).isEqualTo("2025-03-01");
assertThat(parsedDocument.parserProvenance()).isEqualTo("aws-bda:2025-03-01");
```

同样更新 `BdaResultMapperTest` 中 **所有其他** `map()` 调用点。`derivesChunksFromTopLevelPageTextWhenLegacyChunkArrayIsMissing` 中也有一处，改为：

```java
ParsedDocument parsedDocument = new BdaResultMapper()
        .map(payload, "sample-financing.pdf", "c0ffee12",
             "s3://huatai-rag/_bda_output/c0ffee12.json");
```

> **注意（红状态范围）：** Step 3 修改 `BdaResultMapper.map()` 签名为 4 参数后，`BdaDocumentParserAdapter` 内仍是 3 参数调用，会产生额外的编译错误，影响 `documentParserAdapterMapsClientPayloadIntoDomainDocument` 和 `documentParserAdapterUsesConfiguredOutputPrefixInsteadOfInputChildPath` 两个适配器测试。Step 4 修复 `BdaDocumentParserAdapter` 后这些错误消除。Step 1 只需更新测试文件中的直接 `map()` 调用点，适配器的编译错误留给 Step 4 处理。

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" \
  "-Dtest=BdaResultMapperTest" test -q
```

预期：编译错误，`map()` 参数不匹配。

> **注意（TDD 'red' 状态）：** Step 1 完成后整个模块将无法编译，因为测试现在调用了尚未存在的 4 参数 `map()` 重载。这是 **预期的中间状态**，不是需要修复的错误。Step 2 只是用来确认 test 确实指向了尚未实现的接口。看到编译错误即可，继续执行 Step 3 使其通过。

- [ ] **Step 3: 更新 BdaResultMapper.map() 签名**

只有两处改动：① 方法签名新增第 4 参数 `String s3OutputPath`；② `ParsedDocument` 构造中将 `parserProvenance(payload)` 替换为 `s3OutputPath, parserType(payload), parserVersion(payload)`。所有其他逻辑保持不变，完整方法体如下：

```java
// BdaResultMapper.java — 完整 map() 方法体（仅签名和 ParsedDocument 构造有变更）
public ParsedDocument map(JsonNode payload, String fileName, String indexName, String s3OutputPath) {
    JsonNode documentNode = payload.path("document");
    JsonNode pagesNode = selectPagesNode(payload, documentNode);
    Map<String, ParsedAsset> assetsById = mapAssets(documentNode.path("assets"));
    Map<Integer, List<String>> pageSections = mapPageSections(pagesNode);

    List<ParsedPage> pages = mapPages(pagesNode);
    List<ParsedChunk> chunks = mapChunks(
            documentNode.path("chunks"),
            pagesNode,
            assetsById,
            pageSections,
            fileName,
            parserType(payload),
            parserVersion(payload));

    return new ParsedDocument(
            fileName,
            indexName,
            pages,
            chunks,
            new ArrayList<>(assetsById.values()),
            s3OutputPath,
            parserType(payload),
            parserVersion(payload));
}
```

- [ ] **Step 4: 更新 BdaDocumentParserAdapter 传递 outputUri**

> **前置确认：** `outputUri` 变量在现有 `parse()` 方法体中已由 `defaultOutputUri(request.storagePath(), request.indexName())` 计算并赋值。**不需要新增任何变量声明**，只需将其作为第 4 个参数传给 `resultMapper.map()`。
>
> **重要：** 以下仅展示 `parse()` 方法体的变更，其余方法（`defaultOutputUri`、`joinSegments`、`normalizeSegment`、`requireText`）**保持完全不变，不得删除**。这是局部编辑，不是整文件替换。

```java
// BdaDocumentParserAdapter.java — 只修改 parse() 方法内的 resultMapper.map() 调用
@Override
public ParsedDocument parse(ParserRequest request) {
    String outputUri = defaultOutputUri(request.storagePath(), request.indexName());
    return resultMapper.map(
            bdaClient.parse(request.storagePath(), outputUri),
            request.fileName(),
            request.indexName(),
            outputUri);   // 新增第 4 参数——outputUri 已在上方声明，无需重复
}
```

- [ ] **Step 5: 运行测试确认通过**

```bash
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" \
  "-Dtest=BdaResultMapperTest" test -q
```

预期：所有测试 PASS。

- [ ] **Step 6: 运行全量测试确认无回归**

```bash
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" test -q
```

预期：所有测试 PASS。

- [ ] **Step 7: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/infrastructure/bda/BdaResultMapper.java \
        backend-java/src/main/java/com/huatai/rag/infrastructure/bda/BdaDocumentParserAdapter.java \
        backend-java/src/test/java/com/huatai/rag/infrastructure/bda/BdaResultMapperTest.java
git commit -m "feat: thread s3OutputPath from BdaDocumentParserAdapter through BdaResultMapper into ParsedDocument"
```

***

### Task 4: Flyway V4 migration + JPA 实体 + Repository

**Files:**

- Create: `backend-java/src/main/resources/db/migration/V4__bda_parse_result.sql`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/entity/BdaParseResultEntity.java`
- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/repository/BdaParseResultJpaRepository.java`
- Create: `backend-java/src/test/java/com/huatai/rag/infrastructure/persistence/BdaParseResultRepositoryTest.java`
- [ ] **Step 1: 确认 V3 migration 已提交（V4 前置检查）**

Flyway 按文件名顺序执行 migration。如果 V3 尚未提交、但已在本地 PG 上以无记录方式运行过，引入 V4 后可能触发 `FlywayException: Detected resolved migration not applied to database: 3`。先验证：

```bash
git status backend-java/src/main/resources/db/migration/
```

如果输出显示 `V3__filename_unique_constraint.sql` 为 `??`（未追踪），先提交它，再继续：

```bash
git add backend-java/src/main/resources/db/migration/V3__filename_unique_constraint.sql
git commit -m "feat: add V3 unique constraint migration for document_file filename"
```

如果 V3 已在历史 commit 中（`git log` 可见），跳过此步。

- [ ] **Step 2: 写失败测试**

```java
// backend-java/src/test/java/com/huatai/rag/infrastructure/persistence/BdaParseResultRepositoryTest.java
package com.huatai.rag.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.huatai.rag.domain.bda.BdaParseResultRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class BdaParseResultRepositoryTest {

    @Autowired
    private com.huatai.rag.infrastructure.persistence.repository.BdaParseResultJpaRepository jpaRepository;

    @Autowired
    private com.huatai.rag.infrastructure.persistence.repository.DocumentFileJpaRepository documentFileRepository;

    /** V4 migration has `document_file_id REFERENCES document_file(id)`.
     *  Insert a parent row first to satisfy the FK constraint. */
    private UUID insertDocumentFile(String filename, String indexName) {
        var docFile = new com.huatai.rag.infrastructure.persistence.entity.DocumentFileEntity();
        UUID id = UUID.randomUUID();
        docFile.setId(id);
        docFile.setFilename(filename);
        docFile.setIndexName(indexName);
        docFile.setStoragePath("s3://bucket/" + filename);
        docFile.setStatus("PROCESSED");
        documentFileRepository.save(docFile);
        return id;
    }

    @Test
    void savesAndFindsParseResult() {
        UUID docId = insertDocumentFile("sample.pdf", "ced4c5ef");
        var entity = new com.huatai.rag.infrastructure.persistence.entity.BdaParseResultEntity();
        entity.setId(UUID.randomUUID());
        entity.setDocumentFileId(docId);
        entity.setIndexName("ced4c5ef");
        entity.setS3OutputPath("s3://bucket/_bda_output/ced4c5ef.json/job-1/result.json");
        entity.setChunkCount(29);
        entity.setPageCount(8);
        entity.setParserType("aws-bda");
        entity.setParserVersion("2025-03-01");
        entity.setCreatedAt(Instant.now());

        jpaRepository.save(entity);

        List<com.huatai.rag.infrastructure.persistence.entity.BdaParseResultEntity> all = jpaRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getIndexName()).isEqualTo("ced4c5ef");
    }

    @Test
    void findsLatestByIndexName() {
        UUID docId = insertDocumentFile("report.pdf", "ced4c5ef");
        Instant older = Instant.parse("2026-03-01T00:00:00Z");
        Instant newer = Instant.parse("2026-03-22T00:00:00Z");

        for (Instant ts : List.of(older, newer)) {
            var e = new com.huatai.rag.infrastructure.persistence.entity.BdaParseResultEntity();
            e.setId(UUID.randomUUID());
            e.setDocumentFileId(docId);
            e.setIndexName("ced4c5ef");
            e.setS3OutputPath("s3://bucket/result.json");
            e.setChunkCount(10);
            e.setPageCount(3);
            e.setParserType("aws-bda");
            e.setParserVersion("2025-03-01");
            e.setCreatedAt(ts);
            jpaRepository.save(e);
        }

        Optional<com.huatai.rag.infrastructure.persistence.entity.BdaParseResultEntity> latest =
                jpaRepository.findFirstByIndexNameOrderByCreatedAtDesc("ced4c5ef");

        assertThat(latest).isPresent();
        assertThat(latest.get().getCreatedAt()).isEqualTo(newer);
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

```bash
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" \
  "-Dtest=BdaParseResultRepositoryTest" test -q
```

预期：编译错误，类不存在。

- [ ] **Step 4: 创建 Flyway V4 migration**

使用与 V1 migration 相同的类型写法（`timestamp with time zone`，不用 `TIMESTAMPTZ`）：

```sql
-- backend-java/src/main/resources/db/migration/V4__bda_parse_result.sql
CREATE TABLE bda_parse_result (
    id               UUID PRIMARY KEY,
    document_file_id UUID NOT NULL REFERENCES document_file(id),
    index_name       VARCHAR(128) NOT NULL,
    s3_output_path   VARCHAR(1024) NOT NULL,
    chunk_count      INTEGER NOT NULL DEFAULT 0,
    page_count       INTEGER NOT NULL DEFAULT 0,
    parser_type      VARCHAR(64),
    parser_version   VARCHAR(64),
    created_at       timestamp with time zone NOT NULL DEFAULT now()
);

CREATE INDEX ON bda_parse_result (document_file_id);
CREATE INDEX ON bda_parse_result (index_name);
```

- [ ] **Step 5: 创建 BdaParseResultEntity**

```java
// backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/entity/BdaParseResultEntity.java
package com.huatai.rag.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bda_parse_result")
public class BdaParseResultEntity {

    @Id
    private UUID id;

    @Column(name = "document_file_id", nullable = false)
    private UUID documentFileId;

    @Column(name = "index_name", nullable = false, length = 128)
    private String indexName;

    @Column(name = "s3_output_path", nullable = false, length = 1024)
    private String s3OutputPath;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "parser_type", length = 64)
    private String parserType;

    @Column(name = "parser_version", length = 64)
    private String parserVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDocumentFileId() { return documentFileId; }
    public void setDocumentFileId(UUID documentFileId) { this.documentFileId = documentFileId; }
    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }
    public String getS3OutputPath() { return s3OutputPath; }
    public void setS3OutputPath(String s3OutputPath) { this.s3OutputPath = s3OutputPath; }
    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }
    public String getParserType() { return parserType; }
    public void setParserType(String parserType) { this.parserType = parserType; }
    public String getParserVersion() { return parserVersion; }
    public void setParserVersion(String parserVersion) { this.parserVersion = parserVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 6: 创建 BdaParseResultJpaRepository**

```java
// backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/repository/BdaParseResultJpaRepository.java
package com.huatai.rag.infrastructure.persistence.repository;

import com.huatai.rag.infrastructure.persistence.entity.BdaParseResultEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BdaParseResultJpaRepository extends JpaRepository<BdaParseResultEntity, UUID> {

    Optional<BdaParseResultEntity> findFirstByIndexNameOrderByCreatedAtDesc(String indexName);
}
```

- [ ] **Step 7: 运行测试确认通过**

```bash
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" \
  "-Dtest=BdaParseResultRepositoryTest" test -q
```

预期：PASS。

- [ ] **Step 8: Commit**

```bash
git add backend-java/src/main/resources/db/migration/V4__bda_parse_result.sql \
        backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/entity/BdaParseResultEntity.java \
        backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/repository/BdaParseResultJpaRepository.java \
        backend-java/src/test/java/com/huatai/rag/infrastructure/persistence/BdaParseResultRepositoryTest.java
git commit -m "feat: add V4 Flyway migration and JPA entity/repository for bda_parse_result"
```

***

### Task 5: BdaParseResultPersistenceAdapter

**Files:**

- Create: `backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/BdaParseResultPersistenceAdapter.java`
- Create: `backend-java/src/test/java/com/huatai/rag/infrastructure/persistence/BdaParseResultPersistenceAdapterTest.java`
- [ ] **Step 1: 写失败测试 — 验证 adapter 的 record↔entity 映射和 Optional 委托**

```java
// backend-java/src/test/java/com/huatai/rag/infrastructure/persistence/BdaParseResultPersistenceAdapterTest.java
package com.huatai.rag.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huatai.rag.domain.bda.BdaParseResultRecord;
import com.huatai.rag.infrastructure.persistence.entity.BdaParseResultEntity;
import com.huatai.rag.infrastructure.persistence.repository.BdaParseResultJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BdaParseResultPersistenceAdapterTest {

    private final BdaParseResultJpaRepository jpaRepository = mock(BdaParseResultJpaRepository.class);
    private final BdaParseResultPersistenceAdapter adapter = new BdaParseResultPersistenceAdapter(jpaRepository);

    @Test
    void saveMapsRecordToEntityAndBack() {
        UUID id = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        Instant now = Instant.now();
        BdaParseResultRecord record = new BdaParseResultRecord(
                id, docId, "ced4c5ef", "s3://bucket/result.json",
                29, 8, "aws-bda", "2025-03-01", now);

        BdaParseResultEntity savedEntity = new BdaParseResultEntity();
        savedEntity.setId(id);
        savedEntity.setDocumentFileId(docId);
        savedEntity.setIndexName("ced4c5ef");
        savedEntity.setS3OutputPath("s3://bucket/result.json");
        savedEntity.setChunkCount(29);
        savedEntity.setPageCount(8);
        savedEntity.setParserType("aws-bda");
        savedEntity.setParserVersion("2025-03-01");
        savedEntity.setCreatedAt(now);
        when(jpaRepository.save(any())).thenReturn(savedEntity);

        BdaParseResultRecord result = adapter.save(record);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.indexName()).isEqualTo("ced4c5ef");
        assertThat(result.chunkCount()).isEqualTo(29);
        assertThat(result.parserType()).isEqualTo("aws-bda");
    }

    @Test
    void findLatestByIndexNameMapsOptionalEntityToOptionalRecord() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        BdaParseResultEntity entity = new BdaParseResultEntity();
        entity.setId(id);
        entity.setDocumentFileId(UUID.randomUUID());
        entity.setIndexName("ced4c5ef");
        entity.setS3OutputPath("s3://bucket/result.json");
        entity.setChunkCount(5);
        entity.setPageCount(2);
        entity.setParserType("aws-bda");
        entity.setParserVersion("2025-03-01");
        entity.setCreatedAt(now);
        when(jpaRepository.findFirstByIndexNameOrderByCreatedAtDesc("ced4c5ef"))
                .thenReturn(Optional.of(entity));

        Optional<BdaParseResultRecord> result = adapter.findLatestByIndexName("ced4c5ef");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(id);
        assertThat(result.get().pageCount()).isEqualTo(2);
    }

    @Test
    void findAllDelegatesToJpaAndMapsToRecords() {
        BdaParseResultEntity entity = new BdaParseResultEntity();
        entity.setId(UUID.randomUUID());
        entity.setDocumentFileId(UUID.randomUUID());
        entity.setIndexName("abc12345");
        entity.setS3OutputPath("s3://bucket/result.json");
        entity.setChunkCount(3);
        entity.setPageCount(1);
        entity.setParserType("aws-bda");
        entity.setParserVersion("2025-03-01");
        entity.setCreatedAt(Instant.now());
        when(jpaRepository.findAll()).thenReturn(List.of(entity));

        List<BdaParseResultRecord> records = adapter.findAll();

        assertThat(records).hasSize(1);
        assertThat(records.get(0).indexName()).isEqualTo("abc12345");
    }

    @Test
    void findLatestByIndexNameReturnsEmptyWhenNotFound() {
        when(jpaRepository.findFirstByIndexNameOrderByCreatedAtDesc("nonexistent"))
                .thenReturn(Optional.empty());

        assertThat(adapter.findLatestByIndexName("nonexistent")).isEmpty();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" \
  "-Dtest=BdaParseResultPersistenceAdapterTest" test -q
```

预期：编译错误，`BdaParseResultPersistenceAdapter` 不存在。

- [ ] **Step 3: 创建 adapter**

```java
// backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/BdaParseResultPersistenceAdapter.java
package com.huatai.rag.infrastructure.persistence;

import com.huatai.rag.domain.bda.BdaParseResultPort;
import com.huatai.rag.domain.bda.BdaParseResultRecord;
import com.huatai.rag.infrastructure.persistence.entity.BdaParseResultEntity;
import com.huatai.rag.infrastructure.persistence.repository.BdaParseResultJpaRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class BdaParseResultPersistenceAdapter implements BdaParseResultPort {

    private final BdaParseResultJpaRepository jpaRepository;

    public BdaParseResultPersistenceAdapter(BdaParseResultJpaRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "jpaRepository");
    }

    @Override
    public BdaParseResultRecord save(BdaParseResultRecord record) {
        BdaParseResultEntity entity = new BdaParseResultEntity();
        entity.setId(record.id() == null ? UUID.randomUUID() : record.id());
        entity.setDocumentFileId(record.documentFileId());
        entity.setIndexName(record.indexName());
        entity.setS3OutputPath(record.s3OutputPath());
        entity.setChunkCount(record.chunkCount());
        entity.setPageCount(record.pageCount());
        entity.setParserType(record.parserType());
        entity.setParserVersion(record.parserVersion());
        entity.setCreatedAt(record.createdAt());
        return toRecord(jpaRepository.save(entity));
    }

    @Override
    public List<BdaParseResultRecord> findAll() {
        return jpaRepository.findAll().stream().map(this::toRecord).toList();
    }

    @Override
    public Optional<BdaParseResultRecord> findLatestByIndexName(String indexName) {
        return jpaRepository.findFirstByIndexNameOrderByCreatedAtDesc(indexName).map(this::toRecord);
    }

    private BdaParseResultRecord toRecord(BdaParseResultEntity entity) {
        return new BdaParseResultRecord(
                entity.getId(),
                entity.getDocumentFileId(),
                entity.getIndexName(),
                entity.getS3OutputPath(),
                entity.getChunkCount(),
                entity.getPageCount(),
                entity.getParserType(),
                entity.getParserVersion(),
                entity.getCreatedAt());
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" \
  "-Dtest=BdaParseResultPersistenceAdapterTest" test -q
```

预期：3 个测试均 PASS。

- [ ] **Step 5: 编译确认（全量）**

```bash
mvn -f backend-java/pom.xml compile -q
```

- [ ] **Step 6: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/BdaParseResultPersistenceAdapter.java \
        backend-java/src/test/java/com/huatai/rag/infrastructure/persistence/BdaParseResultPersistenceAdapterTest.java
git commit -m "feat: add BdaParseResultPersistenceAdapter implementing BdaParseResultPort"
```

***

## Chunk 3: Application Services + Admin API

### Task 6: 更新 DocumentIngestionApplicationService

**Files:**

- Modify: `backend-java/src/main/java/com/huatai/rag/application/ingestion/DocumentIngestionApplicationService.java`
- Modify: `backend-java/src/test/java/com/huatai/rag/application/DocumentIngestionApplicationServiceTest.java`
- [ ] **Step 1: 在测试中验证 BdaParseResultPort.save() 被调用**

在 `DocumentIngestionApplicationServiceTest.java` 中：

1. 新增 `FakeBdaParseResultPort` fake（在测试类末尾）：

```java
static class FakeBdaParseResultPort implements com.huatai.rag.domain.bda.BdaParseResultPort {
    com.huatai.rag.domain.bda.BdaParseResultRecord savedRecord;

    @Override
    public com.huatai.rag.domain.bda.BdaParseResultRecord save(
            com.huatai.rag.domain.bda.BdaParseResultRecord record) {
        this.savedRecord = record;
        return record;
    }

    @Override
    public java.util.List<com.huatai.rag.domain.bda.BdaParseResultRecord> findAll() {
        return java.util.List.of();
    }

    @Override
    public java.util.Optional<com.huatai.rag.domain.bda.BdaParseResultRecord> findLatestByIndexName(String indexName) {
        return java.util.Optional.empty();
    }
}
```

1. 在主测试方法中，构造 `Default` 时传入第 6 参数，并新增断言：

> **重要前置说明：** 测试中的 `FakeParser.parse()` 直接返回构造器传入的 `parsedDocument` 实例（见测试类 `FakeParser` 内部类：`return parsedDocument;`）。Task 1 Step 4 已将该实例的 `s3OutputPath` 设置为 `"s3://huatai-rag/_bda_output/2374dcf7.json"`。因此，service 调用 `parser.parse()` 后拿到的 `parsedDocument.s3OutputPath()` 正是这个值，后续 `bdaParseResultPort.save()` 中写入的 `s3OutputPath` 断言才能成立。

```java
FakeBdaParseResultPort parseResultPort = new FakeBdaParseResultPort();

DocumentIngestionApplicationService.Default service = new DocumentIngestionApplicationService.Default(
        storage,
        registryPort,
        parser,
        embeddingPort,
        indexWriter,
        parseResultPort);   // 新增第 6 参数

// ... 原有断言保持不变 ...

// 新增断言
assertThat(parseResultPort.savedRecord).isNotNull();
assertThat(parseResultPort.savedRecord.indexName()).isEqualTo("2374dcf7");
assertThat(parseResultPort.savedRecord.s3OutputPath())
        .isEqualTo("s3://huatai-rag/_bda_output/2374dcf7.json");
assertThat(parseResultPort.savedRecord.chunkCount()).isEqualTo(1);
assertThat(parseResultPort.savedRecord.pageCount()).isEqualTo(1);
assertThat(parseResultPort.savedRecord.parserType()).isEqualTo("aws-bda");
assertThat(parseResultPort.savedRecord.parserVersion()).isEqualTo("2025-03-01");
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" \
  "-Dtest=DocumentIngestionApplicationServiceTest" test -q
```

预期：编译错误，`Default` 构造器参数数量不匹配。

- [ ] **Step 3: 更新 DocumentIngestionApplicationService.Default**

在 `Default` 内部类中：

```java
// 新增 import
import com.huatai.rag.domain.bda.BdaParseResultPort;
import com.huatai.rag.domain.bda.BdaParseResultRecord;
import java.time.Instant;
```

修改 `Default` 构造器，新增第 6 参数（只展示变更的两处：field 声明 + 构造器。其余 field 声明和赋值不变）：

```java
// 在现有 5 个 field 声明之后新增:
private final BdaParseResultPort bdaParseResultPort;

// 将现有 5 参数构造器改为 6 参数（只新增最后一个参数 + 赋值，其余不变）:
public Default(
        DocumentStorage documentStorage,
        DocumentRegistryPort documentRegistryPort,
        DocumentParser documentParser,
        EmbeddingPort embeddingPort,
        DocumentChunkWriter documentChunkWriter,
        BdaParseResultPort bdaParseResultPort) {
    this.documentStorage = Objects.requireNonNull(documentStorage, "documentStorage");
    this.documentRegistryPort = Objects.requireNonNull(documentRegistryPort, "documentRegistryPort");
    this.documentParser = Objects.requireNonNull(documentParser, "documentParser");
    this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
    this.documentChunkWriter = Objects.requireNonNull(documentChunkWriter, "documentChunkWriter");
    this.bdaParseResultPort = Objects.requireNonNull(bdaParseResultPort, "bdaParseResultPort");
}
```

在 `handle()` 方法的外层 `try` 块（`try { ParsedDocument parsedDocument = ...` 开始，`} catch (RuntimeException exception)` 结束）内，在 `documentParser.parse()` 调用之后、`List<String> sentences = ...` 之前插入非阻断写入块。完整插入上下文如下（展示 `try` 块开始到 `sentences` 变量，中间新增内层 try/catch）：

```java
try {
    ParsedDocument parsedDocument = documentParser.parse(new ParserRequest(
            command.filename(),
            indexName,
            storedDocument.storagePath()));
    log.info("Parsed document {} into {} chunks", command.filename(), parsedDocument.chunks().size());

    // ↓ 新增：非阻断写入解析元数据（内层 try/catch，失败不影响外层 catch(RuntimeException)）
    try {
        bdaParseResultPort.save(new BdaParseResultRecord(
                null,
                processingDocument.id(),
                indexName,
                parsedDocument.s3OutputPath(),
                parsedDocument.chunks().size(),
                parsedDocument.pages().size(),
                parsedDocument.parserType(),
                parsedDocument.parserVersion(),
                Instant.now()));
    } catch (Exception e) {
        log.warn("Failed to save BDA parse result for index {}: {}", indexName, e.getMessage());
    }
    // ↑ 新增结束

    List<String> sentences = parsedDocument.chunks().stream()
            .map(ParsedChunk::sentenceText)
            .toList();
    // ... 以下代码不变 ...
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" \
  "-Dtest=DocumentIngestionApplicationServiceTest" test -q
```

预期：PASS。

- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/application/ingestion/DocumentIngestionApplicationService.java \
        backend-java/src/test/java/com/huatai/rag/application/DocumentIngestionApplicationServiceTest.java
git commit -m "feat: persist BDA parse result after successful ingestion parse step"
```

***

### Task 7: ParseResultQueryApplicationService + ApplicationWiringConfig

**Files:**

- Create: `backend-java/src/main/java/com/huatai/rag/application/admin/ParseResultQueryApplicationService.java`
- Modify: `backend-java/src/main/java/com/huatai/rag/infrastructure/config/ApplicationWiringConfig.java`
- [ ] **Step 1: 创建 ParseResultQueryApplicationService**

```java
// backend-java/src/main/java/com/huatai/rag/application/admin/ParseResultQueryApplicationService.java
package com.huatai.rag.application.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.domain.bda.BdaParseResultPort;
import com.huatai.rag.domain.bda.BdaParseResultRecord;
import com.huatai.rag.domain.document.DocumentRegistryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.opensearch.client.Request;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

public class ParseResultQueryApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ParseResultQueryApplicationService.class);

    private final BdaParseResultPort bdaParseResultPort;
    private final DocumentRegistryPort documentRegistryPort;
    private final S3Client s3Client;
    private final RestClient openSearchRestClient;
    private final ObjectMapper objectMapper;

    public ParseResultQueryApplicationService(
            BdaParseResultPort bdaParseResultPort,
            DocumentRegistryPort documentRegistryPort,
            S3Client s3Client,
            RestClient openSearchRestClient,
            ObjectMapper objectMapper) {
        this.bdaParseResultPort = Objects.requireNonNull(bdaParseResultPort);
        this.documentRegistryPort = Objects.requireNonNull(documentRegistryPort);
        this.s3Client = Objects.requireNonNull(s3Client);
        this.openSearchRestClient = Objects.requireNonNull(openSearchRestClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    // --- domain records for responses ---

    public record ParseResultSummary(
            String indexName,
            String filename,
            int chunkCount,
            int pageCount,
            String parserType,
            String parserVersion,
            Instant createdAt) {}

    public record IndexedChunk(
            String chunkId,
            int pageNumber,
            List<String> sectionPath,
            String paragraph,
            String sentence,
            List<String> assetReferences) {}

    // --- query methods ---

    public List<ParseResultSummary> listAll() {
        return bdaParseResultPort.findAll().stream()
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .map(record -> {
                    String filename = documentRegistryPort
                            .findByIndexName(record.indexName())
                            .map(doc -> doc.filename())
                            .orElse(record.indexName());
                    return new ParseResultSummary(
                            record.indexName(),
                            filename,
                            record.chunkCount(),
                            record.pageCount(),
                            record.parserType(),
                            record.parserVersion(),
                            record.createdAt());
                })
                .toList();
    }

    public Optional<JsonNode> fetchRawBdaJson(String indexName) {
        Optional<BdaParseResultRecord> record = bdaParseResultPort.findLatestByIndexName(indexName);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        String s3Uri = record.get().s3OutputPath();
        try {
            S3Location loc = S3Location.parse(s3Uri);
            byte[] bytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(loc.bucket()).key(loc.key()).build()
            ).asByteArray();
            return Optional.of(objectMapper.readTree(bytes));
        } catch (NoSuchKeyException e) {
            log.warn("BDA result.json not found in S3: {}", s3Uri);
            throw new S3ObjectNotFoundException("BDA output not found at: " + s3Uri);
        } catch (Exception e) {
            log.error("Failed to fetch BDA JSON from S3: {}", s3Uri, e);
            throw new S3FetchException("Failed to fetch BDA output: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<IndexedChunk> fetchIndexedChunks(String indexName) {
        String body = """
                {"query":{"match_all":{}},"size":200,"_source":{"excludes":["sentence_vector"]}}
                """;
        Request request = new Request("POST", "/" + indexName + "/_search");
        request.setJsonEntity(body);
        try {
            var response = openSearchRestClient.performRequest(request);
            Map<String, Object> responseBody = objectMapper.readValue(
                    response.getEntity().getContent(),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
            Map<String, Object> hitsContainer = (Map<String, Object>) responseBody.getOrDefault("hits", Map.of());
            List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsContainer.getOrDefault("hits", List.of());
            return hits.stream().map(hit -> {
                Map<String, Object> source = (Map<String, Object>) hit.getOrDefault("_source", Map.of());
                Map<String, Object> metadata = (Map<String, Object>) source.getOrDefault("metadata", Map.of());
                return new IndexedChunk(
                        (String) metadata.getOrDefault("chunk_id", ""),
                        ((Number) metadata.getOrDefault("page_number", 0)).intValue(),
                        (List<String>) metadata.getOrDefault("section_path", List.of()),
                        (String) source.getOrDefault("paragraph", ""),
                        (String) source.getOrDefault("sentence", ""),
                        (List<String>) metadata.getOrDefault("asset_references", List.of()));
            }).toList();
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                throw new IndexNotFoundException("OpenSearch index not found: " + indexName);
            }
            throw new IllegalStateException("OpenSearch search failed", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("OpenSearch search I/O failure", e);
        }
    }

    // --- exceptions ---
    public static class S3ObjectNotFoundException extends RuntimeException {
        public S3ObjectNotFoundException(String msg) { super(msg); }
    }
    public static class S3FetchException extends RuntimeException {
        public S3FetchException(String msg) { super(msg); }
    }
    public static class IndexNotFoundException extends RuntimeException {
        public IndexNotFoundException(String msg) { super(msg); }
    }

    // --- S3 URI parser ---
    private record S3Location(String bucket, String key) {
        static S3Location parse(String uri) {
            if (uri == null || !uri.startsWith("s3://")) {
                throw new IllegalArgumentException("Expected S3 URI: " + uri);
            }
            String withoutScheme = uri.substring(5);
            int sep = withoutScheme.indexOf('/');
            return new S3Location(withoutScheme.substring(0, sep), withoutScheme.substring(sep + 1));
        }
    }
}
```

- [ ] **Step 2: 更新 ApplicationWiringConfig**

在 `ApplicationWiringConfig` 中新增以下 beans（imports 和 `@Bean` 方法）：

```java
// 新增 import
import com.huatai.rag.application.admin.ParseResultQueryApplicationService;
import com.huatai.rag.domain.bda.BdaParseResultPort;
import com.huatai.rag.infrastructure.persistence.BdaParseResultPersistenceAdapter;
import com.huatai.rag.infrastructure.persistence.repository.BdaParseResultJpaRepository;

// 新增 beans

@Bean
public BdaParseResultPort bdaParseResultPort(BdaParseResultJpaRepository jpaRepository) {
    return new BdaParseResultPersistenceAdapter(jpaRepository);
}

@Bean
public ParseResultQueryApplicationService parseResultQueryApplicationService(
        BdaParseResultPort bdaParseResultPort,
        DocumentRegistryPort documentRegistryPort,
        S3Client s3Client,
        RestClient openSearchRestClient,
        ObjectMapper objectMapper) {
    return new ParseResultQueryApplicationService(
            bdaParseResultPort,
            documentRegistryPort,
            s3Client,
            openSearchRestClient,
            objectMapper);
}
```

同时更新现有 `documentIngestionApplicationService` bean，注入第 6 参数：

```java
@Bean
public DocumentIngestionApplicationService documentIngestionApplicationService(
        DocumentIngestionApplicationService.DocumentStorage documentStorage,
        DocumentRegistryPort documentRegistryPort,
        DocumentParser documentParser,
        EmbeddingPort embeddingPort,
        DocumentIngestionApplicationService.DocumentChunkWriter documentChunkWriter,
        BdaParseResultPort bdaParseResultPort) {
    return new DocumentIngestionApplicationService.Default(
            documentStorage,
            documentRegistryPort,
            documentParser,
            embeddingPort,
            documentChunkWriter,
            bdaParseResultPort);
}
```

- [ ] **Step 3: 运行全量测试确认无回归**

```bash
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" test -q
```

预期：所有测试 PASS。

- [ ] **Step 4: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/application/admin/ \
        backend-java/src/main/java/com/huatai/rag/infrastructure/config/ApplicationWiringConfig.java
git commit -m "feat: add ParseResultQueryApplicationService and wire BdaParseResultPort into ingestion service"
```

***

### Task 8: AdminController

**Files:**

- Create: `backend-java/src/main/java/com/huatai/rag/api/admin/AdminController.java`
- Create: `backend-java/src/test/java/com/huatai/rag/api/AdminControllerContractTest.java`
- [ ] **Step 1: 写失败测试**

```java
// backend-java/src/test/java/com/huatai/rag/api/AdminControllerContractTest.java
package com.huatai.rag.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huatai.rag.api.admin.AdminController;
import com.huatai.rag.api.common.ApiExceptionHandler;
import com.huatai.rag.application.admin.ParseResultQueryApplicationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AdminController.class})
@Import(ApiExceptionHandler.class)
class AdminControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ParseResultQueryApplicationService parseResultQueryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getParseResultsReturnsList() throws Exception {
        when(parseResultQueryService.listAll()).thenReturn(List.of(
                new ParseResultQueryApplicationService.ParseResultSummary(
                        "ced4c5ef", "sample.pdf", 29, 8,
                        "aws-bda", "2025-03-01",
                        Instant.parse("2026-03-22T10:00:00Z"))));

        mockMvc.perform(get("/admin/parse_results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].index_name").value("ced4c5ef"))
                .andExpect(jsonPath("$[0].filename").value("sample.pdf"))
                .andExpect(jsonPath("$[0].chunk_count").value(29));
    }

    @Test
    void getRawReturns404WhenNotFound() throws Exception {
        when(parseResultQueryService.fetchRawBdaJson(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/parse_results/unknown/raw"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRawReturns502OnS3Failure() throws Exception {
        when(parseResultQueryService.fetchRawBdaJson(anyString()))
                .thenThrow(new ParseResultQueryApplicationService.S3FetchException("S3 error"));

        mockMvc.perform(get("/admin/parse_results/ced4c5ef/raw"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void getRawReturns502WhenS3ObjectMissing() throws Exception {
        when(parseResultQueryService.fetchRawBdaJson(anyString()))
                .thenThrow(new ParseResultQueryApplicationService.S3ObjectNotFoundException("key not found"));

        mockMvc.perform(get("/admin/parse_results/ced4c5ef/raw"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void getChunksReturns404WhenIndexNotFound() throws Exception {
        when(parseResultQueryService.fetchIndexedChunks(anyString()))
                .thenThrow(new ParseResultQueryApplicationService.IndexNotFoundException("not found"));

        mockMvc.perform(get("/admin/parse_results/ced4c5ef/chunks"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getChunksReturnsChunkList() throws Exception {
        when(parseResultQueryService.fetchIndexedChunks("ced4c5ef")).thenReturn(List.of(
                new ParseResultQueryApplicationService.IndexedChunk(
                        "chunk-1", 1, List.of("Executive Summary"),
                        "The client agreement...", "The client agreement.", List.of())));

        mockMvc.perform(get("/admin/parse_results/ced4c5ef/chunks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chunk_id").value("chunk-1"))
                .andExpect(jsonPath("$[0].page_number").value(1));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" \
  "-Dtest=AdminControllerContractTest" test -q
```

预期：编译错误，`AdminController` 不存在。

- [ ] **Step 3: 创建 AdminController**

```java
// backend-java/src/main/java/com/huatai/rag/api/admin/AdminController.java
package com.huatai.rag.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.huatai.rag.application.admin.ParseResultQueryApplicationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ParseResultQueryApplicationService parseResultQueryService;

    public AdminController(ParseResultQueryApplicationService parseResultQueryService) {
        this.parseResultQueryService = parseResultQueryService;
    }

    @GetMapping("/parse_results")
    public List<Map<String, Object>> listParseResults() {
        return parseResultQueryService.listAll().stream()
                .map(s -> Map.<String, Object>of(
                        "index_name", s.indexName(),
                        "filename", s.filename(),
                        "chunk_count", s.chunkCount(),
                        "page_count", s.pageCount(),
                        "parser_type", s.parserType() != null ? s.parserType() : "",
                        "parser_version", s.parserVersion() != null ? s.parserVersion() : "",
                        "created_at", s.createdAt().toString()))
                .toList();
    }

    @GetMapping("/parse_results/{indexName}/raw")
    public ResponseEntity<JsonNode> getRawBdaJson(@PathVariable String indexName) {
        Optional<JsonNode> result = parseResultQueryService.fetchRawBdaJson(indexName);
        return result.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/parse_results/{indexName}/chunks")
    public List<Map<String, Object>> getIndexedChunks(@PathVariable String indexName) {
        return parseResultQueryService.fetchIndexedChunks(indexName).stream()
                .map(c -> Map.<String, Object>of(
                        "chunk_id", c.chunkId(),
                        "page_number", c.pageNumber(),
                        "section_path", c.sectionPath(),
                        "paragraph", c.paragraph(),
                        "sentence", c.sentence(),
                        "asset_references", c.assetReferences()))
                .toList();
    }
}
```

- [ ] **Step 4: 在 ApiExceptionHandler 新增 502 和 404 处理**

在 `ApiExceptionHandler.java` 中，先在文件顶部 import 区新增：

```java
import com.huatai.rag.application.admin.ParseResultQueryApplicationService;
```

然后在现有 handler 方法之后新增三个 handler：

```java
@ExceptionHandler(ParseResultQueryApplicationService.S3FetchException.class)
public ResponseEntity<Map<String, Object>> handleS3Fetch(
        ParseResultQueryApplicationService.S3FetchException e) {
    log.error("S3 fetch failed", e);
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("detail", "BDA output fetch failed"));
}

@ExceptionHandler(ParseResultQueryApplicationService.S3ObjectNotFoundException.class)
public ResponseEntity<Map<String, Object>> handleS3ObjectNotFound(
        ParseResultQueryApplicationService.S3ObjectNotFoundException e) {
    log.warn("BDA output not found in S3: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("detail", e.getMessage()));
}

@ExceptionHandler(ParseResultQueryApplicationService.IndexNotFoundException.class)
public ResponseEntity<Map<String, Object>> handleIndexNotFound(
        ParseResultQueryApplicationService.IndexNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("detail", e.getMessage()));
}
```

- [ ] **Step 5: 运行 Admin 契约测试确认通过**

```bash
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" \
  "-Dtest=AdminControllerContractTest" test -q
```

预期：PASS。

- [ ] **Step 6: 运行全量测试确认无回归**

```bash
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" test -q
```

预期：所有测试 PASS。

- [ ] **Step 7: Commit**

```bash
git add backend-java/src/main/java/com/huatai/rag/api/admin/ \
        backend-java/src/main/java/com/huatai/rag/api/common/ApiExceptionHandler.java \
        backend-java/src/test/java/com/huatai/rag/api/AdminControllerContractTest.java
git commit -m "feat: add AdminController with parse_results list/raw/chunks endpoints"
```

***

## Chunk 4: Frontend

### Task 9: Admin API 客户端 + 路由

**Files:**

- Create: `frontend/src/api/adminApi.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/Layout.tsx`
- [ ] **Step 1: 创建 adminApi.ts**

```typescript
// frontend/src/api/adminApi.ts
const BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8001";

export interface ParseResultSummary {
  index_name: string;
  filename: string;
  chunk_count: number;
  page_count: number;
  parser_type: string;
  parser_version: string;
  created_at: string;
}

export interface IndexedChunk {
  chunk_id: string;
  page_number: number;
  section_path: string[];
  paragraph: string;
  sentence: string;
  asset_references: string[];
}

export async function fetchParseResults(): Promise<ParseResultSummary[]> {
  const res = await fetch(`${BASE}/admin/parse_results`);
  if (!res.ok) throw new Error(`Failed to fetch parse results: ${res.status}`);
  return res.json();
}

export async function fetchRawBdaJson(indexName: string): Promise<unknown> {
  const res = await fetch(`${BASE}/admin/parse_results/${indexName}/raw`);
  if (res.status === 404) throw new Error("Parse result not found");
  if (!res.ok) throw new Error(`Failed to fetch raw BDA JSON: ${res.status}`);
  return res.json();
}

export async function fetchIndexedChunks(indexName: string): Promise<IndexedChunk[]> {
  const res = await fetch(`${BASE}/admin/parse_results/${indexName}/chunks`);
  if (res.status === 404) throw new Error("Index not found");
  if (!res.ok) throw new Error(`Failed to fetch chunks: ${res.status}`);
  return res.json();
}
```

- [ ] **Step 2: 在 App.tsx 添加 /admin 路由**

```tsx
// App.tsx — 在现有 imports 末尾新增
import AdminPage from "./pages/AdminPage";

// 在 Routes 中，在 catch-all 之前新增
<Route path="/admin" element={<AdminPage />} />
```

- [ ] **Step 3: 在 Layout.tsx 添加 DEV-only Admin 导航**

分两个子步骤，防止遗漏：

**(a) 在现有 lucide-react import 中新增** **`Settings`：**

```tsx
// 将当前第 3 行:
import { Home, Upload, MessageSquare, Moon, Sun } from "lucide-react";
// 改为:
import { Home, Upload, MessageSquare, Moon, Sun, Settings } from "lucide-react";
```

**(b) 在** **`navItems`** **数组定义之后新增** **`allNavItems`，并将渲染循环改用** **`allNavItems`：**

```tsx
// 在 navItems 定义之后添加
const allNavItems = import.meta.env.DEV
  ? [...navItems, { path: "/admin", label: "Admin", icon: Settings }]
  : navItems;
```

然后将 `navItems.map(...)` 改为 `allNavItems.map(...)`。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/adminApi.ts frontend/src/App.tsx frontend/src/components/Layout.tsx
git commit -m "feat: add admin API client, /admin route, and DEV-only nav link"
```

***

### Task 10: AdminPage 组件

**Files:**

- Create: `frontend/src/pages/AdminPage.tsx`
- [ ] **Step 1: 创建 AdminPage.tsx**

```tsx
// frontend/src/pages/AdminPage.tsx
import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  fetchParseResults,
  fetchRawBdaJson,
  fetchIndexedChunks,
  ParseResultSummary,
  IndexedChunk,
} from "@/api/adminApi";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ChevronDown, ChevronRight } from "lucide-react";

// ── JSON tree viewer ──────────────────────────────────────────────────────────
function JsonTree({ data, depth = 0 }: { data: unknown; depth?: number }) {
  const [open, setOpen] = useState(depth < 2);
  if (data === null || typeof data !== "object") {
    return <span className="text-green-600 dark:text-green-400">{JSON.stringify(data)}</span>;
  }
  const entries = Array.isArray(data)
    ? data.map((v, i) => [i, v] as [unknown, unknown])
    : Object.entries(data as Record<string, unknown>);
  const preview = Array.isArray(data) ? `[${entries.length}]` : `{${entries.length}}`;
  return (
    <span>
      <button
        className="inline-flex items-center gap-0.5 hover:text-primary"
        onClick={() => setOpen(!open)}
      >
        {open ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
        <span className="text-muted-foreground text-xs">{preview}</span>
      </button>
      {open && (
        <div className="ml-4 border-l border-border pl-2">
          {entries.map(([key, val]) => (
            <div key={String(key)} className="my-0.5">
              <span className="text-blue-600 dark:text-blue-400 mr-1">{String(key)}:</span>
              <JsonTree data={val} depth={depth + 1} />
            </div>
          ))}
        </div>
      )}
    </span>
  );
}

// ── Chunk item ─────────────────────────────────────────────────────────────────
function ChunkItem({ chunk }: { chunk: IndexedChunk }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="border rounded-md mb-2">
      <button
        className="w-full flex items-center gap-2 px-3 py-2 text-sm text-left hover:bg-muted/50"
        onClick={() => setOpen(!open)}
      >
        {open ? <ChevronDown className="h-3 w-3 shrink-0" /> : <ChevronRight className="h-3 w-3 shrink-0" />}
        <span className="font-mono text-xs text-muted-foreground w-20 shrink-0">{chunk.chunk_id}</span>
        <span className="text-xs text-muted-foreground shrink-0">p.{chunk.page_number}</span>
        {chunk.section_path.length > 0 && (
          <Badge variant="outline" className="text-xs shrink-0">{chunk.section_path.join(" > ")}</Badge>
        )}
        <span className="text-xs truncate text-muted-foreground">{chunk.sentence}</span>
      </button>
      {open && (
        <div className="px-4 pb-3 space-y-2 text-sm">
          <div>
            <div className="text-xs font-medium text-muted-foreground mb-1">段落</div>
            <p className="text-sm leading-relaxed">{chunk.paragraph}</p>
          </div>
          <div>
            <div className="text-xs font-medium text-muted-foreground mb-1">摘要句</div>
            <p className="text-sm text-muted-foreground">{chunk.sentence}</p>
          </div>
          {chunk.asset_references.length > 0 && (
            <div>
              <div className="text-xs font-medium text-muted-foreground mb-1">Assets</div>
              {chunk.asset_references.map((ref) => (
                <div key={ref} className="text-xs font-mono text-blue-500 truncate">{ref}</div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ── Detail panel ───────────────────────────────────────────────────────────────
function DetailPanel({ selected }: { selected: ParseResultSummary }) {
  const rawQuery = useQuery({
    queryKey: ["bdaRaw", selected.index_name],
    queryFn: () => fetchRawBdaJson(selected.index_name),
    enabled: false,  // lazy — activated when tab is clicked
  });
  const chunksQuery = useQuery({
    queryKey: ["bdaChunks", selected.index_name],
    queryFn: () => fetchIndexedChunks(selected.index_name),
    enabled: false,
  });

  return (
    <Tabs defaultValue="summary" className="h-full flex flex-col">
      <TabsList className="shrink-0">
        <TabsTrigger value="summary">摘要</TabsTrigger>
        <TabsTrigger value="raw" onClick={() => rawQuery.refetch()}>原始 JSON</TabsTrigger>
        <TabsTrigger value="chunks" onClick={() => chunksQuery.refetch()}>Chunks</TabsTrigger>
      </TabsList>

      <TabsContent value="summary" className="flex-1 overflow-auto">
        <div className="grid grid-cols-2 gap-3 p-1">
          {[
            ["文件名", selected.filename],
            ["Index", selected.index_name],
            ["Chunks", selected.chunk_count],
            ["页数", selected.page_count],
            ["解析器", `${selected.parser_type}:${selected.parser_version}`],
            ["解析时间", new Date(selected.created_at).toLocaleString()],
          ].map(([label, value]) => (
            <div key={String(label)} className="bg-muted/40 rounded p-2">
              <div className="text-xs text-muted-foreground">{label}</div>
              <div className="text-sm font-medium truncate">{String(value)}</div>
            </div>
          ))}
        </div>
      </TabsContent>

      <TabsContent value="raw" className="flex-1 overflow-auto">
        {rawQuery.isFetching && <p className="text-sm text-muted-foreground p-2">加载中…</p>}
        {rawQuery.isError && (
          <p className="text-sm text-destructive p-2">{(rawQuery.error as Error).message}</p>
        )}
        {rawQuery.data && (
          <pre className="text-xs p-3 font-mono overflow-auto">
            <JsonTree data={rawQuery.data} />
          </pre>
        )}
      </TabsContent>

      <TabsContent value="chunks" className="flex-1 overflow-auto">
        {chunksQuery.isFetching && <p className="text-sm text-muted-foreground p-2">加载中…</p>}
        {chunksQuery.isError && (
          <p className="text-sm text-destructive p-2">{(chunksQuery.error as Error).message}</p>
        )}
        {chunksQuery.data && (
          <div className="p-1">
            <p className="text-xs text-muted-foreground mb-2">{chunksQuery.data.length} 个 chunks</p>
            {chunksQuery.data.map((chunk) => (
              <ChunkItem key={chunk.chunk_id} chunk={chunk} />
            ))}
          </div>
        )}
      </TabsContent>
    </Tabs>
  );
}

// ── Main page ──────────────────────────────────────────────────────────────────
const AdminPage: React.FC = () => {
  const [selected, setSelected] = useState<ParseResultSummary | null>(null);
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["parseResults"],
    queryFn: fetchParseResults,
  });

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-bold">BDA 解析观测</h1>
      <div className="grid grid-cols-[280px_1fr] gap-4 h-[calc(100vh-180px)]">
        {/* Left panel */}
        <Card className="overflow-auto">
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">文档列表</CardTitle>
          </CardHeader>
          <CardContent className="p-2">
            {isLoading && <p className="text-sm text-muted-foreground px-2">加载中…</p>}
            {isError && (
              <p className="text-sm text-destructive px-2">{(error as Error).message}</p>
            )}
            {data?.map((item) => (
              <button
                key={`${item.index_name}-${item.created_at}`}
                className={`w-full text-left rounded px-3 py-2 mb-1 hover:bg-muted/50 transition-colors ${
                  selected?.index_name === item.index_name &&
                  selected?.created_at === item.created_at
                    ? "bg-muted"
                    : ""
                }`}
                onClick={() => setSelected(item)}
              >
                <div className="text-sm font-medium truncate">{item.filename}</div>
                <div className="text-xs text-muted-foreground">
                  {item.chunk_count} 块 · {item.page_count} 页
                </div>
                <div className="text-xs text-muted-foreground">
                  {new Date(item.created_at).toLocaleDateString()}
                </div>
              </button>
            ))}
          </CardContent>
        </Card>

        {/* Right panel */}
        <Card className="overflow-hidden">
          <CardContent className="p-3 h-full">
            {selected ? (
              <DetailPanel selected={selected} />
            ) : (
              <div className="flex items-center justify-center h-full text-muted-foreground text-sm">
                选择左侧文档查看解析详情
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default AdminPage;
```

- [ ] **Step 2: 启动前端开发服务器确认页面可渲染**

```bash
cd frontend && npm run dev
```

打开 `http://localhost:8080/admin`，确认：

- 页面加载正常（即使后端未运行，应显示 loading/error 状态）
- 导航栏在 dev 模式下显示 "Admin" 链接
- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/AdminPage.tsx
git commit -m "feat: add AdminPage with document list, raw JSON tree, and chunks viewer"
```

***

## 验收检查

完成所有任务后，运行以下命令验收：

```bash
# 后端全量测试
mvn -f backend-java/pom.xml "-Dspring.profiles.active=test" test -q

# 启动后端（确保 .env 已加载）
mvn -f backend-java/pom.xml spring-boot:run

# 验证 Admin 接口
curl http://localhost:8001/admin/parse_results
curl http://localhost:8001/health
```

上传一个 PDF 后：

```bash
curl http://localhost:8001/admin/parse_results
# 应返回包含该文档的列表

curl http://localhost:8001/admin/parse_results/<index_name>/chunks
# 应返回已索引的 chunk 列表

curl http://localhost:8001/admin/parse_results/<index_name>/raw
# 应返回原始 BDA JSON
```

