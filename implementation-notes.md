# Implementation notes — CO-573 前端金额「分」比较修复

## 背景
PR !2048 审核指出：`ClosureStage.vue` 用 `Number(a) + Number(b) !== Number(dep)` 做金额等值，
存在 IEEE754 浮点误差风险（如 `10.1 + 20.2 !== 30.3`），可能误拦合法提交。

## 决策
- **方案**：按「分」整数比较（`Math.round(n * 100)`），不引入 decimal 库。
  - `toCents(v)` → 分
  - `moneyEquals(a, b)` → 两值分相等
  - `moneySumEquals(parts, total)` → 先分别 toCents 再整数累加，再与总额比
- **范围**：仅前端 `ClosureStage.vue` + 单测；后端已是 `BigDecimal.compareTo`，无需改。
- **未做**：未抽到 `src/utils`（仅结项一处使用，避免过度抽象）。

## 验证
- 新增 T7 回归：`10.1+20.2=30.3` 在裸 Number 下不相等，但 `canSubmit` 应通过。
- 既有 T1–T6 行为保持不变。
