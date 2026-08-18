# Research: 历史投标项目信息批量导入

**Feature**: 041-historical-project-import
**Date**: 2026-08-06

## Research Tasks

### R-001: 批量导入行数上限与异步策略

**Question**: 采集表行数超过 1000 行如何处理？是否需要异步导入 + 进度反馈？

**Decision**: 限制单次导入 500 行（FR-012）。超过 500 行时，接口返回 400 错误，提示"单次导入上限 500 行，请分批上传"。采用同步导入，不引入异步。

**Rationale**:
- 历史项目导入是低频操作（通常一次性），500 行覆盖绝大多数场景
- 同步导入简单可靠，500 行 × 6 表写入 ≈ 3000 条 INSERT，MySQL 本地 <30 秒
- 异步导入需要引入任务表 + 轮询接口 + 进度推送，过度工程化（违反 Constitution VIII）
- SC-001 要求 100 行 <5 分钟，500 行留 5x 余量

**Alternatives considered**:
- 异步导入 + Redis 进度缓存（类似标讯批量导入 spec 031）：否决，复杂度过高
- 分批自动提交（每 100 行一批）：否决，单行独立提交已足够，分批增加事务复杂度

---

### R-002: 重复项目名称与幂等性

**Question**: 重复导入同一份采集表如何处理？是否支持幂等？

**Decision**: 按 `external_id` 前缀 `HISTORICAL_{项目名称hash}` 去重。导入前查询已存在的占位标讯 external_id，匹配则跳过该行并记入"已存在跳过"清单。

**Rationale**:
- external_id 是 tenders 表的唯一索引（UK_i07ipdk97nopwineqty0816ml），天然适合做幂等键
- 项目名称在采集表中可能重复（不同年份的同名项目），所以用 `HISTORICAL_{MD5(项目名称+招标主体)}` 而非纯项目名称
- 跳过策略比覆盖更安全（避免误覆盖已修改的数据）
- "已存在跳过"作为独立分类，区别于"成功"和"失败"

**Alternatives considered**:
- 按 projects.name 去重：否决，名称可能重复
- 覆盖更新：否决，风险过高
- 不做幂等，每次导入都创建新记录：否决，违反 SC-006

---

### R-003: 开标时间格式解析

**Question**: 采集表的"开标时间"格式不规范（只有日期、文本格式"2025年6月20日"）如何解析？

**Decision**: Apache POI 读取 Excel 时，若单元格类型为 DATE，直接取 LocalDate；若为 STRING，尝试以下格式解析：
1. `yyyy-MM-dd HH:mm:ss`
2. `yyyy-MM-dd HH:mm`
3. `yyyy-MM-dd`
4. `yyyy年MM月dd日 HH:mm`
5. `yyyy年MM月dd日`

解析失败记为"开标时间格式无法识别"失败。

**Rationale**:
- Excel 日期单元格 POI 直接返回 Date/LocalDate，无需字符串解析
- 字符串格式覆盖常见的中文日期写法
- 解析失败明确报错，不让脏数据进入数据库

**Alternatives considered**:
- 只接受 Excel DATE 类型：否决，用户可能用纯文本填写
- 用正则万能解析：否决，过度复杂

---

### R-004: 客户营收单位转换

**Question**: 采集表"客户营收(亿)"如何转换单位？

**Decision**: 采集表单位为亿（用户已确认），数据库 `project_initiation_details.annual_revenue` 存储单位为元。导入时 `value × 100000000` 转换。

**Rationale**:
- 用户已明确确认单位是亿
- annual_revenue 字段类型 DECIMAL(20,2)，最大 99999999999999.99 元 ≈ 999999 亿，容量充足
- 转换在纯核心 HistoricalProjectRowMapper 中完成，可单元测试

**Alternatives considered**:
- 存储为亿：否决，与现有 project_initiation_details 数据不一致
- 存储为万：否决，用户已确认单位是亿

---

### R-005: 占位标讯在标讯列表页的显示

**Question**: 历史项目的占位标讯是否需要在标讯列表页显示？

**Decision**: 占位标讯用 `external_id` 前缀 `HISTORICAL_` 标记。标讯列表查询接口默认过滤 `HISTORICAL_` 前缀，不展示占位标讯。项目详情页联动加载时按 tenderId 直接查询，不受过滤影响。

**Rationale**:
- 占位标讯是为历史项目创建的关联数据，不是真实标讯
- 在标讯列表页展示会污染标讯数据，用户困惑
- 项目详情页按 ID 精确查询，不受列表过滤影响
- 现有标讯查询接口 `TenderRepository` 需要加过滤条件（或新增 `findByExternalIdStartingWith(String prefix)` 反向查询）

**Alternatives considered**:
- 新建 `historical_tenders` 表：否决，过度工程化，且需要改动标讯关联逻辑
- 占位标讯软删除：否决，项目详情页需要关联查询
- 不过滤：否决，污染标讯列表

---

### R-006: 权限键注册

**Question**: 导入接口用什么权限键？

**Decision**: 新增权限键 `project:import`，在 `RoleProfileCatalog.java` 的 admin 角色中注册，通过 Flyway 迁移同步。

**Rationale**:
- 遵循 Constitution VI（Authorization Unification），用 `hasAuthority('project:import')`
- admin 角色已有项目管理权限，导入是管理操作
- 不用 `hasRole('ADMIN')`，遵循 Constitution VI 禁止形态

**Alternatives considered**:
- 复用 `project:create`：否决，导入是批量操作，语义不同
- 用 `hasRole('ADMIN')`：否决，Constitution VI 禁止

---

### R-007: 项目负责人与投标负责人是同一人

**Question**: "投标负责人"和"项目负责人"是同一个人时如何处理？

**Decision**: 允许同一人同时担任。`projects.manager_id` 和 `project_lead_assignment.primary_lead_user_id` 可以是同一个 user_id。

**Rationale**:
- 业务上允许一人多职
- 两个字段语义不同：manager_id 是项目经理（投标负责人），primary_lead_user_id 是项目负责人
- 无需特殊处理，直接写入即可

---

### R-008: 导入失败回滚策略

**Question**: 导入过程中某行失败，已成功的行是否回滚？

**Decision**: 每行独立事务。某行失败时，该行不写入数据库（事务回滚），但不影响已成功的行。失败行记入失败清单，导入结束后返回。

**Rationale**:
- 批量导入场景下，全量回滚不可接受（一行脏数据导致整批失败）
- 每行独立事务，失败行回滚，成功行提交
- 用 Spring `@Transactional(propagation = REQUIRES_NEW)` 为每行开新事务
- 失败清单让管理员修正后重导失败行

**Alternatives considered**:
- 全量事务（全部成功或全部失败）：否决，一行脏数据阻塞整批
- 分批事务（每 100 行一批）：否决，粒度过粗，一批中一行失败导致整批回滚
