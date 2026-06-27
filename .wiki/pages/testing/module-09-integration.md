---
title: Module 9 系统集成 — 蓝图功能实现对照
space: engineering
category: testing
tags: [testing, 蓝图对照, 集成, CRM, 组织架构, 企业微信]
sources:
  - .wiki/sources/testing/module-09-integration-test.md
  - .wiki/sources/testing/module-09-integration-test.md
backlinks:
  - _index
created: 2026-05-28
updated: 2026-06-21
health_checked: 2026-06-27
---
> 蓝图章节：§6 非功能性保障（集成相关）
> 对应飞书蓝图：https://my.feishu.cn/docx/FgLAdRmFho4QhwxncgAcfxKJn0d

## 覆盖度总览

| 蓝图功能 | 实现状态 | 测试方式 | 覆盖情况 |
|---------|---------|---------|---------|
| CRM 集成（客户数据同步/商机流转） | 🟡 部分完成 | API | 后端 crm 包完整实现查询/同步；前端 CRM 卡片标记为 Coming Soon |
| OA/审批流集成（用印/付款审批） | ❌ 已取消（2026-05-28 确认） | — | 不再纳入本系统范围 |
| 组织架构同步（部门/用户/角色） | ✅ 已完成 | API | OrganizationDirectorySyncAppService 完整事件驱动同步管线 |
| 企业微信/消息通道集成 | ✅ 已完成 | API | WeComIntegrationAppService 含 SSO + 消息推送 + 连通性测试 |

## 功能 1：CRM 集成（客户数据同步/商机流转）

### 蓝图要求
投标系统内直接检索客户信息、客户经理及跟进状态；CRM 系统调用投标系统标讯创建接口；CRM 匹配成功自动分配，无匹配则需手动分配。

### 实现说明
- 后端：backend/.../crm/ 包完整实现
  - CrmCustomerService — 客户模糊查询/客户联系人查询
  - CrmAuthService — Token 鉴权管理
  - CrmMenuService — 菜单树查询
  - CrmEmployeeService — 员工信息查询
  - CrmMessageService — 企微+站内信消息发送
  - CrmProjectClient — 项目/标讯同步客户端
- 控制器：CrmController（/api/xiyu/crm/*）
  - GET /api/xiyu/crm/customers?keyword=X&pageSize=N
  - GET /api/xiyu/crm/customers/{id}/contacts
  - POST /api/xiyu/crm/messages
- 前端：SystemIntegrationPanel.vue 中集成卡片标记为 IntegrationComingSoonCard（Coming Soon）

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:18081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 1. CRM 客户模糊查询
curl -s "http://127.0.0.1:18081/api/xiyu/crm/customers?keyword=西域&pageSize=10" \
  -H "Authorization: Bearer $TOKEN"

# 2. 系统设置中查看集成配置
curl -s http://127.0.0.1:18081/api/settings \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys, json
data = json.load(sys.stdin)['data']
ic = data.get('integrationConfig', {})
print('CRM 相关集成配置:')
for k in ['orgEnabled','orgSystem','oaEnabled','ssoEnabled','callbackUrl']:
    print(f'  {k}: {ic.get(k, \"N/A\")}')
"

# 3. 验证项目创建时 CRM 商机流转（需通过创建项目流程验证）
echo "CRM 商机导入流程:"
echo "  - 标讯导入时匹配 CRM 客户"
echo "  - 匹配成功自动分配负责人"
echo "  - 无匹配走手动分配"
```

## 功能 2：OA/审批流集成（用印/付款审批）

### 蓝图要求
| 流程名称 | 说明 |
|---------|------|
| 公章盖章申请单 | 投标系统传源字段，西域映射到 OA |
| 报价章盖章申请单 | 同上 |
| 印章及证件借用申请单 | 同上 |
| 投标资料包申请流程 | 明细按流程使用 |
| 一般付款流程 | 同上 |
| 费用报销流程 | 同上 |
| 借款与保证金申请流程 | 同上 |
| 借款与保证金核销流程 | 同上 |

### 实现说明
- 前端：SystemIntegrationPanel.vue 中 WeaverIntegrationCard.vue
- 后端工作流：WorkflowFormAdminController + WorkflowFormSubmissionService
  - 泛微 OA 网关：WeaverOaWorkflowGateway（生产）、MockOaWorkflowGateway（测试）
  - OA 回调：WeaverOaCallbackController（/api/integrations/oa/weaver/callback）
  - 表单模板配置含 OA 绑定映射
- 流程映射：系统设置中 flowMappings 配置（项目立项审批 -> project_start, 投标审批 -> bidding_approval 等）
- 资质借阅已接入 OA：OA 通过前不借出，OA 驳回不创建借阅记录

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:18081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 1. 查看 OA 流程映射配置
curl -s http://127.0.0.1:18081/api/settings \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys, json
data = json.load(sys.stdin)['data']
flows = data.get('flowMappings', [])
print(f'OA 流程映射数: {len(flows)}')
for f in flows:
    print(f'  [{f[\"systemFlow\"]}] -> {f[\"oaFlow\"]} (code={f.get(\"oaFlowCode\",\"?\")})')
"

# 2. 查看 OA 绑定配置
curl -s http://127.0.0.1:18081/api/admin/integrations/wecom \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json
try:
    data = json.load(sys.stdin)
    print(f'集成配置状态: {json.dumps(data, indent=2)[:500]}')
except:
    print('响应非 JSON 格式')
"

# 3. 流程表单 admin 模板列表
curl -s http://127.0.0.1:18081/api/admin/workflow-forms/templates \
  -H "Authorization: Bearer $TOKEN"

# 4. OA 回调端点验证（公网可达性测试）
echo "OA 回调端点: POST /api/integrations/oa/weaver/callback"
echo "回调状态: REJECT (驳回), ARCHIVE (归档), SUPPLIER_SIGNING (供应商签署)"

# 5. 验证表单提交流程
echo "资质借阅完整闭环:"
echo "  1. 用户提交表单 -> 表单实例进入 OA_APPROVING"
echo "  2. OA 回调 APPROVED -> 系统创建借阅记录"
echo "  3. 实例状态变为 BUSINESS_APPLIED"
```

## 功能 3：组织架构同步（部门/用户/角色）

### 蓝图要求
部门数据来自组织主数据，与外部系统保持同步。

### 实现说明
- 后端：backend/.../integration/organization/ 完整事件驱动同步
  - OrganizationDirectorySyncAppService — webhook 接收 + 事件处理核心
  - OrganizationDirectoryHttpGateway — 外部目录 HTTP 网关
  - OrganizationDepartmentSyncWriter — 部门写入
  - OrganizationUserSyncWriter — 用户写入
  - OrganizationEventRetryAppService — 事件重试机制
  - OrganizationReconciliationScheduler — 定期对账调度
- 控制器：
  - OrganizationSyncRunController — POST /api/integrations/organization/sync-runs
  - OrganizationManualResyncController — 手动重新同步
  - OrganizationOperationsController — 运维操作
- 前端：SystemIntegrationPanel.vue 中 OrganizationIntegrationCard.vue

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:18081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 1. 查看组织集成配置状态
curl -s http://127.0.0.1:18081/api/settings \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys, json
data = json.load(sys.stdin)['data']
ic = data.get('integrationConfig', {})
print(f'组织架构集成已启用: {ic.get(\"orgEnabled\", false)}')
print(f'集成系统: {ic.get(\"orgSystem\", \"N/A\")}')
"

# 2. 触发组织同步
curl -s -X POST http://127.0.0.1:18081/api/integrations/organization/sync-runs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}' | python3 -m json.tool

# 3. 组织同步运维状态查询
echo "相关端点:"
echo "  POST /api/integrations/organization/sync-runs - 触发同步"
echo "  POST /api/integrations/organization/operations/events/webhook - 事件 webhook"
echo "  POST /api/integrations/organization/operations/resync - 手动重新同步"
echo "  POST /api/integrations/organization/sync-runs/{runId}/retry - 重试"
```

## 功能 4：企业微信/消息通道集成

### 蓝图要求
企业微信登录（SSO）、消息推送（含站内信 + 企业微信双通道）。

### 实现说明
- 后端：backend/.../integration/ 完整 WeCom 集成
  - WeComIntegrationAppService — 配置保存、连接测试、消息发送
  - WeComOAuthService — SSO 登录认证
  - WeComMessagePublisher — 消息推送
  - WeComConnectivityProbe — 连通性探测
  - WeComCredentialCipher — Corp Secret 加密存储
- 控制器：
  - WeComIntegrationController（/api/admin/integrations/wecom）
  - WeComAuthController（/api/auth/wecom/*）
- 前端：
  - WeComIntegrationCard.vue — 配置面板
  - 登录页 WeCom 登录按钮（wecom-button）
  - 消息通过 NotificationInbox.vue + 企微双通道推送

### 测试方式
API 测试 + E2E

### 测试示例
```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:18081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 1. 获取企业微信集成配置
curl -s http://127.0.0.1:18081/api/admin/integrations/wecom \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 2. 连通性测试
curl -s -X POST http://127.0.0.1:18081/api/admin/integrations/wecom/test \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 3. 发送测试消息（需先配置 CorpID/AgentID/Secret）
# curl -s -X POST http://127.0.0.1:18081/api/admin/integrations/wecom/send-test \
#   -H "Authorization: Bearer $TOKEN" \
#   -H "Content-Type: application/json" \
#   -d '{"content":"这是来自投标系统的测试消息"}' | python3 -m json.tool

# 4. WeCom SSO 配置
echo "企业微信 SSO 登录流程:"
echo "  1. 登录页点击「企业微信登录」按钮"
echo "  2. GET /api/auth/wecom/authorize-params - 获取授权参数"
echo "  3. 前端跳转企业微信 OAuth 授权页"
echo "  4. 回调 /api/auth/wecom/callback?code=X&state=Y"
echo "  5. 绑定验证: 未绑定返回 40101 (WECOM_NOT_BOUND)"

# 5. 消息通道配置
curl -s http://127.0.0.1:18081/api/settings \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys, json
data = json.load(sys.stdin)['data']
print('消息通知配置（集成配置中相关字段）:')
ic = data.get('integrationConfig', {})
for k in ['ssoEnabled','callbackUrl','ipWhitelist']:
    print(f'  {k}: {ic.get(k, \"N/A\")}')
"
```
