# RAG系统开发技术详细设计文档

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档名称 | RAG系统开发技术详细设计文档 |
| 版本 | v2.0 |
| 编写日期 | 2026-03-17 |
| 系统名称 | 基于AWS OpenSearch和Bedrock的检索增强生成系统 |
| 技术栈 | SpringBoot + Spring AI + Python数据处理服务 |

---

## 目录

1. [业务背景](#一业务背景)
2. [系统架构](#二系统架构)
3. [核心组件设计](#三核心组件设计)
4. [数据流转设计](#四数据流转设计)
5. [关键技术点](#五关键技术点)
6. [API接口设计](#六api接口设计)
7. [数据模型设计](#七数据模型设计)
8. [权限管理](#八权限管理)
9. [RAG效果评估](#九rag效果评估)

---

## 一、业务背景

### 1.1 业务概述

本系统是企业内部知识库平台的核心智能问答引擎，通过RAG（Retrieval-Augmented Generation）技术提升运营人员的工作效率，降低人工查询制度文档的时间成本，同时支持业务决策的智能化辅助。

### 1.2 核心业务场景

#### 场景一：OPS/COB合规问答

**业务描述：**
运营部门(Ops)日常需要频繁查询各类制度文档、合规要求、操作规程等内容。传统方式需要人工翻阅大量PDF文档，效率低下且容易遗漏关键信息。

**核心需求：**
- 快速检索：支持自然语言提问，秒级返回相关答案
- 精准溯源：答案需标注来源文档和具体章节
- 多文档关联：一个问题可能涉及多份制度文档的交叉引用
- 版本管理：制度文档存在版本更新，需支持历史版本查询

**典型问题示例：**
```
Q: COB业务中，客户投诉处理的标准流程是什么？
Q: 新版操作规程对清算时间有什么调整？
Q: 合规检查的频率要求是多少？
```

**技术挑战：**
- 制度文档格式多样(PDF/Word/Excel)
- 专业术语较多，需要精确匹配
- 文档更新频繁，索引需要实时同步

#### 场景二：抵押品智能筛选

**业务描述：**
在融资融券、股票质押等业务场景中，需要对抵押品进行合规性检查和筛选。传统方式依赖业务人员人工判断，效率低且存在漏判风险。

**核心需求：**
- 合规检查：自动判断抵押品是否符合监管要求
- 智能筛选：根据业务需求推荐合适的抵押品
- 风险评估：结合抵押品历史数据进行风险提示
- 规则匹配：支持复杂业务规则的自动匹配

**典型问题示例：**
```
Q: 这只股票可以作为融资融券的担保品吗？
Q: 符合以下条件的抵押品有哪些：市盈率<30、流动性>1000万、非ST股？
Q: 该抵押品的风险等级是什么？
```

### 1.3 场景对比分析

| 维度 | OPS/COB合规问答 | 抵押品智能筛选 |
|------|----------------|----------------|
| 数据类型 | 以非结构化文档为主 | 结构化+非结构化混合 |
| 查询方式 | 自然语言问答 | 条件筛选+自然语言 |
| 结果形式 | 答案文本+来源引用 | 筛选列表+分析报告 |
| 实时性要求 | 中等(文档更新频率) | 高(行情数据实时性) |
| 多租户需求 | 部门级隔离 | 业务线+客户级隔离 |
| 权限复杂度 | 基于部门的数据访问权限 | 基于角色的功能+数据权限 |

### 1.4 业务价值

| 场景 | 效率提升 | 风险降低 | 合规保障 |
|------|----------|----------|----------|
| OPS/COB合规问答 | 查询时间从小时级降至秒级 | 减少人工疏漏 | 答案可溯源，支持审计 |
| 抵押品智能筛选 | 筛选效率提升80% | 自动风险预警 | 规则匹配一致性 |

---

## 二、系统架构

### 2.1 整体架构设计

采用混合架构设计，充分发挥各技术栈优势：
- **Java层（SpringBoot）**：负责核心业务编排、API网关、权限管理、企业级特性
- **Python层**：专注PDF文档解析、文本分割、向量化处理

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                   用户层                                        │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                          React Frontend (Port 8080)                      │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────┐   │   │
│  │  │   Upload.tsx    │  │     QA.tsx      │  │   Shadcn/ui Components  │   │   │
│  │  │   文件上传页面   │  │   问答交互页面   │  │      UI组件库           │   │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         │ HTTP/REST API
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                   网关层                                        │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                   SpringBoot Gateway (Port 8080)                         │   │
│  │                   - 认证鉴权                                               │   │
│  │                   - 路由转发                                               │   │
│  │                   - 限流熔断                                               │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
                                         │
                     ┌───────────────────┼───────────────────┐
                     │                   │                   │
                     ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                   服务层                                        │
│                                                                                 │
│  ┌─────────────────────────────┐    ┌───────────────────────────────────────┐  │
│  │      Java RAG服务           │    │      Python数据处理服务               │  │
│  │      (Port 8081)            │    │      (Port 8002)                      │  │
│  │  ┌─────────────────────┐    │    │  ┌───────────────────────────────┐   │  │
│  │  │  RagController      │    │    │  │  DocumentProcessController    │   │  │
│  │  │  - /rag_answer      │    │    │  │  - /process/document          │   │  │
│  │  │  - /processed_files │    │    │  │  - /process/batch             │   │  │
│  │  └─────────────────────┘    │    │  │  - /embedding/generate        │   │  │
│  │                             │    │  └───────────────────────────────┘   │  │
│  │  ┌─────────────────────┐    │    │                                       │  │
│  │  │  RagService         │    │    │  ┌───────────────────────────────┐   │  │
│  │  │  - RAG流程编排      │    │    │  │  DocumentProcessor            │   │  │
│  │  │  - 上下文管理       │    │    │  │  - PDF解析(Docling/PyPDF2)    │   │  │
│  │  │  - Prompt组装       │    │    │  │  - 文本分块                   │   │  │
│  │  └─────────────────────┘    │    │  │  - 向量化处理                 │   │  │
│  │                             │    │  └───────────────────────────────┘   │  │
│  │  ┌─────────────────────┐    │    │                                       │  │
│  │  │  OpenSearchService  │    │    │  ┌───────────────────────────────┐   │  │
│  │  │  - kNN向量搜索      │    │    │  │  BedrockEmbeddingService      │   │  │
│  │  │  - 文本搜索         │    │    │  │  - Titan Embedding调用        │   │  │
│  │  │  - 混合搜索         │    │    │  │  - Rerank调用                 │   │  │
│  │  └─────────────────────┘    │    │  └───────────────────────────────┘   │  │
│  │                             │    │                                       │  │
│  │  ┌─────────────────────┐    │    │  ┌───────────────────────────────┐   │  │
│  │  │  BedrockLlmService  │    │    │  │  OpenSearchDataLoadService    │   │  │
│  │  │  - LLM调用          │    │    │  │  - 向量批量入库               │   │  │
│  │  │  - 模型路由         │    │    │  │  - 索引管理                   │   │  │
│  │  │  - 重试机制         │    │    │  └───────────────────────────────┘   │  │
│  │  └─────────────────────┘    │    │                                       │  │
│  │                             │    │                                       │  │
│  │  ┌─────────────────────┐    │    │                                       │  │
│  │  │  PromptService      │    │    │                                       │  │
│  │  │  - 模板管理         │    │    │                                       │  │
│  │  │  - 动态组装         │    │    │                                       │  │
│  │  └─────────────────────┘    │    │                                       │  │
│  └─────────────────────────────┘    └───────────────────────────────────────┘  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
                     │                       │                        │
                     │                       │                        │
                     ▼                       ▼                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                   数据层                                        │
│                                                                                 │
│  ┌───────────────────────────────┐    ┌───────────────────────────────┐       │
│  │    AWS OpenSearch Service     │    │      AWS Bedrock Service      │       │
│  │    (ap-east-1 香港)           │    │    (ap-northeast-1 东京)      │       │
│  │                               │    │                               │       │
│  │  • kNN向量索引                │    │  • Titan Embeddings           │       │
│  │  • 文本索引                   │    │    amazon.titan-embed-text-v1 │       │
│  │  • 元数据存储                 │    │                               │       │
│  │                               │    │  • Rerank Model               │       │
│  │  Index: {md5(filename)[:8]}   │    │    amazon.rerank-v1:0         │       │
│  │                               │    │                               │       │
│  │                               │    │  • LLM Models                 │       │
│  │                               │    │    qwen.qwen3-235b-a22b-2507  │       │
│  │                               │    │    anthropic.claude-3-sonnet  │       │
│  └───────────────────────────────┘    └───────────────────────────────┘       │
│                                                                                 │
│  ┌───────────────────────────────┐    ┌───────────────────────────────┐       │
│  │      documents/               │    │   processed_files.txt         │       │
│  │      文件存储目录              │    │   处理记录文件                │       │
│  │                               │    │                               │       │
│  │  └── YYYY-MM-DD-HH-MM/       │    │  {file_name, index_name}      │       │
│  │      └── uploaded.pdf        │    │                               │       │
│  └───────────────────────────────┘    └───────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 分层架构说明

| 层次 | 职责 | 技术组件 | 关键特性 |
|------|------|----------|----------|
| 用户层 | 用户交互界面 | React 18 + TypeScript + Vite | 响应式设计、组件化开发 |
| 网关层 | API网关、认证鉴权 | Spring Cloud Gateway | 路由转发、限流熔断、统一认证 |
| 服务层(Java) | RAG核心业务编排 | SpringBoot + Spring AI | RESTful API、依赖注入、事务管理 |
| 服务层(Python) | 文档数据处理 | FastAPI + Docling | 异步处理、PDF解析、文本分块 |
| 数据层 | 数据持久化与AI服务 | AWS OpenSearch + Bedrock | 托管服务、高可用、自动扩展 |

### 2.3 跨区域架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                        AWS跨区域架构                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ap-east-1 (香港)              ap-northeast-1 (东京)              │
│   ┌─────────────────────┐       ┌─────────────────────────────┐    │
│   │    OpenSearch       │       │        Bedrock              │    │
│   │                     │       │                             │    │
│   │ • 向量数据存储       │       │ • Titan Embeddings ✅       │    │
│   │ • 低延迟访问         │       │ • Rerank ✅                 │    │
│   │ • 数据主权合规       │       │ • Qwen/Claude ✅            │    │
│   │                     │       │                             │    │
│   │                     │       │ 区域选择理由：               │    │
│   │                     │       │ • 模型可用性更全             │    │
│   │                     │       │ • 推理延迟较低               │    │
│   │                     │       │ • 亚太区域推荐               │    │
│   └─────────────────────┘       └─────────────────────────────┘    │
│                                                                     │
│   网络延迟考虑：                                                     │
│   • 向量搜索: ~50-100ms (OpenSearch)                               │
│   • Embedding: ~200-300ms (跨区域)                                 │
│   • Rerank: ~150-200ms (跨区域)                                    │
│   • LLM生成: ~1000-3000ms (跨区域)                                 │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.4 技术选型对比

| 层次 | POC技术栈 | 生产技术栈 | 选型理由 |
|------|-----------|------------|----------|
| 前端框架 | React 18 + TypeScript + Vite | React 18 + TypeScript + Vite | 现代化前端技术栈，开发效率高 |
| UI组件库 | Shadcn/ui + Tailwind CSS | Shadcn/ui + Tailwind CSS | 高度可定制，样式统一 |
| API网关 | FastAPI原生CORS | Spring Cloud Gateway | 企业级网关能力 |
| 后端框架 | FastAPI (Python) | SpringBoot (Java) | 企业级特性完善，生态丰富 |
| AI框架 | LangChain AWS | Spring AI / 自定义封装 | 标准化AI组件抽象 |
| 向量数据库 | AWS OpenSearch Service | AWS OpenSearch Service | 托管服务，原生支持kNN |
| 嵌入模型 | AWS Bedrock Titan | AWS Bedrock Titan | 托管服务，1536维高质量向量 |
| LLM服务 | AWS Bedrock (Qwen/Claude) | AWS Bedrock (Qwen/Claude) | 多模型支持，按需调用 |
| PDF处理 | Docling + PyPDF2 (Python) | Docling + PyPDF2 (Python) | Python生态优势，保持独立服务 |

---

## 三、核心组件设计

### 3.1 组件总览

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           核心组件关系图                                 │
└─────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────┐
                              │  Gateway Config │
                              │  网关配置中心    │
                              └────────┬────────┘
                                       │
             ┌─────────────────────────┼─────────────────────────┐
             │                         │                         │
             ▼                         ▼                         ▼
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│   RagController     │    │ DocumentController  │    │   UserController    │
│   (RAG接口层)        │    │ (文档管理接口层)    │    │   (用户接口层)      │
├─────────────────────┤    ├─────────────────────┤    ├─────────────────────┤
│ + ragAnswer()       │    │ + uploadDocument()  │    │ + login()           │
│ + getProcessedFiles │    │ + processDocument() │    │ + getUserInfo()     │
│ + getTopQuestions() │    │ + getDocumentList() │    │ + checkPermission() │
└─────────┬───────────┘    └─────────┬───────────┘    └─────────┬───────────┘
          │                          │                          │
          ▼                          ▼                          ▼
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│    RagService       │    │  DocumentService    │    │    UserService      │
│   (RAG业务逻辑)      │    │ (文档业务逻辑)      │    │   (用户业务逻辑)    │
├─────────────────────┤    ├─────────────────────┤    ├─────────────────────┤
│ - llmService        │    │ - pythonClient      │    │ - jwtUtil           │
│ - embeddingService  │    │ - openSearchService │    │ - permissionService │
│ - searchService     │    │                     │    │                     │
│ + getAnswer()       │    │ + processFile()     │    │ + authenticate()    │
│ + rerankDocuments() │    │ + batchProcess()    │    │ + authorize()       │
└─────────┬───────────┘    └─────────┬───────────┘    └─────────────────────┘
          │                          │
          │                          │
          ▼                          ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          基础设施层                                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐  ┌───────────┐ │
│  │OpenSearchSrvc │  │ BedrockLlmSrvc│  │BedrockEmbdSrvc│  │PythonClnt │ │
│  │               │  │               │  │               │  │           │ │
│  │• kNN Search   │  │• invokeModel  │  │• embedQuery   │  │• HTTP调用 │ │
│  │• Text Search  │  │• retryLogic   │  │• embedDocs    │  │• 文件传输 │ │
│  │• Hybrid Srch  │  │• modelRouting │  │• rerank       │  │• 异步调用 │ │
│  └───────────────┘  └───────────────┘  └───────────────┘  └───────────┘ │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Java核心组件设计

#### 3.2.1 RAG服务组件 (RagService)

```java
@Service
public class RagService {

    @Autowired
    private OpenSearchService openSearchService;

    @Autowired
    private BedrockLlmService bedrockLlmService;

    @Autowired
    private BedrockEmbeddingService embeddingService;

    @Autowired
    private PromptService promptService;

    /**
     * 获取RAG答案
     *
     * @param request RAG请求参数
     * @return RAG响应结果
     */
    public RagResponse getAnswer(RagRequest request) {
        // 1. 问题向量化
        float[] queryVector = embeddingService.embedQuery(request.getQuery());

        // 2. 向量检索
        List<SearchResult> recallDocs = openSearchService.similaritySearch(
            request.getIndexNames(),
            queryVector,
            request.getVecDocsNum(),
            request.getSearchMethod()
        );

        // 3. 过滤低分文档
        List<SearchResult> filteredDocs = filterByThreshold(recallDocs, request.getVecScoreThreshold());

        // 4. 重排序
        List<SearchResult> rerankedDocs = rerankDocuments(
            request.getQuery(),
            filteredDocs,
            request.getRerankScoreThreshold()
        );

        // 5. 格式化上下文
        List<ContextDocument> contextDocs = formatContext(rerankedDocs);

        // 6. LLM生成答案
        String answer = bedrockLlmService.generateAnswer(
            request.getQuery(),
            contextDocs,
            promptService.getSystemPrompt()
        );

        // 7. 记录历史
        saveQuestionHistory(request.getIndexNames(), request.getQuery());

        return RagResponse.builder()
            .answer(answer)
            .recallDocuments(recallDocs)
            .rerankDocuments(rerankedDocs)
            .sourceDocuments(rerankedDocs)
            .build();
    }

    /**
     * 文档重排序
     */
    private List<SearchResult> rerankDocuments(String query,
                                                List<SearchResult> documents,
                                                float threshold) {
        if (documents.isEmpty()) {
            return documents;
        }

        // 调用Bedrock Rerank模型
        List<RerankResult> rerankResults = embeddingService.rerank(query, documents);

        // 过滤并重排序
        return rerankResults.stream()
            .filter(r -> r.getScore() >= threshold)
            .sorted(Comparator.comparing(RerankResult::getScore).reversed())
            .map(RerankResult::getDocument)
            .collect(Collectors.toList());
    }
}
```

#### 3.2.2 OpenSearch服务组件

```java
@Service
public class OpenSearchService {

    @Autowired
    private OpenSearchClient openSearchClient;

    /**
     * 相似度搜索 - 支持三种模式
     *
     * @param indexNames 索引名称列表
     * @param queryVector 查询向量
     * @param k 返回数量
     * @param searchMethod 搜索模式: vector/text/mix
     */
    public List<SearchResult> similaritySearch(List<String> indexNames,
                                                float[] queryVector,
                                                int k,
                                                String searchMethod) {
        List<SearchResult> allResults = new ArrayList<>();

        for (String indexName : indexNames) {
            switch (searchMethod) {
                case "vector":
                    allResults.addAll(vectorSearch(indexName, queryVector, k));
                    break;
                case "text":
                    // 文本搜索需要原始查询，这里简化处理
                    break;
                case "mix":
                    allResults.addAll(vectorSearch(indexName, queryVector, k));
                    // 混合模式下同时执行文本搜索
                    break;
            }
        }

        // 去重并排序
        return deduplicateAndSort(allResults, k);
    }

    /**
     * kNN向量搜索
     */
    private List<SearchResult> vectorSearch(String indexName, float[] queryVector, int k) {
        // 构建kNN查询
        SearchRequest searchRequest = SearchRequest.of(s -> s
            .index(indexName)
            .size(k)
            .query(q -> q
                .knn(k -> k
                    .field("sentence_vector")
                    .vector(queryVector)
                    .k(k)
                )
            )
        );

        SearchResponse<Document> response = openSearchClient.search(searchRequest, Document.class);

        return response.hits().hits().stream()
            .map(hit -> SearchResult.builder()
                .document(hit.source())
                .score(hit.score())
                .build())
            .collect(Collectors.toList());
    }
}
```

#### 3.2.3 Bedrock LLM服务组件

```java
@Service
public class BedrockLlmService {

    @Value("${bedrock.llm.model-id}")
    private String modelId;

    @Value("${bedrock.region}")
    private String region;

    @Autowired
    private BedrockRuntimeClient bedrockClient;

    /**
     * 生成答案
     *
     * @param query 用户问题
     * @param contextDocs 上下文文档
     * @param systemPrompt 系统提示词
     * @return 生成的答案
     */
    public String generateAnswer(String query,
                                  List<ContextDocument> contextDocs,
                                  String systemPrompt) {
        // 构建消息
        List<Message> messages = new ArrayList<>();

        // 系统消息
        messages.add(Message.builder()
            .role(MessageRole.SYSTEM)
            .content(ContentBlock.fromText(systemPrompt))
            .build());

        // 用户消息（包含上下文和问题）
        StringBuilder userContent = new StringBuilder();
        userContent.append("相关文档如下:\n");
        for (ContextDocument doc : contextDocs) {
            userContent.append(doc.getText()).append("\n");
        }
        userContent.append("\n用户问题如下:\n").append(query);

        messages.add(Message.builder()
            .role(MessageRole.USER)
            .content(ContentBlock.fromText(userContent.toString()))
            .build());

        // 调用Bedrock
        ConverseRequest request = ConverseRequest.builder()
            .modelId(modelId)
            .messages(messages)
            .inferenceConfig(InferenceConfiguration.builder()
                .maxTokens(4096)
                .temperature(0.1f)
                .build())
            .build();

        ConverseResponse response = bedrockClient.converse(request);
        return response.output().message().content().get(0).text();
    }

    /**
     * 带重试的模型调用
     */
    @Retryable(value = {BedrockException.class},
               maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public String invokeWithRetry(String query, String systemPrompt, List<ContextDocument> docs) {
        return generateAnswer(query, docs, systemPrompt);
    }
}
```

### 3.3 Python数据处理服务组件

#### 3.3.1 文档处理服务

```python
# document_processing_service.py

from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from pydantic import BaseModel
from typing import List, Optional
import logging

app = FastAPI(title="Document Processing Service", version="1.0.0")

class ProcessRequest(BaseModel):
    file_path: str
    index_name: str

class ProcessResponse(BaseModel):
    status: str
    index_name: str
    chunks_processed: int
    message: str

class DocumentProcessorService:
    """文档处理服务"""

    def __init__(self):
        self.embeddings = BedrockEmbeddingsService()
        self.opensearch = OpenSearchDataLoadService()

    async def process_file(self, file_path: str, index_name: str) -> ProcessResponse:
        """处理单个PDF文件"""
        try:
            # 1. PDF解析
            content = await self._parse_pdf(file_path)

            # 2. 文本分块
            chunks = self._split_text(content)

            # 3. 生成Embedding
            embeddings = self.embeddings.embed_documents([c.text for c in chunks])

            # 4. 入库
            self.opensearch.bulk_insert(index_name, chunks, embeddings)

            return ProcessResponse(
                status="success",
                index_name=index_name,
                chunks_processed=len(chunks),
                message="Document processed successfully"
            )

        except Exception as e:
            logging.error(f"Failed to process file {file_path}: {e}")
            raise HTTPException(status_code=500, detail=str(e))

    async def _parse_pdf(self, file_path: str) -> str:
        """PDF解析 - 优先Docling，失败回退到PyPDF2"""
        try:
            # 尝试Docling
            return await self._parse_with_docling(file_path)
        except Exception as e:
            logging.warning(f"Docling failed: {e}, falling back to PyPDF2")
            return await self._parse_with_pypdf(file_path)

    def _split_text(self, content: str) -> List[TextChunk]:
        """文本分块 - 使用LangChain TextSplitter"""
        from langchain_text_splitters import RecursiveCharacterTextSplitter

        splitter = RecursiveCharacterTextSplitter(
            chunk_size=1000,
            chunk_overlap=200,
            separators=["\n\n", "\n", "。", ". ", " ", ""]
        )

        chunks = splitter.split_text(content)
        return [TextChunk(text=c, metadata={}) for c in chunks]

# API端点
@app.post("/process/document", response_model=ProcessResponse)
async def process_document(
    file: UploadFile = File(...),
    index_name: str = Form(...)
):
    """处理上传的PDF文档"""
    service = DocumentProcessorService()

    # 保存上传文件
    file_path = f"./documents/{file.filename}"
    with open(file_path, "wb") as f:
        content = await file.read()
        f.write(content)

    # 处理文件
    return await service.process_file(file_path, index_name)

@app.post("/embedding/generate")
async def generate_embedding(texts: List[str]):
    """批量生成Embedding"""
    service = BedrockEmbeddingsService()
    embeddings = service.embed_documents(texts)
    return {"embeddings": embeddings}
```

---

## 四、数据流转设计

### 4.1 文档处理数据流

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        文档处理数据流                                   │
└─────────────────────────────────────────────────────────────────────────┘

用户上传PDF
     │
     ▼
┌─────────────┐     ┌─────────────────────────────────────────────────────┐
│ Upload.tsx  │────►│  POST /api/documents/upload                          │
│             │     │  Multipart FormData                                   │
└─────────────┘     └──────────────────────┬──────────────────────────────┘
                                           │
                                           ▼
                              ┌──────────────────────────────┐
                              │     Java DocumentController  │
                              │     1. 接收上传文件          │
                              │     2. 生成索引名(MD5)       │
                              │     3. 转发到Python服务      │
                              └──────────────┬───────────────┘
                                             │
                                             │ HTTP/REST
                                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     Python Document Processing Service                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Step 1: PDF解析                               │   │
│  │  ┌─────────────────┐              ┌─────────────────┐           │   │
│  │  │    Docling      │   失败回退   │     PyPDF2      │           │   │
│  │  │ (首选解析器)    │─────────────►│  (备用解析器)   │           │   │
│  │  │                 │              │                 │           │   │
│  │  │ • 提取文本      │              │ • 提取文本      │           │   │
│  │  │ • 提取图像      │              │ • 按页分块      │           │   │
│  │  │ • 保留格式      │              │                 │           │   │
│  │  └────────┬────────┘              └────────┬────────┘           │   │
│  │           │                                │                    │   │
│  │           └────────────────┬───────────────┘                    │   │
│  │                            │                                    │   │
│  │                            ▼                                    │   │
│  │                   ┌─────────────────┐                           │   │
│  │                   │  Markdown/Plain │                           │   │
│  │                   │    Text         │                           │   │
│  │                   └────────┬────────┘                           │   │
│  └────────────────────────────┼────────────────────────────────────┘   │
│                               │                                        │
│                               ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Step 2: 文本分块                              │   │
│  │                                                                  │   │
│  │  ┌─────────────────────────────────────────────────────────┐    │   │
│  │  │  RecursiveCharacterTextSplitter                          │    │   │
│  │  │                                                          │    │   │
│  │  │  chunk_size=1000                                         │    │   │
│  │  │  chunk_overlap=200                                       │    │   │
│  │  │  separators=["\n\n", "\n", "。", ". ", " ", ""]           │    │   │
│  │  │                                                          │    │   │
│  │  │  处理逻辑:                                                │    │   │
│  │  │  1. 按分隔符层级切分                                      │    │   │
│  │  │  2. 合并重叠部分                                          │    │   │
│  │  │  3. 保留语义完整性                                        │    │   │
│  │  └────────────────────────┬────────────────────────────────┘    │   │
│  │                           │                                     │   │
│  │                           ▼                                     │   │
│  │                  ┌─────────────────┐                            │   │
│  │                  │   Text Chunks   │                            │   │
│  │                  │   [chunk_1...n] │                            │   │
│  │                  └────────┬────────┘                            │   │
│  └───────────────────────────┼─────────────────────────────────────┘   │
│                              │                                          │
│                              ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Step 3: 向量化处理                            │   │
│  │                                                                  │   │
│  │  ┌───────────────────────────────────────────────────────────┐  │   │
│  │  │  BedrockEmbeddingsService                                 │  │   │
│  │  │                                                            │  │   │
│  │  │  Model: amazon.titan-embed-text-v1                        │  │   │
│  │  │  Dimension: 1536                                           │  │   │
│  │  │  Region: ap-northeast-1                                    │  │   │
│  │  │  Batch Size: 10                                            │  │   │
│  │  │                                                            │  │   │
│  │  │  Input:  ["句子1", "句子2", ...]                           │  │   │
│  │  │  Output: [[0.123, -0.456, ...], [0.789, ...], ...]        │  │   │
│  │  └───────────────────────────┬───────────────────────────────┘  │   │
│  │                              │                                     │   │
│  │                              ▼                                     │   │
│  │                  ┌─────────────────┐                            │   │
│  │                  │   Embeddings    │                            │   │
│  │                  │   [n × 1536]    │                            │   │
│  │                  └────────┬────────┘                            │   │
│  └───────────────────────────┼─────────────────────────────────────┘   │
│                              │                                          │
│                              ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Step 4: 数据入库                              │   │
│  │                                                                  │   │
│  │  ┌───────────────────────────────────────────────────────────┐  │   │
│  │  │  OpenSearchDataLoadService                                │  │   │
│  │  │                                                            │  │   │
│  │  │  Index: {md5(filename)[:8]}                                │  │   │
│  │  │  Mapping: kNN vector (1536 dims)                          │  │   │
│  │  │                                                            │  │   │
│  │  │  Documents:                                                │  │   │
│  │  │  {                                                         │  │   │
│  │  │    "sentence_vector": [0.1, -0.2, ...],                   │  │   │
│  │  │    "paragraph": "完整段落内容",                            │  │   │
│  │  │    "sentence": "摘要句子",                                 │  │   │
│  │  │    "metadata": {"source": "file.pdf"}                     │  │   │
│  │  │  }                                                         │  │   │
│  │  └───────────────────────────┬───────────────────────────────┘  │   │
│  │                              │                                     │   │
│  │                              ▼                                     │   │
│  │                  ┌─────────────────┐                            │   │
│  │                  │   OpenSearch    │                            │   │
│  │                  │   Index Created │                            │   │
│  │                  └─────────────────┘                            │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
                                             │
                                             │ 返回处理结果
                                             ▼
                              ┌──────────────────────────────┐
                              │   Java DocumentService       │
                              │   更新processed_files记录    │
                              └──────────────────────────────┘
```

### 4.2 问答查询数据流

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        问答查询数据流                                   │
└─────────────────────────────────────────────────────────────────────────┘

用户提问
     │
     ▼
┌─────────────┐     ┌─────────────────────────────────────────────────────┐
│   QA.tsx    │────►│  POST /api/rag/answer                                │
│             │     │                                                      │
│ 选择文档    │     │  {                                                   │
│ 输入问题    │     │    sessionId: "session_xxx",                         │
│ 设置参数    │     │    indexNames: ["a1b2c3d4"],                        │
│             │     │    query: "TCL科技2024年归母净利...",                │
│             │     │    vecDocsNum: 3,                                    │
│             │     │    vecScoreThreshold: 0.0,                          │
│             │     │    rerankScoreThreshold: 0.5,                       │
│             │     │    searchMethod: "mix"                               │
│             │     │  }                                                   │
└─────────────┘     └──────────────────────┬──────────────────────────────┘
                                           │
                                           ▼
                              ┌──────────────────────────────┐
                              │     Java RagController       │
                              │     接收请求并校验           │
                              └──────────────┬───────────────┘
                                             │
                                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Java RAG Service                               │
│                          getAnswer()                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │                    Step 1: 问题向量化                              │ │
│  │                                                                   │ │
│  │    query = "TCL科技2024年归母净利低于预期的主要原因是什么？"       │ │
│  │                           │                                       │ │
│  │                           ▼                                       │ │
│  │    BedrockEmbeddingService.embedQuery(query)                      │ │
│  │                           │                                       │ │
│  │                           ▼                                       │ │
│  │    queryVector = [0.123f, -0.456f, 0.789f, ..., 0.234f]  // 1536 │ │
│  │                                                                   │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                           │                                             │
│                           ▼                                             │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │                    Step 2: 向量检索 (kNN)                         │ │
│  │                                                                   │ │
│  │    OpenSearch kNN Query:                                         │ │
│  │    {                                                              │ │
│  │      "size": 3,                                                   │ │
│  │      "query": {                                                   │ │
│  │        "knn": {                                                   │ │
│  │          "sentence_vector": {                                     │ │
│  │            "vector": [0.123, ...],                               │ │
│  │            "k": 3                                                 │ │
│  │          }                                                        │ │
│  │        }                                                          │ │
│  │      }                                                            │ │
│  │    }                                                              │ │
│  │                           │                                       │ │
│  │                           ▼                                       │ │
│  │    Recall Documents:                                              │ │
│  │    ┌─────────────────────────────────────────────────────────┐   │ │
│  │    │ Doc1: "TCL科技2024年归母净利同比下降15%..."  Score: 85.2│   │ │
│  │    │ Doc2: "主要原因是面板价格下跌..."             Score: 78.6│   │ │
│  │    │ Doc3: "半导体业务收入增长..."                 Score: 72.1│   │ │
│  │    └─────────────────────────────────────────────────────────┘   │ │
│  │                                                                   │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                           │                                             │
│                           ▼                                             │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │                    Step 3: 文档重排 (Rerank)                      │ │
│  │                                                                   │ │
│  │    BedrockRerankService.rerank(query, documents)                 │ │
│  │                                                                   │ │
│  │    Model: amazon.rerank-v1:0                                     │ │
│  │    Input:                                                        │ │
│  │      - Query: "TCL科技2024年归母净利低于预期的主要原因..."        │ │
│  │      - Documents: [Doc1, Doc2, Doc3]                             │ │
│  │                                                                   │ │
│  │    Output:                                                       │ │
│  │    ┌─────────────────────────────────────────────────────────┐   │ │
│  │    │ Doc2: RerankScore=0.92  →  Rank 1                      │   │ │
│  │    │ Doc1: RerankScore=0.85  →  Rank 2                      │   │ │
│  │    │ Doc3: RerankScore=0.45  →  Filtered (threshold=0.5)    │   │ │
│  │    └─────────────────────────────────────────────────────────┘   │ │
│  │                                                                   │ │
│  │    过滤后保留: Doc2, Doc1                                         │ │
│  │                                                                   │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                           │                                             │
│                           ▼                                             │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │                    Step 4: 上下文格式化                           │ │
│  │                                                                   │ │
│  │    formatContext(docs) → relatedDocs                             │ │
│  │                                                                   │ │
│  │    [                                                              │ │
│  │      {"text": "主要原因是面板价格下跌,导致毛利下降..."},          │ │
│  │      {"text": "TCL科技2024年归母净利同比下降15%..."}             │ │
│  │    ]                                                              │ │
│  │                                                                   │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                           │                                             │
│                           ▼                                             │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │                    Step 5: LLM生成答案                            │ │
│  │                                                                   │ │
│  │    BedrockLlmService.generateAnswer(query, relatedDocs)          │ │
│  │                                                                   │ │
│  │    Messages:                                                     │ │
│  │    ┌─────────────────────────────────────────────────────────┐   │ │
│  │    │ System: "你是一个证券专家，请根据相关文档回答用户问题" │   │ │
│  │    │ User: "相关文档如下:                                    │   │ │
│  │    │        主要原因是面板价格下跌...                         │   │ │
│  │    │        TCL科技2024年归母净利同比下降15%...               │   │ │
│  │    │        用户问题如下:                                     │   │ │
│  │    │        TCL科技2024年归母净利低于预期的主要原因是什么？" │   │ │
│  │    └─────────────────────────────────────────────────────────┘   │ │
│  │                                                                   │ │
│  │    Model: qwen.qwen3-235b-a22b-2507-v1:0                        │ │
│  │    Temperature: 0.1                                               │ │
│  │    Max Tokens: 4096                                               │ │
│  │                           │                                       │ │
│  │                           ▼                                       │ │
│  │    Answer:                                                        │ │
│  │    "TCL科技2024年归母净利低于预期的主要原因是面板价格持续下跌，  │ │
│  │     导致显示业务毛利率下降；同时半导体行业周期下行，"            │ │
│  │     "综合影响了公司整体盈利能力。"                                 │ │
│  │                                                                   │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                           │                                             │
│                           ▼                                             │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │                    Step 6: 返回结果                               │ │
│  │                                                                   │ │
│  │    RagResponse:                                                   │ │
│  │    {                                                              │ │
│  │      "answer": "TCL科技2024年归母净利...",                       │ │
│  │      "recallDocuments": [                                        │ │
│  │        {"pageContent": "...", "score": 85.2},                   │ │
│  │        {"pageContent": "...", "score": 78.6},                   │ │
│  │        {"pageContent": "...", "score": 72.1}                    │ │
│  │      ],                                                           │ │
│  │      "rerankDocuments": [                                        │ │
│  │        {"pageContent": "...", "score": 78.6, "rerankScore": 0.92},│ │
│  │        {"pageContent": "...", "score": 85.2, "rerankScore": 0.85} │ │
│  │      ],                                                           │ │
│  │      "sourceDocuments": [...]                                     │ │
│  │    }                                                              │ │
│  │                                                                   │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
                           │
                           ▼
            ┌──────────────────────────────┐
            │   前端展示                    │
            │   • 答案文本                  │
            │   • Recall文档(可折叠)        │
            │   • Rerank文档(可折叠)        │
            │   • 关键词高亮                │
            └──────────────────────────────┘
```

### 4.3 数据流转时序图

```
┌────────┐     ┌────────┐     ┌────────┐     ┌──────────┐     ┌──────────┐     ┌────────┐     ┌────────┐
│ Client │     │ Gateway│     │RagCtrl │     │ RagSrvc  │     │PythonSvc │     │OpenSearch│    │ Bedrock│
│        │     │        │     │        │     │          │     │          │     │          │    │        │
└───┬────┘     └───┬────┘     └───┬────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘    └───┬────┘
    │              │              │               │                │                │              │
    │ ════════════════════════════════════ 文档上传流程 ═════════════════════════════════════════════│
    │              │              │               │                │                │              │
    │ POST /upload │              │               │                │                │              │
    │─────────────►│              │               │                │                │              │
    │              │              │               │                │                │              │
    │              │ 转发到Python │               │                │                │              │
    │              │──────────────│──────────────►│                │                │              │
    │              │              │               │ POST /process  │                │              │
    │              │              │               │───────────────►│                │              │
    │              │              │               │                │                │              │
    │              │              │               │                │ PDF解析        │              │
    │              │              │               │                │ 文本分块       │              │
    │              │              │               │                │ Embedding生成  │              │
    │              │              │               │                │─────┐          │              │
    │              │              │               │                │     │ Bedrock    │              │
    │              │              │               │                │◄────┘ Titan     │              │
    │              │              │               │                │                │              │
    │              │              │               │                │ Bulk Insert    │              │
    │              │              │               │                │───────────────►│              │
    │              │              │               │                │                │              │
    │              │              │               │   success      │                │              │
    │              │              │               │◄───────────────│                │              │
    │              │              │   success     │                │                │              │
    │              │◄─────────────│───────────────│                │                │              │
    │              │              │               │                │                │              │
    │   success    │              │               │                │                │              │
    │◄─────────────│              │               │                │                │              │
    │              │              │               │                │                │              │
    │ ════════════════════════════════════ 问答查询流程 ═════════════════════════════════════════════│
    │              │              │               │                │                │              │
    │ POST /rag    │              │               │                │                │              │
    │─────────────►│              │               │                │                │              │
    │              │ 转发到RagCtrl│               │                │                │              │
    │              │─────────────►│               │                │                │              │
    │              │              │ 调用getAnswer │                │                │              │
    │              │              │──────────────►│                │                │              │
    │              │              │               │                │                │              │
    │              │              │               │ embedQuery     │                │              │
    │              │              │               │───────────────►│───────────────►│              │
    │              │              │               │                │                │  Titan       │
    │              │              │               │ queryVector    │                │  Embedding   │
    │              │              │               │◄───────────────│◄───────────────│              │
    │              │              │               │                │                │              │
    │              │              │               │ kNN_search     │                │              │
    │              │              │               │───────────────────────────────►│              │
    │              │              │               │                │                │              │
    │              │              │               │ recallResults  │                │              │
    │              │              │               │◄───────────────────────────────│              │
    │              │              │               │                │                │              │
    │              │              │               │ rerank         │                │              │
    │              │              │               │───────────────►│───────────────►│              │
    │              │              │               │                │                │   Rerank     │
    │              │              │               │ rerankResults  │                │   Model      │
    │              │              │               │◄───────────────│◄───────────────│              │
    │              │              │               │                │                │              │
    │              │              │               │ generateAnswer │                │              │
    │              │              │               │───────────────────────────────────────────────►│
    │              │              │               │                │                │              │
    │              │              │               │   answer       │                │    LLM       │
    │              │              │               │◄────────────────────────────────────────────────│
    │              │              │               │                │                │              │
    │              │              │   response    │                │                │              │
    │              │◄─────────────│───────────────│                │                │              │
    │              │              │               │                │                │              │
    │   response   │              │               │                │                │              │
    │◄─────────────│              │               │                │                │              │
    │              │              │               │                │                │              │
```

---

## 五、关键技术点

### 5.1 双PDF解析器策略

**问题背景：** Docling在Windows环境下存在资源文件路径问题

**解决方案：** 主解析器 + 备用解析器（PyPDF2）

```java
@Service
public class DocumentParserService {

    @Autowired
    private PythonProcessingClient pythonClient;

    /**
     * 解析PDF文件
     * Python层实现双解析器策略
     */
    public ParsedDocument parsePdf(String filePath) {
        // 调用Python服务进行解析
        return pythonClient.parseDocument(filePath);
    }
}
```

```python
# Python层实现
class DocumentParser:
    """双解析器策略实现"""

    def parse(self, file_path: str) -> ParsedDocument:
        """解析PDF，优先Docling，失败回退到PyPDF2"""

        if self.docling_available:
            try:
                return self._parse_with_docling(file_path)
            except Exception as e:
                logger.warning(f"Docling failed: {e}, falling back to PyPDF2")
                return self._parse_with_pypdf(file_path)
        else:
            return self._parse_with_pypdf(file_path)
```

**解析器对比：**

| 特性 | Docling | PyPDF2 |
|------|---------|--------|
| 文本提取质量 | 高（保留格式） | 中（纯文本） |
| 图像提取 | 支持 | 不支持 |
| 表格识别 | 支持 | 不支持 |
| Windows兼容性 | 有问题 | 完全兼容 |
| 处理速度 | 较慢 | 较快 |
| 依赖复杂度 | 高 | 低 |

### 5.2 kNN向量搜索实现

**索引映射配置：**

```json
{
  "settings": {
    "index": {
      "knn": true,
      "knn.algo_param.ef_search": 512
    }
  },
  "mappings": {
    "properties": {
      "sentence_vector": {
        "type": "knn_vector",
        "dimension": 1536,
        "method": {
          "name": "hnsw",
          "space_type": "l2",
          "engine": "faiss",
          "parameters": {
            "ef_construction": 512,
            "m": 16
          }
        }
      }
    }
  }
}
```

**HNSW参数说明：**

| 参数 | 值 | 说明 |
|------|-----|------|
| ef_search | 512 | 搜索时考察的邻居数，越大越精确但越慢 |
| ef_construction | 512 | 构建索引时考察的邻居数 |
| m | 16 | 每个节点的最大连接数 |
| space_type | l2 | 欧几里得距离 |

### 5.3 三种搜索模式实现

```java
@Service
public class SearchService {

    /**
     * 三种搜索模式实现
     */
    public List<SearchResult> search(SearchRequest request) {
        switch (request.getSearchMethod()) {
            case "vector":
                return vectorSearch(request);
            case "text":
                return textSearch(request);
            case "mix":
                return hybridSearch(request);
            default:
                throw new IllegalArgumentException("Unknown search method");
        }
    }

    /**
     * 混合搜索：向量 + 文本
     */
    private List<SearchResult> hybridSearch(SearchRequest request) {
        // 并行执行向量搜索和文本搜索
        CompletableFuture<List<SearchResult>> vectorFuture =
            CompletableFuture.supplyAsync(() -> vectorSearch(request));
        CompletableFuture<List<SearchResult>> textFuture =
            CompletableFuture.supplyAsync(() -> textSearch(request));

        // 合并结果
        List<SearchResult> vectorResults = vectorFuture.join();
        List<SearchResult> textResults = textFuture.join();

        // 去重并排序
        return mergeAndDeduplicate(vectorResults, textResults, request.getK());
    }
}
```

**搜索模式选择指南：**

```
                            用户查询
                               │
                               ▼
                      ┌─────────────────┐
                      │ 是否包含专业术语 │
                      │ 或精确关键词？   │
                      └────────┬────────┘
                               │
                ┌──────────────┴──────────────┐
                │ YES                         │ NO
                ▼                             ▼
       ┌─────────────────┐           ┌─────────────────┐
       │ 是否需要语义理解 │           │ 是否需要全面召回 │
       └────────┬────────┘           └────────┬────────┘
                │                              │
         ┌──────┴──────┐               ┌──────┴──────┐
         │ YES         │ NO            │ YES         │ NO
         ▼             ▼               ▼             ▼
     ┌───────┐    ┌───────┐       ┌───────┐    ┌───────┐
     │  mix  │    │ text  │       │  mix  │    │vector │
     └───────┘    └───────┘       └───────┘    └───────┘
```

### 5.4 Rerank重排序机制

**实现原理：**

```java
@Service
public class RerankService {

    @Autowired
    private BedrockAgentRuntimeClient bedrockAgentClient;

    /**
     * 使用Bedrock Rerank模型对文档重新排序
     */
    public List<RerankResult> rerank(String query, List<Document> documents) {
        // 构建请求
        RerankRequest rerankRequest = RerankRequest.builder()
            .queries(Arrays.asList(
                Query.builder()
                    .type("TEXT")
                    .textQuery(TextQuery.builder().text(query).build())
                    .build()
            ))
            .sources(documents.stream()
                .map(doc -> Source.builder()
                    .type("INLINE")
                    .inlineDocumentSource(
                        InlineDocumentSource.builder()
                            .type("TEXT")
                            .textDocument(TextDocument.builder().text(doc.getText()).build())
                            .build()
                    )
                    .build())
                .collect(Collectors.toList()))
            .rerankingConfiguration(
                RerankingConfiguration.builder()
                    .type("BEDROCK_RERANKING_MODEL")
                    .bedrockRerankingConfiguration(
                        BedrockRerankingConfiguration.builder()
                            .numberOfResults(documents.size())
                            .modelConfiguration(
                                ModelConfiguration.builder()
                                    .modelArn("arn:aws:bedrock:ap-northeast-1::foundation-model/amazon.rerank-v1:0")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build();

        // 调用Bedrock
        RerankResponse response = bedrockAgentClient.rerank(rerankRequest);

        // 转换结果
        return response.results().stream()
            .map(result -> RerankResult.builder()
                .index(result.index())
                .score(result.relevanceScore())
                .document(documents.get(result.index()))
                .build())
            .collect(Collectors.toList());
    }
}
```

**重排效果示例：**

```
召回阶段:
┌─────────────────────────────────────────────────────────┐
│ Doc1: Score=85.2  "TCL科技2024年归母净利同比下降15%..." │
│ Doc2: Score=78.6  "主要原因是面板价格下跌..."           │
│ Doc3: Score=72.1  "半导体业务收入增长..."               │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼ Rerank
┌─────────────────────────────────────────────────────────┐
│ Doc2: RerankScore=0.92  "主要原因是面板价格下跌..."     │ ← 最相关
│ Doc1: RerankScore=0.85  "TCL科技2024年归母净利..."      │
│ Doc3: RerankScore=0.45  "半导体业务收入增长..."         │ ← 被过滤
└─────────────────────────────────────────────────────────┘
```

### 5.5 AWS跨区域架构

**设计原因：** Bedrock模型在不同区域的可用性不同

```
┌─────────────────────────────────────────────────────────────────────┐
│                        AWS跨区域架构                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ap-east-1 (香港)              ap-northeast-1 (东京)              │
│   ┌─────────────────────┐       ┌─────────────────────────────┐    │
│   │    OpenSearch       │       │        Bedrock              │    │
│   │                     │       │                             │    │
│   │ • 向量数据存储       │       │ • Titan Embeddings ✅       │    │
│   │ • 低延迟访问         │       │ • Rerank ✅                 │    │
│   │ • 数据主权合规       │       │ • Qwen/Claude ✅            │    │
│   │                     │       │                             │    │
│   │                     │       │ 为什么选东京？               │    │
│   │                     │       │ • 模型可用性更全             │    │
│   │                     │       │ • 推理延迟较低               │    │
│   │                     │       │ • 亚太区域推荐               │    │
│   └─────────────────────┘       └─────────────────────────────┘    │
│                                                                     │
│   网络延迟考虑:                                                     │
│   • 向量搜索: ~50-100ms (OpenSearch)                               │
│   • Embedding: ~200-300ms (跨区域)                                 │
│   • Rerank: ~150-200ms (跨区域)                                    │
│   • LLM生成: ~1000-3000ms (跨区域)                                 │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.6 文档索引命名策略

**设计目标：** 每个上传的PDF文件对应唯一的OpenSearch索引

```java
@Service
public class IndexNamingService {

    /**
     * 生成索引名：使用文件名的MD5哈希前8位
     */
    public String generateIndexName(String fileName) {
        return DigestUtils.md5DigestAsHex(fileName.getBytes(StandardCharsets.UTF_8))
            .substring(0, 8);
    }
    // 示例: "report_2024.pdf" → "a1b2c3d4"
}
```

**优势：**
- 唯一性：不同文件名生成不同索引
- 确定性：相同文件名始终生成相同索引
- 简洁性：8位长度，便于管理和查询

**索引映射关系存储：**

```json
// processed_files.txt
{"file_name": "TCL科技2024年报.pdf", "index_name": "a1b2c3d4"}
{"file_name": "比亚迪2024年报.pdf", "index_name": "e5f6g7h8"}
```

---

## 六、API接口设计

### 6.1 接口总览

| 方法 | 路径 | 功能 | 请求类型 | 所属服务 |
|------|------|------|----------|----------|
| POST | /api/rag/answer | RAG问答 | application/json | Java |
| POST | /api/documents/upload | 上传处理PDF | multipart/form-data | Java → Python |
| GET | /api/documents | 获取已处理文件列表 | - | Java |
| GET | /api/documents/index/{filename} | 获取文件索引名 | - | Java |
| GET | /api/rag/top-questions/{index} | 获取热门问题 | - | Java |
| POST | /api/rag/top-questions/multi | 多索引热门问题 | application/json | Java |
| GET | /api/health | 健康检查 | - | Java |
| POST | /process/document | 处理文档(Python内部) | multipart/form-data | Python |
| POST | /process/batch | 批量处理文档(Python内部) | application/json | Python |
| POST | /embedding/generate | 生成Embedding(Python内部) | application/json | Python |

### 6.2 核心接口详细设计

#### 6.2.1 RAG问答接口

**Endpoint:** `POST /api/rag/answer`

**Request:**
```json
{
  "sessionId": "session_1710493200_abc123",
  "indexNames": ["a1b2c3d4", "e5f6g7h8"],
  "query": "TCL科技2024年归母净利低于预期的主要原因是什么？",
  "module": "RAG",
  "vecDocsNum": 3,
  "txtDocsNum": 3,
  "vecScoreThreshold": 0.0,
  "textScoreThreshold": 0.0,
  "rerankScoreThreshold": 0.5,
  "searchMethod": "mix"
}
```

**Response:**
```json
{
  "answer": "TCL科技2024年归母净利低于预期的主要原因是面板价格持续下跌，导致显示业务毛利率下降；同时半导体行业周期下行，综合影响了公司整体盈利能力。",
  "sourceDocuments": [
    {
      "pageContent": "主要原因是面板价格下跌，导致毛利下降...",
      "score": 78.6,
      "rerankScore": 0.92
    }
  ],
  "recallDocuments": [
    {
      "pageContent": "TCL科技2024年归母净利同比下降15%...",
      "score": 85.2
    }
  ],
  "rerankDocuments": [
    {
      "pageContent": "主要原因是面板价格下跌...",
      "score": 78.6,
      "rerankScore": 0.92
    }
  ]
}
```

**Java实现:**
```java
@RestController
@RequestMapping("/api/rag")
public class RagController {

    @Autowired
    private RagService ragService;

    @PostMapping("/answer")
    public ResponseEntity<RagResponse> ragAnswer(@RequestBody @Valid RagRequest request) {
        RagResponse response = ragService.getAnswer(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/top-questions/{indexName}")
    public ResponseEntity<TopQuestionsResponse> getTopQuestions(
            @PathVariable String indexName,
            @RequestParam(defaultValue = "5") int topN) {
        List<QuestionCount> questions = ragService.getTopQuestions(indexName, topN);
        return ResponseEntity.ok(new TopQuestionsResponse(questions));
    }
}
```

#### 6.2.2 文件上传接口

**Endpoint:** `POST /api/documents/upload`

**Request:**
```
Content-Type: multipart/form-data

file: <PDF二进制文件>
indexName: "a1b2c3d4" (可选，不传则自动生成)
```

**Response (成功):**
```json
{
  "status": "success",
  "indexName": "a1b2c3d4",
  "fileName": "report_2024.pdf",
  "chunksProcessed": 42,
  "message": "Document processed successfully"
}
```

**Java实现:**
```java
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "indexName", required = false) String indexName) {

        UploadResponse response = documentService.processDocument(file, indexName);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ProcessedFile>> getProcessedFiles() {
        return ResponseEntity.ok(documentService.getProcessedFiles());
    }
}
```

### 6.3 数据模型设计

#### 6.3.1 Request/Response Models

```java
// Request
@Data
public class RagRequest {
    @NotBlank
    private String sessionId;

    @NotEmpty
    private List<String> indexNames;

    @NotBlank
    private String query;

    private String module = "RAG";
    private int vecDocsNum = 3;
    private int txtDocsNum = 3;
    private float vecScoreThreshold = 0.0f;
    private float textScoreThreshold = 0.0f;
    private float rerankScoreThreshold = 0.5f;
    private String searchMethod = "vector";
}

// Response
@Data
@Builder
public class RagResponse {
    private String answer;
    private List<SourceDocument> sourceDocuments;
    private List<RecallDocument> recallDocuments;
    private List<SourceDocument> rerankDocuments;
}

@Data
@Builder
public class SourceDocument {
    private String pageContent;
    private float score;
    private Float rerankScore;
}

@Data
@Builder
public class RecallDocument {
    private String pageContent;
    private float score;
}
```

---

## 七、数据模型设计

### 7.1 OpenSearch索引结构

```
Index: {index_name}  # 例: a1b2c3d4
       │
       ├── Document 1
       │   ├── _id: "uuid-xxx"
       │   ├── sentence_vector: [0.123, -0.456, ..., 0.789]  # 1536维
       │   ├── paragraph: "完整段落内容..."
       │   ├── sentence: "摘要句子 (最大300字)"
       │   ├── metadata:
       │   │   ├── source: "report.pdf"
       │   │   └── tenant_id: "tenant_a" (可选)
       │   └── image_base64: "..." (可选)
       │
       ├── Document 2
       │   └── ...
       │
       └── Document N
```

### 7.2 Mapping配置

```json
{
  "settings": {
    "index": {
      "knn": true,
      "knn.algo_param.ef_search": 512
    }
  },
  "mappings": {
    "properties": {
      "sentence_vector": {
        "type": "knn_vector",
        "dimension": 1536,
        "method": {
          "name": "hnsw",
          "space_type": "l2",
          "engine": "faiss",
          "parameters": {
            "ef_construction": 512,
            "m": 16
          }
        }
      },
      "paragraph": {
        "type": "text",
        "analyzer": "standard"
      },
      "sentence": {
        "type": "keyword"
      },
      "metadata": {
        "properties": {
          "source": { "type": "keyword" },
          "created_at": { "type": "date" },
          "created_by": { "type": "keyword" }
        }
      },
      "image_base64": {
        "type": "text",
        "index": false
      },
      "tenant_id": {
        "type": "keyword"
      }
    }
  }
}
```

### 7.3 字段说明

| 字段 | 类型 | 说明 | 用途 |
|------|------|------|------|
| sentence_vector | knn_vector (1536) | 文本嵌入向量 | 向量检索 |
| paragraph | text | 完整段落内容 | 返回给用户 |
| sentence | keyword | 摘要句子 | Rerank输入 |
| metadata | object | 元数据对象 | 存储来源信息 |
| metadata.source | keyword | 来源文件名 | 结果溯源 |
| image_base64 | text | 图像Base64 | 多模态支持(可选) |
| tenant_id | keyword | 租户ID | 多租户隔离(可选) |

### 7.4 Java实体类设计

```java
@Data
@Document(indexName = "#{@indexNameGenerator.generateIndexName()}")
public class RagDocument {

    @Id
    private String id;

    @Field(name = "sentence_vector", type = FieldType.Knn_Vector, dimension = 1536)
    private float[] sentenceVector;

    @Field(name = "paragraph", type = FieldType.Text)
    private String paragraph;

    @Field(name = "sentence", type = FieldType.Keyword)
    private String sentence;

    @Field(name = "metadata", type = FieldType.Object)
    private Metadata metadata;

    @Field(name = "tenant_id", type = FieldType.Keyword)
    private String tenantId;

    @Data
    public static class Metadata {
        private String source;
        private String createdAt;
        private String createdBy;
    }
}
```

### 7.5 问题历史存储

```
question_history/
├── {index_name}.json
│   └── ["问题1", "问题2", "问题3", ...]
├── {index_name2}.json
│   └── [...]
```

---

## 八、权限管理

### 8.1 多租户架构方案

本系统支持两种多租户隔离方案：

#### 方案一：物理隔离 (Index per Tenant)

**实现方式：**
- 每个租户创建独立的OpenSearch索引
- 索引命名规则: `{tenant_id}_{index_name}`
- 通过索引级别的访问控制实现数据隔离

**Java实现:**
```java
@Service
public class TenantIndexService {

    /**
     * 构建租户专属索引名
     */
    public String buildTenantIndex(String tenantId, String indexName) {
        return String.format("%s_%s", tenantId, indexName);
    }

    /**
     * 查询租户文档
     */
    public List<Document> searchDocuments(String tenantId, String query, float[] queryVector) {
        String tenantIndex = buildTenantIndex(tenantId, "documents");

        return openSearchClient.search(s -> s
            .index(tenantIndex)
            .query(q -> q
                .knn(k -> k
                    .field("sentence_vector")
                    .vector(queryVector)
                    .k(10)
                )
            ),
            Document.class
        ).hits().hits().stream()
            .map(Hit::source)
            .collect(Collectors.toList());
    }
}
```

**优势：**
- 数据完全隔离，安全性最高
- 支持索引级别的备份和恢复
- 便于数据迁移和清理
- 性能互不影响

**适用场景：**
- 数据安全要求极高的业务场景（如抵押品筛选）
- 租户数量较少(建议<50个)

#### 方案二：逻辑隔离 (Metadata Filtering)

**实现方式：**
- 所有租户共享同一个索引
- 每个文档添加`tenant_id`元数据字段
- 查询时通过过滤条件实现数据隔离

**Java实现:**
```java
@Service
public class SharedIndexService {

    /**
     * 带租户过滤的查询
     */
    public List<Document> searchDocuments(String tenantId, String query, float[] queryVector) {
        return openSearchClient.search(s -> s
            .index("shared_documents")
            .query(q -> q
                .bool(b -> b
                    .must(m -> m
                        .knn(k -> k
                            .field("sentence_vector")
                            .vector(queryVector)
                            .k(10)
                        )
                    )
                    .filter(f -> f
                        .term(t -> t
                            .field("tenant_id")
                            .value(tenantId)
                        )
                    )
                )
            ),
            Document.class
        ).hits().hits().stream()
            .map(Hit::source)
            .collect(Collectors.toList());
    }
}
```

**优势：**
- 索引管理简单，资源利用率高
- 支持跨租户聚合分析
- 易于扩展新租户

**适用场景：**
- 租户数量多(>50个)
- 数据安全要求适中
- 需要跨租户分析能力

### 8.2 RBAC权限模型

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              RBAC权限模型                                   │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                              用户 (User)                                 │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  user_id | username | department | tenant_id | role_ids            │ │
│  │  u001    | 张三     | 运营部     | tenant_a   | [r001, r002]       │ │
│  │  u002    | 李四     | 风控部     | tenant_b   | [r003]             │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ 分配
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                              角色 (Role)                                 │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  role_id | role_name    | description      | permission_ids        │ │
│  │  r001    | 管理员       | 系统管理员       | [p001, p002, p003]    │ │
│  │  r002    | 文档管理员   | 文档上传和管理   | [p001, p002]          │ │
│  │  r003    | 普通用户     | 只读访问         | [p001]                │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ 包含
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                             权限 (Permission)                            │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  permission_id | resource    | action   | description              │ │
│  │  p001          | document    | READ     | 查看文档和问答           │ │
│  │  p002          | document    | WRITE    | 上传和管理文档           │ │
│  │  p003          | system      | ADMIN    | 系统配置管理             │ │
│  │  p004          | audit_log   | READ     | 查看审计日志             │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

### 8.3 Spring Security配置

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private TenantInterceptor tenantInterceptor;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api/rag/answer").hasAuthority("DOCUMENT_READ")
                .requestMatchers("/api/documents/upload").hasAuthority("DOCUMENT_WRITE")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class)
            .addInterceptor(tenantInterceptor);

        return http.build();
    }
}

@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler) {
        // 从JWT获取用户信息
        String token = request.getHeader("Authorization");
        UserInfo user = jwtService.parseToken(token.replace("Bearer ", ""));

        // 设置租户上下文
        TenantContext.setCurrentTenant(user.getTenantId());
        TenantContext.setCurrentUser(user);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清理上下文
        TenantContext.clear();
    }
}
```

### 8.4 审计日志

```java
@Entity
@Table(name = "audit_logs")
@Data
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String auditId;

    private LocalDateTime timestamp;
    private String userId;
    private String tenantId;
    private String action;
    private String resource;
    private String requestJson;
    private String responseJson;
    private Integer statusCode;
    private String ipAddress;
    private String userAgent;
    private Long executionTimeMs;
}

@Service
public class AuditLogService {

    @Async
    public void logAudit(AuditLog log) {
        auditLogRepository.save(log);
    }
}

@Aspect
@Component
public class AuditAspect {

    @Autowired
    private AuditLogService auditLogService;

    @Around("@annotation(Auditable)")
    public Object logAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();

            // 记录成功审计日志
            auditLogService.logAudit(createAuditLog(joinPoint, result,
                System.currentTimeMillis() - start, 200));

            return result;
        } catch (Exception e) {
            // 记录失败审计日志
            auditLogService.logAudit(createAuditLog(joinPoint, null,
                System.currentTimeMillis() - start, 500));
            throw e;
        }
    }
}
```

### 8.5 方案选择建议

| 场景 | 推荐方案 | 理由 |
|------|----------|------|
| OPS/COB合规问答 | 方案二: 逻辑隔离 | 部门数量有限，需要跨部门分析能力 |
| 抵押品智能筛选 | 方案一: 物理隔离 | 涉及客户敏感数据，安全要求高，需业务线隔离 |
| 混合场景 | 组合方案 | 根据数据敏感度分级别采用不同隔离策略 |

---

## 九、RAG效果评估

### 9.1 评估指标体系

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          RAG效果评估体系                               │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                          检索效果指标                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐         │
│  │   准确率 (Precision)              │  │   召回率 (Recall)               │  │   MRR            │
│  │                 │  │                 │  │                 │         │
│  │  相关文档数     │  │  检索出的相关   │  │  第一个相关     │         │
│  │  ───────────    │  │  ────────────   │  │  文档排名的     │         │
│  │  检索出的总文档 │  │  总相关文档数   │  │  倒数           │         │
│  │                 │  │                 │  │                 │         │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘         │
│                                                                         │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐         │
│  │   NDCG@K        │  │   Hit Rate@K    │  │   Latency       │         │
│  │                 │  │                 │  │                 │         │
│  │  考虑位置的加   │  │  Top-K中是否    │  │  检索响应       │         │
│  │  权评分         │  │  包含相关文档   │  │  时间           │         │
│  │                 │  │                 │  │                 │         │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘         │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                          生成效果指标                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐         │
│  │   BLEU Score    │  │   ROUGE Score   │  │   人工评估       │         │
│  │                 │  │                 │  │                 │         │
│  │  N-gram精确匹配 │  │  最长公共子序列 │  │  • 准确性        │         │
│  │                 │  │                 │  │  • 相关性        │         │
│  │  与参考答案对比 │  │  与参考答案对比 │  │  • 可读性        │         │
│  │                 │  │                 │  │  • 完整性        │         │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘         │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                          端到端效果指标                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐         │
│  │   答案准确率    │  │   溯源准确率    │  │   用户满意度     │         │
│  │                 │  │                 │  │                 │         │
│  │  答案正确的问题 │  │  引用正确的     │  │  评分>=4分的     │         │
│  │  占比           │  │  文档占比       │  │  问答占比       │         │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘         │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 9.2 评估方法

#### 9.2.1 离线评估

```java
@Service
public class RagEvaluationService {

    /**
     * 批量评估RAG效果
     */
    public EvaluationResult evaluate(List<EvalDataset> dataset) {
        EvaluationMetrics metrics = new EvaluationMetrics();

        for (EvalDataset item : dataset) {
            // 执行RAG
            RagResponse response = ragService.getAnswer(
                RagRequest.builder()
                    .query(item.getQuestion())
                    .indexNames(item.getIndexNames())
                    .build()
            );

            // 计算检索指标
            double precision = calculatePrecision(
                response.getRecallDocuments(),
                item.getRelevantDocuments()
            );
            double recall = calculateRecall(
                response.getRecallDocuments(),
                item.getRelevantDocuments()
            );

            // 计算生成指标
            double bleu = calculateBleu(response.getAnswer(), item.getReferenceAnswer());
            double rouge = calculateRouge(response.getAnswer(), item.getReferenceAnswer());

            metrics.add(precision, recall, bleu, rouge);
        }

        return metrics.average();
    }

    /**
     * 计算Precision@K
     */
    private double calculatePrecision(List<Document> retrieved, List<String> relevant) {
        if (retrieved.isEmpty()) return 0.0;

        long relevantCount = retrieved.stream()
            .filter(doc -> relevant.contains(doc.getId()))
            .count();

        return (double) relevantCount / retrieved.size();
    }
}
```

#### 9.2.2 在线评估

```java
@Service
public class OnlineEvaluationService {

    /**
     * 记录用户反馈
     */
    public void recordFeedback(String sessionId, String query,
                               String answer, UserFeedback feedback) {
        FeedbackRecord record = FeedbackRecord.builder()
            .sessionId(sessionId)
            .query(query)
            .answer(answer)
            .rating(feedback.getRating())
            .isHelpful(feedback.isHelpful())
            .comment(feedback.getComment())
            .timestamp(LocalDateTime.now())
            .build();

        feedbackRepository.save(record);
    }

    /**
     * 计算用户满意度
     */
    public double calculateSatisfactionScore(LocalDate start, LocalDate end) {
        List<FeedbackRecord> feedbacks = feedbackRepository
            .findByTimestampBetween(start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        if (feedbacks.isEmpty()) return 0.0;

        double avgRating = feedbacks.stream()
            .mapToInt(FeedbackRecord::getRating)
            .average()
            .orElse(0.0);

        return avgRating / 5.0; // 归一化到0-1
    }
}
```

### 9.3 评估数据集构建

```json
{
  "eval_dataset": [
    {
      "id": "eval_001",
      "question": "TCL科技2024年归母净利低于预期的主要原因是什么？",
      "reference_answer": "主要原因是面板价格持续下跌导致显示业务毛利率下降，同时半导体行业周期下行影响整体盈利能力。",
      "relevant_documents": ["doc_001", "doc_003"],
      "index_names": ["a1b2c3d4"],
      "category": "财务分析",
      "difficulty": "medium"
    }
  ]
}
```

### 9.4 效果优化建议

| 问题 | 优化方向 | 具体措施 |
|------|----------|----------|
| 检索准确率低 | 向量模型优化 | 微调Embedding模型，使用领域特定语料 |
| 召回不足 | 混合搜索 | 结合向量搜索和关键词搜索 |
| 重排序效果差 | Rerank阈值调优 | 根据业务场景调整rerank_score_threshold |
| 答案幻觉 | Prompt工程 | 强化约束，要求严格基于上下文回答 |
| 答案不完整 | 上下文窗口扩展 | 增加vec_docs_num，使用更大的LLM |
| 响应速度慢 | 性能优化 | 引入缓存，异步处理，向量索引优化 |

### 9.5 持续监控指标

```yaml
# 监控指标配置
monitoring:
  retrieval:
    - name: avg_retrieval_latency
      threshold: 200ms
      alert: p99 > 500ms
    - name: recall_rate
      threshold: 0.85
      alert: < 0.80

  generation:
    - name: avg_generation_latency
      threshold: 2000ms
      alert: p99 > 5000ms
    - name: answer_relevance_score
      threshold: 0.80
      alert: < 0.75

  user_feedback:
    - name: satisfaction_score
      threshold: 4.0/5.0
      alert: < 3.5
    - name: thumbs_up_ratio
      threshold: 0.70
      alert: < 0.60
```

---

## 附录

### A. 技术栈版本

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 17+ | LTS版本 |
| SpringBoot | 3.2.x | 最新稳定版 |
| Spring AI | 1.0.x | AI抽象框架 |
| Python | 3.10+ | 数据处理服务 |
| FastAPI | 0.100+ | Python Web框架 |
| React | 18.x | 前端框架 |
| TypeScript | 5.x | 前端语言 |
| AWS SDK | 2.x | Java AWS SDK |
| OpenSearch | 2.x | 向量数据库 |

### B. 项目文件结构

```
huatai-rag-system/
├── java-backend/                    # Java后端服务
│   ├── src/main/java/
│   │   └── com/huatai/rag/
│   │       ├── controller/          # REST API控制器
│   │       ├── service/             # 业务逻辑层
│   │       ├── repository/          # 数据访问层
│   │       ├── config/              # 配置类
│   │       ├── security/            # 安全配置
│   │       └── dto/                 # 数据传输对象
│   ├── src/main/resources/
│   │   ├── application.yml          # 主配置
│   │   ├── application-dev.yml      # 开发环境配置
│   │   └── application-prod.yml     # 生产环境配置
│   └── pom.xml                      # Maven配置
│
├── python-service/                  # Python数据处理服务
│   ├── app/
│   │   ├── services/                # 业务服务
│   │   ├── models/                  # 数据模型
│   │   ├── api/                     # API路由
│   │   └── utils/                   # 工具函数
│   ├── requirements.txt             # Python依赖
│   └── main.py                      # 入口文件
│
├── frontend/                        # 前端应用
│   ├── src/
│   │   ├── pages/                   # 页面组件
│   │   ├── components/              # 公共组件
│   │   ├── services/                # API服务
│   │   └── utils/                   # 工具函数
│   ├── package.json                 # NPM配置
│   └── vite.config.ts               # Vite配置
│
├── docs/                            # 文档
│   ├── technical_design_doc.md      # 技术设计文档
│   ├── api_spec.md                  # API规范
│   └── deployment_guide.md          # 部署指南
│
└── docker/                          # Docker配置
    ├── docker-compose.yml
    ├── Dockerfile.java
    └── Dockerfile.python
```

---

*文档结束*
