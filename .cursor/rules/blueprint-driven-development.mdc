---
name: blueprint-driven-development
description: >
  按飞书产品蓝图逐章节实现标讯中心功能（小节粒度，如 4.2.1）。
  触发：做标讯、按蓝图、实现蓝图、差距分析、对标、检查小节、接着上次、继续做。
  Phase 2 差距分析必须字段级 grep 验证，禁止只 grep 文件名或读 commit message。
  含检查点契约、记忆追踪。
---

# 蓝图标讯中心实现工作流

> **SKILL.md 是路由中心，不是百科全书。** 详细内容按需加载到 references/ 下的对应文件。

---

## 文件清单

```
blueprint-driven-development/
├── SKILL.md                        ← 你在这里（路由中心）
├── config.example.json             ← 用户配置模板
├── skill-metadata.yml             ← 平台兼容性
├── scripts/
│   ├── get-tokens.sh              ← 获取各角色 token
│   ├── memory-log.sh              ← 记忆读写（append-only JSONL）
│   └── verify-page.js             ← Playwright 验证辅助函数库
├── data/
│   └── gap-analysis-log.jsonl     ← 记忆存储（append-only）
└── references/
    ├── setup.md                   ← 首次使用配置流程
    ├── gotchas.md                 ← 常踩的坑（最高优先级参考）
    ├── phase-1.md                 ← Phase 1 蓝图分析
    ├── phase-2.md                 ← Phase 2 差距分析（最关键）
    ├── phase-3.md                 ← Phase 3 实现
    ├── phase-4.md                 ← Phase 4 验证
    ├── phase-5.md                 ← Phase 5 验收提交
    ├── memory.md                  ← 记忆系统用法
    ├── gap-analysis-examples.md   ← 差距分析格式模板
    ├── role-permissions.md         ← 角色权限矩阵
    ├── playwright-verification.md  ← Playwright 使用参考
    ├── troubleshooting.md          ← 故障排查
    └── service-startup.md         ← 服务启动
```

---

## 检查点契约

**文件位置**：`<worktree-root>/.blueprint-checkpoint.json`

```bash
WORKTREE=$(git rev-parse --show-toplevel 2>/dev/null || echo "$PWD")
CHECKPOINT="$WORKTREE/.blueprint-checkpoint.json"
cat "$CHECKPOINT" 2>/dev/null || echo '{"phase":1,"phases_done":[]}'
```

| 阶段 | 前置条件 | 后置产出 |
|------|---------|---------|
| **Phase 1** — 蓝图分析 | phase=1 | implementation-notes.md |
| **Phase 2** — 差距分析 | phase=2 + phase-1 done | gap-analysis.md |
| **Phase 3** — 实现 | gap-analysis.md 存在 | mvn compile + npm build 通过 |
| **Phase 4** — 验证 | Phase 3 通过 | verify-roles.md + 截图 |
| **Phase 5** — 验收提交 | Phase 4 通过 | git commit |

> 如果 phase > 1，直接跳到对应 Phase 继续。上次进度不丢失。

---

## 快速命令参考

```bash
# ── 启动 ─────────────────────────────────────────────
# 读取配置，检查/创建检查点
source .claude/skills/blueprint-driven-development/scripts/memory-log.sh read --recent 3

# ── Phase 1 ─────────────────────────────────────────
# 参考：references/phase-1.md

# ── Phase 2 ─────────────────────────────────────────
# 每次开始前必读
# references/gotchas.md
# references/phase-2.md
# references/gap-analysis-examples.md

# 完成后记录
bash scripts/memory-log.sh append "4.2.1" "gap-analysis.md" "缺字段"

# ── Phase 3 ─────────────────────────────────────────
# 参考：references/phase-3.md
mvn compile -q && npm run build

# ── Phase 4 ─────────────────────────────────────────
# 参考：references/phase-4.md
bash scripts/get-tokens.sh --verbose
# references/role-permissions.md
# references/playwright-verification.md

# ── Phase 5 ─────────────────────────────────────────
# 参考：references/phase-5.md
```

---

## 何时加载哪个 references 文件

| 场景 | 加载文件 |
|------|---------|
| 首次使用 / 缺失配置 | `references/setup.md` |
| Phase 2 开始前 | `references/gotchas.md` + `references/phase-2.md` |
| 写 gap-analysis 格式 | `references/gap-analysis-examples.md` |
| Phase 4 角色验证 | `references/role-permissions.md` |
| Phase 4 写 Playwright | `references/playwright-verification.md` |
| 服务启动失败 | `references/troubleshooting.md` |
| 需要记忆历史 | `references/memory.md` |
| 状态卡住了 | `references/gotchas.md`（找对应 G 条目） |

---

## 核心原则（必须遵守）

1. **Phase 2 必须字段级验证** — `grep 完整声明`，不是 `grep 字段名`
2. **禁止引用 commit message** — 必须 `grep 代码 + 实际输出`
3. **gap-analysis.md 是本 skill 最重要的输出** — 每个结论附带验证命令
4. **每次 Phase 2 完成后记录记忆** — `memory-log.sh append`
5. **Gotchas 踩坑后追加** — 格式见 `references/gotchas.md` 末尾

---

## 关键约束

- 检查点 phase=0 = 本小节全部完成
- Phase 3 进入前必须 gap-analysis.md 存在
- Phase 4 必须有 Playwright 页面截图作为证据
- 权限变更必须配 Flyway 迁移脚本
