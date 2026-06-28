# CO-361 真实复现派工单 — 致 trae agent

> **本派工单的作者**：mavis（minimax worktree）
> **目标执行者**：trae（主工作区，能启动 dev 环境）
> **创建时间**：2026-06-28
> **阻塞状态**：mavis 在 minimax 不能启动 dev，**必须由 trae 执行**

---

## 0. 背景与目标

CO-361 五次反复修复的根因已定位（OSS 用户 `role_id=NULL` 时 `User.getRoleCode()` 实体 fallback 返回 `"manager"`）。PR #1245（局部止血）和 PR #1259（系统性根治）已合入 main，但 **dom 12:45 反馈的真实场景在 dev 环境是否真解决，没有 e2e 证据**。

**目标**：在 trae 主工作区用真实 OSS 同步账号，**真复现 dom 12:45 / 04:35 反馈的场景**，并完成两入口一致性截图矩阵，把证据挂到 CO-361 Linear 评论。

---

## 1. 准备工作（5 分钟）

### 1.1 拉最新代码 + 启动 dev

```bash
cd /Users/user/xiyu/worktrees/trae
./scripts/sync-env.sh .          # 拉最新 main
export XIYU_DEV_CONFIRMED=1
npm run dev:all                  # 启动后端 18089 + 前端 1323 + MySQL + Redis
```

### 1.2 等待服务就绪

```bash
# 等待后端健康
curl http://127.0.0.1:18089/actuator/health
# 等待前端可访问
curl -I http://127.0.0.1:1323
```

### 1.3 找一个 OSS 同步账号

OSS 账号特征：
- `external_org_source_app` 非空（OSS 同步的用户才会有 fallback 雷）
- `role_id=NULL` 或 `roleProfile=null`（DB 字段）
- 在 `OssPermissionCache` Redis 里**有真实角色码**（如 `bid-Team` / `bid-TeamLeader` / `bid-projectLeader`）

**查找方法**：
```sql
-- 在 xiyu_bid_main 库执行
SELECT id, username, full_name, role_id, external_org_source_app
FROM users
WHERE external_org_source_app IS NOT NULL
  AND external_org_source_app != ''
LIMIT 5;
```

如果没有合适的 OSS 账号，需要先通过 OSS 同步流程创建一个，或者联系 infra 帮忙预热缓存。

---

## 2. A1 — 真实复现 dom 12:45 反馈

**dom 12:45 反馈**：
> 在标书制作阶段添加任务并分配任务执行人以后，当任务执行人本身是这个项目的参与人员，登录系统，进入对应的项目详情页，在任务看板中看不到分配给自己的任务。但是如果任务执行人是跨部门执行人员的时候，在独立的任务看板页，是能看到待办任务的。

### 2.1 准备数据

```bash
# 找一个 OSS 用户（满足上面 1.3 条件），假设 username=oss_lead_01
# 1) 把这个用户设为某个项目的参与人员
#    （通过 AdminUserQueryService 添加为 project_member 或经由立项流程）

# 2) 用 admin 账号登录，给这个 OSS 用户分配一个任务
ADMIN_TOKEN=$(curl -s -X POST http://127.0.0.1:18089/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' | jq -r '.data.token')

# 找到 oss_lead_01 的 user_id
OSS_USER_ID=$(curl -s http://127.0.0.1:18089/api/admin/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data[] | select(.username=="oss_lead_01") | .id')

# 找到 oss_lead_01 参与的项目
PROJECT_ID=42  # 替换为实际 ID

# 创建任务分配给 oss_lead_01
curl -X POST http://127.0.0.1:18089/api/projects/$PROJECT_ID/tasks \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"A1-dom-12-45-task\",\"assigneeId\":$OSS_USER_ID}"
```

### 2.2 复现检查

```bash
# 用 oss_lead_01 登录拿 token
OSS_TOKEN=$(curl -s -X POST http://127.0.0.1:18089/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"oss_lead_01","password":"<OSS账号密码>"}' | jq -r '.data.token')

# 1) 项目详情页看板端点 — 必须看到自己作为 assignee 的任务
curl -s http://127.0.0.1:18089/api/projects/$PROJECT_ID/tasks \
  -H "Authorization: Bearer $OSS_TOKEN" | jq

# 2) 独立任务看板端点 — 应该看到同一任务
curl -s http://127.0.0.1:18089/api/task-board/items \
  -H "Authorization: Bearer $OSS_TOKEN" | jq '.data[] | select(.projectId=='$PROJECT_ID')'
```

### 2.3 UI 截图验证

```bash
# 浏览器登录 oss_lead_01 → 进入项目详情页 → 标书编制阶段 → 任务看板
# 截图保存到 /tmp/co361-A1-project-detail-board.png

# 同一账号 → 进入 /task-board 独立看板
# 截图保存到 /tmp/co361-A1-standalone-board.png
```

### 2.4 验收标准

- [ ] `/api/projects/{id}/tasks` 返回数组**包含**刚才创建的任务（`assigneeId=OSS_USER_ID`）
- [ ] `/api/task-board/items` 返回数组**包含**同一任务
- [ ] 两份截图都能看到该任务卡片
- [ ] 如果不通过 → 报 #1245/#1259 修复有遗漏，回滚排查

---

## 3. A2 — 真实复现 dom 04:35 反馈

**dom 04:35 反馈**：
> 管理员新增了三个任务，并且都分配了任务执行人，管理员在项目详情-标书制作阶段的任务看板里面能看到 3 个新增的待办任务，但是对应的任务执行人，进入到标书制作阶段中，任务看板显示是空的，看不到分配给自己的任务。

### 3.1 准备数据

```bash
# 用 admin 创建 3 个任务分配给同一个非 lead 的项目成员
# （用 demo 账号 lizong 是 admin，但他的 id 必须是合法的项目成员）
# 简化版：直接找一个 staff 角色的 OSS 账号

for i in 1 2 3; do
  curl -X POST http://127.0.0.1:18089/api/projects/$PROJECT_ID/tasks \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"A2-dom-04-35-task-$i\",\"assigneeId\":$OSS_USER_ID}"
done
```

### 3.2 复现检查

```bash
# 用 oss 账号登录
OSS_TOKEN=...

# 检查两端点都返回 3 个任务
curl -s http://127.0.0.1:18089/api/projects/$PROJECT_ID/tasks \
  -H "Authorization: Bearer $OSS_TOKEN" | jq '.data | length'

curl -s http://127.0.0.1:18089/api/task-board/items \
  -H "Authorization: Bearer $OSS_TOKEN" | jq '.data | map(select(.projectId=='$PROJECT_ID')) | length'
```

### 3.3 UI 截图验证

```bash
# 截图保存到 /tmp/co361-A2-{project,standalone}-board.png
```

### 3.4 验收标准

- [ ] 两端点都返回 3 个任务（length == 3）
- [ ] 两份截图都显示 3 张任务卡片

---

## 4. A3 — 两入口一致性截图矩阵（4 角色 × 2 入口）

### 4.1 角色矩阵

| 角色 | demo 用户 | 期望：两入口任务数 |
|---|---|---|
| admin | lizong | 一致（看到全部） |
| bid_lead (投标组长) | xiaoliu | 一致（dataScope=all） |
| bid_specialist (投标专员 + lead) | xiaozhou + ProjectLeadAssignment | 一致（看到项目全部） |
| staff (普通员工 + 项目成员 + assignee) | xiaowang | 一致（只看到自己任务） |

**注意**：bid_specialist 场景需要先给 xiaozhou 分配一个项目 lead 身份，否则只看到自己。

### 4.2 截图脚本建议

```bash
for user in lizong xiaoliu xiaozhou xiaowang; do
  TOKEN=$(curl -s -X POST http://127.0.0.1:18089/api/auth/login \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$user\",\"password\":\"123456\"}" | jq -r '.data.token')
  
  # 1) 项目详情页看板端点
  curl -s http://127.0.0.1:18089/api/projects/$PROJECT_ID/tasks \
    -H "Authorization: Bearer $TOKEN" | jq > /tmp/co361-A3-$user-project.json
  
  # 2) 独立任务看板端点
  curl -s http://127.0.0.1:18089/api/task-board/items \
    -H "Authorization: Bearer $TOKEN" | jq > /tmp/co361-A3-$user-standalone.json
done
```

### 4.3 一致性对比表

| 角色 | 项目详情页任务数 | 独立看板任务数 | 一致？ |
|---|---|---|---|
| admin (lizong) | ? | ? | ? |
| bid_lead (xiaoliu) | ? | ? | ? |
| bid_specialist + lead (xiaozhou) | ? | ? | ? |
| staff (xiaowang) | ? | ? | ? |

### 4.4 验收标准

- [ ] 8 个 JSON 文件收集完整
- [ ] 4 角色 × 2 入口一致性对比表填好
- [ ] 任何不一致角色 → 报 bug 回 mavis

---

## 5. 跑 A5 e2e（在 trae 上跑，因为我不能跑 dev）

我已在 `agent/minimax/co361-co373-finish` 分支 commit 了 e2e 测试：
`e2e/task-board-two-entry-consistency.spec.js`

在 trae 跑：

```bash
cd /Users/user/xiyu/worktrees/trae
git fetch origin agent/minimax/co361-co373-finish
git checkout agent/minimax/co361-co373-finish
npm run test:e2e -- task-board-two-entry-consistency 2>&1 | tee /tmp/co361-A5-e2e.log
```

**期望**：所有 4 个测试通过。

**如果失败**：
- 截图保存到 `/tmp/co361-A5-failure-*.png`
- 把失败信息发 Linear CO-361 评论

---

## 6. 提交结果回 Linear

把所有证据汇总成一条 Linear 评论：

```bash
LINEAR_KEY=lin_api_xxx

# 通过 API 发评论
curl -X POST https://api.linear.app/graphql \
  -H "Authorization: $LINEAR_KEY" \
  -H "Content-Type: application/json" \
  -d '{"query":"mutation ...","variables":{"input":{"issueId":"CO-361","body":"..."}}}'
```

评论里附：
- A1 / A2 / A3 截图（如果文件大可挂 gist）
- A5 e2e 日志（`/tmp/co361-A5-e2e.log` 内容）
- 两入口一致性对比表
- 失败情况详细描述（如有）

---

## 7. 完成时间期望

- 准备工作：5 分钟
- A1：15 分钟
- A2：15 分钟
- A3：30 分钟（含 OSS 账号准备）
- A5 e2e：10 分钟
- 提交回 Linear：10 分钟
- **总计：1.5 小时**

---

## 8. 阻塞 & 求助

如果遇到任何阻塞：
- dev 环境起不来 → 检查 `npm run agent:health-check`
- OSS 账号找不到 → 联系 infra / Zoey
- e2e 失败 → 把日志贴到 CO-361 评论
- 需要 mavis 帮忙 → 在 minimax 给我发消息

---

## 9. 相关资源

- mavis 分支：`agent/minimax/co361-co373-finish`（已 push origin）
- 4 个 commit：
  - `f584ce58d` B5+B6 @Deprecated + SAFE 注释 + pre-push 拦截
  - `4382cd519` A5 两入口一致性 e2e
  - `3dad0e958` C1+C2 CO-361 教训 wiki + CLAUDE.md 强约束
  - `9d14b6200` C3 有效角色解析规范
- 相关文档：
  - `.wiki/pages/lessons-learned/CO-361-five-rounds-no-fix.md`
  - `.wiki/pages/architecture/effective-role-resolution.md`
- Linear issue：CO-361

---

**派工时间**：2026-06-28
**期望回执**：完成 Linear 评论后 ping mavis