# 文档目录 (docs/)

一旦我所属的文件夹有所变化，请更新我。

本目录包含西域数智化投标管理平台的完整文档体系，按职责分类管理。

## 目录结构

```
docs/
├── design-system/    # 设计系统规范（颜色、字体、组件）
├── artifacts/         # AI 生成的「活文档」(HTML/MD 格式)
├── specs/             # 最终确认的需求/规格文档
├── reports/           # 报告、总结、审查结果
├── architecture/      # 架构图、系统设计文档
├── research/          # 调研、方案对比、PoC
├── archives/          # 历史版本归档
├── assets/            # 图片、设计稿、共享资源
├── prototypes/        # 可交互原型 HTML
├── plans/             # 开发计划与设计稿 (活动)
└── release/           # 发布相关文档
```

## 各目录职责

| 目录 | 职责 | 维护频率 |
|------|------|----------|
| `design-system/` | 设计系统规范（颜色、字体、组件样式） | 中 |
| `artifacts/` | AI 生成的活文档，可直接在浏览器查看 | 低 |
| `specs/` | 最终确认的需求规格，是团队协作的事实源 | 高 |
| `reports/` | 阶段报告、审查结果、交付物清单 | 中 |
| `architecture/` | 架构设计、安全设计、测试策略 | 中 |
| `research/` | 调研文档、方案对比、技术选型 | 低 |
| `archives/` | 历史版本归档，只读不修改 | 低 |
| `assets/` | 共享资源文件 | 低 |
| `prototypes/` | 可交互原型 HTML | 低 |
| `plans/` | 开发计划与设计稿 | 高 |
| `release/` | 发布检查清单、验收文档、回滚手册 | 中 |

## 文档治理

- 本目录遵守 `DOCUMENTATION_GOVERNANCE.md` 中的治理规范
- `plans/` 目录存放近期开发计划，完成后应归档到 `archives/`
- 新增文档请放在对应职责目录，不要散落在根目录
- 图片等资源统一放在 `assets/` 目录

## 外部参考

- 项目 Wiki: `.wiki/pages/`
- 接口文档: `docs/specs/API_OPENAPI.md` (通过 Swagger/OpenAPI 生成)
