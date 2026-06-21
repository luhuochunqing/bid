---
title: 标讯中心 — 蓝图功能实现对照
space: engineering
category: testing
tags: [testing, 蓝图对照, 标讯中心, bidding]
sources:
  - .wiki/sources/testing/module-02-bidding-test.md
  - .wiki/sources/testing/module-02-bidding-test.md
backlinks:
  - _index
created: 2026-05-28
updated: 2026-06-21
health_checked: 2026-06-21
---
> 蓝图章节：§4.2 标讯中心
> 对应飞书蓝图：https://my.feishu.cn/docx/FgLAdRmFho4QhwxncgAcfxKJn0d

## 覆盖度总览

| 蓝图功能 | 实现状态 | 测试方式 | 关键文件 |
|---------|---------|---------|---------|
| 角色权限矩阵 | ✅ 已完成 | API + E2E | `@PreAuthorize` 全控制器注解 |
| 标讯状态流转(7状态) | ✅ 已完成 | API + E2E | `bidding-utils-status.js` |
| 标讯列表(筛选/排序/分页) | ✅ 已完成 | API + E2E | `List.vue`, `GET /api/tenders` |
| 标讯创建(人工录入) | ✅ 已完成 | API | `ManualTenderDialog.vue`, `POST /api/tenders` |
| 标讯创建(CRM转入) | ✅ 已完成 | API | `TenderImportService` |
| 标讯创建(第三方自动拉取) | ✅ 已完成 | API | `TenderSourceController`, `SourceConfigDialog.vue` |
| 批量导入(Excel) | ✅ 已完成 | API | `BulkImportDialog.vue`, `POST /import` |
| 标讯详情 | ✅ 已完成 | API + E2E | `DetailPage.vue`(3 tabs) |
| 标讯评估 | ✅ 已完成 | API | `TenderEvaluationForm.vue`, `POST /evaluation/submit` |
| 标讯立项 | ✅ 已完成 | API | `POST /api/tenders/{id}/bid` |
| 分配与转派 | ✅ 已完成 | API | `DistributeDialog.vue`, `POST /transfer` |
| 编辑/删除 | ✅ 已完成 | API | 表格操作菜单，软删除 |
| 标讯源配置 | ✅ 已完成 | API | `SourceConfigDialog.vue`, `SourceStatusCard.vue` |
| 市场洞察(趋势分析) | ✅ 已完成 | API | `GET /api/market-insight/insight` |
| AI评分匹配 | ✅ 已完成 | E2E + API | `MatchScorePanel.vue`, `AIAnalysis.vue` |
| 采购方规律分析 | ✅ 已完成 | 手动 | 商机中心已启用 |

## 功能 1：角色权限矩阵

### 蓝图要求
四类角色（投标管理员/组长/项目负责人/投标专员）各有不同的数据可见范围和操作权限。

### 实现说明
后端全控制器使用 `@PreAuthorize` 注解；前端 `useTenderListPage.js` 中 `buildPermissionFlags()` 控制 UI 操作可见性。

### 测试方式
E2E 测试（`e2e/settings-permission-effect.spec.js`）+ API

### 测试示例
```bash
# 登录
TOKEN=$(curl -s -X POST 'http://127.0.0.1:18081/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 查看标讯列表(ADMIN — 全量)
curl -s 'http://127.0.0.1:18081/api/tenders?page=0&size=10' \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json;d=json.load(sys.stdin)
print(f'总记录数: {d[\"data\"][\"totalElements\"]}')"
```

## 功能 2：标讯状态流转

### 蓝图要求
7 状态枚举：待分配→跟踪中→已评估→投标中→已中标→未中标→已放弃，各状态有标签颜色和操作权限。

### 实现说明
- 状态定义：`bidding-utils-status.js` — 7 个状态 + 对应标签样式
- 状态变更：`POST /evaluation/submit`, `POST /bid`, `POST /abandon`

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 标讯评估提交(待分配→跟踪中→已评估)
curl -s -X POST 'http://127.0.0.1:18081/api/tenders/1/evaluation/submit' \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"evaluationResult":"PASS","remark":"评估通过"}' | python3 -m json.tool

# 标讯立项(已评估→投标中)
curl -s -X POST 'http://127.0.0.1:18081/api/tenders/1/bid' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## 功能 3：标讯列表

### 蓝图要求
多条件组合筛选（状态/来源/类型/负责人/日期范围/优先级）、排序、分页、导出。

### 实现说明
- 前端：`List.vue` → `TenderSearchCard.vue` + `TenderTable.vue`
- 后端：`GET /api/tenders` 支持 `TenderSearchCriteria`（多条件+排序+分页）
- 导出：`GET /api/tenders/export` — Excel 导出（筛选条件下全量数据）

### 测试方式
E2E（`e2e/bidding-list-detail-ai-flow.spec.js`）+ API

### 测试示例
```bash
TOKEN=...

# 多条件筛选查询
curl -s 'http://127.0.0.1:18081/api/tenders?status=TRACKING&sourceType=MANUAL&page=0&size=20&sort=createdAt,desc' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## 功能 4：批量导入(Excel)

### 蓝图要求
下载 Excel 模板、文件上传、行级校验、失败反馈。

### 实现说明
- 前端：`BulkImportDialog.vue` — 下载模板、上传文件、进度展示
- 后端：`GET /import-template` + `POST /import`（行级校验 + 去重）

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 下载模板
curl -s -o template.xlsx 'http://127.0.0.1:18081/api/tenders/import-template' \
  -H "Authorization: Bearer $TOKEN"

# 上传导入
curl -s -X POST 'http://127.0.0.1:18081/api/tenders/import' \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@template.xlsx" | python3 -m json.tool
```

## 功能 5：分配与转派

### 蓝图要求
自动分配规则（按地区/类型/负载）、手动分配、转派流程。

### 实现说明
- 前端：`DistributeDialog.vue` + `AssignDialog.vue` + `TransferDialog.vue`
- 后端：`POST /api/tenders/{id}/transfer` — 转派接口

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 转派标讯
curl -s -X POST 'http://127.0.0.1:18081/api/tenders/1/transfer' \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"assigneeId":2,"remark":"转派给刘俊杰跟进"}'
```

## 功能 6：市场洞察与 AI 评分

### 蓝图要求
AI 智能评分(0-100)、风险等级判定、采购方规律分析、市场趋势。

### 实现说明
- 市场洞察：`GET /api/market-insight/insight`（趋势/规律/预测 3 维度）
- AI 评分：`POST /api/tenders/{id}/match-score/evaluate` + `GET /latest` + `GET /history`
- 前端：`MarketInsightDialog.vue` + `MatchScorePanel.vue`

### 测试方式
E2E（`e2e/bidding-list-detail-ai-flow.spec.js`）+ API

### 测试示例
```bash
TOKEN=...

# AI评分
curl -s -X POST 'http://127.0.0.1:18081/api/tenders/1/match-score/evaluate' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 市场洞察
curl -s 'http://127.0.0.1:18081/api/market-insight/insight' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

---

## 相关文件
- E2E: `e2e/bidding-list-detail-ai-flow.spec.js`, `e2e/customer-opportunity-center.spec.js`
- 前端: `src/views/Bidding/`
- 后端: `backend/src/main/java/com/xiyu/bid/tender/`
