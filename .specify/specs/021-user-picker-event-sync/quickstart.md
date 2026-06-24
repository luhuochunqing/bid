# Quickstart: 选人控件统一 + 事件库同步启用

**Date**: 2026-06-24 | **Feature**: 021-user-picker-event-sync

## 前置条件

1. **OSS Kafka 事件总线就绪**：能够推送 BaseOssUser/BaseOssDept/BaseOssJob 事件
2. **EHSY Event Library ClientSDK** 已在 `backend/pom.xml` 中声明依赖
3. **本地 users 表** 已有基础数据（通过历史批量窗口同步或手动初始化）
4. **主工作区开发环境** 运行中（`/Users/user/xiyu/worktrees/trae`）

## 启用事件库 SDK

### 1. 配置环境变量

在主工作区启动后端时注入：

```bash
cd /Users/user/xiyu/worktrees/trae
export XIYU_DEV_CONFIRMED=1
export XIYU_ORG_EVENT_SDK_ENABLED=true
export XIYU_ORG_EVENT_BROKER_SERVER_LIST=<kafka-broker-address>
export XIYU_ORG_EVENT_BROKER_ZK_SERVERS=<zookeeper-address>
export XIYU_ORG_EVENT_BROKER_ENV=<test|staging|prod>
export XIYU_ORG_EVENT_SERVICE_NAME=<service-name>
export XIYU_ORG_EVENT_SERVER_REGISTER_URL=<register-url>
./scripts/start-backend.sh
```

### 2. 验证事件库消费

```bash
# 查看后端日志，确认 SDK 启动
tail -f .runtime/dev-services/backend.log | grep -i "event.*sdk"

# 预期日志：
# OrganizationEventSdkConsumerAdapter: Kafka consumer started, topics=[BaseOssUser, BaseOssDept, BaseOssJob]
# OrganizationEventSdkKafkaStarter: Kafka producer initialized
```

### 3. 验证本地同步

在 OSS 系统中修改某用户信息后，检查本地 users 表：

```sql
-- 检查最近同步的用户
SELECT id, full_name, department_name, last_org_synced_at, last_org_event_key
FROM users
ORDER BY last_org_synced_at DESC
LIMIT 10;
```

## 使用统一候选人 API

### 后端调用

```bash
# 任务指派候选人
curl -H "Authorization: Bearer <token>" \
  "http://127.0.0.1:18089/api/users/assignable-candidates?context=task"

# 标讯分配候选人（带部门过滤）
curl -H "Authorization: Bearer <token>" \
  "http://127.0.0.1:18089/api/users/assignable-candidates?context=tender&deptCode=BID_DEPT"
```

### 前端调用

```javascript
import { usersApi } from '@/api/modules/users'

// 远程搜索模式
const results = await usersApi.search('张', 10)

// 候选人列表模式
const candidates = await usersApi.getAssignableCandidates({ context: 'task' })
```

## 使用 UserPicker 组件

### 远程搜索模式

```vue
<template>
  <UserPicker
    v-model="form.assigneeId"
    mode="search"
    placeholder="请输入姓名搜索"
    @select="onUserSelect"
  />
</template>

<script setup>
import UserPicker from '@/components/common/UserPicker.vue'

const form = reactive({ assigneeId: null })

function onUserSelect(user) {
  console.log('选中用户:', user.name, user.deptName, user.roleName)
}
</script>
```

### 候选人列表模式

```vue
<template>
  <UserPicker
    v-model="form.assigneeId"
    mode="candidates"
    context="task"
    placeholder="请选择执行人"
    @select="onUserSelect"
  />
</template>

<script setup>
import UserPicker from '@/components/common/UserPicker.vue'

const form = reactive({ assigneeId: null })
</script>
```

### 带过滤条件

```vue
<UserPicker
  v-model="form.reviewerId"
  mode="candidates"
  context="tender"
  :dept-code="currentDeptCode"
  :role-code="'bid_admin'"
  placeholder="请选择审核人"
/>
```

## 迁移现有选人控件

### 迁移前（碎片化实现）

```vue
<!-- 旧实现：直接用 el-select + 远程搜索 -->
<el-select
  v-model="form.assigneeId"
  filterable
  remote
  :remote-method="searchUsers"
  :loading="loading"
  placeholder="请选择执行人"
>
  <el-option
    v-for="user in userOptions"
    :key="user.id"
    :label="user.name"
    :value="user.id"
  />
</el-select>
```

### 迁移后（统一组件）

```vue
<!-- 新实现：统一 UserPicker -->
<UserPicker
  v-model="form.assigneeId"
  mode="search"
  placeholder="请选择执行人"
/>
```

## 验证清单

- [ ] 事件库 SDK 启用后，后端日志显示 Kafka consumer 已启动
- [ ] OSS 用户变更后，本地 users 表在 5 分钟内更新
- [ ] `GET /api/users/assignable-candidates?context=task` 返回按权限过滤的候选人
- [ ] `GET /api/users/assignable-candidates?context=tender` 返回按权限过滤的候选人（修复无过滤问题）
- [ ] UserPicker search 模式输入关键字能搜索到用户
- [ ] UserPicker candidates 模式预加载候选人列表
- [ ] 原 3 处失效控件（BasicInfoStep/TaskStep/CollaborationCenter）下拉正常显示
- [ ] `npm run build` 通过
- [ ] `cd backend && mvn test` 通过
- [ ] `mvn test -Dtest=ArchitectureTest` 通过
