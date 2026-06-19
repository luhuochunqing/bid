# 通用工程教训与复盘

> 本文件记录跨模块、可复用的工程教训与流程改进，按 session 追加章节。

---

## 1. 后端接口契约变更必须同步前端所有入口

### 问题背景

CO-274 中，V130 评估表重构后 `/api/tenders/{id}/bid` 被设计为「评估-审核后创建项目」，要求请求时标讯已存在 `TenderEvaluation`。但前端标讯详情页的「投标」按钮仍走快速投标流程（`participate → bid`），该流程不会提交评估表，导致 `/bid` 返回 404 并被静默吞掉，项目未创建。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 后端 `/bid` 契约变更后，前端仍按旧流程调用 | 任何后端接口新增前置条件时，必须梳理前端所有调用方 | 变更接口契约时，在 PR 描述中列出所有前端调用点并逐一验证 |
| 前端 `catch {}` 吞掉关键错误 | 核心业务错误不应静默处理 | 对「创建项目」等关键操作，必须向用户反馈失败或降级处理 |
| 同一功能存在两条差异路径 | 列表页和详情页「投标」入口行为不一致，导致测试覆盖遗漏 | 同一业务动作尽量统一入口；无法统一时，两套路径都要覆盖 |

### 操作规范（建议固化到 CLAUDE.md / RULES.md）

1. 后端接口新增 `orElseThrow` / 前置校验时，必须在 PR 中标注「前端调用点影响范围」。
2. 前端对关键写操作禁止空 `catch`；至少记录日志、上报埋点或弹出错误提示。
3. 一个业务动作存在多个前端入口时，每条入口都应有对应的集成测试或 E2E。

### 验证命令

```bash
# 检查前端是否有空 catch 吞掉关键 API 错误
grep -R "catch {\s*}" src/views/Bidding src/views/Project
# 期望输出：无关键路径上的空 catch

# 检查 /bid 调用方是否覆盖两种入口
grep -R "proceedToBid" src/api src/views
```

### 相关文档

- `docs/lessons/root-cause-analysis-co-274.md` — 完整根因分析
- `docs/exec-plans/tech-debt-tracker.md` — 相关技术债登记

---

## 2. 前端热更新部署时不能只保留 index.html 引用的文件

### 问题背景

2026-06-19 部署前端更新时，清理旧 assets 脚本只保留了 `index.html` 直接引用的 7 个文件，但 Vite 入口 JS 通过动态 import() 引用了大量 chunk 文件（如 `expensePageShared-*.js`、`MainLayout-*.js` 等）。这些 chunk 被误删后，用户访问页面时报 404，页面白屏。

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 清理脚本只检查 index.html 引用 | Vite 动态 import 的 chunk 不在 index.html 中 | 前端部署必须保留完整 dist 目录 |
| 先清理旧文件再部署新文件 | 清理和部署顺序错误 | 先部署新文件 → 验证通过 → 再清理旧版本 |
| 缺少部署后验证 | 未检查动态 chunk 是否完整 | 部署后检查 assets 目录文件数是否与本地 dist 一致 |

### 正确做法

```bash
# 方式 1：直接覆盖整个 dist 目录（推荐热更新）
rm -rf /srv/www/xiyu-bid/*
cp -R dist/. /srv/www/xiyu-bid/

# 方式 2：版本化目录切换（推荐正式发布）
ln -sfn /opt/xiyu-bid/releases/<hash>/frontend /srv/www/xiyu-bid
```

### 验证命令

```bash
# 本地 dist 文件数
ls dist/assets/ | wc -l

# 服务器文件数（应一致）
ssh jetty@172.16.38.78 'ls /srv/www/xiyu-bid/assets/ | wc -l'
```

### 相关文档

- `docs/lessons/root-cause-analysis-frontend-404.md` — 完整根因分析
