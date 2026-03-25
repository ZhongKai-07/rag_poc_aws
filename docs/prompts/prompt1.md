# 接力 Prompt — RAG 问答链路调试（2026-03-22）

## 背景

这是一个 Python → Java Spring Boot 后端迁移项目。12 个迁移 task 已全部完成，当前处于 post-migration runtime 调试阶段。

**必读文档**（按优先级）：

- `CLAUDE.md` — 全局上下文和规则
- `Documentation.md` — 最新进展和决策记录
- `Plan.md` — 里程碑状态和决策历史
- `Implement.md` — 开发规则和运行时注意事项
- `Prompt.md` — 目标、约束和完成标准

## 当前状态

### 已解决

1. **CORS** — 添加了 `CorsConfig.java`，前端可以正常请求后端
2. **Frontend .env** — 创建了 `frontend/.env`，`VITE_API_BASE_URL=http://localhost:8001`
3. **OpenSearch 索引映射** — `ensureIndex()` 重写为用 `GET /_mapping` 作为唯一真相源（3-state enum: `VALID`/`INVALID`/`NOT_FOUND`），DELETE 容忍 404
4. **Bedrock API** — `BedrockAnswerGenerationAdapter` 从 `invokeModel` 切换到 `converse` API（第三方模型如 Qwen 只支持 `converse`）
5. **默认值** — `application.yml` 和 `RagProperties.java` 的默认 answer model 改为 `qwen.qwen3-vl-235b-a22b`，bedrock-region 改为 `us-west-2`
6. **数据清理** — PostgreSQL 三张表（`document_file`、`ingestion_job`、`question_history`）和 OpenSearch 用户索引已全部清空，干净状态

### 未解决 — RAG 问答链路仍然失败

最后一次错误仍然是 `ValidationException: The provided model identifier is invalid`。

**关键线索**：虽然代码已改为 `converse` API + `qwen.qwen3-vl-235b-a22b`，但用户的 IDE 启动配置**没有加载** **`.env`** **文件**中的全部环境变量。从启动日志第 1 行可以看到：

- `RAG_ANSWER_MODEL_ID` — **缺失**（会回退到 `application.yml` 默认值）
- `BEDROCK_REGION` — 显示 `us-east-1`（但 `.env` 里是 `us-west-2`，`application.yml` 默认值已改为 `us-west-2`）
- `S3_DOCUMENT_BUCKET` — **缺失**

已经把 `application.yml` 和 `RagProperties.java` 的默认值改成了正确的模型和区域，但用户尚未用最新代码重新编译和重启后端验证。

## 下一步行动

1. **确认代码已编译**：`mvn -f backend-java/pom.xml "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" compile`
2. **重启 Java 后端**，检查启动日志第 1 行确认环境变量是否完整
3. **上传一个测试文件**，检查日志中应出现：
   - `Index XXXXXXXX does not exist, creating with knn_vector mapping (dimension=1536)`
   - `Index XXXXXXXX created successfully`
4. **执行 RAG 问答**，如果仍然报错：
   - 如果是 `model identifier is invalid` — 检查 Bedrock 在 `us-west-2` 是否真正支持 `qwen.qwen3-vl-235b-a22b`（可能需要在 AWS Bedrock 控制台启用模型访问）
   - 如果是 `AccessDeniedException` — 模型存在但当前 IAM 用户没有权限
   - 如果是 OpenSearch `401 Unauthorized` — 之前 curl 直接认证也偶现 401（bash `!` 转义问题），用 `curl -H "Authorization: Basic dGVzdDpaa3RqMTAxNiE="` 测试
5. **如果模型问题持续**，参考 Python 基线 `api/llm_processor.py`：Python 用 `litellm.completion(model="bedrock/qwen.qwen3-235b-a22b-2507-v1:0")` 成功调用过，可以尝试把
   Java 模型 ID 改回 `qwen.qwen3-235b-a22b-2507-v1:0`（注意：这个 ID 在 `invokeModel` 上失败过，但现在 Java 用的是 `converse` API，可能可以工作）

## 关键文件

| 文件                                                                           | 作用                          |
| :--------------------------------------------------------------------------- | :-------------------------- |
| `backend-java/src/main/java/.../bedrock/BedrockAnswerGenerationAdapter.java` | 答案生成，已改为 `converse` API     |
| `backend-java/src/main/resources/application.yml`                            | 所有配置默认值                     |
| `backend-java/src/main/java/.../config/RagProperties.java`                   | Java 默认值                    |
| `backend-java/src/main/java/.../config/ClientConfig.java`                    | AWS 客户端创建（region 绑定）        |
| `backend-java/src/main/java/.../opensearch/OpenSearchIndexManager.java`      | 索引创建/校验                     |
| `backend-java/src/main/java/.../opensearch/OpenSearchRetrievalAdapter.java`  | 搜索（曾遇 401）                  |
| `backend-java/.env`                                                          | 运行时环境变量（IDE 可能不加载）          |
| `api/llm_processor.py`                                                       | Python 基线的 Bedrock 调用方式（参考） |

## 约束提醒

- 42 个测试全部通过，不要破坏
- 不要修改前端或 Python 后端
- 每次修复后更新 `Prompt.md`、`Plan.md`、`Implement.md`、`Documentation.md`
- OpenSearch Basic Auth base64: `dGVzdDpaa3RqMTAxNiE=`（`test:Zktj1016!`）

