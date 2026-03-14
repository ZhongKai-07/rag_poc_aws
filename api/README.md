# API 后端文档

基于 AWS Bedrock + OpenSearch 的 RAG（检索增强生成）系统后端，提供 PDF 文档处理、向量存储和智能问答能力。

## 技术栈

- **FastAPI** — HTTP 服务框架，端口 8001
- **Docling** — PDF 解析，支持图文混合提取
- **AWS Bedrock** — 向量模型（Titan）+ LLM（Qwen3-235B）+ Rerank 模型
- **OpenSearch** — 向量数据库，支持 kNN 搜索
- **liteLLM** — 统一 LLM 调用接口

---

## 文件结构

```
api/
├── api.py                          # FastAPI 入口，定义所有 HTTP 接口
├── RAG_System.py                   # 核心 RAG 编排逻辑
├── document_processing.py          # PDF 解析、切块、向量化流水线
├── embedding_model.py              # Bedrock 向量模型 & Rerank 模型封装
├── llm_processor.py                # LLM 调用封装（liteLLM + Bedrock）
├── opensearch_search.py            # OpenSearch 查询逻辑（向量/文本/混合搜索）
├── opensearch_multimodel_dataload.py  # OpenSearch 写入逻辑（建索引 + bulk 导入）
├── config.py                       # 全局配置
├── processed_files.txt             # 已处理文件记录（文件名 → index 映射）
└── requirements.txt                # Python 依赖
```

---

## 快速启动

```bash
cd api
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# 配置 AWS 凭证
export AWS_DEFAULT_REGION=us-west-2
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key

python api.py
```

服务启动后访问 `http://localhost:8001/health` 验证。

---

## 配置说明（config.py）

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `OPENSEARCH_HOST` | — | OpenSearch 集群地址 |
| `OPENSEARCH_USERNAME` | admin | 用户名 |
| `OPENSEARCH_PASSWORD` | — | 密码 |
| `LLM_MODEL_NAME` | qwen.qwen3-235b-a22b-2507-v1:0 | Bedrock LLM 模型 ID |
| `EMBEDDING_MODEL_NAME` | amazon.titan-embed-text-v1 | Bedrock 向量模型 ID |
| `REGION_NAME` | us-west-2 | AWS 区域 |
| `TEXT_MAX_LENGTH` | 300 | 单句最大字符数（用于向量化） |
| `LLM_MAX_SIZE` | 800 | LLM 输入最大长度 |
| `IMAGE_RESOLUTION_SCALE` | 2.0 | PDF 图片提取分辨率倍数 |
| `CHUNK_SIZE_THRESHOLD` | 100 | 切块最小字符数，低于此值合并到下一块 |
| `ACCELERATOR_THREADS` | 10 | Docling 解析线程数 |
| `LLM_MAX_TOKENS` | 4096 | LLM 最大输出 token 数 |
| `LLM_TEMPERATURE` | 0.1 | LLM 温度参数 |

---

## HTTP 接口

### POST /upload_files
上传并处理 PDF 文件。

**请求（multipart/form-data）**
```
file: PDF 文件
directory_path: 存储目录路径，如 ./documents/2025-11-22-15-28
```

**响应**
```json
{"status": "success", "message": "Files processed successfully"}
```

---

### POST /rag_answer
对已处理的文档进行问答。

**请求**
```json
{
  "session_id": "session_abc123",
  "index_name": "a3f2c1b8",
  "query": "TCL科技1Q25的营收情况如何？",
  "module": "RAG",
  "vec_docs_num": 3,
  "txt_docs_num": 3,
  "vec_score_threshold": 0.0,
  "text_score_threshold": 0.0,
  "rerank_score_threshold": 0.5,
  "search_method": "mix"
}
```

| 参数 | 说明 |
|------|------|
| `search_method` | `vector` / `text` / `mix` 三种搜索模式 |
| `vec_docs_num` | 向量搜索召回文档数 |
| `rerank_score_threshold` | Rerank 分数过滤阈值，低于此值的文档丢弃 |

**响应**
```json
{
  "answer": "TCL科技1Q25营收...",
  "source_documents": [
    {
      "page_content": "原文片段...",
      "score": 85.32,
      "rerank_score": 0.91
    }
  ]
}
```

---

### GET /processed_files
获取所有已处理文件列表。

**响应**
```json
{
  "status": "success",
  "files": [
    {"filename": "报告.pdf", "index_name": "a3f2c1b8"}
  ]
}
```

---

### GET /get_index/{filename}
根据文件名查询对应的 OpenSearch index 名称。

---

### GET /health
健康检查。

---

## PDF 上传处理流程

上传一个 PDF 后，系统按以下步骤处理：

```
前端上传 PDF
    │
    ▼
1. api.py /upload_files
    │  保存文件到 ./documents/{timestamp}/
    │  初始化 DocumentProcessor（配置 embedding 模型、Markdown 分割器、Docling 转换器）
    │
    ▼
2. DocumentProcessor.process_directory(files_path)
    │  扫描目录下所有 PDF 文件
    │  读取 processed_files.txt，跳过已处理文件
    │  对每个新文件用 hashlib.md5(filename)[:8] 生成唯一 index_name
    │  逐个调用 process_file()
    │
    ▼
3. process_file(file_path, index_name)  ── Docling 解析 PDF
    │
    │  3a. self.doc_converter.convert(file_path)
    │      Docling 将 PDF 转为结构化文档对象（conv_res）
    │      转换器由 _setup_document_converter() 初始化，配置了：
    │        - images_scale: 图片提取分辨率倍数
    │        - generate_page_images: 启用页面图片生成
    │        - AcceleratorOptions: 解析线程数
    │
    │  3b. generate_multimodal_pages(conv_res)
    │      遍历每页，提取纯文本（content_text）和 Markdown（content_md）
    │      结果存入 rows 列表，供后续页眉页脚分析使用
    │
    │  3c. _extract_header_footer(rows)
    │      取前 3 页内容，比较每页开头和结尾的重复文本
    │      识别跨页重复的页眉（如公司名、报告标题）和页脚（如页码、免责声明）
    │      返回 (header_lines, footer_lines) 列表
    │
    │  3d. conv_res.document.export_to_markdown(image_mode=ImageRefMode.EMBEDDED)
    │      导出图文混合 Markdown（图片以 base64 内嵌为 ![Image](data:image/...) 格式）
    │
    │  3e. _clean_markdown_document(markdown_document, header, end_page)
    │      遍历识别到的页眉/页脚文本，从 Markdown 中移除这些重复内容
    │
    ▼
4. MarkdownHeaderTextSplitter.split_text()  ── 按标题切块
    │  按 # / ## / ### 标题层级切分为多个 chunk
    │  每个 chunk 的 metadata 记录所属标题（Header_1/2/3）
    │
    ▼
5. _process_text_splits(md_header_splits, file_name, index_name)  ── 块处理
    │  遍历每个 chunk：
    │    - 从 metadata 提取所属标题，拼接到 chunk 内容前面作为上下文
    │    - 短于 CHUNK_SIZE_THRESHOLD（默认 100 字符）的 chunk 合并到下一个
    │    - 对合格的 chunk 调用 _process_sentences()
    │
    ▼
6. _process_sentences(content, file_name, index_name)  ── 向量化
    │  将 chunk 按 \n 拆成句子列表
    │  过滤图片行（![Image]...）
    │  每句截断到 text_max_length（默认 300 字符）作为 sentence（用于向量化和 rerank）
    │  整块文本作为 paragraph（返回给 LLM 的完整上下文）
    │  调用 embeddings.embed_documents() 批量生成 1536 维向量
    │
    ▼
7. add_multimodel_documents(index_name, texts, embeddings, metadatas)  ── OpenSearch 写入
    │  （位于 opensearch_multimodel_dataload.py）
    │  自动检查 index 是否存在，不存在则用 _default_text_mapping() 创建
    │    - HNSW 算法，knn_vector 类型，nmslib 引擎
    │  通过 _bulk_ingest_embeddings() 使用 bulk API 批量写入
    │  每条记录包含：
    │    sentence_vector: 1536 维向量
    │    paragraph: 完整 chunk 文本
    │    sentence: 短句（用于 rerank 匹配）
    │    metadata: {sentence, source: 文件名}
    │
    ▼
8. 写入 processed_files.txt
    {"file_name": "报告.pdf", "index_name": "a3f2c1b8"}
```

---

### 使用 AWS Textract / Bedrock Data Automation 替换 Docling 的重构方案

当前流程中，Docling 承担了 PDF 解析的核心工作（文字提取、表格识别、图片提取、版面分析）。如果替换为 AWS 原生服务，需要重构 `document_processing.py` 中的以下部分：

#### 当前 Docling 职责（需替换的部分）

| 当前函数 / 逻辑 | Docling 做了什么 | 替换方案 |
|---|---|---|
| `_setup_document_converter()` | 初始化 Docling 转换器 | 初始化 Textract / BDA 客户端 |
| `process_file()` 中的 `self.doc_converter.convert()` | PDF → 结构化文档 | Textract `AnalyzeDocument` 或 BDA `InvokeDataAutomationAsync` |
| `generate_multimodal_pages()` | 逐页提取文字+Markdown | Textract 的 Blocks 解析 / BDA 的结构化输出 |
| `export_to_markdown(ImageRefMode.EMBEDDED)` | 导出图文混合 Markdown | 自行拼接 Textract/BDA 输出为 Markdown |
| `_extract_header_footer()` | 页眉页脚识别 | Textract `LAYOUT` 特征类型可识别 HEADER/FOOTER |



---

## 问答查询流程

```
用户提问（前端 POST /rag_answer）
    │
    ▼
1. api.py get_rag_answer(request)
    │  从请求中提取参数：index_names, query, search_method, vec_docs_num 等
    │  将 index_names 列表拼接为逗号分隔字符串（支持多文档跨 index 查询）
    │  调用 rag_system.get_answer_from_multimodel()
    │
    ▼
2. RAG_System.get_answer_from_multimodel(index_name, query, module, ...)
    │  入口函数，根据 module 参数分流：
    │    - module='RAG' → 走检索增强流程（下方步骤 3-7）
    │    - module='Chat' → 跳过检索，直接调用 LLM 对话（步骤 6-7）
    │  如果 query 包含不当用词标记，直接返回，不调用 LLM
    │
    ▼
3. opensearch_search.similarity_search(embeddings, query, index_name, ...)
    │  （位于 opensearch_search.py）
    │  统一检索入口，根据 search_method 选择检索策略：
    │
    │  3a. search_method="vector" → vector_search()
    │      调用 embeddings.embed_query(query) 将问题转为 1536 维向量
    │      （embeddings 是 BedrockEmbeddings，模型为 amazon.titan-embed-text-v1）
    │      构建 kNN 查询，在 OpenSearch 的 sentence_vector 字段上做近似最近邻搜索
    │      返回 top-k 结果（k = vec_docs_num），分数 ×100 转为百分制
    │
    │  3b. search_method="text" → text_search()
    │      对 sentence 字段做 BM25 关键词匹配（OpenSearch match 查询）
    │      返回 top-k 结果（k = txt_docs_num），分数为 BM25 原始分
    │
    │  3c. search_method="mix"
    │      同时执行 vector_search() 和 text_search()
    │      合并结果，按分数降序排序，按 page_content 去重
    │      截取前 (vec_docs_num + txt_docs_num) 条
    │
    │  每条结果经 _format_results() 格式化为 [Document, score, image]
    │    - Document.page_content = paragraph（完整 chunk 文本）
    │    - Document.metadata.sentence = 短句（用于后续 rerank）
    │    - score = 相似度分数
    │  按阈值过滤：向量结果 >= vec_score_threshold，文本结果 >= text_score_threshold
    │
    ▼
4. get_reranker_scores_bedrock(query, documents)  ── Rerank 重排序
    │  （位于 embedding_model.py）
    │  从每条结果的 metadata 中取出 sentence 作为 rerank 输入文本
    │  调用 boto3 bedrock-agent-runtime.rerank() API
    │    - 模型：config.RERANK_MODEL_NAME（如 amazon.rerank-v1:0）
    │    - 区域：config.RERANK_REGION_NAME（可与主区域不同）
    │  返回每条文档的 relevanceScore（0-1），并按分数降序排列
    │  过滤低于 rerank_score_threshold 的结果
    │  最终输出两组结果：
    │    - recall_documents: [Document, similarity_score]（rerank 前）
    │    - rerank_documents: [Document, similarity_score, rerank_score]（rerank 后）
    │
    ▼
5. RAG_System.format_context(docs)  ── 格式化上下文
    │  遍历 rerank 后的文档，从 page_content（paragraph）中解析内容
    │  按 '![Image]' 分割文本，识别内嵌的 base64 图片
    │    - 纯文本部分 → {"text": "..."}
    │    - 图片部分 → {"image": "base64_str"}（经 conver_image() 缩放到 800px 以内）
    │  输出 related_docs 列表：[{text:...}, {image:...}, {text:...}, ...]
    │  注意：当前图片虽然被解析出来，但 LLM 调用时图片传入已被注释（未启用）
    │
    ▼
6. LLMProcessor.answer(question, related_docs)
    │  （位于 llm_processor.py）
    │  6a. PromptManager.get_system_prompt()
    │      返回系统提示词："你是一个证券专家，请根据相关文档回答用户的问题。"
    │
    │  6b. PromptManager.get_user_prompt(question)
    │      将用户问题填入模板："用户问题如下：{question}\n不需要前言与解释，直接输出答案."
    │
    │  6c. BedrockClient.invoke_with_retry(prompt, system_prompt, related_docs, max_retries=3)
    │      组装 liteLLM messages：
    │        - system message: 系统提示词
    │        - user message: "相关文档如下:" + 各文档文本 + 用户问题
    │      调用 litellm.completion(model="bedrock/{modelId}", messages=..., max_tokens, temperature)
    │      失败时指数退避重试（2^attempt 秒），最多重试 3 次
    │      返回 LLM 生成的答案文本
    │
    ▼
7. api.py 格式化响应  ── 返回给前端
    │  将结果格式化为 RAGResponse：
    │    - answer: LLM 生成的答案
    │    - recall_documents: rerank 前的召回文档 [{page_content, score}]
    │    - rerank_documents: rerank 后的文档 [{page_content, score, rerank_score}]
    │    - source_documents: 与 rerank_documents 相同（用于前端高亮展示）
    │  同时将 query 写入 question_history（按 index_name 分文件存储）
    │
    ▼
返回 JSON 响应给前端
```

---

## 模块说明

### embedding_model.py
- `init_embeddings_bedrock()` — 初始化 Bedrock Titan 向量模型
- `get_reranker_scores_bedrock()` — 调用 Bedrock Rerank 模型对文档重新打分

### opensearch_multimodel_dataload.py
- 写入时若 index 不存在自动创建，使用 HNSW 算法的 knn_vector 类型
- 支持图文混合存储（`sentence_vector` + `paragraph` + `image_base64`）
- 用 bulk API 批量写入

### opensearch_search.py
- `vector_search()` — kNN 向量相似度搜索
- `text_search()` — BM25 关键词匹配
- `similarity_search()` — 统一入口，支持三种模式，内置去重和 rerank

### llm_processor.py
- `BedrockClient` — 通过 liteLLM 调用 Bedrock 模型，支持重试
- `PromptManager` — 管理 system prompt 和 user prompt 模板
- 图片传入功能已预留（`image_url` 部分，当前注释）

### RAG_System.py
- `RAGSystem` — 顶层编排，串联搜索、格式化、LLM 调用
- 支持 `RAG` 模式（检索增强）和 `Chat` 模式（直接对话）
- `conver_image()` — 对超过 800px 的图片自动缩放，减少 token 消耗

---

## processed_files.txt 格式

每行一条 JSON 记录，记录文件名和对应的 OpenSearch index：

```
{"file_name": "【华泰研究】TCL科技.pdf", "index_name": "a3f2c1b8"}
{"file_name": "【华泰研究】兴业银行.pdf", "index_name": "d7e4f2a1"}
```

index_name 由文件名的 MD5 前8位生成，保证同名文件始终映射到同一个 index。
