<!-- 一旦我所属的文件夹有所变化，请更新我。 -->
# 运营与协作文档 (docs/operations/)

> 本目录存放项目运营、交接、协作流程等文档。

## 目录结构

```
docs/operations/
├── README.md                          ← 你在这里（目录说明）
├── HANDOFF.md                         ← 工作交接文档
├── WORKFLOW.md                        ← 工作流说明
├── MULTI_AGENT_SOP.md                 ← 多 Agent 协作 SOP
└── logging-bug-investigation-guide.md ← 日志系统查 Bug 手册
```

## 各文件职责

| 文件 | 职责 | 维护频率 |
|------|------|----------|
| `HANDOFF.md` | 工作交接规范与模板 | 低 |
| `WORKFLOW.md` | 项目工作流与流程说明 | 中 |
| `MULTI_AGENT_SOP.md` | 多 Agent 协作 SOP | 中 |
| `logging-bug-investigation-guide.md` | 日志系统查 Bug 手册 | 中 |

## 维护声明

- 本目录文件由项目管理者维护
- 如有文件增删，请同步更新本 README
- 最终态文档应通过 `npm run wiki:ingest` + `wiki:build` 合成为知识页面

## 相关链接

- [项目导航地图](../../../AGENTS.md)
- [执行入口](../../../CLAUDE.md)
- [项目规则](../../../RULES.md)
