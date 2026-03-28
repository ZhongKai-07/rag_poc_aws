# CLAUDE.md

Repository-wide guidance for Claude Code and other coding agents.

## Current Project State

RAG 知识库问答系统，正在从 Python 后端 (`api/`) 向 Spring Boot 后端 (`backend-java/`) 迁移。

- **Java 后端**：运行在端口 `8001`，Enhanced RAG Batch A+B 已实现 (2026-03-28)，115 个测试通过。
- **Python 后端**：保留为行为基线和回滚目标，不可删除。
- **前端**：`frontend/` 为当前生产前端；`NEWTON/` 为产品设计的目标聊天式 UI，迁移计划已确认。
- **Batch A 完成**：Query 改写（COB 关键词 + Collateral 结构化）、答案溯源（Citation）、离线评估（RAGAS sidecar）。Feature flags: `rag.query-rewrite.enabled`, `rag.citation.enabled`。
- **Batch B 完成**：多轮对话（Session + 滑动窗口记忆）、SSE流式输出、用户反馈（👍👎）、推荐追问、答案置信度、Citation显示优化。
- **RAGAS sidecar**：`ragas-evaluator/`（Python FastAPI + Docker），端口 8002。
- **Pending work**：docling-java integration — 添加 docling 作为 BDA 之外的替代文档解析器。见 `control/docling/`。
- **Next**：前端对接 Batch B API（Session UI、反馈按钮、流式显示）、Docling 集成。
- 不要将此仓库当作"仅 Python"项目。Java 后端尚未完全切换。

## Source of Truth Documents

| Scope | Documents |
|-------|-----------|
| 后端迁移 | `control/Prompt.md`, `control/Plan.md`, `control/Implement.md`, `control/Documentation.md` |
| Enhanced RAG Batch A | `control/enhanced_rag/Prompt.md`, `control/enhanced_rag/Plan.md`, `control/enhanced_rag/Implement.md`, `control/enhanced_rag/Documentation.md` |
| Enhanced RAG Batch B | `control/enhanced_rag_b/Prompt.md`, `control/enhanced_rag_b/Plan.md`, `control/enhanced_rag_b/Implement.md`, `control/enhanced_rag_b/Documentation.md` |
| Docling 集成 | `control/docling/Prompt.md`, `control/docling/Plan.md`, `control/docling/Implement.md`, `control/docling/Documentation.md` |
| 迁移计划 | `docs/superpowers/plans/2026-03-19-springboot-rag-migration-layered-plan.md` |
| 切换检查 | `docs/superpowers/plans/2026-03-19-migration-cutover-checklist.md` |
| 前端迁移 | `docs/superpowers/plans/2026-03-26-frontend-to-newton-migration.md` |
| 代码审计 | `docs/ccodeReview/code-review-2026-03-22.md` (C1–C4 已修复) |
| 后端说明 | `backend-java/README.md` |

做迁移相关变更前，先读对应的 control 文档。

---

## Project Map

### Top-Level Directory Structure

```
.
├── api/                    # Legacy Python FastAPI backend (baseline/rollback)
├── backend-java/           # Spring Boot migration target (active development)
├── frontend/               # React + TypeScript + Vite frontend (current production)
├── ragas-evaluator/        # RAGAS Python sidecar (FastAPI + Docker, port 8002)
├── NEWTON/                 # Product demo frontend (migration target UI)
├── control/                # Migration control documents (source of truth)
│   ├── Prompt.md / Plan.md / Implement.md / Documentation.md
│   └── docling/            # Docling parser integration control docs
├── docs/                   # Architecture docs, code reviews, plans
│   ├── ccodeReview/        # Code review reports
│   ├── superpowers/plans/  # Implementation plans (dated)
│   ├── product_analyze/    # Product analysis documents
│   └── *.md               # Design docs, deployment guide
├── dev_logs/               # Development logs
├── error_logs/             # Error logs
├── test_pdf/               # Test PDF files for upload testing
└── CLAUDE.md               # This file
```

### backend-java/ — Spring Boot RAG Backend

```
backend-java/
├── .env / .env.example             # AWS credentials & service config (runtime)
├── diagnose-aws.sh                 # AWS connectivity diagnostic script
├── pom.xml                         # Maven build (Java 17, Spring Boot)
└── src/
    ├── main/java/com/huatai/rag/
    │   ├── RagApplication.java             # Spring Boot entry point
    │   ├── api/                            # REST Controllers (HTTP layer)
    │   │   ├── admin/                      #   AdminController — BDA parse result viewer
    │   │   ├── health/                     #   HealthController — /health
    │   │   ├── question/                   #   QuestionController — /top_questions*
    │   │   ├── rag/                        #   RagController — /rag_answer
    │   │   ├── upload/                     #   UploadController — /upload_files, /processed_files
    │   │   └── common/                     #   ApiExceptionHandler
    │   ├── application/                    # Application Services (use cases)
    │   │   ├── admin/                      #   ParseResultQueryApplicationService
    │   │   ├── common/                     #   ContextAssemblyService
    │   │   ├── history/                    #   QuestionHistoryApplicationService
    │   │   ├── ingestion/                  #   DocumentIngestionApplicationService
    │   │   ├── rag/                        #   RagQueryApplicationService, QueryRewriteRouter, CitationAssemblyService
    │   │   └── registry/                   #   ProcessedFileQueryApplicationService
    │   ├── evaluation/                     # Offline Evaluation (independent module)
    │   │   ├── model/                      #   TestCase, TraceRecord, EvaluationReport
    │   │   ├── application/                #   EvaluationRunner, ReportGenerator, TestDatasetLoader
    │   │   └── infrastructure/             #   RagasClient (HTTP → Python sidecar)
    │   ├── domain/                         # Domain Model (pure, no AWS deps)
    │   │   ├── bda/                        #   BdaParseResultPort, BdaParseResultRecord
    │   │   ├── document/                   #   DocumentFileRecord, DocumentRegistryPort, IndexNamingPolicy
    │   │   ├── history/                    #   QuestionHistoryPort
    │   │   ├── parser/                     #   DocumentParser, ParsedDocument/Page/Chunk/Asset
    │   │   ├── rag/                        #   AnswerGenerationPort, QueryRewriteStrategy, RewriteResult, Citation, CitedAnswer
    │   │   └── retrieval/                  #   EmbeddingPort, RerankPort, RetrievalPort, RetrievalRequest, SearchMethod
    │   └── infrastructure/                 # External Service Adapters
    │       ├── bda/                        #   BdaClient, BdaDocumentParserAdapter, BdaResultMapper
    │       ├── bedrock/                    #   BedrockEmbeddingAdapter, BedrockRerankAdapter,
    │       │                               #   BedrockAnswerGenerationAdapter, PromptTemplateFactory
    │       ├── config/                     #   AwsProperties, RagProperties, StorageProperties,
    │       │                               #   OpenSearchProperties, ClientConfig, CorsConfig
    │       ├── opensearch/                 #   OpenSearchRetrievalAdapter, IndexManager, ChunkMapper,
    │       │                               #   DocumentWriter, DocumentChunkWriter
    │       ├── persistence/                #   JPA Entities + Repositories (PostgreSQL)
    │       │   ├── entity/                 #     DocumentFileEntity, IngestionJobEntity,
    │       │   │                           #     QuestionHistoryEntity, BdaParseResultEntity
    │       │   └── repository/             #     JPA repositories for each entity
    │       ├── storage/                    #   S3DocumentStorageAdapter, LocalFileStorageAdapter
    │       └── support/                    #   RequestCorrelationFilter, RetryUtils
    ├── main/resources/
    │   ├── application.yml                 # Main config (env var → property binding)
    │   ├── application-local.yml           # Local dev overrides
    │   ├── application-test.yml            # Test config (H2 in-memory DB)
    │   ├── aws_config.txt                  # PowerShell startup env var template
    │   └── db/migration/                   # Flyway migrations (V1–V4)
    └── test/java/                          # 24 test classes (unit + contract + integration + regression)
```

### REST API Endpoints (19 total)

| # | Method | Path | Controller | Purpose |
|---|--------|------|------------|---------|
| 1 | `GET` | `/health` | HealthController | Health check |
| 2 | `POST` | `/upload_files` | UploadController | Upload PDF → S3 → BDA parse → OpenSearch index |
| 3 | `GET` | `/processed_files` | UploadController | List processed files `[{filename, index_name}]` |
| 4 | `GET` | `/get_index/{filename}` | UploadController | Lookup index name by filename |
| 5 | `POST` | `/rag_answer` | RagController | RAG query: retrieve → rerank → LLM answer |
| 6 | `POST` | `/rag_answer/stream` | RagController | SSE streaming RAG answer |
| 7 | `GET` | `/top_questions/{index_name}` | QuestionController | Hot questions for single index |
| 8 | `GET` | `/top_questions_multi` | QuestionController | Hot questions across multiple indices |
| 9 | `POST` | `/sessions` | ChatSessionController | Create new chat session |
| 10 | `GET` | `/sessions` | ChatSessionController | List sessions (paginated) |
| 11 | `GET` | `/sessions/{id}` | ChatSessionController | Session detail with messages |
| 12 | `DELETE` | `/sessions/{id}` | ChatSessionController | Delete session |
| 13 | `PATCH` | `/sessions/{id}` | ChatSessionController | Rename session |
| 14 | `POST` | `/sessions/{sid}/messages/{mid}/feedback` | ChatSessionController | Submit feedback |
| 15 | `GET` | `/admin/parse_results` | AdminController | List BDA parse result summaries |
| 16 | `GET` | `/admin/parse_results/{indexName}/raw` | AdminController | Raw BDA JSON output |
| 17 | `GET` | `/admin/parse_results/{indexName}/chunks` | AdminController | Indexed chunk details |
| 18 | `GET` | `/admin/feedback` | AdminController | List feedback (paginated) |
| 19 | `GET` | `/admin/feedback/stats` | AdminController | Feedback statistics |

### AWS Service Dependencies

| Service | 用途 | Config Env Var |
|---------|------|---------------|
| **S3** | 文档存储 + BDA 输出 | `S3_DOCUMENT_BUCKET` |
| **Bedrock Runtime** | 向量嵌入 (`titan-embed-text-v1`), 问答 (`qwen3-235b`) | `BEDROCK_REGION`, `RAG_*_MODEL_ID` |
| **Bedrock Agent Runtime** | 重排序 (`cohere.rerank-v3-5:0`) | `AWS_DEFAULT_REGION` |
| **Bedrock Data Automation** | 文档智能解析 (PDF → chunks) | `BDA_PROJECT_ARN`, `BDA_PROFILE_ARN` |
| **OpenSearch Service** | 向量/全文检索 | `OPENSEARCH_ENDPOINT`, `OPENSEARCH_USERNAME/PASSWORD` |
| **PostgreSQL** (非 AWS 可选) | 文件注册、问题历史、解析结果 | `DB_HOST/PORT/NAME/USERNAME/PASSWORD` |

### frontend/ — React Frontend (Current)

```
frontend/src/
├── App.tsx                 # React Router: /, /upload, /qa, /admin
├── main.tsx                # Entry point
├── pages/
│   ├── Index.tsx           # 首页 — 导航入口
│   ├── Upload.tsx          # 上传页 — PDF 拖拽上传 + 文件列表
│   ├── QA.tsx              # 问答页 — 文档选择 + RAG 问答 + 证据展示
│   ├── AdminPage.tsx       # 管理页 — BDA 解析观测 (dev only)
│   └── NotFound.tsx        # 404
├── api/adminApi.ts         # Admin 接口调用
├── components/
│   ├── Layout.tsx          # 导航栏 + 页面布局
│   ├── NavLink.tsx         # 导航链接
│   └── ui/                 # 50+ Shadcn/ui 组件
├── hooks/                  # use-toast, use-mobile
└── lib/                    # utils, legacy api.ts
```

**Tech Stack:** React 18 + TypeScript + Vite 5 + Tailwind v3 + Shadcn/ui + TanStack Query

| 页面 | 调用接口 |
|------|----------|
| Upload | `GET /processed_files`, `POST /upload_files` |
| QA | `GET /processed_files`, `POST /rag_answer`, `GET /top_questions_multi` |
| Admin | `GET /admin/parse_results`, `GET .../raw`, `GET .../chunks` |

### NEWTON/ — Product Demo Frontend (Migration Target)

```
NEWTON/src/
├── app/
│   ├── App.tsx                     # 根组件 — 浮动按钮/全屏聊天切换
│   ├── components/
│   │   ├── MaximizedChat.tsx       # 主聊天界面 (核心组件)
│   │   ├── FileParseModal.tsx      # 文件上传弹窗
│   │   ├── FloatingButton.tsx      # 可拖拽浮动按钮
│   │   ├── HistoryPanel.tsx        # 聊天历史侧边栏
│   │   ├── HotQuestions.tsx        # 场景热门问题
│   │   ├── ScenarioButtonsWeb.tsx  # 场景选择轮播 (开户/合规/SOP)
│   │   ├── CitationModal.tsx       # 引用详情弹窗
│   │   ├── MessageWithCitations.tsx # 消息+引用渲染
│   │   ├── AgreementAssistantModal.tsx # 协议助手 (Mock)
│   │   ├── DownloadModal.tsx       # 下载弹窗
│   │   └── ui/                     # Radix UI 组件
│   ├── hooks/useRagApi.ts          # RAG API Hook
│   ├── services/
│   │   ├── ragApi.ts               # API 服务层 (已对接后端)
│   │   ├── types.ts                # TypeScript 类型定义
│   │   └── config.ts               # 配置 (legacy, 未使用)
│   └── i18n/translations.ts        # 多语言 (zh-CN/zh-TW/en)
├── styles/                         # 全局样式
└── main.tsx                        # 入口
```

**Tech Stack:** React 18 + TypeScript + Vite 6 + Tailwind v4 + Radix UI + Emotion + Framer Motion

**调用接口：** `GET /processed_files`, `POST /upload_files`, `POST /rag_answer`, `GET /top_questions_multi`, `GET /health`

### api/ — Legacy Python Backend (Baseline)

```
api/
├── api.py                              # FastAPI 主应用 (端口 8001)
├── RAG_System.py                       # RAG 系统核心
├── bda_connection.py                   # BDA 解析连接
├── document_processing.py              # 文档处理
├── embedding_model.py                  # Bedrock 嵌入
├── llm_processor.py                    # LLM 调用
├── opensearch_multimodel_dataload.py   # OpenSearch 数据写入
├── opensearch_search.py                # OpenSearch 检索
└── verify_connection.py                # 连接验证
```

### Data Flow

```
Upload Flow:
  PDF → POST /upload_files → S3 Storage → BDA Parse → OpenSearch Index → PostgreSQL Registry

RAG Query Flow:
  Question → POST /rag_answer
    → OpenSearch (vector + text retrieve)
    → Bedrock Rerank (cohere)
    → Context Assembly
    → Bedrock LLM (qwen3) → Answer + Sources

Index Naming: md5(filename)[:8] (Python-compatible)
```

### Plans & Roadmap

| Document | Purpose |
|----------|---------|
| `docs/superpowers/plans/2026-03-26-frontend-to-newton-migration.md` | frontend/ → NEWTON UI 迁移计划 |
| `docs/superpowers/plans/2026-03-26-enhanced-rag-batch-a-plan.md` | Enhanced RAG 批次 A 计划 |
| `docs/superpowers/specs/2026-03-28-enhanced-rag-batch-b-design.md` | Enhanced RAG 批次 B 设计规格 |
| `docs/superpowers/plans/2026-03-28-enhanced-rag-batch-b-plan.md` | Enhanced RAG 批次 B 实施计划 |
| `docs/superpowers/plans/2026-03-23-bda-observability.md` | BDA 观测性实现计划 |
| `docs/superpowers/plans/2026-03-19-springboot-rag-migration-layered-plan.md` | Spring Boot 分层迁移计划 |
| `docs/superpowers/plans/2026-03-19-migration-cutover-checklist.md` | 前端切换检查清单 |

---

## Development Rules

### Architecture Constraints

- **分层边界**：`domain/` 层保持纯净 — 不可依赖 AWS/OpenSearch/S3/BDA/PostgreSQL。
- **索引命名**：`md5(filename)[:8]`，与 Python 基线一致。
- **OpenSearch 字段**：`sentence_vector`, `paragraph`, `sentence`, `metadata.*`。
- **前端契约**：上方 10 个 REST 端点不可破坏，除非用户明确要求变更。

### What NOT to Do

- 不要删除或破坏 Python 后端 (`api/`)。
- 不要修改前端来适配后端契约漂移。
- 不要提交 `backend-java/target/`。
- 不要将密钥/凭证复制到文档、提交或响应中。
- 不要随意重构 `api/`，如需理解基线行为，读取 `api/` 源码即可。
- 不要在 worktree 中 revert 用户的未关联变更。

### Workflow

1. 读 `control/` 文档确定任务归属（`api/` 基线 / `backend-java/` 实现 / 切换验证）。
2. 保持前端契约和 Python 行为一致性。
3. 运行最小验证命令。
4. 如果状态或指引有变，同步更新 `control/` 四个文档 + `CLAUDE.md`。

---

## Runtime & Configuration

- **Port `8001`**：两个后端共用，同一时间只能运行一个。
- **Spring Boot 不会自动加载 `.env`**：IDE 启动命令须显式 export 环境变量。`.env` 仅为参考模板。
- **Java 版本**：17。
- **Maven**：`-Dmaven.repo.local=$env:USERPROFILE\.m2\repository` (PowerShell) 或 `$HOME/.m2/repository` (Bash)。
- **前端 dev server**：`localhost:8080`，需要 `frontend/.env` 中 `VITE_API_BASE_URL=http://localhost:8001`，后端需启用 CORS (`CorsConfig.java`)。
- **Answer model**：默认 `qwen.qwen3-235b-a22b-2507-v1:0` (us-west-2)，使用 `converse` API（非 `invokeModel`）。
- **Model IDs 可配置**：`RAG_ANSWER_MODEL_ID`, `RAG_EMBEDDING_MODEL_ID`, `RAG_RERANK_MODEL_ID`，默认值在 `application.yml` 和 `RagProperties.java` 中均有设置。
- **Index 自动管理**：`ensureIndex()` 通过单次 `GET /_mapping` 自动检测并处理缺失/无效/有效的索引映射。
- **AWS 诊断**：`bash backend-java/diagnose-aws.sh`。
- **存储路径**：Java 后端用 S3 存储上传文档和 BDA 输出；Python 后端用本地文件系统。

---

## Verification Commands

```powershell
# PowerShell:
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" test
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" spring-boot:run
```

```bash
# Bash:
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" test
mvn -f backend-java/pom.xml "-Dmaven.repo.local=$HOME/.m2/repository" spring-boot:run
curl http://localhost:8001/health
bash backend-java/diagnose-aws.sh
```

Never mark migration work complete without running the appropriate verification command.
