# CO-431 关联 CRM 商机时客户职位映射错误 — 实施笔记

> 任务：关联 CRM 商机时，客户对接人职位信息映射错误（CRM「招标文件制作人」→ 投标系统「项目最高决策人」）。
> 分支：`agent/zcode/co-431-crm-position-mapping`
> 入口 ticket：[CO-431](https://linear.app/ericforai/issue/CO-431)
> 阶段：**诊断阶段（仅加日志，未改业务逻辑）**

## 1. 重要的方向修正：第一轮方案是被回滚过的

### 第一轮判断（错误）

初读 `useCrmOpportunitySelector.js:190` 看到 `CUSTOMER_INFO_ROWS[idx % 14].roleKey` 按下标分配，且 `CRM_POSITION_TO_ROLE` 已 import 但未用，判断根因为"前端按下标分配 roleKey"，提议用 `CRM_POSITION_TO_ROLE[c.position]` 字典映射。

### 用户要求按 lessons-learned §23【全链路日志排查 SOP】复查

git log 揭示 CO-329 已经走过这条路：

| 时间 | commit | 改动 | 结果 |
|---|---|---|---|
| 6/27 13:55 | `245fc816c` | 用 `CRM_POSITION_TO_ROLE[c.position]` 字典映射 | **被判定"invalid"** |
| 6/27 20:45 | `41f02ccc1` | **回滚**到 `CUSTOMER_INFO_ROWS[idx % 14]`，msg: "revert **invalid** c.position mapping" | 当前现状 |

**第一轮方案正是 7 小时后被回滚的那个方案。** 如果直接照抄就重蹈覆辙（lessons §4 警告）。

## 2. 按 SOP 查到的真实证据（非猜测）

CO-329 评论里有两份**真实抓包/日志证据**，但互相矛盾：

| 证据 | 来源 | 内容 | position 字段 |
|---|---|---|---|
| ① 生产日志 | eric 06-26 02:44 | `{"code":"0","data":[{"name":...,"position":"1",...}]}` | **有，值 "1"**（数字字符串） |
| ② F12 抓包 | dom.chuya 06-26 08:43 | `{"id":15476,"name":"张三","phone":...}` | **没有这个字段** |

回滚 commit `41f02ccc1` 的依据是 eric 06-27 13:57 的评论："实际上 CRM 接口响应里根本不存在 position 这个字段"。

## 3. 用代码证据解释矛盾 — eric 当年误判了

`application.yml:57` 配置 `default-property-inclusion: non_null`。后端 `ContactPersonInfoVO` 是 record，`position` 为 null 时 Jackson 序列化会**丢弃该字段**。

所以真相是：
- CRM 上游对接人**填了职位** → 返回 `position:"1"`（证据①）→ 后端透传 → 前端能看到
- CRM 上游对接人**没填职位** → CRM 不返回 position → 后端 VO position=null → `non_null` 丢掉 → 前端看不到（证据②）

eric 把"某个没填职位的对接人导致 position 缺失"误判成"接口根本不返回 position"，于是回滚正确方向改用 idx 分配。结果：
- 短期"14 个值都带过来"（不丢人，用户以为修好）
- 长期**填了职位的对接人也被按 idx 错位分配** ← CO-431 报的现象

## 4. SOP §23 第 4 条「禁止乱猜」— 必须先确诊

issue CO-431 描述"CRM 中填写的「招标文件制作人」"暗示 position 可能是**中文职位名**而非数字。如果是中文，`CRM_POSITION_TO_ROLE["招标文件制作人"]`=undefined 会再次导致对接人被过滤。

本地无法确诊：dev 环境 backend.log 无 contact-person 调用记录，MySQL 未连接。

**结论：不能贸然改业务逻辑，先加诊断日志确诊 position 真实格式。**

## 5. 本次改动（仅诊断日志，不改业务逻辑）

### 改动文件

| 文件 | 改动 |
|---|---|
| `backend/src/main/java/com/xiyu/bid/crm/application/CrmContactPersonService.java` | `parseListResponse` 加 3 行 INFO 日志，打印每条对接人的 `id`/`name`/`position` 原始值（从 JsonNode 取，绕过 non_null） |
| `backend/src/test/java/com/xiyu/bid/crm/application/CrmContactPersonServiceTest.java` | 加 ListAppender 日志断言模式 + 2 个测试（含 position / 缺失 position） |

### 日志格式

```
CRM contact-person raw position: id=1, name=张三, position=8
CRM contact-person raw position: id=2, name=李四, position=<missing>
```

`<missing>` 标记缺失的 position，方便 grep。

### 设计要点

1. 从 `resolved.node()`（JsonNode）取 position 原始值，**绕过 non_null 序列化**——这是关键，否则 null position 会被吞，日志看不到真相
2. 只加日志，不动返回值、不改解析逻辑、不影响现有 8 个测试
3. 复刻项目已有的 `OperationLogAspectTest` 日志断言模式（ListAppender），不引入新依赖

## 6. TDD 验证

- **Red**：2 个新测试失败（当前日志不含 position 原始值），原有 8 个全绿
- **Green**：加日志后 10 个测试全绿
- 命令：`cd backend && mvn -o test -Dtest=CrmContactPersonServiceTest`

## 7. 部署后验证清单（交给用户）

部署后需要做一次关联 CRM 商机操作（选一个对接人填了职位的商机），然后看日志：

```bash
# 在生产服务器上
journalctl -u xiyu-bid-backend --since "10 minutes ago" | grep "CRM contact-person raw position"
```

预期能看到类似：
- `position=8` → 数字格式，对应"招标文件制作人"，可放心用 `CRM_POSITION_TO_ROLE` 映射
- `position=招标文件制作人` → 中文格式，需要新建中文→roleKey 映射表
- `position=<missing>` → CRM 后台没填职位，需要回退策略

确诊后才能定第二阶段（改前端映射）的具体方案。**这是 lessons §23「禁止乱猜」的硬要求。**

## 8. 不做的事（防顺手重构）

- ❌ 不改前端 `useCrmOpportunitySelector.js`（等诊断结果）
- ❌ 不改 `customerInfoMatrixConfig.js`（映射表本身没错）
- ❌ 不改 `ContactPersonInfoVO`（VO 已正确透传 position）
- ❌ 不动 `non_null` 全局配置（影响面太大，与本任务无关）
- ❌ 不删 `CRM_POSITION_TO_ROLE` import（dead import 待第二阶段处理）
