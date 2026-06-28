# Quickstart: 统一服务层角色码解析入口

**Date**: 2026-06-27

## 目标

修复 CO-373：OSS 用户（投标负责人/辅助人员）在标书制作阶段无权提交标书审核和提交投标。

## 核心变更

1. 新增纯核心 `EffectiveRolePolicy`（`security/domain`）+ record `EffectiveRoleResult`
2. 新增外壳 `EffectiveRoleResolver`（`security`），注入 `OssPermissionCache`
3. `CurrentUserResolver.getCurrentRoleCode()` 改调 resolver
4. 19 处 Guard/Service 直调 `user.getRoleCode()` 改调 resolver
5. 4 处既有缓存读取收敛到 resolver
6. 前端 `useInitiationStageActions.js` 辅助人员字段回显兜底

## 验证命令

```bash
# 后端纯核心门禁
cd backend && mvn test -Dtest=FPJavaArchitectureTest

# 后端单元测试（新增）
cd backend && mvn test -Dtest=EffectiveRolePolicyTest,EffectiveRoleResolverTest

# 后端回归测试（受影响 Guard/Service）
cd backend && mvn test -Dtest=TaskPermissionGuardTest,ProjectDraftingServiceTest,AuthServiceTest,UserDetailsServiceImplTest,DataScopeConfigServiceTest

# 后端架构测试
cd backend && mvn test -Dtest=ArchitectureTest,MaintainabilityArchitectureTest

# 前端构建
npm run build

# 前端行数预算
npm run check:line-budgets

# 自审清单
scripts/preflight-self-review.sh
```

## 验证场景

### 场景1：OSS 用户分配任务不再 403
- 以 OSS 用户（role_id=NULL，缓存角色 bid-Team）身份
- 调用 `POST /api/tasks` 分配任务
- 期望：200 成功，不再 403

### 场景2：OSS 用户提交标书审核不再 403
- 同一用户，项目所有任务完成 + 标书文件已上传
- 调用 `POST /api/projects/{id}/drafting/submit-review`
- 期望：200 成功

### 场景3：本地用户回归不变
- 以本地建号用户（role_id 非空，角色 bid-projectLeader）身份
- 调用上述接口
- 期望：行为不变

### 场景4：前端辅助人员回显
- 分配投标辅助人员 → 保存 → 进起草阶段 → 返回立项阶段
- 期望：辅助人员字段显示已分配人员姓名，不显示"未分配"
