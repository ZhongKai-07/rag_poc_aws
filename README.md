# RAG System

基于 OpenSearch 和 AWS Bedrock 的检索增强生成（RAG）系统，支持 PDF 文档处理、向量存储和智能问答。

## 系统架构

- **文档处理**: 使用 Docling 处理 PDF 文档，提取文本和图像
- **向量存储**: OpenSearch 作为向量数据库
- **嵌入模型**: AWS Bedrock 嵌入服务
- **语言模型**: liteLLM + AWS Bedrock LLM 服务
- **前端界面**: React + TypeScript + Vite
- **后端服务**: FastAPI

## 文件结构

```
├── api/                           # 后端 API 服务
│   ├── api.py                     # FastAPI 服务器
│   ├── RAG_System.py              # 核心 RAG 系统
│   ├── document_processing.py     # PDF 文档处理
│   ├── llm_processor.py          # LLM 处理器（liteLLM）
│   ├── opensearch_search.py      # OpenSearch 搜索
│   ├── opensearch_multimodel_dataload.py # 数据导入
│   ├── embedding_model.py         # 嵌入模型
│   ├── config.py                  # 配置文件
│   ├── processed_files.txt        # 已处理文件记录
│   └── requirements.txt           # 后端依赖包
├── frontend/                      # 前端应用
│   ├── src/
│   │   ├── pages/
│   │   │   ├── Upload.tsx         # 文件上传页面
│   │   │   └── QA.tsx            # 问答页面
│   │   └── components/           # UI 组件
│   ├── .env                      # 环境变量配置
│   └── package.json              # 前端依赖包
├── documents/                     # 上传文件存储目录
└── README.md                      # 说明文档
```

## 安装依赖

### 后端依赖
```bash
cd api
pip install -r requirements.txt
```

### 前端依赖
```bash
cd frontend
npm install
```

## 配置

### 1. 后端配置 (api/config.py)

```python
# OpenSearch 配置
OPENSEARCH_INDEX = "your_index_name"
OPENSEARCH_HOST = "your-opensearch-host.com"
OPENSEARCH_USERNAME = "your-username"
OPENSEARCH_PASSWORD = "your-password"

# 模型配置
LLM_MODEL_NAME = "qwen.qwen3-235b-a22b-2507-v1:0"
EMBEDDING_MODEL_NAME = "amazon.titan-embed-text-v1"

# LLM 配置
LLM_MAX_TOKENS = 4096
LLM_TEMPERATURE = 0.1
LLM_MAX_RETRIES = 3

# 文件路径
FILES_PATH = './documents'
```

### 2. 前端配置 (frontend/.env)

```bash
# 本地开发
VITE_API_BASE_URL=http://localhost:8001

# 生产部署
# VITE_API_BASE_URL=http://your-instance-ip:8001
```

## 使用方法

### 1. 文档处理

```python
python document_processing.py
```

### 2. RAG 查询

```python
from RAG_System import RAGSystem

# 初始化 RAG 系统
rag = RAGSystem()

# 获取答案
result = rag.get_answer_from_multimodel(
    index_name="your_index",
    query="你的问题",
    vec_docs_num=3,
    vec_score_threshold=0.5
)

print(result['answer'])
```

### 3. 启动服务

```bash
# 启动后端服务
cd api
python api.py

# 启动前端服务（新终端）
cd frontend
npm run dev

# 访问应用
前端: http://localhost:5173
后端: http://localhost:8001
```

## API 接口

### 文件上传

```http
POST /upload_files
Content-Type: multipart/form-data

file: [PDF文件]
directory_path: ./documents/2024-01-15-14-30
```

### 获取 RAG 答案

```http
POST /rag_answer
Content-Type: application/json

{
    "session_id": "web-session",
    "index_name": "your_index",
    "query": "你的问题",
    "vec_docs_num": 3,
    "vec_score_threshold": 0.5,
    "search_method": "mix"
}
```

### 获取已处理文件

```http
GET /processed_files
```

### 根据文件名获取索引

```http
GET /get_index/{filename}
```

## 主要功能

### 文档处理
- PDF 文档解析和文本提取
- 图像提取和处理（Base64 编码）
- 文档分块和向量化
- 基于文件名哈希的自动索引分配
- 多文件批量上传支持
- 按日期时间自动目录组织

### 智能搜索
- 向量相似度搜索
- 文本关键词搜索
- 混合搜索模式
- 分数阈值过滤

### 问答生成
- 基于检索文档的答案生成
- 多模态内容支持（文本+图像）
- liteLLM 统一 LLM 接口
- 自定义系统提示
- 自动滚动到答案位置

## Web 界面功能

### 文件上传页面 (Upload.tsx)
1. **多文件上传**: 支持一次选择多个 PDF 文件
2. **自动目录管理**: 按日期时间创建存储目录
3. **上传进度显示**: 实时显示上传和处理状态
4. **已处理文件列表**: 显示所有已处理的文件

### 问答页面 (QA.tsx)
1. **文档选择**: 从已处理文件中选择
2. **问题输入**: 输入自然语言问题
3. **参数调整**: 调整相似度阈值、文档数量、搜索模式
4. **答案显示**: 显示生成的答案，自动滚动到答案位置
5. **源文档展示**: 查看检索到的源文档，支持图像显示

## 配置说明

### 相似度阈值
- 范围: 0.0 - 1.0
- 默认: 0.5
- 较高值: 更严格的匹配
- 较低值: 更宽松的匹配

### 搜索参数
- **vec_docs_num**: 向量搜索返回的文档数（1-5）
- **search_method**: 搜索模式（mix/vector/text）
- **相似度阈值**: 文档匹配的最低相似度要求

## 故障排除

### 常见问题

1. **OpenSearch 连接失败**
   - 检查 config.py 中的连接配置
   - 确认网络连接和认证信息

2. **AWS Bedrock 访问失败**
   - 检查 AWS 凭证配置
   - 确认模型访问权限

3. **文档处理失败**
   - 检查 PDF 文件格式
   - 确认 Docling 依赖安装

4. **前端访问失败**
   - 确认前端服务正常运行（端口 5173）
   - 确认后端服务正常运行（端口 8001）
   - 检查 frontend/.env 中的 API 地址配置

### 日志查看

```python
import logging
logging.basicConfig(level=logging.DEBUG)
```

## 性能优化

- 批量文档处理
- 向量搜索优化
- 内存使用控制
- 并发请求处理

## 技术特性

### 前端技术栈
- React 18 + TypeScript
- Vite 构建工具
- Tailwind CSS 样式
- Shadcn/ui 组件库

### 后端技术栈
- FastAPI 异步框架
- liteLLM 统一 LLM 接口
- OpenSearch 向量数据库
- AWS Bedrock 模型服务

### 文件管理
- 按时间戳自动目录组织
- 文件名哈希索引生成
- 重复文件检测和跳过
- 多模态内容处理

## AWS Ubuntu 部署

### 1. 环境准备

#### 创建 EC2 实例
```bash
# 推荐配置
# 实例类型: t3.large 或更高（至少 2 vCPU, 8GB RAM）
# 操作系统: Ubuntu 22.04 LTS
# 存储: 至少 20GB EBS
# 安全组: 开放端口 22, 8001, 5173
```

#### 连接到实例
```bash
ssh -i your-key.pem ubuntu@your-instance-ip
```

### 2. 系统依赖安装

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 安装基础依赖
sudo apt install -y python3 python3-pip python3-venv git curl

# 安装 Node.js 和 npm
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs

# 验证安装
node --version
npm --version
python3 --version
```

### 3. 项目部署

#### 克隆项目
```bash
cd /home/ubuntu
git clone <your-repo-url> huatai_rag
cd huatai_rag
```

#### 后端部署
```bash
# 创建虚拟环境
cd api
python3 -m venv venv
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt

# 配置环境变量
export AWS_DEFAULT_REGION=us-west-2
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
```

#### 前端部署
```bash
cd ../frontend

# 安装依赖
npm install

# 配置环境变量
echo "VITE_API_BASE_URL=http://your-instance-ip:8001" > .env

# 构建生产版本
npm run build
```

### 4. 配置文件设置

#### 后端配置 (api/config.py)
```python
# OpenSearch 配置
OPENSEARCH_HOST = "your-opensearch-endpoint"
OPENSEARCH_USERNAME = "your-username"
OPENSEARCH_PASSWORD = "your-password"

# 其他配置保持默认
```

### 5. 使用 systemd 管理服务

#### 创建后端服务
```bash
sudo tee /etc/systemd/system/huatai-api.service > /dev/null <<EOF
[Unit]
Description=Huatai RAG API Service
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/huatai_rag/api
Environment=PATH=/home/ubuntu/huatai_rag/api/venv/bin
Environment=AWS_DEFAULT_REGION=us-west-2
Environment=AWS_ACCESS_KEY_ID=your-access-key
Environment=AWS_SECRET_ACCESS_KEY=your-secret-key
ExecStart=/home/ubuntu/huatai_rag/api/venv/bin/python api.py
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF
```

#### 创建前端服务
```bash
sudo tee /etc/systemd/system/huatai-frontend.service > /dev/null <<EOF
[Unit]
Description=Huatai RAG Frontend Service
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/huatai_rag/frontend
ExecStart=/usr/bin/npm run preview -- --host 0.0.0.0 --port 5173
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF
```

#### 启动服务
```bash
# 重新加载 systemd
sudo systemctl daemon-reload

# 启动并启用服务
sudo systemctl enable huatai-api
sudo systemctl enable huatai-frontend
sudo systemctl start huatai-api
sudo systemctl start huatai-frontend

# 检查服务状态
sudo systemctl status huatai-api
sudo systemctl status huatai-frontend
```

### 6. 使用 Nginx 反向代理（可选）

```bash
# 安装 Nginx
sudo apt install -y nginx

# 配置 Nginx
sudo tee /etc/nginx/sites-available/huatai-rag > /dev/null <<EOF
server {
    listen 80;
    server_name your-domain.com;

    # 前端
    location / {
        proxy_pass http://localhost:5173;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_cache_bypass \$http_upgrade;
    }

    # 后端 API
    location /api/ {
        proxy_pass http://localhost:8001/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_cache_bypass \$http_upgrade;
    }
}
EOF

# 启用站点
sudo ln -s /etc/nginx/sites-available/huatai-rag /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

### 7. 监控和日志

```bash
# 查看服务日志
sudo journalctl -u huatai-api -f
sudo journalctl -u huatai-frontend -f

# 查看系统资源使用
htop
df -h
free -h
```

### 8. 安全配置

```bash
# 配置防火墙
sudo ufw enable
sudo ufw allow ssh
sudo ufw allow 80
sudo ufw allow 443
sudo ufw allow 8001  # 如果不使用 Nginx
sudo ufw allow 5173  # 如果不使用 Nginx

# 定期更新系统
sudo apt update && sudo apt upgrade -y
```

### 9. 备份策略

```bash
# 创建备份脚本
sudo tee /home/ubuntu/backup.sh > /dev/null <<EOF
#!/bin/bash
DATE=\$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/home/ubuntu/backups/\$DATE"
mkdir -p \$BACKUP_DIR

# 备份配置文件
cp /home/ubuntu/huatai_rag/api/config.py \$BACKUP_DIR/
cp /home/ubuntu/huatai_rag/frontend/.env \$BACKUP_DIR/

# 备份已处理文件记录
cp /home/ubuntu/huatai_rag/api/processed_files.txt \$BACKUP_DIR/

# 备份文档目录
cp -r /home/ubuntu/huatai_rag/documents \$BACKUP_DIR/

echo "Backup completed: \$BACKUP_DIR"
EOF

chmod +x /home/ubuntu/backup.sh

# 设置定时备份
(crontab -l 2>/dev/null; echo "0 2 * * * /home/ubuntu/backup.sh") | crontab -
```

## 注意事项

1. 确保 AWS 凭证正确配置
2. OpenSearch 集群需要足够的存储空间
3. 大文件处理时注意内存使用
4. 定期备份 processed_files.txt 文件
5. 上传的文件会保存在 documents/ 目录下
6. 前后端需要同时运行才能正常使用
7. 生产环境建议使用 HTTPS 和域名
8. 定期监控系统资源使用情况
9. 配置日志轮转避免磁盘空间不足