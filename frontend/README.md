# Frontend 前端文档

基于 React + TypeScript + Vite 构建的研报问答系统前端，提供 PDF 上传和 AI 问答两个核心功能页面。

## 技术栈

- **React 18** + **TypeScript**
- **Vite** — 构建工具，开发端口 8080
- **React Router v6** — 客户端路由
- **Tailwind CSS** — 样式
- **Shadcn/ui** — UI 组件库（基于 Radix UI）
- **TanStack Query** — 服务端状态管理
- **lucide-react** — 图标库

---

## 文件结构

```
frontend/
├── src/
│   ├── App.tsx                  # 路由配置入口
│   ├── main.tsx                 # React 挂载点
│   ├── index.css                # 全局样式 / Tailwind 变量
│   ├── pages/
│   │   ├── Index.tsx            # 首页（导航入口）
│   │   ├── Upload.tsx           # PDF 上传页面
│   │   ├── QA.tsx               # 问答页面
│   │   └── NotFound.tsx         # 404 页面
│   ├── components/
│   │   ├── NavLink.tsx          # 导航链接组件
│   │   └── ui/                  # Shadcn/ui 组件（button, card, select 等）
│   ├── hooks/
│   │   ├── use-toast.ts         # Toast 通知 hook
│   │   └── use-mobile.tsx       # 移动端检测 hook
│   └── lib/
│       ├── api.ts               # API 请求封装
│       └── utils.ts             # 工具函数（cn 类名合并）
├── .env                         # 本地开发环境变量
├── .env.production              # 生产环境变量模板
├── vite.config.ts               # Vite 配置
├── tailwind.config.ts           # Tailwind 配置
├── tsconfig.json                # TypeScript 配置
└── package.json                 # 依赖和脚本
```

---

## 快速启动

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:8080`，确保后端已在 `http://localhost:8001` 运行。

---

## 环境变量配置

**本地开发（.env）**
```bash
VITE_API_BASE_URL=http://localhost:8001
```

**生产部署（.env.production）**
```bash
VITE_API_BASE_URL=http://your-server-ip:8001
```

所有 `VITE_` 前缀的变量会被 Vite 注入到客户端代码，在代码中通过 `import.meta.env.VITE_API_BASE_URL` 访问。

---

## 页面说明

### / — 首页（Index.tsx）
导航入口页，提供两个卡片分别跳转到上传页和问答页，并简要说明系统使用流程。

---

### /upload — 上传页面（Upload.tsx）

**功能：**
- 点击上传区域选择一个或多个 PDF 文件（仅支持 PDF）
- 自动生成按时间戳命名的存储目录（格式：`./documents/YYYY-MM-DD-HH-MM`）
- 逐个上传文件，调用后端 `/upload_files` 接口处理
- 上传处理期间显示 loading 状态
- 页面底部展示所有已处理文件列表

**上传流程：**
```
选择 PDF 文件
    │
    ▼
生成目录路径（按当前时间戳）
    │
    ▼
逐个 POST /upload_files
    │  body: FormData { file, directory_path }
    │
    ▼
后端处理完成后刷新已处理文件列表
```

---

### /qa — 问答页面（QA.tsx）

**功能：**
- 从下拉菜单选择已处理的文档
- 输入自然语言问题
- 调整搜索参数（相似度阈值、召回文档数、搜索模式）
- 展示 AI 生成的答案，自动滚动到答案位置
- 可展开查看召回的原始文档片段，支持图片渲染

**搜索参数说明：**

| 参数 | 范围 | 说明 |
|------|------|------|
| Similarity Threshold | 0.0 ~ 1.0 | Rerank 分数过滤阈值，越高越严格 |
| Number of Related Docs | 1 ~ 5 | 向量搜索召回的文档数量 |
| Search Mode | mix / vector / text | mix = 向量+关键词混合；vector = 纯向量；text = 纯关键词 |

**问答流程：**
```
选择文档 + 输入问题
    │
    ▼
POST /rag_answer
    │  body: { session_id, index_name, query, search_method, ... }
    │
    ▼
展示答案 + 自动滚动
    │
    ▼
可展开查看 Source Documents
    │  每条显示 Recall Score + Rerank Score + 原文内容
    │  内容中的 base64 图片自动渲染为 <img>
```

---

## 与后端的接口对应

| 前端操作 | 调用接口 | 说明 |
|----------|----------|------|
| 页面加载时获取文件列表 | `GET /processed_files` | Upload 和 QA 页面都会调用 |
| 上传 PDF | `POST /upload_files` | multipart/form-data |
| 提交问题 | `POST /rag_answer` | JSON body |

所有请求的 base URL 来自 `VITE_API_BASE_URL` 环境变量。

---

## 常用脚本

```bash
npm run dev        # 启动开发服务器（端口 8080）
npm run build      # 构建生产版本，输出到 dist/
npm run preview    # 本地预览构建产物
npm run lint       # ESLint 检查
npm run test:run   # 运行单元测试（单次，非 watch 模式）
```

---

## 路径别名

`@` 映射到 `src/`，例如：

```ts
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
```

---

## 生产部署

```bash
# 设置生产 API 地址
echo "VITE_API_BASE_URL=http://your-server-ip:8001" > .env.production

# 构建
npm run build

# dist/ 目录即为静态产物，可用 Nginx 或 npm run preview 托管
npm run preview -- --host 0.0.0.0 --port 5173
```
