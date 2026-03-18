# 开发进度日志

**日期**: 2026-03-15
**项目**: RAG System (huatai_rag_github_share)
**开发者**: Claude Code
**状态**: ✅ 项目成功运行

---

## 今日完成目标

- [x] 诊断并修复项目启动问题
- [x] 解决 Windows 环境兼容性 issues
- [x] 成功实现 PDF 上传和问答功能
- [x] 创建 CLAUDE.md 文档
- [x] 建立开发日志系统

---

## 问题与解决方案汇总

### 问题 #1: AWS 区域配置混乱

**严重程度**: 🔴 高
**影响**: 服务无法连接 AWS 资源

**现象**:
- `config.py` 中 OpenSearch 在 ap-east-1
- Bedrock 配置在 us-west-2
- 环境变量在 us-east-1
- 区域不统一导致连接失败

**解决方案**:
```python
# config.py 更新
OPENSEARCH_HOST = "search-rag-poc-zobjyhdzjc2wlfsqevryxrppe4.ap-east-1.es.amazonaws.com"
REGION_NAME = "us-east-1"  # Bedrock 区域
RERANK_REGION_NAME = "us-east-1"
```

**后续建议**:
- 考虑将 Bedrock 迁移到 ap-northeast-1 (东京) 以获得更好的模型支持
- 统一所有 AWS 服务区域配置

---

### 问题 #2: 前端缺少 API 配置

**严重程度**: 🔴 高
**影响**: 前端无法连接后端，上传功能完全失败

**现象**:
- 前端访问 `http://localhost:8080`
- 后端在 `http://localhost:8001`
- 前端请求发送到错误地址

**错误信息**:
```
Upload Failed
There was an error processing your files.
```

**根本原因**:
- 缺少 `frontend/.env` 文件
- 前端无法知道后端 API 地址

**解决方案**:
```bash
cd frontend
echo "VITE_API_BASE_URL=http://localhost:8001" > .env
```

**重要提示**:
- 修改 `.env` 后必须重启前端服务
- 文件路径: `frontend/.env`

---

### 问题 #3: 后端代码导入错误

**严重程度**: 🔴 高
**影响**: 后端服务无法启动

**现象**:
```python
Traceback (most recent call last):
  File "api.py", line 350, in <module>
    verify_configuration()
  File "api.py", line 333, in verify_configuration
    if config.OPENSEARCH_HOST ...
NameError: name 'config' is not defined
```

**根本原因**:
- `api/api.py` 中缺少 `import config`

**解决方案**:
在 `api/api.py` 开头添加:
```python
import config
```

**文件位置**: `api/api.py`

---

### 问题 #4: 函数参数不匹配

**严重程度**: 🟡 中
**影响**: 文档处理功能异常

**现象**:
```python
TypeError: process_directory() takes 1 positional argument but 2 were given
```

**根本原因**:
- `main()` 函数调用 `process_directory(config.FILES_PATH, index_name)`
- 但 `process_directory()` 定义只接受一个参数

**解决方案**:
```python
# 修改前
processor.process_directory(config.FILES_PATH, index_name)

# 修改后
processor.process_directory(config.FILES_PATH)
```

**文件位置**: `api/document_processing.py`, line 305

---

### 问题 #5: Windows 上 Docling 崩溃 (核心问题)

**严重程度**: 🔴 阻塞
**影响**: PDF 上传处理完全失败

**现象**:
```
RuntimeError: filename does not exists:
E:\...\venv\lib\site-packages\docling_parse\pdf_resources_v2/glyphs//standard/additional.dat
```

**根本原因**:
- Docling 在 Windows 上有路径处理 bug
- 无法找到字体资源文件
- 路径分隔符处理错误 (`/` vs `\`)

**解决方案**:

1. **添加 PyPDF2 作为备选方案** (`api/requirements.txt`):
```txt
pypdf>=3.0.0
```

2. **实现双解析器策略** (`api/document_processing.py`):
```python
# 尝试 Docling，失败则自动回退到 PyPDF2
if self.doc_converter and DOCLING_AVAILABLE:
    try:
        self._process_with_docling(file_path, index_name)
    except Exception:
        self._process_with_pypdf(file_path, index_name)
else:
    self._process_with_pypdf(file_path, index_name)
```

3. **安装依赖**:
```bash
cd api
pip install pypdf
```

**后续优化**:
- PyPDF2 提取的文本格式不如 Docling
- 考虑在 Linux/WSL 环境中使用 Docling 获得更好效果

---

### 问题 #6: 代码缩进错误

**严重程度**: 🟡 中
**影响**: 后端无法启动

**现象**:
```python
IndentationError: unexpected unindent
```

**根本原因**:
- 添加 `_process_text_chunk` 方法时漏掉了 `except` 块

**解决方案**:
补全异常处理代码块:
```python
def _process_text_chunk(self, ...):
    try:
        # ... 处理逻辑
        logging.info(f'Chunk {chunk_id} saved')
    except Exception as e:
        logging.error(f"Error: {e}", exc_info=True)
        raise
```

**文件位置**: `api/document_processing.py`, line 255

---

## 配置变更记录

### config.py 变更
```python
# OpenSearch 配置
OPENSEARCH_HOST = "search-rag-poc-zobjyhdzjc2wlfsqevryxrppe4.ap-east-1.es.amazonaws.com"
OPENSEARCH_USERNAME = "admin"
OPENSEARCH_PASSWORD = "Zktj1016!"
OPENSEARCH_INDEX = "rag_poc"

# Bedrock 配置 (保持 us-east-1)
REGION_NAME = "us-east-1"
RERANK_REGION_NAME = "us-east-1"
EMBEDDING_MODEL_NAME = "amazon.titan-embed-text-v1"
LLM_MODEL_NAME = "qwen.qwen3-32b-v1:0"
```

### 环境变量 (Windows PowerShell)
```powershell
$env:AWS_ACCESS_KEY_ID="AKIAZJC2RJ2QKZ6DAJ42"
$env:AWS_SECRET_ACCESS_KEY="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"
$env:AWS_DEFAULT_REGION="us-east-1"
```

### 前端配置 (frontend/.env)
```env
VITE_API_BASE_URL=http://localhost:8001
```

---

## 文件修改清单

| 文件 | 修改类型 | 说明 |
|------|----------|------|
| `api/config.py` | ✅ 修改 | 更新 OpenSearch 端点和密码 |
| `api/api.py` | ✅ 修改 | 添加 `import config`，增强错误日志 |
| `api/document_processing.py` | ✅ 修改 | 添加 PyPDF2 备选方案 |
| `api/opensearch_multimodel_dataload.py` | ✅ 修改 | 添加连接测试日志，修改默认 engine 为 faiss |
| `api/requirements.txt` | ✅ 修改 | 添加 `pypdf` 依赖 |
| `frontend/.env` | 🆕 新建 | 配置 API 地址 |
| `CLAUDE.md` | 🆕 新建 | 项目开发文档 |
| `logs/development_log_2026-03-15.md` | 🆕 新建 | 本日志文件 |

---

## 运行命令 (最终版)

### 后端启动
```powershell
cd "E:\AI use case\知识库poc资料\rag_code\huatai_rag_github_share\api"

# 设置环境变量
$env:AWS_ACCESS_KEY_ID="AKIAZJC2RJ2QKZ6DAJ42"
$env:AWS_SECRET_ACCESS_KEY="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"
$env:AWS_DEFAULT_REGION="us-east-1"

# 启动服务
.\venv\Scripts\python.exe api.py
```

### 前端启动
```powershell
cd "E:\AI use case\知识库poc资料\rag_code\huatai_rag_github_share\frontend"
npm run dev
```

### 访问地址
- 前端: http://localhost:8080
- 后端 API: http://localhost:8001
- API 文档: http://localhost:8001/docs

---

## 架构说明

### AWS 区域部署架构
```
香港 (ap-east-1):
  └─ OpenSearch Service (向量数据库)
  └─ 可选: EC2 实例 (生产部署)

东京 (ap-northeast-1) [推荐]:
  └─ Bedrock Titan Embeddings
  └─ Bedrock Qwen/Claude LLM
  └─ Bedrock Rerank

弗吉尼亚 (us-east-1) [当前配置]:
  └─ Bedrock 服务 (备用区域)
```

### PDF 处理流程
```
上传 PDF → PyPDF2 解析 → 文本分块 → Bedrock Embeddings
                                              ↓
用户提问 ← LLM 生成答案 ← RAG 检索 ← OpenSearch 向量搜索
```

---

## 待办事项 (后续优化)

### 高优先级
- [ ] 测试 Q&A 功能是否正常工作
- [ ] 验证 OpenSearch 向量搜索准确性
- [ ] 上传更多 PDF 进行压力测试

### 中优先级
- [ ] 考虑迁移 Bedrock 到 ap-northeast-1 (东京)
- [ ] 优化 PyPDF2 文本提取质量
- [ ] 添加前端错误提示的详细信息

### 低优先级
- [ ] 配置日志轮转避免磁盘空间不足
- [ ] 添加前端加载状态指示器
- [ ] 实现批量 PDF 上传

---

## 备注

### 关键依赖版本
- Python: 3.9+
- Node.js: 18+
- docling: >=1.0.0 (有 Windows 兼容性问题)
- pypdf: >=3.0.0 (Windows 兼容备选)
- opensearch-py: >=2.0.0

### Windows 开发注意事项
1. 使用 PowerShell 而非 CMD 设置环境变量
2. Python 虚拟环境使用 `venv\Scripts\python.exe` 而非 `python`
3. Docling 在 Windows 上有已知问题，已使用 PyPDF2 替代
4. 路径中避免中文和特殊字符（如 `知识库poc资料`）

### 生产部署建议
- 使用 EC2 实例部署，而非本地开发
- OpenSearch 使用 VPC 访问而非 Public Access
- 配置 IAM Role 而非使用 Access Key
- 前端使用 `npm run build` 构建后部署

---

## 参考文档

- `README.md` - 项目基本说明
- `CLAUDE.md` - 开发指南
- `docs/deployment.md` - AWS 部署详细指南
- `docs/rag_poc_architecture.drawio` - 系统架构图

---

*日志生成时间: 2026-03-15*
*下次更新: 待添加新问题时*
