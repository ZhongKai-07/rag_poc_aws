# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Retrieval-Augmented Generation (RAG) system built with OpenSearch and AWS Bedrock for PDF document processing and intelligent Q&A. The system extracts text from PDFs, stores vector embeddings in OpenSearch, and uses Bedrock LLMs to generate answers based on retrieved context.

**Architecture:**
- **Frontend:** React + TypeScript + Vite (port 8080)
- **Backend:** Python + FastAPI (port 8001)
- **Vector DB:** AWS OpenSearch Service (kNN vector search)
- **LLM/Embeddings:** AWS Bedrock (Titan Embeddings, Qwen/Claude LLM)
- **PDF Processing:** PyPDF2 (Windows compatible, with Docling fallback)

**AWS Regional Architecture:**
- OpenSearch: ap-east-1 (Hong Kong)
- Bedrock: ap-northeast-1 (Tokyo) - better model availability
- Optional Textract: us-east-1 (Virginia)

## Development Commands

### Backend (api/)

```bash
cd api

# Create and activate virtual environment (Windows)
python -m venv venv
venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Run development server
python api.py
# Server starts at http://localhost:8001

# Health check
curl http://localhost:8001/health
```

### Frontend (frontend/)

```bash
cd frontend

# Install dependencies
npm install

# Create environment file
echo "VITE_API_BASE_URL=http://localhost:8001" > .env

# Run development server
npm run dev
# Server starts at http://localhost:8080

# Build for production
npm run build
# Output in dist/ directory
```

## Configuration

### Backend Configuration (api/config.py)

Key settings that must be configured:

```python
# OpenSearch Configuration
OPENSEARCH_HOST = "search-xxx.ap-east-1.es.amazonaws.com"
OPENSEARCH_USERNAME = "admin"
OPENSEARCH_PASSWORD = "your-password"

# Bedrock Configuration
REGION_NAME = "ap-northeast-1"  # Tokyo for Bedrock models
LLM_MODEL_NAME = "anthropic.claude-3-sonnet-20240229-v1:0"
EMBEDDING_MODEL_NAME = "amazon.titan-embed-text-v1"
```

### Environment Variables

```bash
# Windows PowerShell
$env:AWS_ACCESS_KEY_ID="your-key"
$env:AWS_SECRET_ACCESS_KEY="your-secret"
$env:AWS_DEFAULT_REGION="ap-northeast-1"
```

## Code Architecture

### Document Processing Pipeline

**File:** `api/document_processing.py`

The system uses a dual-parser approach for Windows compatibility:
1. **Primary:** PyPDF2 (Windows-safe, pure Python)
2. **Fallback:** Docling (if available, better formatting)

Processing flow:
1. PDF uploaded to `./documents/{timestamp}/`
2. Text extracted page-by-page
3. Content split into chunks using RecursiveCharacterTextSplitter
4. Embeddings generated via Bedrock Titan
5. Vectors stored in OpenSearch with metadata

### Vector Search Flow

**Files:** `api/opensearch_search.py`, `api/RAG_System.py`

1. Query converted to embedding via Titan
2. kNN similarity search in OpenSearch
3. Optional reranking via Bedrock Rerank
4. Top-K chunks retrieved with scores
5. Context formatted for LLM
6. Answer generated via Bedrock LLM

### Key Data Structures

**OpenSearch Index Mapping:**
- `sentence_vector`: kNN vector field (1536 dims for Titan)
- `paragraph`: Raw text content
- `metadata`: Source file, chunk ID, etc.
- `image_base64`: Optional image data (not currently used)

**API Request/Response:**
- Upload: `POST /upload_files` (multipart/form-data)
- Query: `POST /rag_answer` with index_names, query, search parameters
- Files list: `GET /processed_files`

## Common Issues

### Windows-Specific Issues

1. **Docling fails with resource file errors**
   - Already handled: System auto-falls back to PyPDF2
   - Error: `filename does not exists: .../glyphs//standard/additional.dat`

2. **Python path issues in PowerShell**
   - Use explicit path: `venv\Scripts\python.exe api.py`
   - Not just: `python api.py`

### Configuration Issues

1. **AWS Region mismatch**
   - Bedrock models may not be available in all regions
   - Check model availability in target region

2. **OpenSearch connection fails**
   - Verify security group allows your IP on 443
   - Check credentials in config.py
   - For VPC-only OpenSearch, must deploy to EC2 in same VPC

## Testing the System

1. **Upload PDF:**
   - Go to http://localhost:8080
   - Select PDF file (text-based works best)
   - Wait for processing completion

2. **Verify processing:**
   - Check `api/processed_files.txt` for entry
   - Or call: `curl http://localhost:8001/processed_files`

3. **Test Q&A:**
   - Go to Q&A page
   - Select processed document
   - Ask question related to document content

## Deployment Notes

See `docs/deployment.md` for full AWS deployment guide.

Quick production checklist:
- [ ] OpenSearch domain created (ap-east-1 recommended)
- [ ] Bedrock model access enabled (ap-northeast-1)
- [ ] IAM role with bedrock:InvokeModel and es:ESHttp* permissions
- [ ] EC2 instance in same VPC as OpenSearch (if VPC access)
- [ ] Security groups configured (22 for SSH, 8080 for frontend)
- [ ] config.py updated with production endpoints
- [ ] frontend/.env pointing to production backend

## File Locations

Critical files to know:
- `api/config.py` - All service configuration
- `api/document_processing.py` - PDF parsing logic
- `api/RAG_System.py` - Main RAG orchestration
- `frontend/src/pages/Upload.tsx` - File upload UI
- `frontend/src/pages/QA.tsx` - Question answering UI
- `api/processed_files.txt` - Tracks processed PDFs with index mappings
