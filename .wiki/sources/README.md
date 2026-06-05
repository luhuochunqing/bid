# 原始资料 / Raw Sources

> 本目录存放项目原始资料，遵循“源文件不可变、仅追加版本”的原则。

## 混合摄入模式（默认）

1. 优先直接放入原始文件（docx/xlsx/pdf/图片/md）
2. 运行 `npm run wiki:ingest` 自动抽取到 `.wiki/extracts/`
3. 抽取状态为 `manual_review` 时，补充人工 Markdown 再次执行 ingest

## 分类目录

- `bidding/`
- `contract/`
- `industry/`
- `competitor/`
- `customer/`
- `technical/`
- `internal/`
- `implementation/`

