# Java Backend Architecture Diagrams

## 1. Layered Architecture & Port/Adapter Isolation

```mermaid
graph TB
    subgraph Frontend["Frontend (React + Vite :8080)"]
        FE[React App]
    end

    subgraph API["api layer"]
        UC[UploadController<br/>POST /upload_files]
        RC[RagController<br/>POST /rag_answer]
        PC[ProcessedFileController<br/>GET /processed_files<br/>GET /get_index/filename]
        QC[QuestionController<br/>GET /top_questions/index<br/>GET /top_questions_multi]
        HC[HealthController<br/>GET /health]
        EH[ApiExceptionHandler]
    end

    subgraph Application["application layer"]
        DIS[DocumentIngestion<br/>ApplicationService]
        RQS[RagQuery<br/>ApplicationService]
        PFS[ProcessedFile<br/>ApplicationService]
        QHS[QuestionHistory<br/>ApplicationService]
    end

    subgraph Domain["domain layer (zero framework imports)"]
        subgraph Ports["Port Interfaces"]
            PP[DocumentParser]
            EP[EmbeddingPort]
            RP[RerankPort]
            AP[AnswerGenerationPort]
            RTP[RetrievalPort]
            DRP[DocumentRegistryPort]
            QHP[QuestionHistoryPort]
        end
        subgraph Models["Domain Models"]
            DM[ParsedDocument<br/>ParsedChunk<br/>RetrievedDocument<br/>DocumentFileRecord<br/>IngestionJobRecord]
        end
        DS[DocumentStorage]
        DCW[DocumentChunkWriter]
    end

    subgraph Infrastructure["infrastructure layer"]
        subgraph AWS_Adapters["AWS Adapters"]
            BDA[BdaDocumentParserAdapter<br/>+ BdaClient]
            BEA[BedrockEmbeddingAdapter]
            BRA[BedrockRerankAdapter]
            BAA[BedrockAnswerGenerationAdapter]
            S3A[S3DocumentStorageAdapter]
        end
        subgraph OS_Adapters["OpenSearch Adapters"]
            OSIM[OpenSearchIndexManager]
            OSDW[OpenSearchDocumentWriter]
            OSRA[OpenSearchRetrievalAdapter]
        end
        subgraph PG_Adapters["PostgreSQL Adapters"]
            DRPA[DocumentRegistryPersistence<br/>Adapter]
            QHPA[QuestionHistoryPersistence<br/>Adapter]
        end
        subgraph Config["Configuration"]
            CC[ClientConfig<br/>AWS clients creation]
            AWC[ApplicationWiringConfig<br/>port-adapter binding]
            CORS[CorsConfig]
        end
    end

    subgraph External["External Services"]
        BEDROCK["AWS Bedrock<br/>(us-west-2)<br/>converse API"]
        BDAS["AWS BDA<br/>(us-east-1)"]
        S3["AWS S3<br/>(us-east-1)"]
        OS["AWS OpenSearch<br/>(us-east-1)"]
        PG["PostgreSQL<br/>(localhost)"]
    end

    FE -->|HTTP :8001| UC & RC & PC & QC & HC

    UC --> DIS
    RC --> RQS
    PC --> PFS
    QC --> QHS

    DIS -.->|uses port| DS & PP & EP & DCW & DRP
    RQS -.->|uses port| RTP & RP & AP & QHP
    PFS -.->|uses port| DRP
    QHS -.->|uses port| QHP

    PP -.-|impl| BDA
    EP -.-|impl| BEA
    RP -.-|impl| BRA
    AP -.-|impl| BAA
    DS -.-|impl| S3A
    RTP -.-|impl| OSRA
    DCW -.-|impl| OSDW & OSIM
    DRP -.-|impl| DRPA
    QHP -.-|impl| QHPA

    BDA --> BDAS & S3
    BEA --> BEDROCK
    BRA -.->|stub| BEDROCK
    BAA --> BEDROCK
    S3A --> S3
    OSRA & OSDW & OSIM --> OS
    DRPA & QHPA --> PG

    style Domain fill:#e8f5e9,stroke:#2e7d32
    style API fill:#e3f2fd,stroke:#1565c0
    style Application fill:#fff3e0,stroke:#ef6c00
    style Infrastructure fill:#fce4ec,stroke:#c62828
    style External fill:#f3e5f5,stroke:#7b1fa2
```

### Port 替换成本矩阵

```
                        +-----------------------+
                        |     domain layer      |
                        |  (pure Java, 0 deps)  |
                        +-----------+-----------+
                                    |
              port interfaces       |        can swap independently
         +----------+----------+---+---+----------+----------+
         |          |          |       |          |          |
    +----v---+ +---v----+ +--v---+ +-v------+ +-v------+ +-v---------+
    |Embedding| |Rerank  | |Answer| |Retrieval| |Parser  | |Storage    |
    |Port     | |Port    | |Gen   | |Port     | |        | |           |
    +----+----+ +---+----+ +--+---+ +----+----+ +---+----+ +-----+-----+
         |          |         |          |           |            |
    current impl    |         |          |           |            |
    +----v----+ +---v----+ +--v------+ +-v--------+ +--v-------+ +--v----+
    |Bedrock  | |Bedrock | |Bedrock  | |OpenSearch| |BDA       | |S3     |
    |Titan    | |Cohere  | |Qwen     | |          | |          | |       |
    +---------+ +--------+ +---------+ +----------+ +----------+ +-------+
         |          |         |          |           |            |
    possible swap   |         |          |           |            |
    +----v----+ +---v----+ +--v------+ +-v--------+ +--v-------+ +--v----+
    |OpenAI   | |Cohere  | |OpenAI   | |Milvus   | |Tika      | |MinIO  |
    |Embedding| |Direct  | |GPT-4    | |Qdrant   | |Unstruct. | |Local  |
    |Azure    | |Jina    | |Azure    | |Elastic  | |LlamaParse| |Azure  |
    +---------+ +--------+ +---------+ +----------+ +----------+ +-------+
```

***

## 2. Upload & Ingestion Flow

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant UC as UploadController
    participant DIS as IngestionService
    participant REG as DocumentRegistry<br/>(PostgreSQL)
    participant S3 as S3Storage
    participant BDA as BDA Parser
    participant EMB as Embedding<br/>(Bedrock Titan)
    participant OS as OpenSearch

    FE->>UC: POST /upload_files<br/>[multipart files]
    UC->>DIS: ingest(filename, bytes)

    Note over DIS: For each file:

    DIS->>REG: findByFilename()
    alt Already COMPLETED
        DIS-->>UC: skip (already processed)
    else New or FAILED
        DIS->>REG: saveDocument(PROCESSING)
        DIS->>REG: createJob(PROCESSING)

        DIS->>S3: store(filename, bytes)
        S3-->>DIS: s3Key

        DIS->>BDA: parse(s3Uri)
        Note over BDA: 1. invokeDataAutomationAsync<br/>2. poll getDataAutomationStatus<br/>3. follow standard_output_path<br/>4. fetch result.json from S3
        BDA-->>DIS: ParsedDocument<br/>(List of chunks)

        loop For each chunk
            DIS->>EMB: embed(sentence)
            EMB-->>DIS: float[1536]
        end

        DIS->>OS: ensureIndex(md5[:8])
        Note over OS: GET /_mapping<br/>VALID / INVALID / NOT_FOUND
        alt NOT_FOUND or INVALID
            OS->>OS: DELETE (tolerate 404)<br/>PUT with knn_vector mapping
        end

        DIS->>OS: writeChunks(bulk request)
        OS-->>DIS: indexed

        DIS->>REG: markCompleted()
        DIS-->>UC: success
    end

    UC-->>FE: {"message": "Files processed successfully"}
```

***

## 3. RAG Query Flow

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant RC as RagController
    participant RQS as RagQueryService
    participant OS as OpenSearch
    participant RR as "Rerank<br/>(Bedrock Cohere)"
    participant LLM as "Answer Gen<br/>(Bedrock Qwen)"
    participant QH as "QuestionHistory<br/>(PostgreSQL)"

    FE->>RC: POST /rag_answer<br/>{question, index_names,<br/> search_method, top_k}
    RC->>RQS: query(RagRequest)

    RQS->>OS: retrieve(indexNames,<br/>question, searchMethod, topK)

    alt search_method = "mix"
        Note over OS: Parallel execution
        OS->>OS: KNN vector search<br/>(sentence_vector, k=topK)
        OS->>OS: BM25 text search<br/>(paragraph + sentence)
        OS->>OS: mergeUnique(vec + text)
    else search_method = "vec"
        OS->>OS: KNN vector search only
    else search_method = "text"
        OS->>OS: BM25 text search only
    end

    OS-->>RQS: List<RetrievedDocument>

    alt documents found
        RQS->>RR: rerank(question, documents)
        Note over RR: Currently stub:<br/>sort by score,<br/>filter by threshold
        RR-->>RQS: reranked documents

        alt reranked not empty
            RQS->>LLM: generateAnswer<br/>(question, documents)
            Note over LLM: Bedrock converse API<br/>model: qwen.qwen3-235b<br/>-a22b-2507-v1:0<br/>region: us-west-2
            LLM-->>RQS: answer text
        else all filtered by rerank
            RQS-->>RQS: NO_DOCS_FALLBACK
        end
    else no documents
        RQS-->>RQS: NO_DOCS_FALLBACK
    end

    RQS->>QH: recordQuestion<br/>(indexNames, question,<br/> answer, source_docs)

    RQS-->>RC: RagResult(answer,<br/>source_documents)
    RC-->>FE: {"answer": "...",<br/>"source_documents": [...]}
```

***

## 4. AWS Service Dependency Map

```mermaid
graph LR
    subgraph "us-east-1"
        S3["S3<br/>bda-rag-docs-*"]
        BDA["BDA<br/>Data Automation"]
        OS["OpenSearch<br/>search-java-rag-poc-*"]
    end

    subgraph "us-west-2"
        BR_EMB["Bedrock<br/>amazon.titan-embed-text-v1"]
        BR_RR["Bedrock<br/>cohere.rerank-v3-5:0"]
        BR_ANS["Bedrock<br/>qwen.qwen3-235b-a22b<br/>-2507-v1:0"]
    end

    subgraph "localhost"
        PG["PostgreSQL :5432<br/>rag database"]
        APP["Java Backend :8001"]
        VITE["Frontend :8080"]
    end

    VITE -->|CORS| APP
    APP -->|upload store| S3
    APP -->|parse| BDA
    BDA -->|read/write| S3
    APP -->|embed| BR_EMB
    APP -->|rerank stub| BR_RR
    APP -->|converse| BR_ANS
    APP -->|index & search| OS
    APP -->|metadata| PG

    style APP fill:#ffeb3b,stroke:#f57f17,stroke-width:2px


```

