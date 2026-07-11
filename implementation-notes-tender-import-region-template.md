# 标讯批量导入模板「总部所在地」示例格式修复 — 实施笔记

> 分支：`agent/kimi/fix-pr2020-systemadmin-frontend`（在既有任务分支上顺手修）  
> 问题：下载模板示例写 `北京市-北京市`，与字典/校验/前端推荐的 `北京市北京市` 不一致。

## 复现结论（改前）

| 检查项 | 结果 |
|---|---|
| 模板示例行地区 | `北京市-北京市`（旧市-市） |
| 字典参考 | 仅推荐 `北京市北京市` 等一级+二级拼接 |
| `isValid("北京市-北京市")` | true（兼容旧数据，并非直接失败） |
| `isValid("广东省-深圳市")` | false（用户照示例学「用连字符」会挂） |
| 直接导入模板自带示例行 | 通过（直辖市旧格式仍兼容） |

用户感知「必须写成无连字符才能导入」主要来自：
1. 错误提示与字典只展示推荐格式；
2. 普通省若写成 `省-市` 会失败；
3. 模板示例教的是带 `-` 的旧写法。

## 改动

1. **`TenderImportTemplateBuilder.EXAMPLE_ROW`**：`北京市-北京市` → `北京市北京市`
2. **`TenderImportServiceTest.exampleRow()`**：同步推荐格式
3. **新增回归测试** `templateExampleRowRegionUsesRecommendedConcatFormat`：断言示例行 = `北京市北京市` 且不含 `-`
4. **`BulkImportDialog.vue` 提示**：补充总部所在地填写口径（一级+二级、无连字符）
5. **前端单测**同步断言提示文案

## 未改（刻意）

- **`TenderRegionCatalog` 仍兼容** `北京市-北京市` / 单名 `北京市`：历史数据与旧 Excel 不因本次模板修正而报废。
- **`TenderRequestValidationTest` 等兼容性用例**保留，证明旧格式仍可校验通过。

## 权衡

- 只改「教什么」（示例 + UI 提示），不收紧白名单：导入成功率不倒退，UX 与 CO-381 统一口径对齐。
- 若未来要彻底废弃连字符格式，需另开任务：迁移存量 `region` + 从白名单移除旧格式 + 公告期。

## 验证

```bash
cd backend && mvn test -Dtest=TenderImportServiceTest
npx vitest run src/views/Bidding/list/components/BulkImportDialog.spec.js
```
