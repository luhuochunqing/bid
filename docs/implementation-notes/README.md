<!-- 一旦我所属的文件夹有所变化，请更新我。 -->
# 实现笔记 (docs/implementation-notes/)

> 本目录存放特定任务/功能点的实现笔记与临时设计记录。

## 目录结构

```
docs/implementation-notes/
├── README.md                              ← 你在这里（目录说明）
├── CO-212.md                              ← CO-212 实现笔记
├── project-permission-audit.md            ← 项目权限审计笔记
└── role-permission-audit.md               ← 角色权限审计笔记
```

## 各文件职责

| 文件 | 职责 | 维护频率 |
|------|------|----------|
| `CO-212.md` | CO-212 相关实现细节记录 | 低 |
| `project-permission-audit.md` | 项目权限审计过程记录 | 低 |
| `role-permission-audit.md` | 角色权限审计过程记录 | 低 |

## 维护声明

- 本目录存放临时实现笔记，完成后可考虑归档到 `docs/archives/`
- 如有文件增删，请同步更新本 README
- 最终态文档应通过 `npm run wiki:ingest` + `wiki:build` 合成为知识页面

## 相关链接

- [项目导航地图](../../../AGENTS.md)
- [执行入口](../../../CLAUDE.md)
- [项目规则](../../../RULES.md)
