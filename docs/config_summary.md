📋 RAG 项目配置总结
🔐 AWS 凭证配置
配置项	值
Access Key ID	AKIAZJC2RJ2QKZ6DAJ42
Secret Access Key	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
凭证文件位置	~/.aws/credentials
IAM 用户	BedrockAPIKey-5qv0
账户 ID	637992521376
⚠️ 此凭证为永久凭证，无需 Session Token

🗄️ OpenSearch 配置
配置项	值
域名	rag-poc
端点	search-rag-poc-zobjyhdzjc2wlfsqevryxrppe4.ap-east-1.es.amazonaws.com
区域	ap-east-1（香港）
引擎版本	OpenSearch 3.3
用户名	admin
密码	Zktj1016!
索引名	rag_poc
访问模式	Public Access
kNN 支持	✅ 支持
🤖 Bedrock 模型配置
配置项	值
区域	ap-northeast-1（东京）
LLM 模型
配置项	值
模型 ID	qwen.qwen3-235b-a22b-2507-v1:0
模型名称	Qwen3 235B A22B 2507
提供商	Qwen
状态	✅ 可调用
Embedding 模型
配置项	值
模型 ID	amazon.titan-embed-text-v1
模型名称	Titan Embeddings G1 - Text
提供商	Amazon
向量维度	1536
状态	✅ 可调用
Rerank 模型
配置项	值
模型 ID	amazon.rerank-v1:0
模型名称	Rerank 1.0
提供商	Amazon
状态	✅ 可调用
📁 项目配置文件
位置: /root/rag_poc_aws/api/config.py

# OpenSearch Configuration
OPENSEARCH_HOST = "search-rag-poc-zobjyhdzjc2wlfsqevryxrppe4.ap-east-1.es.amazonaws.com"
OPENSEARCH_USERNAME = "admin"
OPENSEARCH_PASSWORD = "Zktj1016!"
OPENSEARCH_INDEX = "rag_poc"
OPENSEARCH_REGION = "ap-east-1"

# Model Configuration (Bedrock)
LLM_MODEL_NAME = "qwen.qwen3-235b-a22b-2507-v1:0"
EMBEDDING_MODEL_NAME = "amazon.titan-embed-text-v1"
REGION_NAME = "ap-northeast-1"

# Rerank Configuration
RERANK_MODEL_NAME = "amazon.rerank-v1:0"
RERANK_REGION_NAME = "ap-northeast-1"
🗺️ 架构概览
┌─────────────────────────────────────────────────────────────────────────────┐
│                              RAG 系统架构                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│    ┌─────────────────┐                                                      │
│    │   用户 / 前端    │  http://localhost:5173                              │
│    └────────┬────────┘                                                      │
│             │ API 请求                                                      │
│             ▼                                                               │
│    ┌─────────────────┐                                                      │
│    │  FastAPI 后端   │  http://localhost:8001                              │
│    │    (api.py)     │                                                      │
│    └────────┬────────┘                                                      │
│             │                                                               │
│    ┌────────┴────────┬──────────────────────────────────────┐              │
│    │                 │                                      │              │
│    ▼                 ▼                                      ▼              │
│ ┌──────────────┐ ┌──────────────┐              ┌──────────────────┐        │
│ │  文档处理     │ │  向量搜索    │              │    LLM 生成      │        │
│ │ (Docling)    │ │ (OpenSearch) │              │   (Bedrock)      │        │
│ └──────────────┘ └──────┬───────┘              └────────┬─────────┘        │
│                         │                               │                  │
│                         ▼                               ▼                  │
│              ┌─────────────────────┐         ┌─────────────────────┐       │
│              │   ap-east-1 (香港)  │         │ ap-northeast-1 (东京)│       │
│              │                     │         │                     │       │
│              │  ┌───────────────┐  │         │  ┌───────────────┐  │       │
│              │  │  OpenSearch   │  │         │  │   Bedrock     │  │       │
│              │  │   rag-poc     │  │         │  │   • Qwen LLM  │  │       │
│              │  │   (向量存储)   │  │         │  │   • Titan Emb │  │       │
│              │  └───────────────┘  │         │  │   • Rerank    │  │       │
│              └─────────────────────┘         │  └───────────────┘  │       │
│                                              └─────────────────────┘       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
🚀 启动命令
# 1. 进入项目目录
cd /root/rag_poc_aws/api

# 2. 激活虚拟环境（如果已创建）
source venv/bin/activate

# 3. 安装依赖（首次运行）
pip install -r requirements.txt

# 4. 启动后端服务
python api.py
# 后端运行在 http://localhost:8001

# 5. 新终端启动前端
cd /root/rag_poc_aws/frontend
npm install  # 首次运行
npm run dev
# 前端运行在 http://localhost:5173
✅ 配置检查清单
检查项	状态
AWS 凭证配置正确	✅
OpenSearch 集群 Active	✅
OpenSearch 公网可访问	✅
Bedrock LLM 权限	✅ ap-northeast-1
Bedrock Embedding 权限	✅ ap-northeast-1
Bedrock Rerank 权限	✅ ap-northeast-1
config.py 区域配置	✅ 已修正
config.py 模型 ID	✅ 已修正
一切就绪，可以开始运行了！🎉