---
title: 立项表单假0显示伪装成列表/详情数据不一致（PR !2436）
space: engineering
category: analysis
tags: [bug, 根因分析, 立项表单, 数值字段, 列表一致性]
created: 2026-08-27
updated: 2026-09-18
health_checked: 2026-10-25
archive: true
sources:
  - src/views/Project/stages/InitiationStage.vue
  - src/views/Project/stages/useInitiationStageActions.js
  - docs/lessons/lessons-learned.md
backlinks:
  - _index
---
# 立项表单假 0 显示伪装成列表/详情数据不一致

PR: https://gitee.com/allinai888/bid/pulls/2436

---

## 现场还原

测试环境 `/project/1365`：列表「计划入围供应商数量」显示 `-`，详情立项表单"看着有值"。表象像查询漏数，实为**前端显示层造假**：

1. 项目是当天测「关联标讯按钮→转立项」的半成品，`expected_bidders` / `planned_shortlisted_count` 从未持久化；
2. 列表三层兜底全空返回 null 是正确行为（1366/1367/1368 反向验证正常）；
3. 金额字段：初值 `0` + `handleAmountBlur` 空值静默回填 0；
4. 报障主字段 `expectedBidders` **从未绑 blur**：初值 `0` + `:min="1"` 被 InputNumber clamp 成 `1`。Jackson `non_null` 省略 null 字段后 `Object.assign` 覆盖不了初值。

## 排查手法（可直接复用）

| 步骤 | 动作 |
|---|---|
| DB 直查 | 同一业务字段的所有候选源逐一 `SELECT`（含新旧列并存场景，如 `shortlisted_count` vs `planned_shortlisted_count`） |
| API 双端实测 | 列表接口 + 详情接口同字段对比；必要时用服务器 JWT_SECRET 自签 token 过鉴权 |
| 反向验证 | 找库里**有值**的同类项目确认逻辑链路本身是好的，把"逻辑 bug"与"数据态差异"切分开 |
| 审计日志 | `audit_logs` 按 project_id 拉时间线，判断值"从未写入"还是"被覆盖清空" |

## 根治决策

- 初值语义缺陷在初值上根治：数值业务字段 `null` 即空，不静默回填 0；
- `handleAmountFocus/handleAmountBlur` 补零 hack 属于绕行补丁，随初值修复一并删除（连带 defineExpose 与模板绑定）；
- `depositAmount` 例外保持 0：onDepositChange 显式管理且默认隐藏；
- 回归必须覆盖「Jackson 省略 key + InputNumber min clamp」，只测 404 初值锁不住已有立项路径。

## 关联坑位

- 同一字段三名分裂：前端 `expectedBidders` / 后端 `plannedShortlistedCount` / CRM `planSupplierCount`，详见 lessons §169 遗留专项。
- dead fallback 教训：`autoFillFromTender` 的 `src.expectedBidders ?? ...` 引用了 tenders 表不存在的字段，跨实体映射必须先核对目标表列清单。
