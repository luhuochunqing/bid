---
title: 投标项目 — 蓝图功能实现对照
space: engineering
category: testing
tags: [testing, 蓝图对照, 投标项目, project]
sources:
  - .wiki/sources/testing/module-03-project-test.md
  - .wiki/sources/testing/module-03-project-test.md
backlinks:
  - _index
created: 2026-05-28
updated: 2026-06-21
health_checked: 2026-06-27
---
> 蓝图章节：§4.3 投标项目
> 对应飞书蓝图：https://my.feishu.cn/docx/FgLAdRmFho4QhwxncgAcfxKJn0d

## 覆盖度总览

| 蓝图功能 | 实现状态 | 测试方式 | 关键文件 |
|---------|---------|---------|---------|
| 项目列表(筛选/排序/导出) | ✅ 已完成 | E2E + API | `List.vue`, `GET /api/projects`, `GET /export` |
| 项目创建(从标讯/手动) | ✅ 已完成 | E2E + API | `Create.vue`(4步表单), `POST /api/projects` |
| 项目详情(6 阶段 Tab) | ✅ 已完成 | E2E | `Detail.vue` → `ProjectDetailShell.vue` |
| Tab1-项目立项 | ✅ 已完成 | API | `InitiationStage.vue`, `InitiationController` |
| Tab2-标书制作 | ✅ 已完成 | E2E | `DraftingStage.vue`, 含 AI 拆解/合规检查 |
| Tab3-评标中 | ✅ 已完成 | API | `EvaluationStage.vue` |
| Tab4-结果确认 | ✅ 已完成 | API | `ResultConfirmStage.vue`(4 种结果) |
| Tab5-项目复盘 | ✅ 已完成 | API | `RetrospectiveStage.vue`(中标/未中标不同表单) |
| Tab6-项目结项 | ✅ 已完成 | API | `ClosureStage.vue`(保证金闭环) |
| 任务看板(4 阶段) | ✅ 已完成 | E2E | 待办/进行中/审核/完成 |
| AI 拆解任务 | ✅ 已完成 | E2E | `POST /api/projects/{id}/tasks/decompose` |
| 合规性检查 | ✅ 已完成 | E2E | 双引擎：合规+质量 |
| 结果录入 | ✅ 已完成 | API | 中标/未中标/流标/弃标 |
| AI 生成复盘案例 | 🟡 部分完成 | 手动 | 依赖 AI Provider 真实配置 |

## 功能 1：项目列表

### 蓝图要求
多维度筛选（状态/类型/负责人/日期）、排序、分页、字段展示、导出。

### 实现说明
- 前端：`src/views/Project/List.vue`
- 后端：`GET /api/projects` — 支持多条件筛选
- 导出：`GET /api/projects/export` — Excel 导出

### 测试方式
E2E（`e2e/project-detail-layout.spec.js`）+ API

### 测试示例
```bash
# 登录
TOKEN=$(curl -s -X POST 'http://127.0.0.1:18081/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 查询项目列表
curl -s 'http://127.0.0.1:18081/api/projects?page=0&size=10&sort=createdAt,desc' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## 功能 2：项目创建

### 蓝图要求
从标讯立项自动创建、手动创建，项目基本信息字段。

### 实现说明
- 前端：`src/views/Project/Create.vue` — 4 步表单（BasicInfo, Detail, AiAssist, Task）
- 后端：`POST /api/projects` — 项目创建
- 标讯预填：`createTenderPrefill.js`

### 测试方式
E2E（`e2e/project-create-prefill.spec.js`）

### 测试示例
```bash
TOKEN=...

# 从标讯创建项目
curl -s -X POST 'http://127.0.0.1:18081/api/projects' \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tenderId":1,"name":"测试项目-智慧园区","type":"TECHNOLOGY","managerId":1,"department":"投标部"}'
```

## 功能 3：项目详情 — 6 阶段 Tab

### 蓝图要求
6 个阶段 Tab：项目立项→标书制作→评标中→结果确认→项目复盘→项目结项。完成阶段自动锁定只读。

### 实现说明
- 前端：`Detail.vue` + `ProjectDetailShell.vue` 编排 6 个阶段组件
- 阶段锁定：项目状态机自动控制可编辑范围
- 管理员可全局修改负责人

### 测试方式
E2E（`e2e/project-detail-workflow.spec.js`）+ API

### 测试示例
```bash
TOKEN=...

# 查看项目详情
curl -s 'http://127.0.0.1:18081/api/projects/1' \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json; d=json.load(sys.stdin)['data']
print(f'项目: {d[\"name\"]}, 阶段: {d[\"currentStage\"]}')"
```

## 功能 4：任务看板

### 蓝图要求
四阶段看板（待办、进行中、审核中、已完成），任务分配与派发、状态跟踪、交付物关联。

### 实现说明
- 后端：`ProjectWorkflowController` — 任务 CRUD + 状态更新
- 交付物：`POST /api/projects/{id}/tasks/{taskId}/deliverables`
- 覆盖度：`GET /api/projects/{id}/tasks/{taskId}/deliverables/coverage`

### 测试方式
E2E（`e2e/task-board-customization.spec.js`）+ API

### 测试示例
```bash
TOKEN=...

# AI 拆解任务
curl -s -X POST 'http://127.0.0.1:18081/api/projects/1/tasks/decompose' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## 功能 5：结果录入与复盘

### 蓝图要求
中标/未中标/流标/弃标结果确认、审批；项目复盘（中标优势/丢标原因/改进建议）。

### 实现说明
- 结果确认：`POST /api/projects/{id}/result` — 4 种结果类型
- 复盘：`POST /api/projects/{id}/retrospective` — 中标/未中标不同表单

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 录入结果（中标）
curl -s -X POST 'http://127.0.0.1:18081/api/projects/1/result' \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"outcome":"WON","bidAmount":500.00,"competitor":"竞对A","remark":"中标"}' | python3 -m json.tool
```

---

## 相关文件
- E2E: `e2e/project-detail-workflow.spec.js`, `e2e/project-detail-layout.spec.js`, `e2e/project-create-prefill.spec.js`, `e2e/tender-breakdown-task-flow.spec.js`, `e2e/commercial-main-flow.spec.js`
- 前端: `src/views/Project/`
- 后端: `backend/src/main/java/com/xiyu/bid/project/`
