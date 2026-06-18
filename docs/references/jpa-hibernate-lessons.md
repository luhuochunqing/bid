# JPA / Hibernate 持久化层经验教训

> 来源：2026-06-18 标讯修改接口 evaluation 二次更新导致 500 事故复盘（PR784/PR785/PR786/PR787）
> 适用范围：所有使用 JPA `@OneToMany(cascade=ALL, orphanRemoval=true)` 的实体集合替换场景

## 0. 事故一句话总结

客户报障：标讯修改接口传 evaluation 数据时 500，**空数组也挂、有数据也挂、null 才不挂**。PR784/PR785/PR786 三次修复都未找到根因，PR787 通过测试复现 + 根因分析彻底修复。

## 1. 根因：Hibernate INSERT-before-DELETE flush 顺序

JPA 规范要求 Hibernate flush 时**先执行 INSERT 再执行 DELETE**。当实体集合有唯一约束（如 `uk_eval_role_info (evaluation_id, role_key, info_key)`）时，这个顺序会致命：

```
二次更新流程（错误）：
1. clear() 旧集合          → 标记旧实体为 removed（未执行 SQL）
2. add() 新行              → 标记新实体为 persist
3. save() 触发 flush：
   3a. INSERT 新行         → 旧行仍在数据库中 → 撞唯一约束 → 500
   3b. DELETE 旧行         → 永远到不了这里
```

**关键认知**：`clear()` 和 `add()` 在同一事务内时，Hibernate 不会自动中间 flush，INSERT 和 DELETE 在事务末尾一起执行，顺序固定是 INSERT 先。

## 2. 正确修复：通过父实体 cascade 触发 DELETE 并立即 flush

```java
// 正确方案（PR787）
if (evalEntity.getCustomerInfos() == null) {
    evalEntity.setCustomerInfos(new ArrayList<>());
}
if (!evalEntity.getCustomerInfos().isEmpty()) {
    evalEntity.getCustomerInfos().clear();                    // 标记 orphan
    tenderEvaluationRepository.saveAndFlush(evalEntity);      // 立即执行 DELETE SQL
}
// 此时旧行已从数据库删除，可以安全 INSERT 新行
for (...) {
    evalEntity.getCustomerInfos().add(row);
}
// 末尾的 tenderEvaluationRepository.save(evalEntity) 执行 INSERT SQL
```

**三个关键点**：
1. **`clear()` 而非 `deleteAll()`**：通过父实体集合的 `clear()` 触发 `orphanRemoval`，与 cascade 机制一致
2. **`saveAndFlush()` 立即执行**：确保 DELETE SQL 真正执行到数据库，清空持久化上下文
3. **`add()` 而非 `setCustomerInfos(newRows)`**：用 `add()` 增量添加，避免替换集合引用与 `PersistentCollection` 快照机制冲突

## 3. 错误方案对比（为什么 PR784/PR785/PR786 都失败）

| PR | 方案 | 失败原因 |
|---|---|---|
| PR784 | 加 `hasEvaluationData` 判断跳过空数组 | 只规避"空数组"场景，"有数据"场景仍挂 |
| PR785 | `deleteAll + clear`（无 flush） | INSERT 新行时旧行未 DELETE，撞唯一约束 |
| PR786 | `deleteAll + flush + clear`（通过子 repository） | 通过子 repository `deleteAll` 绕过父实体 cascade，持久化上下文不一致；`clear()` 后父实体 `merge()` 时与 orphanRemoval 冲突 |

### PR786 的具体陷阱

```java
// PR786 错误方案
customerInfoRepository.deleteAll(evalEntity.getCustomerInfos());  // 通过子 repo 删除
customerInfoRepository.flush();                                   // 执行 DELETE SQL
evalEntity.getCustomerInfos().clear();                            // 清空父实体集合
// ... add 新行 ...
tenderEvaluationRepository.save(evalEntity);                      // merge() 时与 orphanRemoval 冲突
```

**问题**：`TenderEvaluation.customerInfos` 是 `@OneToMany(cascade=ALL, orphanRemoval=true)`，子实体的生命周期由**父实体**管理。通过**子 repository** `deleteAll` 删除绕过了父实体的 cascade 机制，导致：
- 父实体 `evalEntity` 的 `PersistentCollection` 集合快照未同步
- 末尾 `save(evalEntity)` → `merge()` 时 Hibernate 重新 reconcile 集合
- 与 `orphanRemoval` 机制冲突，旧行可能被重新插入或 DELETE 未生效
- INSERT 新行时撞唯一约束 → 500

**测试证据**：用 5 个 JPA 集成测试验证 PR786 方案，2 个测试失败，仍报 `JdbcSQLIntegrityConstraintViolationException: uk_eval_role_info`。

## 4. 测试必须覆盖"二次更新"场景

本次事故的测试盲区：**没有任何针对 `saveEvaluation` 的单元/集成测试**。PR784/PR785/PR786 三次修复都没发现问题的原因就是没测试。

### 必测场景

1. **首次保存**：customerInfos 从 null → 有数据
2. **二次更新相同 key**（bug 复现场景）：customerInfos 已有数据 → 更新相同 roleKey+infoKey 的值
3. **二次更新不同 key**：customerInfos 已有数据 → 替换为不同 roleKey+infoKey
4. **二次更新空数组**：customerInfos 已有数据 → 传入空数组清空
5. **多角色多维度二次更新**：覆盖 14 角色 × 17 维度的真实场景

### 测试技术要点

- 用 `@DataJpaTest` + H2 内存数据库 + `ddl-auto=create-drop`
- H2 会自动从 `@UniqueConstraint` 创建唯一约束，可稳定复现 bug
- 用反射调用 private 方法，聚焦测试 JPA 层行为，避免拉起完整 Spring 上下文
- 参考实现：`backend/src/test/java/com/xiyu/bid/integration/external/TenderIntegrationServiceEvaluationTest.java`

## 5. 诊断方法论：为什么"昨天晚上还可以，后面不行了"

客户报障现象的根因解释：

| 现象 | 原因 |
|---|---|
| 有数据也挂 | INSERT 新行时旧行未 DELETE，违反唯一约束 |
| 空数组也挂 | `deleteAll + clear` 双重标记 removed 触发异常 |
| null 不报错 | 跳过 `saveEvaluation`，不触发任何 DB 操作 |
| 昨天晚上还可以，后面不行了 | **首次创建** evaluation 时 evalEntity 是新建的、customerInfos 为 null，不进 deleteAll 分支，INSERT 不冲突；**二次更新**时 evalEntity 已有旧 customerInfos，触发唯一约束冲突 |

**关键认知**：这类 bug 具有"首次成功、二次失败"的特征。测试如果只测首次保存，永远发现不了问题。

## 6. 通用规则：JPA 集合替换 + 唯一约束 = 高危场景

任何满足以下条件的代码都是高危场景，必须按本文档 §2 的方案实现并补测试：

1. 实体有 `@OneToMany(cascade=ALL, orphanRemoval=true)` 集合
2. 集合对应的表有唯一约束（含复合唯一约束）
3. 业务逻辑是"全量替换"集合（先删后增）

### 项目内已知的高危场景

- `TenderEvaluation.customerInfos`（本次事故，已修复）
- 任何其他 EAV 模式存储的实体（`roleKey + infoKey` 复合唯一约束）
- 任何"展平格式 → EAV"的保存逻辑

### 排查方法

```bash
# 搜索所有 @OneToMany(cascade=ALL, orphanRemoval=true) 的集合
grep -rn "orphanRemoval = true" backend/src/main/java/

# 搜索所有 @UniqueConstraint 注解
grep -rn "@UniqueConstraint" backend/src/main/java/

# 交叉比对：集合对应的表有唯一约束的就是高危场景
```

## 7. PR 修复的纪律

本次事故有 4 个 PR（784/785/786/787），前 3 个都失败。教训：

1. **不要试错式修复**：PR784/PR785/PR786 都在调整 `deleteAll/clear/flush` 的顺序，属于试错。应先用测试复现 bug，再针对根因修复。
2. **修复前先写失败测试**：先写一个能复现 bug 的测试（红），再修复代码让测试通过（绿）。PR787 就是这个流程。
3. **补测试是修复的一部分**：没有测试的修复等于没修复——下次回归还会犯同样错误。
4. **根因分析 > 症状掩盖**：PR784 加 `hasEvaluationData` 判断是症状掩盖，不是根因修复。

## 相关文档

- [crm-integration-lessons.md](./crm-integration-lessons.md) — CRM 外部系统对接经验教训
- [TenderIntegrationServiceEvaluationTest.java](../../backend/src/test/java/com/xiyu/bid/integration/external/TenderIntegrationServiceEvaluationTest.java) — 本次修复的测试实现
- [TenderEvaluationCustomerInfo.java](../../backend/src/main/java/com/xiyu/bid/tender/entity/TenderEvaluationCustomerInfo.java) — 唯一约束定义位置
