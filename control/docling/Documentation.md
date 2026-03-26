# Docling-Java 集成文档

## Current Status

- M1 (配置基础设施): not started
- M2 (Maven 依赖): not started
- M3 (MarkdownHeaderChunker): not started
- M4 (DoclingDocumentParserAdapter): not started
- M5 (条件装配): not started
- M6 (集成测试 + 运行配置): not started

起始条件：

- 现有 Spring Boot 迁移（Task 1-12）全部完成。
- 端到端 RAG 管线已验证（2026-03-22）。
- 42 个测试全部通过。
- 代码稳定性审计完成。

## Decisions And Reasons

1. 双解析器共存
   - Reason: BDA 用于 S3 场景（云端），Docling 用于本地开发/部署场景，通过 `huatai.parser.type` 配置切换。

2. 控制文档独立于主迁移文档
   - Reason: 主迁移（`control/Prompt.md` 等）已完成 12 个里程碑，docling 集成是独立的新功能，避免混淆两套工作。

## Run And Demo Commands

当前验证命令（仅 BDA 模式，直到 docling 里程碑完成）：

```bash
# 全量测试
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test

# 启动服务
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" spring-boot:run

# 健康检查
curl http://localhost:8001/health
```

Docling 模式命令（M6 完成后可用）：

```bash
# 启动 docling-serve
docker run -p 5001:5001 quay.io/docling-project/docling-serve

# 启动 Java 后端（docling 模式）
PARSER_TYPE=docling DOCLING_ENDPOINT=http://localhost:5001 \
  mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" spring-boot:run

# 上传测试
curl -X POST http://localhost:8001/upload_files \
  -F "file=@test.pdf" -F "directory_path=test"

# 查看已处理文件
curl http://localhost:8001/processed_files

# 问答测试
curl -X POST http://localhost:8001/rag_answer \
  -H "Content-Type: application/json" \
  -d '{"query":"测试问题","index_names":["<index_name>"],"search_method":"MIX"}'
```

## Known Gaps

- docling-java Maven 坐标（groupId、artifactId、版本号）需在 M2 开始时从 Maven Central 确认。预期为 `ai.docling:docling-serve-client`。
- docling-serve Docker 镜像确切 tag 需确认。预期为 `quay.io/docling-project/docling-serve`。
- Docling 的 `ConvertDocumentRequest` / `ConvertDocumentResponse` 具体 API 形状需在实现 M4 时从 docling-java 源码确认。

## Known Warnings

- 现有 Spring AI Bedrock 自动配置在部分测试中产生 AWS region 警告，已在 `application-test.yml` 中排除，不影响本次工作。
- Maven 在此环境需要 `-Dmaven.repo.local` 覆盖。

## Next Recommended Step

开始 M1：创建 `ParserProperties.java` 和 `DoclingProperties.java` 配置类。

## Maintenance Rule

这四个控制文档是 docling-java 集成的活跃工作文档，每个里程碑完成后必须更新：

- `Prompt.md` — 约束或范围变化时
- `Plan.md` — 里程碑状态和决策变化时
- `Implement.md` — 工作规则变化时
- `Documentation.md` — 每次进度、决策、命令变化时
