import json
from litellm import completion
import os

from dataclasses import asdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
import time
import logging
from typing import Dict, Any, List, Optional
import asyncio
import config


def get_config():
    """Get configuration from config.py"""
    return {
        "modelId": getattr(config, 'LLM_MODEL_NAME', "qwen.qwen3-235b-a22b-2507-v1:0"),
        "region_name": "us-west-2",
        "max_tokens": getattr(config, 'LLM_MAX_TOKENS', 4096),
        "temperature": getattr(config, 'LLM_TEMPERATURE', 0.1),
        "max_retries": getattr(config, 'LLM_MAX_RETRIES', 3)
    }


logger = logging.getLogger(__name__)


class BedrockClient:
    """AWS Bedrock client wrapper for LLM operations."""
    
    def __init__(self, config=None):
        """Initialize Bedrock client with configuration."""
        self.config = config
        # Set AWS region for liteLLM
        os.environ["AWS_DEFAULT_REGION"] = self.config["region_name"]
        logger.info(f"Initialized Bedrock client with model: {self.config['modelId']}")
    
    def invoke_model(self, prompt: str, system_prompt: str = None, related_docs: List = []) -> str:
        """Invoke the Bedrock model with a prompt."""
        try:
            # Build messages for liteLLM
            messages = []
            
            # Add system message if provided
            if system_prompt:
                messages.append({"role": "system", "content": system_prompt})
            
            # Build user content
            user_content = []
            
            if len(related_docs) > 0:
                user_content.append({"type": "text", "text": "相关文档如下:"})
                for doc in related_docs:
                    for k, value in doc.items():
                        if k == 'text':
                            user_content.append({"type": "text", "text": value})
                        # elif k == 'image':
                        #     user_content.append({
                        #         "type": "image_url",
                        #         "image_url": {"url": f"data:image/jpeg;base64,{value}"}
                        #     })
            
            user_content.append({"type": "text", "text": prompt})
            messages.append({"role": "user", "content": user_content})
            
            # Invoke the model using liteLLM
            start_time = time.time()
            response = completion(
                model=f"bedrock/{self.config['modelId']}",
                messages=messages,
                max_tokens=self.config["max_tokens"],
                temperature=self.config["temperature"]
            )
            processing_time = time.time() - start_time
            
            logger.debug(f"Model invocation completed in {processing_time:.2f} seconds")
            
            return response.choices[0].message.content
            
        except Exception as e:
            logger.error(f"Error during model invocation: {str(e)}")
            raise
    
    def invoke_with_retry(self, prompt: str, system_prompt: str = None, related_docs: List = [],max_retries: int = None) -> str:
        """Invoke model with exponential backoff retry logic."""
        max_retries = max_retries or int(self.config.max_retries)
        print('max_retries:',max_retries)
        
        for attempt in range(max_retries):
            try:
                return self.invoke_model(prompt, system_prompt, related_docs)
            except Exception as e:
                if attempt < max_retries:
                    wait_time = (2 ** attempt) + (time.time() % 1)
                    logger.warning(f"Error occurred, retrying in {wait_time:.2f} seconds (attempt {attempt + 1}/{max_retries + 1}): {str(e)}")
                    time.sleep(wait_time)
                    continue
                else:
                    raise
        
        raise logger.error(f"Failed after {max_retries + 1} attempts")
    



class PromptManager:

    def get_system_prompt(self) -> str:
        system_prompt = """
        你是一个证券专家，请根据相关文档回答用户的问题。

        """

        return system_prompt

    def get_user_prompt(self,question:str) -> str:

        prompt_template = """
            用户问题如下:
            {question}

            不需要前言与解释，直接输出答案.
        """

        prompt = prompt_template.format(question=question)
        return prompt


class LLMProcessor:
    """Main LLM processor for coordinating data extraction tasks."""
    
    def __init__(self, config=None):
        """Initialize LLM processor."""
        self.config = config or get_config()
        self.bedrock_client = BedrockClient(self.config)
        self.prompt_manager = PromptManager()



    def answer(self,question:str,related_docs: List = []) -> Dict[str, Any]:
        # try:
        if True:
            system_prompt = self.prompt_manager.get_system_prompt()
            prompt = self.prompt_manager.get_user_prompt(question)

            response = self.bedrock_client.invoke_with_retry(
                prompt,
                system_prompt,
                related_docs,
                max_retries = 3
            )

            return response


        # except json.JSONDecodeError as e:
        #     logger.error(f"Failed to parse account info JSON response: {str(e)}")
        # except Exception as e:
        #     logger.error(f"Account info extraction failed: {str(e)}")

