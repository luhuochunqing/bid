---
title: Agent Wiki 运行规范
space: engineering
category: governance
tags: [wiki, schema, governance, ai-first]
sources:
  - .wiki/WIKI.md
  - AGENTS.md
backlinks:
  - AGENTS.md
  - .wiki/pages/_index.md
created: 2026-07-23
updated: 2026-07-23
health_checked: 2026-07-23
---

# Agent Wiki 运行规范（Schema 配置层）

> 本文件是 Agent 维护知识库的**行为宪法**，决定 AI 智能体如何从"带文件访问权限的聊天机器人"
> 升级为"纪律严明的知识维护者"。AGENTS.md 是项目导航地图，本文件是 Wiki 子系统的运行规范。
>
> **核心转变**：从"查询时检索"（问到才 grep 代码）→ "摄入时编译"（代码/Wiki 一变就重新编译页面，
> 查询时直接读现成的高密度结构化页面）。

## 1. 三层模型

| Layer | 路径 | 职责 | 所有者 |
|---|---|---|---|
| **L1 原始资源** | `.wiki/sources/` + `.wiki/extracts/` | 原始素材 + 抽取中间层（Office/PDF → Markdown） | 脚本自动 |
| **L2 Wiki 知识** | `.wiki/pages/` | Agent 拥有的合成页面（架构图/API/逻辑流程/交叉索引） | Agent |
| **L3 Schema 配置** | `.wiki/WIKI.md`（本文件） + `AGENTS.md` | 工作流、组织结构、触发器 | Maintainer |

**铁律**：
- L1 只读，Agent 不直接编辑
- L2 完全由 Agent 拥有，每次有新结论必须回填
- L3 是行为宪法，修改需同步更新 AGENTS.md 索引

## 2. 四个硬触发点（不可绕过）

### 触发点 1：任务收尾时

`scripts/agent-finish-task.sh` 第 5 步「知识沉淀」**前**插入 wiki checkpoint：

- **判断**：本次任务有没有产生新结论 / 新根因 / 新陷阱 / 新业务规则？
- **有** → 强制要求：
  1. 写入 `.wiki/pages/<topic>.md`（含 frontmatter + backlinks）
  2. 更新 `.wiki/pages/_index.md` 索引
  3. 追加 `.wiki/log.md` 一条记录
- **无** → 跳过（避免噪声，但需在 log.md 写一行 `## [日期] no-op | <task> 无新结论`）

**判断标准**（任一满足即"有"）：
- 修了 ≥1 个 bug，且根因不是简单笔误
- 涉及跨模块逻辑变更（≥3 个文件）
- 产生了新的业务规则或约束
- 发现了之前 wiki 没记录的陷阱/边界条件
- 完成 PR 后，PR 描述里的"变更类型"包含"架构调整"或"根因修复"

### 触发点 2：PR 创建时

`scripts/pr-create.sh` 的 PR body 模板追加勾选项：

```
## Wiki 更新
- [ ] 已更新相关 .wiki/pages/ 页面 + _index.md + log.md
- [ ] 本次无需更新（纯文档/配置/重构无新逻辑）
```

不强制阻塞，靠 Agent 自律勾选。但 PR 审查者应重点检查"无需更新"的合理性。

### 触发点 3：复杂问题答完后（复合查询回填）

**复合查询的定义**（任一满足）：
- 涉及 ≥3 个文件
- 跨模块逻辑（前端+后端 / 多个 Service / Controller+Entity）
- 根因分析（5 Whys / 故障链）
- 涉及外部系统集成（OSS / CRM / 企微 / 标讯 / 组织架构 SDK）

**回填流程**：
1. 回答完问题后，判断：**这个问题下次还会被问到吗？**
2. 是 → 把结论写成新 `.wiki/pages/<topic>.md`：
   - 含完整 frontmatter（title/space/category/tags/sources/backlinks/updated/health_checked）
   - 使用列表/表格/结构化段落，不写散文
   - 标注跨模块副作用（RAG 最容易丢的部分）
3. 更新 `.wiki/pages/_index.md` 索引（追加新页面链接）
4. 追加 `.wiki/log.md` 一条记录：`## [日期] compound | <topic> 复合查询回填`

### 触发点 4：pre-push 时

`scripts/pre-push-gate.sh` 新增 `wiki-health-check` 子项：

- 调用 `scripts/wiki-check.mjs`
- **仅检查当前 PR 改动相关页面**（不全量扫历史债）
- 前 2 周（至 2026-08-06）：仅 warning 不阻塞 push
- 2 周后：转 error，强制阻塞 push
- 逃生阀：`WIKI_CHECK_MODE=warning` 临时降级（需 PR 描述说明理由）
- 逃生阀：`WIKI_CHECK_SKIP=1` 完全跳过（仅限紧急修复）

## 3. AI-First 编写标准

### 3.1 不要写优美散文

| 反模式 | 正确做法 |
|---|---|
| "这个服务主要负责处理用户的登录请求，它会验证..." | "AuthService.login() 职责：1. 验证密码 2. 颁发 JWT 3. 写审计日志" |
| 长段落描述 | 列表 + 表格 + 结构化段落 |
| "可以通过以下方式..." | 代码块 + 注释副作用 |

### 3.2 维护关联性

- 每个实体（Service/Controller/规则/外部系统）应有独立页面
- 在被引用处使用 `[[文件名]]` 双向链接格式
- frontmatter 的 `backlinks` 字段必须列出所有反向引用本页的文件

### 3.3 重点记录副作用

RAG 检索最容易丢的部分，必须显式标注：

```markdown
## 副作用（Cross-Module Impact）

- **AuthService.login()** 会触发：
  - AuditService.writeLoginEvent()（异步，失败不阻塞登录）
  - WeComSyncService.syncUser()（仅 OSS 用户触发，本地用户跳过）
- **数据一致性**：JWT 颁发后 5 秒内 Redis 缓存可能未生效，需考虑降级路径
```

### 3.4 frontmatter 必填字段

```yaml
---
title: <页面标题>
space: engineering | implementation
category: guide | reference | playbook | pitfalls | lessons
tags: [<主题>, <子主题>]
sources:
  - <源文件相对路径>
backlinks:
  - <引用本页的文件>
created: YYYY-MM-DD
updated: YYYY-MM-DD
health_checked: YYYY-MM-DD  # 必须 ≤7 天内
---
```

- `updated` 超过 30 天 → wiki-check 报错
- `health_checked` 超过 7 天 → wiki-check 报错
- `sources` 列出的文件必须真实存在

## 4. 标准操作流程（Operations）

### 4.1 摄入与编译（Ingest）

当检测到原始资源变化时（新源文件加入 `.wiki/sources/`），执行两阶段扫描：

**阶段 A：结构化扫描**
- 分析入口文件、配置清单、CI 流程
- 更新"项目骨架"页面（`.wiki/pages/overview.md` / `architecture.md` / `modules.md`）

**阶段 B：语义扫描**
- 深入分析路由、API 端点、核心服务类、数据库 Schema
- 编译成面向模型阅读的结构化 Markdown
- 侧重：精确的上下文、清晰的标题层级、高频交叉引用

**执行命令**：
```bash
npm run wiki:ingest  # 摄入新源文件
npm run wiki:build   # 编译生成页面
```

### 4.2 知识自检（Lint）

定期对 `.wiki/pages/` 执行 Lint 检查：

| 检查项 | 触发时机 | 命令 |
|---|---|---|
| 冲突检测（新代码 vs 旧 Wiki） | pre-push + 手动 | `npm run wiki:check` |
| 陈旧清理（过期页面标记） | 每周 + 手动 | `npm run wiki:fix` |
| 孤岛修复（补双向引用） | 每周 + 手动 | `npm run wiki:fix` |

**手动全量体检**：
```bash
node scripts/wiki-check.mjs
```

**自动修复建议**：
```bash
npm run wiki:fix
```

### 4.3 复合查询（Query & Compound）

回答问题时，**优先检索 Wiki 层**，不要重新推导原始代码：

```bash
bash scripts/wiki-search.sh "<关键词>"
```

若发现 Wiki 中缺失某个复杂问题的答案，在推导出结论后，**将其作为新的 Wiki 页面回填**，
使知识产生复合效应。详见"触发点 3"。

## 5. 规模化处理

- 当 Wiki 页面超过 100 个时（当前 60+），向用户建议开启 `qmd` 混合检索模式
- 始终区分"语料库知识"（代码是什么）与"用户记忆"（用户偏好）：
  - 本 Wiki 仅负责前者（代码/架构/规则/陷阱）
  - 用户偏好走 `~/.trae-cn/memory/user_profile.md`
  - 项目约束走 `~/.trae-cn/memory/projects/<project>/project_memory.md`

## 6. 常见反模式（必须避免）

| 反模式 | 后果 | 正确做法 |
|---|---|---|
| 任务做完不回填 wiki | 下次同样问题重新推导 | 任务收尾必过 wiki checkpoint |
| 写散文不写结构化 | RAG 检索精度低 | 列表/表格/代码块 |
| 不记副作用 | 跨模块变更漏改 | 显式标注 Cross-Module Impact |
| 改了代码不改 wiki | wiki 与代码矛盾 | pre-push wiki-check 拦截 |
| 全量扫描历史债 | 一次性铺开过大 | 增量检查 + 单独 sprint 清存量 |
| 双轨维护（.wiki + .agent_wiki） | 双向漂移 | 单一真相源：`.wiki/` |

## 7. 相关文档

- [[_index]] — Wiki 首页导航
- [[agent-sop-quickref]] — Agent 开发 SOP 快速参考
- [[engineering-discipline]] — 工程纪律手册
- [[lessons-learned]] — 工程经验总结
- [[multi-agent-defense-playbook]] — 多 Agent 并行开发防御工程化手册

## 8. 变更日志

- **2026-07-23**：初版，建立 Agent Wiki 运行规范（Schema 配置层）。基于"摄入时编译"理念，
  替代"查询时检索"模式。引入 4 个硬触发点 + AI-First 编写标准 + 复合查询回填机制。
  解决"wiki 有了但不维护"的根因（架构搭好但纪律未建立）。
