
import sys
import os
import logging
from opensearchpy import OpenSearch
import config
from embedding_model import init_embeddings_bedrock

# Setup logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def check_opensearch():
    logger.info("Checking OpenSearch connection...")
    try:
        host = getattr(config, 'OPENSEARCH_HOST', None)
        username = getattr(config, 'OPENSEARCH_USERNAME', None)
        password = getattr(config, 'OPENSEARCH_PASSWORD', None)
        
        if not host:
            logger.error("OpenSearch host not configured")
            return False
            
        if host.startswith('http'):
            host = host.replace('https://', '').replace('http://', '').rstrip('/')
            
        client = OpenSearch(
            hosts=[{'host': host, 'port': 443}],
            http_auth=(username, password),
            use_ssl=True,
            verify_certs=True
        )
        
        info = client.info()
        logger.info(f"OpenSearch Connected! Version: {info['version']['number']}")
        return True
    except Exception as e:
        logger.error(f"OpenSearch Connection Failed: {e}")
        return False

def check_bedrock():
    logger.info("Checking Bedrock connection...")
    try:
        embeddings = init_embeddings_bedrock(config.EMBEDDING_MODEL_NAME)
        vector = embeddings.embed_query("test")
        logger.info(f"Bedrock Connected! Embedding dimension: {len(vector)}")
        return True
    except Exception as e:
        logger.error(f"Bedrock Connection Failed: {e}")
        return False

if __name__ == "__main__":
    os_ok = check_opensearch()
    bedrock_ok = check_bedrock()
    
    if os_ok and bedrock_ok:
        logger.info("ALL SYSTEMS GO: Connections verified successfully")
        sys.exit(0)
    else:
        logger.error("CONNECTION CHECK FAILED")
        sys.exit(1)
