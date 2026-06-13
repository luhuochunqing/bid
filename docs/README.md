# 文档目录 (docs/)

西域数智化投标管理平台的原始文档库 / 事实源仓库。

> ⚠️ **重要**：本目录是"写入"的地方（放原始素材和交付物）。  
> **查阅知识**请走 `.wiki/pages/_index.md`（那里有结构化的双空间知识库，支持全文检索和自动索引）。

## 目录结构

```
docs/
├── design-system/    # 设计系统规范（颜色、字体、组件）
├── artifacts/         # AI 生成/整理的「活文档」(HTML/MD 格式)
├── specs/             # 最终确认的需求/规格文档
├── reports/           # 报告、总结、审查结果
├── architecture/      # 架构图、系统设计文档（最终态走 wiki 合成）
├── research/          # 调研、方案对比、PoC
├── archives/          # 历史版本归档（只读不修改）
│   ├── plans-2026-03/ # 2026年3月已完成开发计划
│   ├── plans-2026-04/ # 2026年4月已完成开发计划
│   ├── plans-2026-05/ # 2026年5月已完成开发计划
│   └── ...
├── assets/            # 图片、设计稿、共享资源
├── prototypes/        # 可交互原型 HTML
├── plans/             # 活跃中的开发计划（历史已完成计划已归档到 archives/）
├── release/           # 发布相关文档
├── integration/       # 系统集成对接文档
├── issues/            # 问题排查记录
├── testing/           # 测试用例与 UAT 记录
└── governance/        # 治理规范
```

## 各目录职责

| 目录 | 职责 | 维护频率 |
|------|------|----------|
| `design-system/` | 设计系统规范（颜色、字体、组件样式） | 中 |
| `artifacts/` | AI 生成/整理的活文档（讲标/演示等最终产物） | 低 |
| `specs/` | 最终确认的需求规格，是团队协作的事实源 | 高 |
| `reports/` | 阶段报告、审查结果、交付物清单 | 中 |
| `architecture/` | 架构设计、安全设计、测试策略 | 中 |
| `research/` | 调研文档、方案对比、技术选型 | 低 |
| `archives/` | 历史版本归档（含已完成计划），只读不修改 | 低 |
| `assets/` | 共享资源文件 | 低 |
| `prototypes/` | 可交互原型 HTML | 低 |
| `plans/` | 开发计划与设计稿（仅保留活跃中/未完成的计划） | 高 |
| `release/` | 发布检查清单、验收文档、回滚手册 | 中 |
| `integration/` | 外部系统对接设计文档 | 中 |
| `issues/` | 问题排查与 Bug 修复记录 | 中 |
| `testing/` | 测试用例（manual cases 和 UAT 记录） | 中 |
| `governance/` | 异步治理规范 | 低 |

## 文档治理

- 分层协议详见 `docs/specs/WIKI.md` §11（docs 原始文档库 vs .wiki 合成知识库）
- `plans/` 目录存放**活跃中**的开发计划；已完成计划一律归档到 `archives/`
- 新增原始文档请放在对应职责目录，不要散落在根目录
- 图片等资源统一放在 `assets/` 目录
- 最终态文档应通过 `npm run wiki:ingest` + `wiki:build` 合成为知识页面

## 外部参考

- **项目 Wiki 知识库**: `.wiki/pages/_index.md`（建议优先浏览）
- 接口文档: `docs/specs/`
- 开发计划: `docs/plans/`（活跃） / `docs/archives/`（历史）
- 治理规范: `docs/specs/WIKI.md`
