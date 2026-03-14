#!/usr/bin/env python3
"""
Simplified OpenSearch operations for RAG System
"""

import json
import boto3
from typing import List, Dict, Any, Optional
from opensearchpy import OpenSearch
import config
from embedding_model import get_reranker_scores_bedrock


class Document:
    """Simple document class"""
    def __init__(self, page_content: str, metadata: Dict = None):
        self.page_content = page_content
        self.metadata = metadata or {}


def get_opensearch_client() -> OpenSearch:
    """Get OpenSearch client"""
    # Try to get from config first, fallback to secrets manager
    try:
        host = getattr(config, 'OPENSEARCH_HOST', None)
        username = getattr(config, 'OPENSEARCH_USERNAME', None)
        password = getattr(config, 'OPENSEARCH_PASSWORD', None)
        
        if host and username and password:
            # Use config values
            print('opensearch use config')
            if host.startswith('http'):
                host = host.replace('https://', '').replace('http://', '').rstrip('/')
        else:
            # Fallback to secrets manager
            sm_client = boto3.client('secretsmanager')
            
            # Get host
            master_user = sm_client.get_secret_value(SecretId='opensearch-host-url')['SecretString']
            data = json.loads(master_user)
            es_host_name = data.get('host')
            
            if 'http' in es_host_name:
                host = es_host_name.replace('https://', '').replace('http://', '').rstrip('/')
            else:
                host = es_host_name
            
            # Get credentials
            master_user = sm_client.get_secret_value(SecretId='opensearch-master-user')['SecretString']
            data = json.loads(master_user)
            username = data.get('username')
            password = data.get('password')
        
        # Create client
        client = OpenSearch(
            hosts=[{'host': host, 'port': 443}],
            http_auth=(username, password),
            use_ssl=True,
            verify_certs=True
        )
        
        return client
        
    except Exception as e:
        print(f"Error creating OpenSearch client: {e}")
        raise


def vector_search(client: OpenSearch,
                 index_name: str,
                 query_vector: List[float],
                 k: int = 4,
                 vector_field: str = "sentence_vector") -> List[Dict]:
    """Perform vector search"""
    search_query = {
        "size": k,
        "query": {
            "knn": {
                vector_field: {
                    "vector": query_vector,
                    "k": k
                }
            }
        }
    }
    
    response = client.search(index=index_name, body=search_query)
    return response["hits"]["hits"]


def text_search(client: OpenSearch,
               index_name: str,
               query: str,
               k: int = 4,
               text_field: str = "sentence") -> List[Dict]:
    
    # Try simple term search first
    simple_query = {
        "size": k,
        "query": {
            "match": {
                "sentence": query
            }
        }
    }
    
    response = client.search(index=index_name, body=simple_query)
    hits = response["hits"]["hits"]
    print(f"match returned {len(hits)} hits")
    
    return hits


def similarity_search(embeddings,
                     query: str,
                     index_name: str,
                     vec_docs_num: int = 4,
                     txt_docs_num: int = 0,
                     search_method: str = "vector",
                     vector_field: str = "sentence_vector",
                     text_field: str = "paragraph",
                     image_field: str = "image_base64",
                     vec_score_threshold: float = 0.0,
                     text_score_threshold: float = 0.0,
                     rerank_score_threshold: float = 0.0) -> Dict[str, List]:
    """
    Perform similarity search with score filtering

    Returns:
        Dict with 'recall_documents' (before reranking) and 'rerank_documents' (after reranking)
    """
    client = get_opensearch_client()
    all_results = []
    
    if search_method == "vector":
        query_vector = embeddings.embed_query(query)
        hits = vector_search(client, index_name, query_vector, vec_docs_num, vector_field)
        results = _format_results(hits, text_field, image_field, "bedrock")
        # print('vector search result:',results)
        all_results.extend([r for r in results if r[1] >= vec_score_threshold])
        
    elif search_method == "text":
        print("txt_docs_num:",txt_docs_num)
        hits = text_search(client, index_name, query, txt_docs_num, "sentence")
        results = _format_results(hits, text_field, image_field, "text")
        print('text search result:',results)
        all_results.extend([r for r in results if r[1] >= text_score_threshold])
        
    else:  # mix
        # Vector search
        query_vector = embeddings.embed_query(query)
        vec_hits = vector_search(client, index_name, query_vector, vec_docs_num, vector_field)
        vec_results = _format_results(vec_hits, text_field, image_field, "bedrock")
        print('Mix vector search result:',vec_results)
        all_results.extend([r for r in vec_results if r[1] >= vec_score_threshold])
        
        # Text search
        if txt_docs_num > 0:
            text_hits = text_search(client, index_name, query, txt_docs_num, "sentence")
            text_results = _format_results(text_hits, text_field, image_field, "text")
            print('Mix text search result:',text_results)
            all_results.extend([r for r in text_results if r[1] >= text_score_threshold])
    
    # Remove duplicates and sort by score
    seen_content = set()
    unique_results = []
    for result in sorted(all_results, key=lambda x: x[1], reverse=True):
        content = result[0].page_content
        if content not in seen_content:
            seen_content.add(content)
            unique_results.append(result)

    # Limit results before reranking
    unique_results = unique_results[:vec_docs_num + txt_docs_num]
    print('unique_results:',unique_results)

    # Save recall results (before reranking) - format: [Document, similarity_score]
    recall_results = [[r[0], r[1]] for r in unique_results]

    # Apply reranking if we have results
    rerank_results = []
    if unique_results:
        try:
            # Extract sentences from metadata for reranking
            documents = [result[0].metadata.get('sentence', result[0].page_content) for result in unique_results]
            rerank_scores = get_reranker_scores_bedrock(query, documents)

            # Update results with rerank scores and sort
            reranked_results = []
            for i, rerank_result in enumerate(rerank_scores):
                if i < len(unique_results):
                    original_result = unique_results[rerank_result['index']]
                    # Keep similarity score, add rerank score
                    similarity_score = original_result[1]
                    rerank_score = rerank_result['relevanceScore']
                    # Format: [Document, similarity_score, rerank_score]
                    reranked_results.append([original_result[0], similarity_score, rerank_score])

            # Sort by rerank score and filter by threshold
            rerank_results = sorted(reranked_results, key=lambda x: x[2], reverse=True)
            rerank_results = [r for r in rerank_results if r[2] >= rerank_score_threshold]
            print('rerank results after filtering:',rerank_results)
        except Exception as e:
            print(f"Reranking failed, using original results: {e}")
            # Fallback: use recall results as rerank results (no rerank score)
            rerank_results = unique_results

    return {
        'recall_documents': recall_results,
        'rerank_documents': rerank_results
    }


def _format_results(hits: List[Dict], text_field: str, image_field: str, 
                   embedding_type: str) -> List[List]:
    """Format search results"""
    results = []
    for hit in hits:
        source = hit["_source"]
        
        content = source.get(text_field, "")
        if isinstance(content, list):
            content = content[0] if content else ""
        
        metadata = source.get("metadata", {})
        if "sentence" in source:
            metadata["sentence"] = source["sentence"]
        
        doc = Document(page_content=content, metadata=metadata)
        score = hit["_score"] * 100 if embedding_type == "bedrock" else hit["_score"]
        
        image = source.get(image_field, "")
        if isinstance(image, list):
            image = image[0] if image else ""
        results.append([doc, score, image])
    
    return results