"""RAGAS Evaluation Service — FastAPI sidecar for offline RAG evaluation."""

import os
import logging
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI(title="RAGAS Evaluator", version="0.1.0")
logger = logging.getLogger("ragas-evaluator")


class TraceItem(BaseModel):
    case_id: str
    question: str
    contexts: List[str]
    answer: str
    ground_truth: Optional[str] = None


class EvalRequest(BaseModel):
    traces: List[TraceItem]


class DocRequest(BaseModel):
    documents: List[str]
    size: int = 10


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/evaluate")
async def run_evaluation(payload: EvalRequest):
    """Evaluate traces using RAGAS metrics.

    Expects a list of traces with question, contexts, answer, and optional ground_truth.
    Returns per-metric scores averaged across all traces.
    """
    try:
        from datasets import Dataset
        from ragas import evaluate
        from ragas.metrics import (
            faithfulness,
            answer_relevancy,
            context_precision,
            context_recall,
        )

        data = {
            "question": [t.question for t in payload.traces],
            "contexts": [t.contexts for t in payload.traces],
            "answer": [t.answer for t in payload.traces],
        }
        if any(t.ground_truth for t in payload.traces):
            data["ground_truth"] = [t.ground_truth or "" for t in payload.traces]

        dataset = Dataset.from_dict(data)

        metrics = [faithfulness, answer_relevancy, context_precision]
        if "ground_truth" in data:
            metrics.append(context_recall)

        result = evaluate(dataset, metrics=metrics)
        return result.to_pandas().mean().to_dict()

    except ImportError as e:
        logger.warning("RAGAS not fully installed: %s", e)
        raise HTTPException(status_code=503, detail=f"RAGAS dependency missing: {e}")
    except Exception as e:
        logger.error("Evaluation failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/generate_testset")
async def generate_testset(payload: DocRequest):
    """Generate synthetic test dataset from documents using RAGAS TestsetGenerator."""
    try:
        from ragas.testset.generator import TestsetGenerator
        from langchain_core.documents import Document

        docs = [Document(page_content=d) for d in payload.documents]
        generator = TestsetGenerator.from_default()
        testset = generator.generate_with_langchain_docs(docs, test_size=payload.size)
        return testset.to_pandas().to_dict(orient="records")

    except ImportError as e:
        logger.warning("RAGAS testset generation not available: %s", e)
        raise HTTPException(status_code=503, detail=f"Dependency missing: {e}")
    except Exception as e:
        logger.error("Test generation failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("PORT", "8002"))
    uvicorn.run(app, host="0.0.0.0", port=port)
