#!/usr/bin/env python3
"""
Document Processing Module for RAG System
Processes PDF files and loads them into OpenSearch vector database
"""

import sys
import os
import json
import time
import logging
import hashlib
from pathlib import Path
from tqdm import tqdm
from typing import List, Dict, Tuple, Optional
from datetime import datetime

# Import local modules
from embedding_model import init_embeddings_bedrock
from opensearch_multimodel_dataload import add_multimodel_documents
import config

# Docling imports
from docling_core.types.doc import ImageRefMode
from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import PdfPipelineOptions, AcceleratorDevice, AcceleratorOptions
from docling.document_converter import DocumentConverter, PdfFormatOption
from docling.utils.export import generate_multimodal_pages

# Updated LangChain imports for new version
from langchain_text_splitters import MarkdownHeaderTextSplitter

# Setup logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')


class DocumentProcessor:
    """Document processor for PDF files to OpenSearch vector database"""
    
    def __init__(self, 
                 embedding_model_name: str = None,
                 text_max_length: int = None,
                 llm_max_size: int = None,
                 image_resolution_scale: float = None):
        """
        Initialize document processor
        
        Args:
            embedding_model_name: embedding model name
            text_max_length: Maximum text length for processing
            llm_max_size: Maximum LLM input size
            image_resolution_scale: Image resolution scaling factor
        """
        # Use config defaults if not provided
        self.text_max_length = text_max_length
        self.llm_max_size = llm_max_size
        
        self.embeddings = init_embeddings_bedrock(embedding_model_name)
        
        # Initialize markdown splitter with updated syntax
        self.markdown_splitter = MarkdownHeaderTextSplitter(
            headers_to_split_on=config.MARKDOWN_HEADERS,
        )
        
        # Initialize document converter
        image_resolution_scale = image_resolution_scale or config.IMAGE_RESOLUTION_SCALE
        self._setup_document_converter(image_resolution_scale)

    def _setup_document_converter(self, image_resolution_scale: float):
        """Setup document converter with pipeline options"""
        pipeline_options = PdfPipelineOptions()
        pipeline_options.images_scale = image_resolution_scale
        pipeline_options.generate_page_images = True
        pipeline_options.accelerator_options = AcceleratorOptions(
            num_threads=config.ACCELERATOR_THREADS,
            device=AcceleratorDevice.AUTO
        )

        self.doc_converter = DocumentConverter(
            format_options={
                InputFormat.PDF: PdfFormatOption(pipeline_options=pipeline_options)
            }
        )
    
    def _extract_header_footer(self, rows):
        """Extract header and footer content from document rows"""
        header = []
        end_page = []
        
        # Process first 3 rows to identify headers and footers
        for row_num in range(min(3, len(rows))):
            row = rows[row_num]
            content = row['contents'].strip().split(' ')
            contents_md = row['contents_md'].split('\n')
            
            if not content:
                continue
                
            start_word = content[0]
            last_word = content[-1]
            
            # Extract header content
            for line in contents_md:
                if line.find(start_word) < 0 and line not in header and len(line) > 0:
                    header.append(line)
                else:
                    break
            
            # Extract footer content
            contents_md.reverse()
            for line in contents_md:
                if line.find(last_word) < 0 and line not in end_page and len(line) > 0:
                    end_page.append(line)
                else:
                    break
        
        return header, end_page
    
    def _clean_markdown_document(self, markdown_document, header, end_page):
        """Clean markdown document by removing headers and footers"""
        # Remove header content
        for line in header:
            markdown_document = markdown_document.replace(line, '')
        
        # Remove footer content
        for line in end_page:
            markdown_document = markdown_document.replace(line, '')
        
        return markdown_document.strip()
    
    def _process_text_splits(self, md_header_splits, file_name, index_name):
        """Process markdown text splits and prepare for embedding"""
        last_content = ''
        
        for text_split in md_header_splits:
            content = text_split.page_content
            metadata = text_split.metadata
            
            # Extract header information
            header = ''
            if 'Header_1' in metadata:
                header = metadata['Header_1']
            elif 'Header_2' in metadata:
                header = metadata['Header_2']
            elif 'Header_3' in metadata:
                header = metadata['Header_3']
            
            # Combine content with headers
            if len(last_content) > 0:
                content = last_content + '\n' + header + '\n' + content
            elif len(header) > 0:
                if isinstance(header, str):
                    content = header + '\n' + content
                elif isinstance(header, list):
                    content = ','.join(header) + '\n' + content
            
            # Handle short content
            if len(content) < config.CHUNK_SIZE_THRESHOLD:
                last_content = content
                continue
            
            print('Processing content:', content[:100] + '...' if len(content) > 100 else content)
            last_content = ''
            
            # Process sentences and create embeddings
            self._process_sentences(content, file_name, index_name)
    
    def _process_sentences(self, content, file_name, index_name):
        """Process sentences and add to vector store"""
        try:
            sentences = content.split('\n')
            texts = []
            metadatas = []

            for sentence in sentences:
                sentence = sentence.strip()
                if len(sentence) > 0 and sentence.find('![Image]') < 0:
                    metadata = {
                        'sentence': sentence[:self.text_max_length] if len(sentence) > self.text_max_length else sentence,
                        'source': file_name.split('/')[-1]
                    }
                    metadatas.append(metadata)
                    texts.append(content)

            if len(texts) > 0:
                logging.info(f"Generating embeddings for {len(texts)} texts")
                text_embeddings = self.embeddings.embed_documents([metadata['sentence'] for metadata in metadatas])
                logging.info(f"Embeddings generated successfully. Shape: {len(text_embeddings)}x{len(text_embeddings[0]) if text_embeddings else 0}")

                # Add to vector store
                logging.info(f"Adding documents to OpenSearch index: {index_name}")
                add_multimodel_documents(
                    index_name,
                    texts=texts,
                    embeddings=text_embeddings,
                    metadatas=metadatas
                )
                logging.info(f'Finished saving to vector store: {index_name}')
        except Exception as e:
            logging.error(f"Error in _process_sentences: {str(e)}", exc_info=True)
            raise
    
    def process_file(self, file_path, index_name: str):
        """Process a single PDF file"""
        try:
            start_time = time.time()
            print(f'Processing file: {file_path}')
            logging.info(f'Starting to process file: {file_path}')

            # Convert document
            logging.info('Converting PDF document...')
            conv_res = self.doc_converter.convert(file_path)
            convert_time = time.time() - start_time
            print(f'Convert file time: {convert_time:.2f}s')
            logging.info(f'PDF conversion completed in {convert_time:.2f}s')

            # Extract multimodal pages
            logging.info('Extracting multimodal pages...')
            rows = []
            for (content_text, content_md, content_dt, page_cells, page_segments, page) in generate_multimodal_pages(conv_res):
                rows.append({
                    "contents": content_text,
                    "contents_md": content_md
                })
            logging.info(f'Extracted {len(rows)} pages')

            # Extract headers and footers
            header, end_page = self._extract_header_footer(rows)

            # Export to markdown
            logging.info('Exporting to markdown...')
            markdown_document = conv_res.document.export_to_markdown(image_mode=ImageRefMode.EMBEDDED)
            print('Markdown document extracted')
            print('-' * 50)

            # Clean markdown document
            markdown_document = self._clean_markdown_document(markdown_document, header, end_page)

            # Split markdown by headers
            logging.info('Splitting markdown by headers...')
            md_header_splits = self.markdown_splitter.split_text(markdown_document)
            logging.info(f'Split into {len(md_header_splits)} sections')

            # Process text splits
            self._process_text_splits(md_header_splits, file_path, index_name)

            total_time = time.time() - start_time
            print(f'Total processing time: {total_time:.2f}s')
            logging.info(f'Total file processing time: {total_time:.2f}s')

        except Exception as e:
            logging.error(f'Error processing file {file_path}: {str(e)}', exc_info=True)
            raise
    
    def process_directory(self, files_path):
        """Process all PDF files in a directory"""
        logging.info(f"Starting to process directory: {files_path}")

        if not os.path.exists(files_path):
            error_msg = f"Directory not found: {files_path}"
            logging.error(error_msg)
            raise FileNotFoundError(error_msg)

        files = os.listdir(files_path)
        pdf_files = [f for f in files if f.lower().endswith('.pdf') and 'ipynb_checkpoints' not in f]

        print(f"Found {len(pdf_files)} PDF files to process")
        logging.info(f"Found {len(pdf_files)} PDF files in {files_path}")

        # Create log file for tracking processed files
        log_file = "processed_files.txt"

        # Read existing processed files to avoid duplicates
        processed_files = set()
        if os.path.exists(log_file):
            with open(log_file, 'r', encoding='utf-8') as f:
                for line in f:
                    if line.strip():
                        try:
                            file_record = json.loads(line)
                            processed_files.add(file_record["file_name"])
                        except json.JSONDecodeError:
                            continue

        for i, file in enumerate(tqdm(pdf_files, desc="Processing PDFs")):
            # Skip if already processed
            if file in processed_files:
                print(f"Skipping already processed file: {file}")
                continue

            file_path = os.path.join(files_path, file)
            print(f"\nProcessing file {i+1}/{len(pdf_files)}: {file}")
            logging.info(f"Processing file {i+1}/{len(pdf_files)}: {file}")

            # Generate index name based on filename hash
            current_index = hashlib.md5(file.encode('utf-8')).hexdigest()[:8]

            try:
                # Process file
                self.process_file(file_path, current_index)

                # Log processed file in JSON format
                file_record = {
                    "file_name": file,
                    "index_name": current_index
                }
                with open(log_file, 'a', encoding='utf-8') as f:
                    f.write(json.dumps(file_record, ensure_ascii=False) + "\n")

                # Add to processed set
                processed_files.add(file)
                logging.info(f"Successfully processed file: {file}")

            except Exception as e:
                logging.error(f"Failed to process file {file}: {str(e)}", exc_info=True)
                # Continue processing other files
                continue


def main():
    """Main function to run document processing"""
    # Initialize processor with config defaults
    embedding_model_name = config.EMBEDDING_MODEL_NAME
    text_max_length = config.TEXT_MAX_LENGTH
    llm_max_size = config.LLM_MAX_SIZE
    image_resolution_scale = config.IMAGE_RESOLUTION_SCALE
    processor = DocumentProcessor(embedding_model_name,text_max_length,llm_max_size,image_resolution_scale)

    # Process directory
    processor.process_directory(config.FILES_PATH)


def process_single_file(file_path: str, index_name: str):
    """Process a single file - utility function"""
    processor = DocumentProcessor()
    processor.process_file(file_path, index_name)


def process_files_batch(file_paths: List[str], index_name: str):
    """Process multiple files in batch"""
    processor = DocumentProcessor()
    
    for file_path in tqdm(file_paths, desc="Processing files"):
        try:
            processor.process_file(file_path, index_name)
        except Exception as e:
            logging.error(f"Failed to process {file_path}: {str(e)}")
            continue


if __name__ == "__main__":
    main()