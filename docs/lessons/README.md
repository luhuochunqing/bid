# 经验沉淀索引

本目录记录开发过程中积累的根因分析、技术陷阱和工程教训。

## 目录结构

| 文件 | 类型 | 主题 | 日期 |
|------|------|------|------|
| `root-cause-analysis-co-259.md` | Bug 根因分析 | 标讯导入"总部所在地"字段值丢失 | 2026-06-18 |
| `root-cause-analysis-co-264.md` | Bug 根因分析 | CRM更换商机双提示+props不同步 | 2026-06-18 |
| `root-cause-analysis-co-266-co-267.md` | Bug 根因分析 | CRM推送客户信息字段名不一致导致前端不显示 | 2026-06-18 |
| `root-cause-analysis-co-274.md` | Bug 根因分析 | 标讯快速投标未创建项目导致列表不显示 | 2026-06-19 |
| `lessons-learned.md` | 通用工程教训 | 后端接口契约变更必须同步前端所有入口 | 2026-06-19 |
| `crm-integration-lessons.md` | 外部集成经验 | CRM 接口字段映射、405 事故、code 字段错填、status 枚举映射错位 | 2026-06-18 |
| `element-plus-gotchas.md` | 技术陷阱 | el-cascader 级联选择器与后端字符串字段转换 | 2026-06-18 |
| `vue-gotchas.md` | 技术陷阱 | Composable 中 ref 初始化与 props 同步 | 2026-06-18 |

## 如何使用

- **遇到 Bug 时**：先查阅 `root-cause-analysis-*.md`，看是否有类似根因
- **使用 Element Plus 组件时**：查阅 `element-plus-gotchas.md` 避免已知陷阱
- **沉淀新知识**：参考 `.claude/skills/knowledge-capture/` 下的模板
