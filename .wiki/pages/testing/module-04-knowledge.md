---
title: 知识库 — 蓝图功能实现对照
space: engineering
category: testing
tags: [testing, 蓝图对照, 知识库, knowledge]
sources:
  - .wiki/sources/testing/module-04-knowledge-test.md
  - .wiki/sources/testing/module-04-knowledge-test.md
backlinks:
  - _index
created: 2026-05-28
updated: 2026-06-21
health_checked: 2026-06-21
---
> 蓝图章节：§4.4 知识库
> 对应飞书蓝图：https://my.feishu.cn/docx/FgLAdRmFho4QhwxncgAcfxKJn0d

## 覆盖度总览

| 蓝图功能 | 实现状态 | 测试方式 | 关键文件 |
|---------|---------|---------|---------|
| 方案管理(KbLayout Tab 导航) | ✅ 已完成 | 手动 | KbLayout.vue 统一 5 Tab 容器 |
| 项目档案-自动归档 | ✅ 已完成 | API + E2E | 过程中即时归档 |
| 项目档案-台账/筛选/导出 | ✅ 已完成 | API | Excel/ZIP 导出 |
| 案例库-CRUD | ✅ 已完成 | E2E + API | `POST /api/knowledge/cases` |
| 案例库-检索/筛选 | ✅ 已完成 | E2E | 关键词+多维度筛选 |
| 案例库-AI 推荐 | ✅ 已完成 | API | `GET /cases/{id}/related` |
| 案例库-置顶/优质/上下架 | ✅ 已完成 | API | `POST /pin`, `POST /off-shelf` |
| 模板库-6 大分类 | ✅ 已完成 | E2E | 7 个历史分类 Tab |
| 模板库-版本控制 | ✅ 已完成 | API | `GET /versions` |
| 模板库-使用统计 | ✅ 已完成 | API | `POST /use-records` |
| 模板库-一键应用 | ✅ 已完成 | E2E | 应用模板对话框 |
| 资质证书-CRUD | ✅ 已完成 | API | `POST /api/knowledge/qualifications` |
| 资质证书-AI 解析 | ✅ 已完成 | API | `POST /upload-parse` |
| 资质证书-借阅归还 | ✅ 已完成 | API | `POST /borrow`, `POST /return` |
| 资质证书-过期提醒 | ✅ 已完成 | API | `POST /scan-expiring` |
| 权限矩阵 | ✅ 已完成 | E2E | `@PreAuthorize` 全注解 |

## 功能 1：项目档案

### 蓝图要求
项目过程中文件即时归档，按阶段标签分类展示，台账导出、文件包导出。

### 实现说明
- 前端：`src/views/Knowledge/views/ProjectArchive.vue`
- 后端：`ProjectArchiveController`（`GET /api/archive` 分页，`POST /api/archive/export-excel`，`POST /archive/export-zip`）
- 归档服务：`ProjectArchiveWorkflowService` — 文件上传时即时归档
- 分类：按商务/技术/澄清/其他标签展示

### 测试方式
API 测试

### 测试示例
```bash
# 登录
TOKEN=$(curl -s -X POST 'http://127.0.0.1:18081/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 查询档案列表
curl -s 'http://127.0.0.1:18081/api/archive?page=0&size=10' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 导出 Excel 台账
curl -s -X POST 'http://127.0.0.1:18081/api/archive/export-excel' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{}' -o archive.xlsx
```

## 功能 2：案例库

### 蓝图要求
案例创建/编辑/检索/复用，置顶/优质标记，上架/下架/删除，AI 推荐案例。

### 实现说明
- 前端：`Case.vue`(传统案例库) + `CaseWrapper.vue`(统一容器) + `CaseDetail.vue`
- 后端：2 套控制器并行：
  - `CaseController`（`/api/knowledge/cases` — CRUD + 搜索 + 相关推荐）
  - `KnowledgeCaseController`（`/api/cases` — 置顶/上下架/网格查询）
- E2E: `e2e/case-advanced-flow.spec.js`

### 测试方式
E2E + API

### 测试示例
```bash
TOKEN=...

# 创建案例
curl -s -X POST 'http://127.0.0.1:18081/api/knowledge/cases' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"智慧园区项目案例","industry":"INFRASTRUCTURE","outcome":"WON","amount":500,"description":"案例描述","customerName":"测试客户","tags":["智慧园区"]}'

# 搜索案例
curl -s 'http://127.0.0.1:18081/api/knowledge/cases?keyword=智慧&industry=INFRASTRUCTURE' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# AI 相关推荐
curl -s 'http://127.0.0.1:18081/api/knowledge/cases/1/related?limit=5' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 置顶
curl -s -X POST 'http://127.0.0.1:18081/api/cases/1/pin' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## 功能 3：模板库

### 蓝图要求
6 大分类（实际 7 个 Tab），版本控制，使用次数统计，一键应用。

### 实现说明
- 前端：`src/views/Knowledge/Template.vue`
- 7 个历史分类 Tab：全部、技术方案、商务文件、实施方案、报价清单、资质文件、合同范本
- 三维分类：产品类型/行业/文档类型（`templatecatalog` 领域）
- 后端：`TemplateController` — 完整 CRUD + 版本 + 使用记录 + 复制
- E2E: `e2e/template-advanced-flow.spec.js`, `e2e/template-three-dimensional-flow.spec.js`

### 测试方式
E2E + API

### 测试示例
```bash
TOKEN=...

# 创建模板
curl -s -X POST 'http://127.0.0.1:18081/api/knowledge/templates' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"智慧城市技术方案","category":"TECHNICAL","productType":"智慧城市","industry":"政府","documentType":"技术方案","description":"适用于智慧城市类项目"}'

# 三维分类查询
curl -s 'http://127.0.0.1:18081/api/knowledge/templates?productType=智慧城市&industry=政府' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 版本历史
curl -s 'http://127.0.0.1:18081/api/knowledge/templates/1/versions' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 记录使用
curl -s -X POST 'http://127.0.0.1:18081/api/knowledge/templates/1/use-records' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"projectId":1,"documentName":"xxx项目技术方案"}' | python3 -m json.tool
```

## 功能 4：资质证书管理

### 蓝图要求
资质分类/录入/编辑/过期提醒/附件上传/检索、借阅归还流程。

### 实现说明
- 前端：`src/views/Knowledge/Qualification.vue`
- 后端：`QualificationController` — 完整 CRUD + 上传解析(AI) + 借阅归还
- 过期提醒：`ScanExpiringQualificationsAppService` + `AlertConfigController`
- 资质分类：BUSINESS / QUALIFICATION / CERTIFICATION / LICENSE / PATENT / OTHER

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 创建资质
curl -s -X POST 'http://127.0.0.1:18081/api/knowledge/qualifications' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"ISO9001认证","certificateNo":"ISO-2025-001","issuer":"中国质量认证中心","holderName":"西域公司","expiryDate":"2027-06-01","category":"QUALIFICATION"}'

# 查询资质
curl -s 'http://127.0.0.1:18081/api/knowledge/qualifications?category=QUALIFICATION&keyword=ISO' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 扫描过期资质
curl -s -X POST 'http://127.0.0.1:18081/api/knowledge/qualifications/scan-expiring?thresholdDays=30' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 借阅
curl -s -X POST 'http://127.0.0.1:18081/api/knowledge/qualifications/1/borrow' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"borrower":"张三","department":"投标部","purpose":"投标使用"}'

# 归还
curl -s -X POST 'http://127.0.0.1:18081/api/knowledge/qualifications/1/return' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"returner":"张三","remark":"已用完归还"}'
```

---

## 相关文件
- E2E: `e2e/case-advanced-flow.spec.js`, `e2e/template-advanced-flow.spec.js`, `e2e/template-three-dimensional-flow.spec.js`, `e2e/document-editor-case-knowledge.spec.js`
- 前端: `src/views/Knowledge/`
- 后端: `backend/src/main/java/com/xiyu/bid/qualification/`, `casework/`, `template/`
