# 投标负责人筛选「主 OR 副」语义 + 展示列只显示主负责人姓名导致用户误以为筛错根因分析

> Issue: 投标项目列表筛选"陈梦瑶"时张莉娜的项目也显示
> 日期: 2026-07-03
> 排查者: kimi
> 修复 PR: `!1642` (commit `da5bc9ca3`, merge `487231f35`)
> 关联 PR: `!1574` (UserPicker value-key 修复，让筛选真正生效从而暴露本 bug)

---

## 现场还原

**症状素描**：用户在投标项目列表按投标负责人筛选"陈梦瑶"，但张莉娜的项目也显示出来。用户反馈"PR1574 已经改了但是没生效"。

**第一反应陷阱**：用户以为 PR1574 没部署生效，要求"看服务器上的数据库信息"。但实际 PR1574 已部署，"没生效"是错觉——PR1574 修复之前筛选根本不工作（所有项目都显示），修复之后筛选真正生效，反而暴露了之前被掩盖的 OR 匹配语义问题。

---

## 剥洋葱：三层诊断体系应用

### Layer 1（Sentry 自动诊断）— 不适用

此 bug 不触发任何异常：
- 筛选逻辑正常执行，HTTP 200 OK
- 无 NPE、无 SQL 异常、无外部服务失败
- 属于 SOP 定义的"Layer 2 适用：业务逻辑错误、Sentry 未覆盖场景"

### Layer 2（代码证据链 + 数据库验证）— 定位根因

#### Step 1：确认 PR1574 是否真的部署

```bash
# 服务器前端 dist 时间戳
ls -la /srv/www/xiyu-bid/assets/*.js | head -5
# → Jul 3 17:31（今天部署）

# UserPicker chunk 里搜 value-key 字面量
grep -oE "value-key[^,}]{0,50}" /srv/www/xiyu-bid/assets/UserPicker-Co7JMLd0.js
# → value-key":"value"  ✅ PR1574 修复已生效
```

**结论**：PR1574 已部署，"没生效"是错觉。

#### Step 2：追溯 PR1574 修复前的行为

```javascript
// PR1574 修复前：UserPicker.vue
:value-key="valueField"  // valueField 默认 'id'

// 但 selectOptions 生成的 option 格式是 { value, label }
// option 对象中没有 id 字段 → 选中后值为 undefined
```

```javascript
// useProjectFilter.js matchId 函数
function matchId(filterVal, ...fieldVals) {
  if (filterVal == null || filterVal === '') return true  // ← undefined == null 为 true
  // ...
}
// → 筛选值 undefined → 永远返回 true → 等于不筛选 → 所有项目都显示
```

**关键洞察**：PR1574 修复前，`value-key=id` 与 option 格式不匹配导致选中值变 `undefined`，而 `undefined == null` 永真 → 等于不筛选，所有项目都显示。用户当时看到"所有项目都显示"以为是正常状态，其实筛选根本没工作。PR1574 修好后筛选真正生效，反而暴露了 OR 匹配语义问题。

#### Step 3：定位筛选 OR 语义

```javascript
// useProjectFilter.js:68（修复前）
if (!matchId(f.biddingLeaderId, p.biddingLeaderId, p.secondaryBiddingLeaderId)) return false
//                                                                    ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑
// matchId 是 fieldVals.some(...) → 主负责人 OR 副负责人任一匹配即命中
```

#### Step 4：定位展示列只显示主负责人姓名

后端 `ProjectQueryService.java`：
```java
// 第 147 行：biddingLeaderName 只来自 project_initiation_details.bidding_leader_name
dto.setBiddingLeaderName(det.getBiddingLeaderName());

// 第 169-170 行：biddingLeaderId / secondaryBiddingLeaderId 来自 project_lead_assignment
dto.setBiddingLeaderId(leadAssignment.getPrimaryLeadUserId());
dto.setSecondaryBiddingLeaderId(leadAssignment.getSecondaryLeadUserId());
```

**两表无强同步约束**：
- `project_initiation_details.bidding_leader_name`（VARCHAR）— 只存主负责人姓名，没有副负责人姓名字段
- `project_lead_assignment.primary_lead_user_id` / `secondary_lead_user_id`（BIGINT）— 主+副 ID

#### Step 5：数据库验证（生产 winbid）

陈梦瑶 id=7246，张莉娜 id=7396。Join 三张表：

| project_id | 展示列 `bidding_leader_name` | 主投标 ID | 副投标 ID | 筛"陈梦瑶"是否命中 | 用户看到 |
|---|---|---|---|---|---|
| 114 | 陈梦瑶 | 7246 陈梦瑶 | NULL | ✓ 主匹配 | 陈梦瑶 ✓ |
| 136 | **张莉娜** | 7396 **张莉娜** | 7246 **陈梦瑶** | ✓ **副匹配** | **张莉娜** ✗ |
| 146 | 陈梦瑶 | 7246 陈梦瑶 | 7396 张莉娜 | ✓ 主匹配 | 陈梦瑶 ✓ |

**根因锁定**：project 136 主=张莉娜、副=陈梦瑶，筛"陈梦瑶"命中该项目（副匹配），但列表展示列只显示主负责人姓名"张莉娜"，用户误以为筛错了。

### Layer 3（git log 追溯）— 确认非回归

```bash
git log -S "secondaryBiddingLeaderId" --oneline -- src/views/Project/composables/useProjectFilter.js
# → 从 useProjectFilter 最初实现就存在 OR 语义，是历史设计
```

```bash
# V133 迁移添加 bidding_leader_name 列
git log --oneline -- backend/src/main/resources/db/migration-mysql/V133__project_list_initiation_alignment.sql
# → 从初始就只存主负责人姓名，没有副负责人姓名字段
```

**结论**：OR 语义从初始实现就存在，是历史设计问题，非回归。PR1574 让筛选真正生效后才被用户感知。

---

## 修复方案

### 方案选择（产品决策）

| 方案 | 改动 | 效果 |
|---|---|---|
| A. 筛选只匹配主负责人 | useProjectFilter.js 去掉 `p.secondaryBiddingLeaderId` | 副负责人筛不到，语义简单 |
| B. 展示列同时显示主+副 | 后端 enrich + 前端列改为"陈梦瑶、张莉娜" | 筛选结果可解释 |
| C. 副匹配时标记"（副）" | 前端筛选后给副匹配的项目加标记 | 最准确但改动大 |

用户选择方案 A：确认用户实际筛的是"投标负责人"，副负责人不参与筛选。

### 代码改动

**前端 `useProjectFilter.js`**（真正生效路径）：
```javascript
// 修复前
if (!matchId(f.biddingLeaderId, p.biddingLeaderId, p.secondaryBiddingLeaderId)) return false

// 修复后
// 投标负责人筛选只匹配主负责人（primaryLeadUserId）。
// 副负责人（secondaryLeadUserId）不参与筛选 — 否则用户筛"陈梦瑶"时，
// 主=张莉娜、副=陈梦瑶的项目也会出现，而列表只显示主负责人姓名，
// 用户会看到"张莉娜的项目"误以为筛错了。
if (!matchId(f.biddingLeaderId, p.biddingLeaderId)) return false
```

**后端 `ProjectController.java`**（dead code，但契约需一致）：
```java
// 修复前
if (biddingLeaderId != null) projects = projects.stream()
    .filter(p -> biddingLeaderId.equals(p.getBiddingLeaderId())
              || biddingLeaderId.equals(p.getSecondaryBiddingLeaderId())).toList();

// 修复后
// 投标负责人筛选只匹配主负责人，与前端 useProjectFilter 契约保持一致。
if (biddingLeaderId != null) projects = projects.stream()
    .filter(p -> biddingLeaderId.equals(p.getBiddingLeaderId())).toList();
```

**回归测试 `useProjectFilter.spec.js`**（新增）：
```javascript
it('筛"陈梦瑶"时，主=张莉娜/副=陈梦瑶的项目不再混入（核心回归用例）', async () => {
  // project 136：主=张莉娜(7396)、副=陈梦瑶(7246) — 之前会被错误命中
  // project 114：主=陈梦瑶(7246) — 应命中
  // project 146：主=陈梦瑶(7246)、副=张莉娜(7396) — 应命中
  // 筛陈梦瑶 → 只命中 114, 146
})
```

---

## 教训

### 1. 「筛选值==null 时永真」是隐形的 bug 放大器

`matchId` 函数第 35 行 `if (filterVal == null || filterVal === '') return true` 是合理的"空值不过滤"设计，但当 UserPicker 因 `value-key` 配置错误返回 `undefined` 时，`undefined == null` 为 true → 永远返回 true → 等于不筛选。

**这个 fallback 把"筛选不工作"伪装成"筛选正常但所有项目都显示"，掩盖了真正的 bug**。用户以为筛选在工作，实际根本没工作。

### 2. 修复一个 bug 可能暴露另一个隐藏 bug

PR1574 修复 UserPicker value-key 后，筛选真正生效，反而暴露了 OR 匹配语义问题。这是"修复一个 bug 暴露另一个 bug"的经典案例。排查时**不能因为"刚修过"就跳过验证**，必须确认修复是否真正生效，以及修复后是否暴露新的设计问题。

### 3. 筛选语义必须与展示列对齐

筛选用「主 OR 副」匹配，但展示列只显示主负责人姓名 → 用户看到"筛 A 命中 B"的错觉。

**检查清单**：
- 筛选匹配的字段范围 vs 展示列显示的字段范围是否对齐？
- 如果筛选匹配主+副，展示列是否也显示主+副？
- 如果展示列只显示主，筛选是否也只匹配主？

### 4. 展示用姓名、筛选用 ID 的双数据源设计需要强同步

| 字段用途 | 数据源 | 字段 |
|---|---|---|
| 展示 | `project_initiation_details` | `bidding_leader_name`（VARCHAR，只存主负责人） |
| 筛选 | `project_lead_assignment` | `primary_lead_user_id` + `secondary_lead_user_id`（BIGINT） |

两表无外键约束、无强同步机制。转派/投标负责人分配时如果只改 `primary_lead_user_id` 不回写 `bidding_leader_name`，就会出现"筛 A 命中但显示 B"的问题（本次 bug 的一部分）。

**顺带发现**：`project_leader_name` 列在生产数据中多为 NULL，立项表单没回写姓名列。如果用户去筛"项目负责人"，也会遇到类似的展示/筛选不一致。

### 5. 前端内存筛选 vs 后端筛选的契约一致性

前端 `useProjectFilter.js` 做内存筛选（不传参给后端），但后端 `ProjectController.java` 第 87 行也有同样的筛选逻辑（dead code，前端不传参）。**虽然后端代码不执行，但契约必须保持一致**，否则未来如果改成后端筛选，会踩坑。本次修复同步改了前后端。

---

## 验证命令

```bash
# 1. 确认 PR1574 是否真的部署（服务器前端 chunk）
ssh -i ~/.ssh/xiyu_cursor_deploy jetty@172.16.38.78 \
  'grep -oE "value-key[^,}]{0,50}" /srv/www/xiyu-bid/assets/UserPicker-Co7JMLd0.js'
# 期望：value-key":"value"

# 2. 数据库验证姓名/ID 是否不同步
mysql -h winbid-01.test.rds.ehsy.com -u ea_bid -p"***" winbid -e "
SELECT d.project_id, d.bidding_leader_name AS 展示姓名,
       a.primary_lead_user_id AS 主ID, u2.full_name AS 主姓名,
       a.secondary_lead_user_id AS 副ID, u3.full_name AS 副姓名
FROM project_initiation_details d
LEFT JOIN project_lead_assignment a ON a.project_id = d.project_id
LEFT JOIN users u2 ON u2.id = a.primary_lead_user_id
LEFT JOIN users u3 ON u3.id = a.secondary_lead_user_id
WHERE d.owner_user_id IN (7246,7396)
   OR a.primary_lead_user_id IN (7246,7396)
   OR a.secondary_lead_user_id IN (7246,7396);"

# 3. 前端回归测试
npx vitest run src/views/Project/composables/useProjectFilter.spec.js
```

---

## 相关文档

- §23 — 全链路日志排查 SOP（本次排查使用）
- §25 — 前端禁止 `catch { /* silent */ }` 吞掉 API 错误（同类：fallback 把 bug 伪装成正常）
- §35 — 筛选语义必须与展示列对齐（本次沉淀的新教训）
- PR !1574 — UserPicker value-key 修复（修复前掩盖了本次 bug）
- PR !1642 — 本次修复（投标负责人筛选只匹配主负责人）
- `src/views/Project/composables/useProjectFilter.js` — 前端筛选逻辑（修复后）
- `src/views/Project/composables/useProjectFilter.spec.js` — 回归测试
- `backend/src/main/java/com/xiyu/bid/project/controller/ProjectController.java` — 后端契约对齐
- `backend/src/main/java/com/xiyu/bid/project/service/ProjectQueryService.java` — enrich 逻辑（姓名与 ID 来源分叉点）
