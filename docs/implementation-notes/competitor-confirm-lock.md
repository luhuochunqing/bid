# 实现笔记：结果登记竞品二次确认 + 登记后锁定

- 日期：2026-08-27
- 分支：`agent/codex/competitor-confirm-lock`
- 需求来源：口头需求（无 CO 编号）——「项目中心-项目详情-结果确认阶段：填写输入竞品确认时，需要给二次确认的提示，登记以后竞争对手情况不能再修改」

## 需求拆解与现状结论

| 需求点 | 现状 | 本次动作 |
|---|---|---|
| 登记前二次确认提示 | 缺失：`submit()` 校验通过直接调 API | 新增 `ElMessageBox.confirm` |
| 登记后竞品不可修改 | **前后端早已闭环** | 仅补 UI 显性标识，无数据层改动 |

"登记后不可修改"原本就有三重保障（本次验证过、未改动）：

1. 后端 `ProjectResultRegistrationService.register()`：同项目已登记 → `409 CONFLICT("项目结果已登记，不可重复提交")`；且 CLOSED 阶段经 `ProjectFieldLockPolicy.assertWritable` 拒写；全仓**不存在**结果更新接口。
2. 前端：`registeredAt` 存在 → `resultDone=true` → 全部输入置灰、「添加一行/删除」隐藏、提交按钮换成禁用的「已登记」。
3. 竞品行以子表（`ProjectResultCompetitor`）持久化，仅随首次登记写入。

## 关键决策（spec 未明确的取舍）

1. **二次确认弹窗放在所有必填校验之后**。避免用户表单填一半就被弹窗打断；也保证弹窗文案里的竞品家数是过滤空行后的真实数字。
2. **弹窗文案动态化**：已填 N 行竞品 → 「已填写 N 家竞争对手情况」；一行没填 → 「尚未填写竞争对手情况」。需求原文只说竞品确认，但投标结果登记本身也是不可逆操作，文案一并覆盖（见第 4 点 tradeoff）。
3. **用户取消（cancel）静默返回**，不弹"已取消"之类的 info 提示——弹窗本身就是明确的退出动作，再弹一层提示属噪音。
4. **警示范围从"竞品"扩大到"结果+竞品整体"**：技术层面上整个登记是一次原子事务（409 幂等锁），单独强调竞品会让用户误以为其他字段还能改。若产品后续要求"竞品可后期修正"，那是一个独立的后端功能（部分更新接口 + 字段级锁），不是前端能解的。
5. **登记后在竞品卡片 header 加 `已登记 · 锁定不可修改` tag**：把不可变事实显性传达给验收方。原实现只有整页置灰，语义不够醒目。
6. **`defineExpose` 追加 `form`/`submit`**：jsdom 里模拟 el-upload 直传链路拿到 evidenceFileIds 成本高，测试直接写入 form 再点按钮走完整提交路径。暴露面扩大限于测试用途，接受。

## 排查中的发现（不改动，仅记录）

- 项目详情存在一条**旧的竞品录入旁路**：`ProjectDetailResultDialogs.vue`（"投标结果录入"弹窗）+ `useProjectDetailProjectOps.js#handleSaveResult`，它直接 `projectStore.updateProject` 写 `resultData.competitors`，无锁定、可反复覆盖。但触发函数 `handleRecordResult` 已不被任何模板引用，属于死代码（不可达 UI）。**本次未触碰**；建议后续任务清理，否则容易误导后续开发者以为存在双链路。
- 该组件 `notes` 字段历史上是自由文本，现在 UI 上存「是否中标」枚举（代码内已有注释），与本次改动无关。

## 改动清单

- `src/views/Project/stages/ResultConfirmStage.vue`
  - submit 流程插入二次确认（校验后、API 前）
  - 竞品卡片 header 加锁定 tag（resultDone 时）
  - defineExpose 暴露 form/submit
- `src/views/Project/stages/ResultConfirmStage.spec.js`
  - mock `ElMessageBox.confirm` 与竞品字典 API
  - 新增 5 个用例：取消不提交 / 文案含竞品数+不可修改警示 / 未填竞品文案 / 确认后正常提交并进入锁定态 / 必填校验失败不弹窗

## 测试证据

```
npx vitest run src/views/Project/stages/ResultConfirmStage.spec.js
Test Files  1 passed (1)
     Tests  9 passed (9)   # 4 旧 + 5 新
```
