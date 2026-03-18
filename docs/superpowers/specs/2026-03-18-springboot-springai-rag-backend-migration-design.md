# AWS RAG Backend Migration to Spring Boot + Spring AI

## 1. Background

This repository currently implements a RAG proof of concept with:

- Python FastAPI backend
- React frontend
- AWS OpenSearch for vector and keyword retrieval
- AWS Bedrock for embeddings, rerank, and answer generation
- Docling/PyPDF as document parsing capabilities

The target is to migrate the backend only to a Java technology stack based on Spring Boot and Spring AI while keeping the frontend React application unchanged. All business capabilities must remain equivalent. No functional downgrade is acceptable.

## 2. Goal

Build a Spring Boot backend that fully replaces the current Python FastAPI backend and preserves:

- Existing frontend-visible HTTP capabilities and response shapes
- AWS OpenSearch retrieval capabilities, including vector, text, and mixed search
- AWS Bedrock integration for embedding, rerank, and answer generation
- Document ingestion, parsing, chunking, indexing, and history/statistics capabilities
- Future compatibility with AWS BDA as the long-term document parsing solution

## 3. Non-Goals

- Rewriting or redesigning the React frontend
- Replacing AWS OpenSearch or AWS Bedrock
- Launching with reduced retrieval quality or reduced parsing fidelity
- Keeping Python Docling as a long-term production dependency

## 4. Constraints

- Frontend remains unchanged except for environment-level backend address switching.
- Backend migration should be a one-time move to the target architecture, not a transitional dual-language long-term solution.
- OpenSearch and Bedrock remain the core managed services.
- Docling may be treated as a reference capability only, not as the target runtime architecture.
- AWS BDA is the target document parsing direction and should be accommodated as a first-class parser implementation.

## 5. Recommended Architecture

### 5.1 Overall Shape

Use a single Spring Boot service with internal module separation:

- `api`: REST controllers and DTO compatibility layer
- `ingestion`: upload orchestration and document processing entrypoint
- `parser`: parser SPI and parser implementations
- `indexing`: chunk normalization, embedding generation, index management, bulk ingestion
- `retrieval`: vector search, BM25 search, mixed search, deduplication, thresholding, rerank
- `generation`: prompt assembly and Bedrock LLM answer generation
- `history`: question history and top question statistics
- `common`: shared models, exceptions, configuration, observability

### 5.2 Key Design Choice

The backend should use Spring AI for model-facing orchestration where it fits well, but retrieval and indexing should remain explicitly controlled with the OpenSearch Java client and AWS SDK integrations. This keeps high control over search behavior, rerank wiring, and future parser evolution.

## 6. Capability Equivalence Matrix

### 6.1 Existing Python Capability to Java Mapping

1. File upload and local directory storage
   - Python: FastAPI multipart upload
   - Java target: Spring MVC multipart upload with configurable storage abstraction

2. Processed file registry
   - Python: `processed_files.txt`
   - Java target: relational persistence table, with compatibility reader if legacy data must be imported

3. PDF/document parsing
   - Python: Docling primary, PyPDF fallback
   - Java target: `DocumentParser` abstraction, first-class `BdaDocumentParser`

4. Chunking and metadata extraction
   - Python: markdown header split and fallback recursive chunking
   - Java target: normalized chunk pipeline preserving page, section path, source file, chunk id, sentence, paragraph, and multimodal asset references

5. Embedding
   - Python: Bedrock Titan embedding
   - Java target: Bedrock embedding client integrated through Spring AI and AWS SDK

6. Retrieval
   - Python: OpenSearch vector, text, mix
   - Java target: OpenSearch Java client with equivalent query strategies

7. Rerank
   - Python: Bedrock rerank
   - Java target: dedicated rerank service using AWS SDK

8. Answer generation
   - Python: liteLLM + Bedrock
   - Java target: Spring AI `ChatClient` backed by Bedrock

9. Question history
   - Python: per-index JSON files
   - Java target: relational persistence and aggregation query endpoints

10. Frontend API compatibility
    - Python: FastAPI DTOs
    - Java target: response contract parity for all current endpoints

## 7. Parser Strategy

### 7.1 Decision

Implement a parser SPI and make AWS BDA the target parser implementation from day one.

### 7.2 Why Not Keep a Python Docling Service

Using a Python Docling sidecar would lower short-term migration cost, but it would violate the intent of a one-time move to the target architecture and create:

- an extra operational runtime
- cross-language failure modes
- duplicated deployment and monitoring concerns
- a second migration later when BDA becomes primary

### 7.3 Parser Interface

Introduce a parser abstraction like:

- `DocumentParser`
- `ParserRequest`
- `ParsedDocument`
- `ParsedPage`
- `ParsedChunk`
- `ParsedAsset`

This interface must preserve enough semantic richness to support:

- page-aware chunking
- section/header lineage
- extracted text blocks
- table/text/image region information
- multimodal payload references
- parser provenance and parser version

### 7.4 Parser Normalization Contract

All parser implementations must output a common normalized structure so that retrieval, rerank, and generation are parser-agnostic.

This normalization layer is the main design investment that protects the system from future parser replacement costs.

## 8. Data Model Design

### 8.1 Persistence

Introduce a database for application state. Recommended minimum tables:

- `document_file`
  - `id`
  - `file_name`
  - `storage_path`
  - `checksum`
  - `index_name`
  - `parser_type`
  - `parser_version`
  - `status`
  - `created_at`
  - `updated_at`

- `question_history`
  - `id`
  - `index_name`
  - `question`
  - `asked_at`

- `ingestion_job`
  - `id`
  - `file_id`
  - `status`
  - `error_message`
  - `started_at`
  - `finished_at`

### 8.2 OpenSearch Document Shape

Retain a document structure compatible with current retrieval behavior, with room for BDA-rich metadata:

- `sentence_vector`
- `paragraph`
- `sentence`
- `metadata.source`
- `metadata.chunk_id`
- `metadata.page_number`
- `metadata.section_path`
- `metadata.parser_type`
- `metadata.asset_refs`
- `image_base64` or durable image/object reference field

### 8.3 Index Naming

Keep deterministic index naming behavior compatible with current frontend expectations. If the existing `md5(filename)[:8]` rule is externally relied upon, preserve it or provide a transparent compatibility layer.

## 9. API Compatibility Design

The following endpoints remain available in Java:

- `POST /upload_files`
- `POST /rag_answer`
- `GET /processed_files`
- `GET /get_index/{filename}`
- `GET /top_questions/{index_name}`
- `GET /top_questions_multi`
- `GET /health`

### 9.1 Compatibility Principle

The Java backend should preserve:

- endpoint names
- request parameter semantics
- response field names
- frontend-consumed data shapes

Behavioral improvements are allowed, but contract regressions are not.

## 10. Retrieval and Generation Flow

### 10.1 Retrieval Pipeline

1. Receive question and one or more target index names
2. Generate embedding with Bedrock
3. Execute vector search, text search, or both depending on `search_method`
4. Apply score thresholds
5. Deduplicate by content identity or stable content fingerprint
6. Limit top candidate set
7. Rerank with Bedrock rerank service
8. Filter by rerank threshold
9. Build answer context payload

### 10.2 Generation Pipeline

1. Build system prompt and user prompt
2. Convert reranked documents into LLM-ready context
3. Preserve multimodal extension points for image-aware prompts
4. Generate answer with Bedrock through Spring AI
5. Return answer plus recall/rerank/source document lists

### 10.3 Multimodal Handling

Even if the first production version remains text-dominant for answer generation, the internal data model must preserve image and asset references so that enabling multimodal prompt input later does not require retrieval or parser redesign.

## 11. Error Handling and Reliability

### 11.1 Required Reliability Behaviors

- Clear ingestion status transitions
- Retry policy for Bedrock invocation failures
- Timeout and failure reporting for BDA parsing tasks
- Index creation idempotency
- Bulk ingestion error visibility
- Partial-failure observability for multi-index query requests

### 11.2 Operational Observability

Add:

- structured logs
- request correlation id
- ingestion job logs
- metrics for parse time, embedding time, retrieval time, rerank time, answer time

## 12. Security and Configuration

Configuration should move to Spring configuration properties and environment variables. Sensitive values must not remain in source-controlled static config files.

At minimum externalize:

- AWS credentials and region settings
- Bedrock model identifiers
- OpenSearch endpoint and credentials
- parser timeouts and concurrency settings
- storage location
- chunking thresholds

## 13. Migration Execution Strategy

### 13.1 Phase 1: Baseline and Contract Freeze

- Inventory all current backend-visible capabilities
- Freeze HTTP contracts
- Capture OpenSearch mapping and sample indexed records
- Build a regression dataset of representative PDFs and user questions

### 13.2 Phase 2: Java Skeleton and Compatibility Layer

- Create Spring Boot project structure
- Implement controllers, DTOs, error handling, and configuration
- Stand up health and compatibility endpoints

### 13.3 Phase 3: Retrieval Core Migration

- Implement OpenSearch index manager
- Implement embeddings, vector search, text search, mix search, deduplication, rerank
- Implement answer generation flow
- Validate `/rag_answer` parity against the Python baseline

### 13.4 Phase 4: Ingestion and Parser Migration

- Implement upload flow
- Implement BDA parser integration
- Normalize parsed output into chunk model
- Index parsed chunks into OpenSearch
- Validate `/upload_files` and processed registry parity

### 13.5 Phase 5: Cutover and Verification

- Run old/new comparison on the same document and question set
- Compare chunk counts, top-k retrieval overlap, rerank behavior, and answer quality
- Point frontend to the Java backend
- Retire the Python backend after acceptance

## 14. Testing Strategy

### 14.1 Test Layers

- unit tests for parser normalization, chunking, index naming, prompt assembly
- integration tests for OpenSearch and Bedrock client wrappers
- API contract tests for existing frontend endpoints
- regression tests using fixed document and question fixtures
- end-to-end tests from upload through answer generation

### 14.2 Equivalence Acceptance Gates

The migration is acceptable only if:

- upload and ingestion success rate is not lower than the Python baseline
- retrieval modes remain available and produce comparable relevance
- rerank does not reduce answer quality
- frontend works without endpoint-level rewrites
- critical question set answer quality is not degraded

## 15. Main Risks

### 15.1 Parser Output Drift

BDA output may not align exactly with Docling chunk boundaries. This can affect retrieval quality and answer grounding. The mitigation is a strong normalization and regression-comparison layer.

### 15.2 Framework Abstraction Gaps

Spring AI may not fully cover all Bedrock-specific capabilities. The mitigation is to allow selective direct AWS SDK usage behind clean service interfaces.

### 15.3 Hidden Frontend Contract Coupling

The React frontend may rely on undocumented response details. The mitigation is to test against the real frontend and preserve DTO naming and field semantics.

## 16. Final Recommendation

Proceed with a single Spring Boot backend using:

- Spring Boot for service runtime
- Spring AI for Bedrock-facing model orchestration where appropriate
- OpenSearch Java client for retrieval and indexing
- AWS SDK for BDA and rerank integration
- Parser SPI with BDA as the target parser implementation
- relational persistence for backend state replacing text and JSON files

This gives the project a one-time migration to the target architecture, keeps AWS OpenSearch and Bedrock intact, avoids long-term Python sidecars, and preserves the ability to evolve parser implementations without reworking the RAG core.
