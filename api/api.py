#!/usr/bin/env python3
"""
FastAPI server for RAG System
"""

from fastapi import FastAPI, HTTPException, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from pydantic import BaseModel
from typing import List, Dict, Any, Optional
from RAG_System import RAGSystem
from document_processing import DocumentProcessor
import logging
import os
import json
from collections import Counter
from pathlib import Path
import config

QUESTION_HISTORY_DIR = Path("question_history")

# Setup logging
logging.basicConfig(level=logging.DEBUG, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# Initialize FastAPI app
app = FastAPI(title="RAG System API", version="1.0.0")

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Serve static files
app.mount("/static", StaticFiles(directory="."), name="static")

# Initialize RAG system
rag_system = RAGSystem(
    embedding_model_id="amazon.titan-embed-text-v1"
)

# Request model
class RAGRequest(BaseModel):
    session_id: str
    index_names: List[str]
    query: str
    module: str = "RAG"
    vec_docs_num: int = 3
    txt_docs_num: int = 3
    vec_score_threshold: float = 0.0
    text_score_threshold: float = 0.0
    rerank_score_threshold: float = 0.5
    search_method: str = "vector"

class UploadRequest(BaseModel):
    file_path: str

# Response model
class SourceDocument(BaseModel):
    page_content: str
    score: float
    rerank_score: Optional[float] = None

class RecallDocument(BaseModel):
    page_content: str
    score: float

class RAGResponse(BaseModel):
    answer: str
    source_documents: List[SourceDocument]
    recall_documents: List[RecallDocument]
    rerank_documents: List[SourceDocument]

def _get_history_path(index_name: str) -> Path:
    QUESTION_HISTORY_DIR.mkdir(exist_ok=True)
    return QUESTION_HISTORY_DIR / f"{index_name}.json"

def _load_question_history(index_name: str) -> List[str]:
    path = _get_history_path(index_name)
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    return []

def _save_question(index_name: str, query: str):
    questions = _load_question_history(index_name)
    questions.append(query.strip())
    _get_history_path(index_name).write_text(
        json.dumps(questions, ensure_ascii=False), encoding="utf-8"
    )

@app.get("/top_questions/{index_name}")
async def get_top_questions(index_name: str, top_n: int = 5):
    """Get top N most frequently asked questions for an index"""
    try:
        questions = _load_question_history(index_name)
        counter = Counter(questions)
        top = counter.most_common(top_n)
        return {
            "status": "success",
            "questions": [{"question": q, "count": c} for q, c in top]
        }
    except Exception as e:
        logger.error(f"Error getting top questions: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/top_questions_multi")
async def get_top_questions_multi(index_names: str, top_n: int = 5):
    """Get top N most frequently asked questions aggregated across multiple indices"""
    try:
        combined: Counter = Counter()
        for idx_name in index_names.split(","):
            idx_name = idx_name.strip()
            if idx_name:
                questions = _load_question_history(idx_name)
                combined.update(questions)
        top = combined.most_common(top_n)
        return {
            "status": "success",
            "questions": [{"question": q, "count": c} for q, c in top]
        }
    except Exception as e:
        logger.error(f"Error getting top questions multi: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/rag_answer", response_model=RAGResponse)
async def get_rag_answer(request: RAGRequest):
    """
    Get answer from RAG system
    """
    try:
        # Call RAG system with comma-separated index names
        index_name_str = ",".join(request.index_names)
        print('index:', index_name_str)
        logger.info(f"Received RAG request for indices: {index_name_str}")
        result = rag_system.get_answer_from_multimodel(
            index_name=index_name_str,
            query=request.query,
            module=request.module,
            vec_docs_num=request.vec_docs_num,
            txt_docs_num=request.vec_docs_num,
            vec_score_threshold=request.vec_score_threshold,
            text_score_threshold=request.text_score_threshold,
            rerank_score_threshold=request.rerank_score_threshold,
            search_method=request.search_method
        )

        # Record question history for each index
        for idx_name in request.index_names:
            _save_question(idx_name, request.query)

        # Format recall documents (before reranking)
        response_recall_documents = []
        for doc in result['recall_documents']:
            response_recall_documents.append(RecallDocument(
                page_content=doc[0].page_content,
                score=doc[1]
            ))

        # Format rerank documents (after reranking)
        response_rerank_documents = []
        response_source_documents = []
        for doc in result['rerank_documents']:
            if len(doc) >= 3 and isinstance(doc[2], (int, float)):
                source_doc = SourceDocument(
                    page_content=doc[0].page_content,
                    score=doc[1],
                    rerank_score=doc[2]
                )
                response_rerank_documents.append(source_doc)
                response_source_documents.append(source_doc)

        # Return response
        return RAGResponse(
            answer=result['answer'],
            source_documents=response_source_documents,
            recall_documents=response_recall_documents,
            rerank_documents=response_rerank_documents
        )
        
    except Exception as e:
        logger.error(f"Error processing RAG request: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/processed_files")
async def get_processed_files():
    """Get list of processed files"""
    try:
        import os
        import json
        processed_files = []
        
        if os.path.exists("processed_files.txt"):
            with open("processed_files.txt", "r", encoding="utf-8") as f:
                lines = f.read().strip().split("\n")
                
            for line in lines:
                if line.strip():
                    try:
                        file_record = json.loads(line)
                        processed_files.append({
                            "filename": file_record["file_name"],
                            "index_name": file_record["index_name"]
                        })
                    except json.JSONDecodeError:
                        # Fallback for old format
                        parts = line.split(",")
                        if len(parts) >= 2:
                            processed_files.append({
                                "filename": parts[0],
                                "index_name": parts[1]
                            })
        
        return {"status": "success", "files": processed_files}
        
    except Exception as e:
        logger.error(f"Error reading processed files: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/upload_files")
async def upload_files(file: UploadFile = File(...), directory_path: str = Form(...)):
    """Process uploaded files"""
    try:
        # Convert relative path to absolute path
        if directory_path.startswith('./'):
            directory_path = os.path.join(os.getcwd(), directory_path[2:])

        # Create directory if it doesn't exist
        os.makedirs(directory_path, exist_ok=True)
        logger.info(f"Directory created: {directory_path}")

        print('file:',file.filename)
        logger.info(f"file.filename: {file.filename}")

        # Save uploaded file
        file_path = os.path.join(directory_path, file.filename)
        print('file_path:',file_path)
        logger.info(f"file_path: {file_path}")

        with open(file_path, "wb") as buffer:
            content = await file.read()
            buffer.write(content)

        logger.info(f"File saved successfully: {file_path}")

        # Initialize document processor with config parameters
        try:
            from config import EMBEDDING_MODEL_NAME, TEXT_MAX_LENGTH, LLM_MAX_SIZE, IMAGE_RESOLUTION_SCALE
            logger.info(f"Initializing DocumentProcessor with embedding model: {EMBEDDING_MODEL_NAME}")
            processor = DocumentProcessor(
                embedding_model_name=EMBEDDING_MODEL_NAME,
                text_max_length=TEXT_MAX_LENGTH,
                llm_max_size=LLM_MAX_SIZE,
                image_resolution_scale=IMAGE_RESOLUTION_SCALE
            )
            logger.info("DocumentProcessor initialized successfully")
        except Exception as init_error:
            logger.error(f"Failed to initialize DocumentProcessor: {str(init_error)}")
            raise HTTPException(status_code=500, detail=f"DocumentProcessor initialization failed: {str(init_error)}")

        # Process the directory containing the uploaded file
        logger.info(f"Starting to process directory: {directory_path}")
        processor.process_directory(directory_path)
        logger.info(f"Directory processing completed: {directory_path}")

        return {"status": "success", "message": "Files processed successfully"}

    except Exception as e:
        logger.error(f"Error processing uploaded files: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/get_index/{filename}")
async def get_index_by_filename(filename: str):
    """Get index name by filename"""
    try:
        import os
        import json
        
        if os.path.exists("processed_files.txt"):
            with open("processed_files.txt", "r", encoding="utf-8") as f:
                lines = f.read().strip().split("\n")
                
            for line in lines:
                if line.strip():
                    try:
                        file_record = json.loads(line)
                        if file_record["file_name"] == filename:
                            return {"status": "success", "index_name": file_record["index_name"]}
                    except json.JSONDecodeError:
                        # Fallback for old format
                        parts = line.split(",")
                        if len(parts) >= 2 and parts[0] == filename:
                            return {"status": "success", "index_name": parts[1]}
        
        return {"status": "error", "message": "File not found"}
        
    except Exception as e:
        logger.error(f"Error finding index for file {filename}: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/")
async def read_index():
    """Serve the main page"""
    return FileResponse('index.html')

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {"status": "healthy"}

def verify_configuration():
    """Verify all required configurations are set correctly"""
    logger.info("=" * 60)
    logger.info("Starting RAG API Server - Configuration Check")
    logger.info("=" * 60)

    # Check AWS credentials
    aws_key = os.environ.get('AWS_ACCESS_KEY_ID')
    aws_secret = os.environ.get('AWS_SECRET_ACCESS_KEY')
    aws_region = os.environ.get('AWS_DEFAULT_REGION')

    if not aws_key or not aws_secret:
        logger.warning("AWS credentials not found in environment variables!")
        logger.warning("Make sure to set: AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY")
    else:
        logger.info(f"✓ AWS credentials found")
        logger.info(f"✓ AWS Region: {aws_region or config.REGION_NAME}")

    # Check OpenSearch configuration
    if config.OPENSEARCH_HOST and config.OPENSEARCH_USERNAME and config.OPENSEARCH_PASSWORD:
        logger.info(f"✓ OpenSearch host: {config.OPENSEARCH_HOST}")
    else:
        logger.warning("✗ OpenSearch configuration incomplete!")

    # Check model configuration
    logger.info(f"✓ Embedding model: {config.EMBEDDING_MODEL_NAME}")
    logger.info(f"✓ LLM model: {config.LLM_MODEL_NAME}")

    # Create documents directory if not exists
    documents_dir = os.path.join(os.getcwd(), "documents")
    os.makedirs(documents_dir, exist_ok=True)
    logger.info(f"✓ Documents directory: {documents_dir}")

    logger.info("=" * 60)

# Run configuration check on startup
verify_configuration()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)