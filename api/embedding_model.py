from langchain_aws import BedrockEmbeddings
import boto3
import json
import logging

import config
region_name = config.REGION_NAME

logger = logging.getLogger(__name__)

def init_embeddings_bedrock(model_id:str="amazon.titan-embed-text-v1"):
    try:
        logger.info(f"Initializing Bedrock embeddings with model: {model_id}, region: {region_name}")
        embeddings = BedrockEmbeddings(
                model_id=model_id,
                region_name=region_name
            )
        logger.info("Bedrock embeddings initialized successfully")
        return embeddings
    except Exception as e:
        logger.error(f"Failed to initialize Bedrock embeddings: {str(e)}", exc_info=True)
        raise

def get_reranker_scores_bedrock(text_query, documents, modelId: str = None):
    rerank_model = modelId or config.RERANK_MODEL_NAME
    rerank_region = getattr(config, 'RERANK_REGION_NAME', region_name)
    model_package_arn = f"arn:aws:bedrock:{rerank_region}::foundation-model/{rerank_model}"
    num_results = len(documents)
    text_sources = []
    for text in documents:
        text_sources.append({
            "type": "INLINE",
            "inlineDocumentSource": {
                "type": "TEXT",
                "textDocument": {
                    "text": text,
                }
            }
        })

    bedrock_agent_runtime = boto3.client('bedrock-agent-runtime', region_name=rerank_region)
    response = bedrock_agent_runtime.rerank(
        queries=[
            {
                "type": "TEXT",
                "textQuery": {
                    "text": text_query
                }
            }
        ],
        sources=text_sources,
        rerankingConfiguration={
            "type": "BEDROCK_RERANKING_MODEL",
            "bedrockRerankingConfiguration": {
                "numberOfResults": num_results,
                "modelConfiguration": {
                    "modelArn": model_package_arn,
                }
            }
        }
    )
    return response['results']
