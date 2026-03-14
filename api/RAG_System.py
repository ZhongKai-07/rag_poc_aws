#!/usr/bin/env python3
"""
RAG System without LangChain dependencies
Implements get_answer_from_multimodel logic using direct OpenSearch integration
"""

import json
import numpy as np
import base64
import io
from PIL import Image
from typing import List, Dict, Any, Optional, Tuple
from embedding_model import init_embeddings_bedrock
from opensearch_search import similarity_search, get_opensearch_client,Document
from llm_processor import LLMProcessor

class RAGSystem:
    """RAG System with direct OpenSearch integration"""
    
    def __init__(self,
                 embedding_model_id: str = "amazon.titan-embed-text-v1"):
        """
        Initialize RAG System

        Args:
            embedding_model_id: Bedrock embedding model ID
        """
        self.embeddings = init_embeddings_bedrock(embedding_model_id)
        self.llm_processor = LLMProcessor()
        self.opensearch_client = get_opensearch_client()
    

    def conver_image(self,base64_string):
        resize_max_size = 800
        image_bytes = base64.b64decode(base64_string)
        image_stream = io.BytesIO(image_bytes)
        image = Image.open(image_stream)
        if image.size[0] > resize_max_size:
            image = image.resize((resize_max_size,int(image.size[1]*resize_max_size / image.size[0])))
            print('resize image size:',image.size)
            
            buffered = io.BytesIO()
            image.save(buffered, format="JPEG")
            base64_string = base64.b64encode(buffered.getvalue()).decode("utf-8")
        return base64_string
    
    def format_context(self, docs: List[List]) -> List:
        """Format retrieved documents as context"""
        related_docs = []
        if len(docs) > 0:    
            for doc in docs:
                txt_list = doc[0].page_content.split('![Image]')
                for text in txt_list:
                    if text.find('data:image') > 0:
                        image_str_list = text.split(')',1)
                        image_str = image_str_list[0].split(',',1)[-1]
                        if len(image_str) > 0:
                            related_doc = {}
                            related_doc['image'] = self.conver_image(image_str)
                            related_docs.append(related_doc)

                        text_str = image_str_list[1]
                        if len(text_str) > 0:
                            related_doc = {}
                            related_doc['text'] = text_str
                            related_docs.append(related_doc)
                    else:
                        if len(text) > 0:
                            related_doc = {}
                            related_doc['text'] = text
                            related_docs.append(related_doc)
        return related_docs
    
    
    def get_answer_from_multimodel(self,
                                  index_name: str,
                                  query: str,
                                  module: str = 'RAG',
                                  system_prompt: str = '',
                                  vec_docs_num: int = 3,
                                  txt_docs_num: int = 0,
                                  vec_score_threshold: float = 0.5,
                                  text_score_threshold: float = 0.5,
                                  rerank_score_threshold: float = 0.0,
                                  search_method: str = "vector",
                                  response_if_no_docs_found: str = "抱歉，我无法找到相关信息来回答您的问题。",
                                  vector_field: str = "sentence_vector",
                                  text_field: str = "paragraph",
                                  image_field: str = "image_base64",
                                  **kwargs) -> Dict[str, Any]:
        """
        Main RAG function - equivalent to get_answer_from_multimodel from smart_search_qa.py
        
        Args:
            index_name: OpenSearch index name
            query: User query
            module: Module type ('RAG', 'Chat')
            system_prompt: System prompt
            vec_docs_num: Number of vector documents to retrieve
            txt_docs_num: Number of text documents to retrieve
            vec_score_threshold: Vector search score threshold
            text_score_threshold: Text search score threshold
            search_method: Search method
            response_if_no_docs_found: Default response when no docs found
            vector_field: Vector field name
            text_field: Text field name
            image_field: Image field name
            
        Returns:
            Dictionary containing answer and source documents
        """
        result = {
            'answer': '',
            'source_documents': [],
            'recall_documents': [],
            'rerank_documents': []
        }
        
        # Handle inappropriate content check
        if '你的問題涉及不當用詞' in query:
            result['answer'] = query
            return result
        

        
        # RAG mode with knowledge base
        if module == 'RAG':
            # Search documents
            docs = similarity_search(
                embeddings=self.embeddings,
                query=query,
                index_name=index_name,
                vec_docs_num=vec_docs_num,
                txt_docs_num=txt_docs_num,
                search_method=search_method,
                vector_field=vector_field,
                text_field=text_field,
                image_field=image_field,
                vec_score_threshold=vec_score_threshold,
                text_score_threshold=text_score_threshold,
                rerank_score_threshold=rerank_score_threshold
            )
            
            result['recall_documents'] = docs['recall_documents']
            result['rerank_documents'] = docs['rerank_documents']
            result['source_documents'] = docs['rerank_documents']

            if docs['rerank_documents']:
                # Format context
                related_docs = self.format_context(docs['rerank_documents'])
                
                # Generate answer with context
                answer = self.llm_processor.answer(query, related_docs)
                result['answer'] = answer
            else:
                result['answer'] = response_if_no_docs_found
        
        # Chat mode or no knowledge base
        elif module == 'Chat':
            answer = self.llm_processor.answer(query)
            result['answer'] = answer
        
        return result
    


# Example usage
if __name__ == "__main__":
    # Initialize RAG system
    rag = RAGSystem(
        embedding_model_id="amazon.titan-embed-text-v1"
    )
    
    # Example query
    query = "TCL科技2024年归母净利低于预期的主要原因是什么？"
    
    # Get answer with RAG
    result = rag.get_answer_from_multimodel(
        index_name="haitai_demo_1118",
        query=query,
        module='RAG',
        vec_docs_num=3
    )

    print("Query:", query)
    print("Answer:", result['answer'])
    source_documents = result['source_documents']
    response_source_documents = []
    for source_document in source_documents:
        response_source_document = {}
        response_source_document['page_content'] = source_document[0].page_content
        print("page_content:",source_document[0].page_content)
        print('---------------')
        print("metadata:",source_document[0].metadata)
        print('---------------')
        response_source_document['score'] = source_document[1]
        print("Score:",source_document[1])
        print('****************')
        response_source_documents.append(response_source_document)

    response = {
        "answer":result['answer'],
        "source_documents":response_source_documents
    }
    
