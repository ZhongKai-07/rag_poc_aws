# Worktree Cleanup & Push Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前 worktree（`ff34`）中积累的所有未整理工作清理并推送到 GitHub，不破坏任何已有工作成果。

**Architecture:** 当前 HEAD 处于 detached 状态，先创建分支锚定工作，再按逻辑分组提交未 commit 的变更，最后推送到远程。不做 squash，保留完整历史。

**Tech Stack:** Git, Bash

---

## 当前状态说明

```
Worktree ff34: detached HEAD @ 657ad1ad
├── 已 commit，未 push：34 个 commits（Spring Boot 迁移 + BDA 可观测性全部在此）
├── 未 commit 的修改（M）：16 个 backend-java 基础设施文件 + 5 个控制文档
├── 未 commit 的新文件（??）：7 个 backend-java 新文件 + 多个 docs/dev_logs
└── 文件移动（D→??）：docs/*.drawio 已移到 docs/drawio/，git 未感知
```

**绝对不要 commit 的文件：**
- `frontend/node_modules/` — 构建产物
- `NEWTON.zip`, `NEWTON/` — 数据文件
- `error_logs/` — 运行时日志
- `test_pdf/` — 测试数据
- `prompt/` — 内部提示文件
- `.trae/` — IDE 配置
- `.claude/settings.json` — Claude Code 本地设置
- `backend-java/src/main/resources/aws_config.txt` — 可能含 AWS 凭据，**禁止提交**

---

## Chunk 1：分支创建 + 文件移动

### Task 1：从 detached HEAD 创建分支

**目的：** 锚定所有已有 commits，防止 HEAD 被覆盖丢失。

- [ ] **Step 1: 创建并切换到新分支**

```bash
cd "C:/Users/zhong kai/.codex/worktrees/ff34/huatai_rag_github_share"
git checkout -b feature/spring-boot-migration-complete
```

预期输出：`Switched to a new branch 'feature/spring-boot-migration-complete'`

- [ ] **Step 2: 确认分支已创建且 HEAD 正确**

```bash
git branch -v
git log --oneline -3
```

预期：显示 `* feature/spring-boot-migration-complete  657ad1ad fix: harden...`

---

### Task 2：提交 docs/drawio 文件移动

**背景：** 图表文件从 `docs/` 移到 `docs/drawio/`，但 git 不知道——`docs/*.drawio` 显示为已删除（D），`docs/drawio/` 显示为未追踪（??）。需要同时 add 新位置，让 git 识别为 rename。

- [ ] **Step 1: 验证文件确实在 docs/drawio/ 中**

```bash
ls docs/drawio/
```

预期：列出 13 个 .drawio 和 .png 文件。

- [ ] **Step 2: 同时 add 删除和新增，让 git 识别 rename**

```bash
git add docs/drawio/
git add -u docs/
```

- [ ] **Step 3: 确认 git 识别为 rename**

```bash
git diff --cached --name-status | grep "^R"
```

预期：显示类似 `R100  docs/RAG_System_Architecture.drawio  docs/drawio/RAG_System_Architecture.drawio`。
如果显示为 D + A 而非 R 也没问题，效果等同。

- [ ] **Step 4: Commit**

```bash
git commit -m "refactor: move drawio diagrams to docs/drawio/"
```

---

## Chunk 2：backend-java 变更

### Task 3：提交 backend-java 基础设施修复（已修改文件）

**背景：** 这些是 2026-03-21 至 2026-03-22 间 Java 后端运行时调试阶段应用的修复，涵盖 Bedrock/OpenSearch/BDA/Config 适配器。

- [ ] **Step 1: 检查 aws_config.txt 内容，确认不含凭据**

```bash
cat backend-java/src/main/resources/aws_config.txt
```

如果含有 `aws_access_key_id`、`aws_secret_access_key` 等字段，**跳过此文件不添加**。

- [ ] **Step 2: Stage 基础设施修复文件**

```bash
git add \
  backend-java/README.md \
  backend-java/src/main/java/com/huatai/rag/infrastructure/bda/BdaClient.java \
  backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/BedrockAnswerGenerationAdapter.java \
  backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/BedrockRerankAdapter.java \
  backend-java/src/main/java/com/huatai/rag/infrastructure/bedrock/PromptTemplateFactory.java \
  backend-java/src/main/java/com/huatai/rag/infrastructure/config/AwsProperties.java \
  backend-java/src/main/java/com/huatai/rag/infrastructure/config/ClientConfig.java \
  backend-java/src/main/java/com/huatai/rag/infrastructure/config/RagProperties.java \
  backend-java/src/main/java/com/huatai/rag/infrastructure/config/StorageProperties.java \
  backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchDocumentWriter.java \
  backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchIndexManager.java \
  backend-java/src/main/java/com/huatai/rag/infrastructure/opensearch/OpenSearchRetrievalAdapter.java \
  backend-java/src/main/java/com/huatai/rag/infrastructure/persistence/entity/DocumentFileEntity.java \
  backend-java/src/main/resources/application-local.yml \
  backend-java/src/main/resources/application.yml \
  backend-java/src/test/java/com/huatai/rag/infrastructure/bedrock/BedrockAdapterWiringTest.java
```

- [ ] **Step 3: Commit**

```bash
git commit -m "fix: apply post-migration runtime fixes to infrastructure adapters"
```

---

### Task 4：提交 backend-java 新增文件

**背景：** 这些文件是迁移过程中新增的，尚未追踪。

- [ ] **Step 1: 检查 aws_config.txt（若 Task 3 Step 1 已检查可跳过）**

确认不含凭据后决定是否加入。如果含凭据则跳过，只提交其他文件。

- [ ] **Step 2: Stage 新增文件（不含 aws_config.txt）**

```bash
git add \
  backend-java/.env.example \
  backend-java/diagnose-aws.sh \
  backend-java/src/main/java/com/huatai/rag/infrastructure/config/CorsConfig.java \
  backend-java/src/main/java/com/huatai/rag/infrastructure/storage/S3DocumentStorageAdapter.java \
  backend-java/src/test/java/com/huatai/rag/infrastructure/config/ \
  backend-java/src/test/java/com/huatai/rag/infrastructure/storage/
```

- [ ] **Step 3: 确认没有意外 stage 凭据文件**

```bash
git diff --cached --name-only | grep -i "secret\|credential\|key\|aws_config"
```

预期：无输出。如果有输出立即执行 `git reset HEAD <file>` 撤销。

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add CorsConfig, S3 storage adapter, env example, and AWS diagnostics"
```

---

## Chunk 3：文档变更

### Task 5：提交迁移控制文档更新

**背景：** `CLAUDE.md`、`Documentation.md`、`Plan.md`、`Implement.md`、`Prompt.md` 反映了 2026-03-22 运行时验证后的最新迁移状态。

- [ ] **Step 1: Stage 控制文档**

```bash
git add CLAUDE.md Documentation.md Plan.md Implement.md Prompt.md
```

- [ ] **Step 2: Commit**

```bash
git commit -m "docs: update migration control documents to reflect 2026-03-22 runtime verification"
```

---

### Task 6：提交文档新增内容

**背景：** 代码审查记录、架构文档、产品分析、开发日志、新计划文件。

- [ ] **Step 1: Stage 文档**

```bash
git add \
  docs/ccodeReview/ \
  docs/java-backend-architecture.md \
  docs/product_analyze/ \
  docs/superpowers/plans/2026-03-23-bda-observability.md \
  docs/superpowers/plans/2026-03-19-migration-cutover-checklist.md \
  docs/superpowers/plans/2026-03-23-newton-ui-refactor.md \
  dev_logs/
```

- [ ] **Step 2: Commit**

```bash
git commit -m "docs: add code review notes, architecture docs, plans, and dev logs"
```

---

### Task 7：处理旧日志删除

**背景：** `logs/development_log_2026-03-15.md` 显示为已删除（D），对应内容已迁移到 `dev_logs/`。

- [ ] **Step 1: 确认该文件确实不再需要**

```bash
ls dev_logs/
```

确认 dev_logs/ 中有替代文件后继续。

- [ ] **Step 2: Stage 删除**

```bash
git add logs/development_log_2026-03-15.md
```

- [ ] **Step 3: Commit**

```bash
git commit -m "chore: remove old development log (superseded by dev_logs/)"
```

---

## Chunk 4：推送到 GitHub

### Task 8：推送分支到远程

- [ ] **Step 1: 确认当前分支和所有 commits**

```bash
git log --oneline origin/main..HEAD | wc -l
```

预期：显示约 40 个 commits（34 个已有 + 本次新增约 6 个）。

- [ ] **Step 2: 推送新分支到 GitHub**

```bash
git push -u origin feature/spring-boot-migration-complete
```

预期：显示 `Branch 'feature/spring-boot-migration-complete' set up to track remote branch`。

- [ ] **Step 3: 确认 GitHub 上已有该分支**

```bash
git branch -vv
```

预期：`* feature/spring-boot-migration-complete  [...] [origin/feature/spring-boot-migration-complete]`

---

### Task 9（可选）：合并到主仓库 main

> ⚠️ 此步骤在主仓库目录（`E:/AI use case/RAG-poc/rag_code/huatai_rag_github_share`）中执行，不在 worktree 中。
> 需要先确认主仓库 main 没有冲突。仅在你决定切换到 Java 后端为主线时执行。

- [ ] **Step 1: 切换到主仓库**

```bash
cd "E:/AI use case/RAG-poc/rag_code/huatai_rag_github_share"
git log --oneline -3
```

确认这是主仓库的 main 分支。

- [ ] **Step 2: 拉取远程最新**

```bash
git fetch origin
```

- [ ] **Step 3: 合并 feature 分支**

```bash
git merge feature/spring-boot-migration-complete --no-ff -m "chore: merge spring-boot migration and BDA observability into main"
```

- [ ] **Step 4: 推送 main 到 GitHub**

```bash
git push origin main
```

---

## 验收检查

执行完成后确认：

```bash
# 在 worktree ff34 中：
git status | grep -v "node_modules" | grep -v "^$"
# 预期：只剩不需要 commit 的文件（NEWTON/, error_logs/ 等）

git log --oneline origin/feature/spring-boot-migration-complete..HEAD
# 预期：空（本地和远程已同步）

git log --oneline origin/main..feature/spring-boot-migration-complete | wc -l
# 预期：约 40 个 commits
```
