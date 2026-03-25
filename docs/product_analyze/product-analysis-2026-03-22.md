# RAG 产品分析：OPS 合规知识库（2026-03-22）

## 背景

当前为 OPS 部门构建内部知识库试点，业务部门将知识文档直接入库，不做预处理。未来将接入更多部门和团队。

### 场景定义

**场景：OPS/COB 合规制度与流程问答**

| 维度 | 要求 |
|------|------|
| 用户群 | EST/EQD/FICC 内部职员 + OPS COB 团队 |
| 查询量 | ~100 次/天 |
| 内容 | AML/KYC 制度守则、操作手册 |
| 文件类型 | PDF/Word/Excel，含流程图，本期不含图片 |
| 权限 | 第一阶段全员可访问所有文档，DLS 为未来多租户预留 |

---

## 一、当前系统能力全景

### RAG 链路（截至 2026-03-22 已端到端验证）

```
上传 → S3存储 → BDA解析 → Titan嵌入 → OpenSearch索引
查询 → Titan嵌入 → KNN/BM25/MIX检索 → Cohere Rerank → Qwen生成
```

| 组件 | 实现 |
|------|------|
| 文档存储 | AWS S3 |
| 文档解析 | AWS BDA（Bedrock Data Automation） |
| 嵌入模型 | `amazon.titan-embed-text-v1`（1536维） |
| 向量数据库 | AWS OpenSearch（per-file 独立 index） |
| 重排模型 | `cohere.rerank-v3-5:0` |
| 答案模型 | `qwen.qwen3-235b-a22b-2507-v1:0`（us-west-2，converse API） |
| 元数据持久化 | PostgreSQL（DocumentFileRecord, IngestionJobRecord） |
| 问题历史 | PostgreSQL（QuestionHistoryEntity） |

---

## 二、产品适配度分析：OPS 合规场景

### 2.1 文档解析（BDA）

| 文件类型 | BDA 支持 | 合规场景适配性 | 风险等级 |
|----------|----------|----------------|----------|
| PDF（文字型） | 成熟 | 好 | 低 |
| PDF（扫描件） | 依赖 OCR | AML 旧文件可能是扫描件 | 中 |
| Word（.docx） | 支持 | 一般 | 低 |
| Excel（.xlsx） | 弱 | **合规台账、操作清单核心场景** | 高 |
| 流程图（嵌入 PDF/Word） | 不提取 | 操作手册的关键步骤在流程图里 | 高 |

**Excel 是最大盲点。** AML/KYC 的"客户风险评级矩阵"、"操作审批清单"通常是 Excel 表格，BDA 平铺处理后表格的行列语义完全丢失，检索结果毫无意义。

**建议：** 试点上线前用 3-5 份真实 Excel 合规文档跑一遍，人工验证检索结果是否可用，否则应在界面上标注"Excel 文档检索效果有限"。

流程图内容丢失是无法绕过的限制。AML/KYC 操作手册中流程图往往承载了最核心的步骤信息（如 STR 申报流程），用户问"XX 流程的第三步是什么"时系统无法回答。需要在文档上线前主动告知用户这一限制。

### 2.2 检索质量

**嵌入模型中文语义风险：**

Titan v1 的中文语义理解能力有限，AML/KYC 文档全中文，具体表现：
- "什么情况需要做增强尽调" → 可能召回不到含 "EDD" 简写的段落
- 监管术语的同义词覆盖弱（"可疑交易报告"/"STR"/"大额交易报告"）
- 中英文混排的操作手册（如 "根据 KYC Policy Section 3.2"）召回不稳定

建议评估 `amazon.titan-embed-text-v2:0`（多语言能力更强）或 `cohere.embed-multilingual-v3` 作为替代。

**MIX 检索合并策略：**

当前 MIX 模式的合并是简单去重（`putIfAbsent`），向量候选和 BM25 候选没有分数归一化，进入 Rerank 的候选集质量取决于哪种方式先放进去。对合规场景，精确关键词命中（如"第四条"、"7 个工作日内"）比语义相似更可靠，BM25 权重应该更高。建议引入 RRF（Reciprocal Rank Fusion）做分数融合。

### 2.3 答案生成

**Prompt 对合规场景未定制：**

当前 `PromptTemplateFactory` 生成通用 RAG prompt。合规场景需要特定约束：
1. **引用原文，不要总结**：合规答案必须来源可溯，用户需要知道"这句话在哪一条"
2. **强制拒答边界**：不在知识库中的问题必须明确说"未找到相关规定"，绝对不能 hallucinate 一个"可能的合规规定"
3. **多文档冲突处理**：同一问题在不同版本文档中规定不同时，LLM 需要识别并提示用户

**低置信度答案风险：**

当前仅 `rerankedDocuments.isEmpty()` 时返回 `NO_DOCS_FALLBACK`，但 Rerank 分数低于阈值（0.5）却非空时仍会生成答案。对合规场景这是风险点，需增加低置信度时的 fallback 阈值。

### 2.4 可溯源性（最关键的产品缺口）

当前 `SourceDocumentDto` 只有 `pageContent`、`score`、`rerankScore`，**没有文件名和页码**。

```java
// 当前 SourceDocumentDto - 缺失来源信息
pageContent: String
score: double
rerankScore: Double
// 缺失: filename, pageNumber, sectionTitle
```

OpenSearch 里实际存储了 `metadata` 字段（BDA 解析结果中包含页码信息），但当前 API 响应丢弃了这些 metadata。

这是**合规场景的 P0 缺口**：监管要求员工给出答案的来源依据，用户无法接受"系统说是这样的"而没有原文引用。

---

## 三、扩展性分析：从 OPS 到多部门

### 3.1 当前架构的扩展瓶颈

| 维度 | 当前设计 | 多部门扩展的问题 |
|------|----------|-----------------|
| 索引命名 | `md5(filename)[:8]` | 无部门/租户命名空间，不同部门的同名文件会冲突 |
| 查询入口 | 前端传 `indexNames` 列表 | 用户需要知道"哪些文件"才能查询，不适合大规模内容库 |
| 用户身份 | 无 | 请求链路中没有 Principal，无法做访问控制 |
| 问题历史 | 存 `indexName + question` | 无用户 ID，无法做个人历史或部门级分析 |
| 文件目录 | `directoryPath` 字段 | 有路径概念但未做权限隔离 |

### 3.2 多部门扩展核心能力

**第一层：命名空间隔离**
```
当前: md5("AML_Policy.pdf")[:8] = "4c408463"
需要: {tenant_id}_{md5("AML_Policy.pdf")[:8]} = "ops_4c408463"
```
或使用 OpenSearch index alias 做逻辑隔离。此改动需同步修改 `IndexNamingPolicy`、`ensureIndex()` 以及跨部门联查的聚合逻辑。

**第二层：用户身份上下文**
```java
// 当前 QueryCommand 缺少 userId/tenantId
record QueryCommand(
    String sessionId,
    List<String> indexNames,  // 需要变成"按权限过滤后的 indexNames"
    ...
)
```
引入后，`DocumentRegistryPort.listVisibleIndices(userId)` 可以替代前端传入 indexNames，实现服务端访问控制。

**第三层：Document-Level Security**

OpenSearch 原生支持 DLS（Document-Level Security）和 FLS（Field-Level Security），可以在 index 级别绑定 role。当前 per-file 独立 index 的设计天然适合这个模型，这是架构上做对的一个决策。

### 3.3 演进路径

```
阶段 0（当前）: OPS 单部门，全员访问，无 DLS
        ↓
阶段 1（多部门接入）: 引入 tenantId 命名空间 + 服务端 indexNames 过滤
        ↓
阶段 2（分级访问）: OpenSearch DLS + 用户角色绑定
        ↓
阶段 3（个性化）: 个人问题历史 + 部门级知识库分析
```

阶段 0→1 的改动量最大，且需要迁移存量索引。**应在 OPS 试点稳定后立即规划，避免上线后做破坏性重构。**

---

## 四、运营与监控缺口

当前系统是无可观测性的黑盒，对 AI 产品来说是严重问题：

| 缺口 | 影响 |
|------|------|
| 无答案质量反馈机制 | 不知道哪些问题回答错了 |
| 无检索命中率统计 | 不知道哪些文档没被用到 |
| 无 token 消耗统计 | 无法预估 Bedrock 费用 |
| 无慢查询告警 | BDA 轮询超时无感知 |
| 问题历史无趋势分析 | `top_questions` API 有但无时序统计 |

对 100次/天的内部工具，最小可行的监控是：**问题 + 答案 + 用户主观评分（👍👎）的日志落库**，用于人工抽查答案质量，这是 AI 产品冷启动阶段最重要的质量反馈回路。

---

## 五、优先级汇总

### P0（上线必须，否则产品不可信）

1. **答案来源引用**：在 `sourceDocuments` 中暴露 `filename` + `pageNumber`（metadata 已在 OpenSearch 中，需在 DTO 层透传）
2. **低置信度拒答**：Rerank 分数低于阈值但非空时，增加兜底策略而非强行生成答案
3. **Excel 文档实测**：用真实合规 Excel 验证检索质量，上线前确认或明确告知用户限制

### P1（试点期间必须解决）

4. **Prompt 合规化**：添加"引用原文条款"和"禁止推断"的 system instruction
5. **嵌入模型中文评估**：用 20 个典型合规问题评测 Titan v1 vs v2 的召回率，决定是否切换
6. **用户反馈机制**：最简单的 👍👎 + 落库，为后续质量优化建立数据基础

### P2（多部门扩展前必须完成）

7. **tenantId 命名空间**：在 `IndexNamingPolicy` 中引入部门前缀，迁移方案同步设计
8. **服务端 indexNames 过滤**：用户只能查自己有权限的索引，不依赖前端传参控制权限
9. **用户身份上下文**：在 `QueryCommand` 和 `IngestionCommand` 中引入 `userId/tenantId`

### P3（中期路线图）

10. OpenSearch DLS + 角色绑定（真正的 document-level security）
11. 问题历史个性化 + 部门级热词分析
12. BDA 超时的异步降级（上传变为异步 + 状态轮询接口）
13. MIX 检索引入 RRF（Reciprocal Rank Fusion）分数融合

---

## 六、总结判断

当前系统是一个**工程质量合格、产品完整度 60%** 的 RAG 基线。对 OPS 内部试点，技术管道已通，但上线前必须补齐答案可溯源性和合规场景的 Prompt 约束，否则用户第一次拿到一个无来源的合规答案后信任度会直接崩塌。

多部门扩展的**最大技术债**是索引命名没有 tenantId 命名空间——这个设计债如果不在 OPS 试点阶段就还清，等到第二个部门接入时会是一次破坏性迁移。建议在试点稳定后 2 周内完成命名空间方案设计，锁定架构决策。
