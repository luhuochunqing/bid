# 基础设施层 README

一旦我所属的文件夹有所变化，请更新我。

> 维护声明：本目录承载跨模块复用的基础工具。
> 一旦本目录结构、职责或对外契约发生变化，请同步更新本文与所属文件夹的 md。

## 目录结构

```
infrastructure/
├── README.md
└── excel/                          # Excel 通用读取工具
```

## 文件清单

| 文件 | 功能 |
| --- | --- |
| `README.md` | 本目录索引与维护说明 |
| `excel/SingleSheetExcelReader.java` | 通用单 Sheet Excel 读取器（POI 封装），用于批量导入；各导入模块通过依赖注入复用 |

## 后续补充

- 当前仅含 `excel/SingleSheetExcelReader.java`；如新增导出、流式解析、单元格类型适配等通用能力，请归入 `excel/` 子包并在本 README 添加条目。
