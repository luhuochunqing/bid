---
title: 系统设置 — 蓝图功能实现对照
space: engineering
category: testing
tags: [testing, 蓝图对照, 系统设置, settings]
sources:
  - .wiki/sources/testing/module-07-settings-test.md
  - .wiki/sources/testing/module-07-settings-test.md
backlinks:
  - _index
created: 2026-05-28
updated: 2026-06-21
health_checked: 2026-06-21
---
> 蓝图章节：§4.7 系统设置
> 对应飞书蓝图：https://my.feishu.cn/docx/FgLAdRmFho4QhwxncgAcfxKJn0d

## 覆盖度总览

| 蓝图功能 | 实现状态 | 测试方式 | 关键文件 |
|---------|---------|---------|---------|
| 数据权限(部门树) | ✅ 已完成 | API | `DepartmentTreePanel.vue`, `PUT /api/admin/settings/departments` |
| 角色权限 | ✅ 已完成 | API | `RoleManagementPanel.vue`, `CRUD /api/admin/roles` |
| 接口权限矩阵 | ✅ 已完成 | API | `InterfacePermissionMatrixPanel.vue`, `GET /api/admin/permissions/endpoints` |
| 任务状态字典 | ✅ 已完成 | API | `TaskStatusDictPanel.vue` (仅 admin) |
| 任务扩展字段 | ✅ 已完成 | API | `TaskExtendedFieldPanel.vue` (仅 admin) |
| 用户组织归属 | ✅ 已完成 | API | `UserOrganizationPanel.vue`, `PUT /api/admin/users/{id}/organization` |
| AI 模型配置 | ✅ 已完成 | API | `AiModelSettingsPanel.vue`, `GET/PUT /api/settings` |
| 投标匹配评分 | ✅ 已完成 | API | `BidMatchScoringSettingsPanel.vue`, `GET/PUT /api/settings` |
| 系统集成 | ✅ 已完成 | API + 手动 | `SystemIntegrationPanel.vue`, 集成配置子页面 |
| 审计日志 | ✅ 已完成 | API | `AuditLogPanel.vue`, `GET /api/audit` |

## 功能 1：数据权限(部门树)

### 蓝图要求
部门层级管理、数据可见范围控制。

### 实现说明
- 前端：`DepartmentTreePanel.vue` — el-tree 部门树，可拖拽排序、增删改
- 后端：`AdminSettingsController` — `GET/PUT /api/admin/settings/departments`

### 测试方式
API 测试

### 测试示例
```bash
# 获取当前测试 token（使用 claude worktree 端口）
TOKEN=$(curl -s -X POST 'http://127.0.0.1:18081/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 获取当前部门树
curl -s 'http://127.0.0.1:18081/api/admin/settings/data-scope' \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json;d=json.load(sys.stdin)
print(f'部门数量: {len(d.get(\"data\",{}).get(\"deptTree\",[]))}')
for dept in d.get('data',{}).get('deptTree',[]):
    print(f'  - {dept.get(\"name\")} (id={dept.get(\"id\")})')
"

# 保存部门树
curl -s -X PUT 'http://127.0.0.1:18081/api/admin/settings/departments' \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '[
    {"id":1,"name":"市场部","parentId":null,"sort":1},
    {"id":2,"name":"技术部","parentId":null,"sort":2},
    {"id":3,"name":"前端组","parentId":2,"sort":1}
  ]' | python3 -m json.tool
```

## 功能 2：角色权限

### 蓝图要求
角色 CRUD、菜单权限勾选、数据范围（本人/本部门/全部）、项目/部门访问限制。

### 实现说明
- 前端：`RoleManagementPanel.vue` — 角色列表 + 编辑弹窗（菜单权限树、数据范围选择器）
- 后端：`AdminRoleController` — 完整 CRUD（`GET/POST/PUT/PATCH/DELETE /api/admin/roles`）
- 角色实体含 RoleProfile（`ADMIN/MANAGER/STAFF/AUDITOR` 等），每种角色有独立 data scope

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 列出所有角色
curl -s 'http://127.0.0.1:18081/api/admin/roles' \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json;d=json.load(sys.stdin)
for r in d.get('data',[]):
    print(f'  {r[\"code\"]}: {r[\"name\"]} (用户数: {r.get(\"userCount\",0)}, 数据范围: {r.get(\"dataScope\",\"-\")})')
"

# 创建新角色
curl -s -X POST 'http://127.0.0.1:18081/api/admin/roles' \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "TEST_ROLE",
    "name": "测试角色",
    "description": "测试用角色",
    "dataScope": "SELF",
    "menuPermissions": ["dashboard","project","knowledge"]
  }' | python3 -m json.tool

# 启用/禁用角色
curl -s -X PATCH 'http://127.0.0.1:18081/api/admin/roles/1/status' \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"DISABLED"}' | python3 -m json.tool
```

## 功能 3：接口权限矩阵

### 蓝图要求
可视化展示所有后端接口与角色的权限映射关系。

### 实现说明
- 前端：`InterfacePermissionMatrixPanel.vue` — 只读表格，按模块/路径/方法分组
- 后端：`AdminEndpointPermissionController` — `GET /api/admin/permissions/endpoints`
- 核心逻辑：`interface-permission-matrix-core.js` 提供筛选和搜索

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 获取接口权限矩阵
curl -s 'http://127.0.0.1:18081/api/admin/permissions/endpoints' \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json;d=json.load(sys.stdin)
eps = d.get('data',{})
print(f'接口总数: {len(eps)}')
# 按模块分组统计
from collections import Counter
modules = Counter()
for ep in eps:
    parts = ep.get('path','').split('/')
    if len(parts) >= 3:
        modules[parts[2]] += 1
for mod, cnt in modules.most_common(10):
    print(f'  /api/{mod}: {cnt} 个接口')
"

# 非 admin 用户无权访问
USER_TOKEN=$(curl -s -X POST 'http://127.0.0.1:18081/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"xiaowang","password":"123456"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

curl -s -o /dev/null -w 'HTTP %{http_code}' \
  'http://127.0.0.1:18081/api/admin/permissions/endpoints' \
  -H "Authorization: Bearer $USER_TOKEN"
# 预期返回 403
```

## 功能 4：任务状态字典

### 蓝图要求
管理员配置任务状态流转定义。

### 实现说明
- 前端：`TaskStatusDictPanel.vue` — 仅 admin 可见的状态字典管理
- 包括状态名称、颜色、排序、默认值配置
- 访问控制：`v-if="isAdmin"` 控制 Tab 可见性

### 测试方式
API 测试（需 admin 角色）

### 测试示例
```bash
TOKEN=...  # admin

# 获取设置（含系统配置中的状态定义）
curl -s 'http://127.0.0.1:18081/api/settings' \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data']
sc = d.get('systemConfig',{})
print(f'系统名称: {sc.get(\"sysName\",\"-\")}')
print(f'押金提醒天数: {sc.get(\"depositWarnDays\",\"-\")}')
print(f'资质提醒天数: {sc.get(\"qualWarnDays\",\"-\")}')
"

# 非 admin 用户应看不到此 Tab
# 前端验证：登录 xiaowang(staff) 角色，系统设置 Tab 不应显示"任务状态字典"
```

## 功能 5：任务扩展字段

### 蓝图要求
管理员配置任务自定义元数据字段。

### 实现说明
- 前端：`TaskExtendedFieldPanel.vue` — 仅 admin 可见
- 支持自定义字段名称、类型、是否必填等配置

### 测试方式
API 测试（需 admin 角色）

### 测试示例
```bash
TOKEN=...  # admin

# 验证前端组件挂载
echo "TaskExtendedFieldPanel 在前端 Settings.vue 中 v-if='isAdmin' 控制"
echo "验证路径: /settings?tab=task-extended-fields"
```

## 功能 6：用户组织归属

### 蓝图要求
用户与部门、角色的关联分配。

### 实现说明
- 前端：`UserOrganizationPanel.vue` — 用户表格 + 分配弹窗（选择部门和角色）
- 后端：`PUT /api/admin/users/{id}/organization` — 更新用户组织归属
- 角色过滤：仅显示启用状态的角色

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 获取所有用户及组织信息
curl -s 'http://127.0.0.1:18081/api/admin/users' \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json;d=json.load(sys.stdin)
for u in d.get('data',{}).get('content',[]):
    print(f'{u[\"username\"]} (部门: {u.get(\"departmentName\",\"-\")}, 角色: {u.get(\"roleName\",\"-\")})')
"

# 更新用户组织归属
USER_ID=$(curl -s 'http://127.0.0.1:18081/api/admin/users' \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['content'][0]['id'])" 2>/dev/null)

curl -s -X PUT "http://127.0.0.1:18081/api/admin/users/$USER_ID/organization" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"departmentId":1,"roleId":1}' | python3 -m json.tool
```

## 功能 7：AI 模型配置

### 蓝图要求
配置 AI Provider（基础地址、模型、API Key），测试连接，启用/禁用 AI 功能。

### 实现说明
- 前端：`AiModelSettingsPanel.vue` — Provider 列表 + 编辑表单 + 测试连接按钮
- 后端：`GET/PUT /api/settings` — 读取/保存 AI 配置
- `POST /api/settings/ai-models/test` — 测试 AI Provider 连接
- API Key 加密存储（`encryptedApiKey` 字段）

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 查看当前 AI 配置
curl -s 'http://127.0.0.1:18081/api/settings' \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data']
ac = d.get('aiModelConfig',{})
print(f'活跃 Provider: {ac.get(\"activeProvider\",\"-\")}')
for p in ac.get('providers',[]):
    print(f'  {p[\"providerName\"]}: 启用={p[\"enabled\"]}, 模型={p.get(\"model\",\"-\")}')
print(f'AI 总开关: {d[\"systemConfig\"][\"enableAI\"]}')
"

# 测试 AI 连接
curl -s -X POST 'http://127.0.0.1:18081/api/settings/ai-models/test' \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"provider":"openai","model":"gpt-4"}' | python3 -m json.tool
```

## 功能 8：投标匹配评分

### 蓝图要求
配置评分维度、规则、权重，支持多模型切换。

### 实现说明
- 前端：`BidMatchScoringSettingsPanel.vue` — 评分维度表格、规则编辑器、权重校验
- 后端：配置存储在 `SettingsResponse` 中的投标匹配相关字段
- 验证：权重之和必须为 100，证据键必须唯一

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 获取当前设置查看评分配置（投标匹配相关字段在 systemConfig 中）
curl -s 'http://127.0.0.1:18081/api/settings' \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data']
sc = d.get('systemConfig',{})
# 投标匹配评分配置在 settings 的完整数据中
print('当前投标匹配配置包含:')
for k,v in sc.items():
    if 'bid' in k.lower() or 'score' in k.lower() or 'match' in k.lower():
        print(f'  {k}: {v}')
"

# 前端验证评分维度：
# 1. 打开 /settings?tab=bid-match-scoring
# 2. 验证维度列表可编辑（名称、权重、证据键）
# 3. 验证权重之和校验
# 4. 验证模型创建/激活/删除
```

## 功能 9：系统集成

### 蓝图要求
企业微信、~~泛微 OA（已取消）~~、CRM、组织架构同步配置入口。

### 实现说明
- 前端：`SystemIntegrationPanel.vue` — 集成配置总面板
  - `WeComIntegrationCard.vue` — 企业微信绑定（Corp ID、Agent ID、Secret）
  - `WeaverIntegrationCard.vue` — 泛微 OA 集成（地址、账号、密钥）
  - `CrmIntegrationCard.vue` — CRM 连接配置（地址、Token、Client ID）
  - `OrganizationIntegrationCard.vue` — 组织架构同步 SDK 配置
- 后端：集成配置存储在 `SettingsResponse.integrationConfig` 中
- 测试连接：各卡片内置"测试连接"按钮

### 测试方式
API + 手动

### 测试示例
```bash
TOKEN=...

# 查看当前集成配置
curl -s 'http://127.0.0.1:18081/api/settings' \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json;d=json.load(sys.stdin)['data']
ic = d.get('integrationConfig',{})
print('集成配置:')
for k,v in ic.items():
    if not any(s in k for s in ['secret','password','key']):
        print(f'  {k}: {v}')
    else:
        print(f'  {k}: ******')
print()
print('各集成卡片状态:')
print(f'  企业微信: {\"已配置\" if ic.get(\"wecomCorpId\") else \"未配置\"}')
print(f'  泛微 OA: {\"已配置\" if ic.get(\"oaUrl\") else \"未配置\"}')
print(f'  组织架构同步: {\"已启用\" if ic.get(\"orgEnabled\") else \"未启用\"}')
"

# 保存企业微信配置
curl -s -X PUT 'http://127.0.0.1:18081/api/settings' \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "integrationConfig": {
      "wecomCorpId": "ww123456",
      "wecomAgentId": "1000001",
      "wecomSecret": "test-secret"
    }
  }' | python3 -m json.tool
```

## 功能 10：审计日志

### 蓝图要求
查看所有用户操作日志，支持搜索和筛选。

### 实现说明
- 前端：`AuditLogPanel.vue` — 两种模式：
  - `operation`（操作日志）: 当前用户自己的操作记录（`/operation-logs`）
  - `audit`（审计日志）: 全量用户操作（需 `audit-logs` 权限，`/audit-logs` 或 `/settings?tab=audit`）
- 后端：`AuditLogController` — `GET /api/audit`（全量） + `GET /api/audit/my`（个人）
- 日志字段：操作人、时间、动作、模块、目标、详情、IP、用户代理
- 搜索维度：关键词、操作类型、模块、操作人、日期范围、状态

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 查询审计日志（全量，需 admin/auditor 角色）
curl -s 'http://127.0.0.1:18081/api/audit?page=0&size=5&sort=createdAt,desc' \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json;d=json.load(sys.stdin)
logs = d.get('data',{}).get('content',[])
print(f'审计日志共 {d[\"data\"][\"totalElements\"]} 条，最近 5 条:')
for log in logs:
    print(f'  [{log.get(\"action\",\"-\")}] {log.get(\"operator\",\"-\")} - {log.get(\"module\",\"-\")} ({log.get(\"createdAt\",\"\")})')
"

# 个人操作日志
curl -s 'http://127.0.0.1:18081/api/audit/my?page=0&size=5' \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json;d=json.load(sys.stdin)
logs = d.get('data',{}).get('content',[])
print(f'个人操作日志: {d[\"data\"][\"totalElements\"]} 条')
for log in logs:
    print(f'  {log.get(\"action\",\"-\")} -> {log.get(\"target\",\"-\")}')
"

# 按模块筛选审计日志
curl -s 'http://127.0.0.1:18081/api/audit?module=PROJECT&page=0&size=3' \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json;d=json.load(sys.stdin)
print(f'项目模块操作日志: {d[\"data\"][\"totalElements\"]} 条')

# 非 auditor 用户操作日志访问权限
USER_TOKEN=$(curl -s -X POST 'http://127.0.0.1:18081/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"xiaowang","password":"123456"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

echo ''
echo 'staff 角色访问审计日志:'
curl -s -o /dev/null -w 'HTTP %{http_code}' \
  'http://127.0.0.1:18081/api/audit?page=0&size=1' \
  -H "Authorization: Bearer $USER_TOKEN"
# 预期返回 403（staff 无 audit-logs 权限）
```

---

## 相关文件
- 前端: `src/views/System/Settings.vue`, `src/views/System/settings/`
- 后端: `backend/src/main/java/com/xiyu/bid/settings/`, `controller/AdminUserController.java`, `controller/AdminRoleController.java`, `audit/`
- 单元测试: `Settings.spec.js`, `AuditLogPanel.spec.js`, `useWeComSettings.spec.js`, `useSystemIntegrationSettings.spec.js`
