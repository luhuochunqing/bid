---
title: 标讯人工创建截止时间零点格式化规则与排错
space: engineering
category: pitfalls
tags: [标讯, 人工创建, formatManualTenderDeadline, 日期格式化, 0点0分, 时间改写]
created: 2026-10-10
updated: 2026-10-10
health_checked: 2026-10-25
sources:
  - src/views/Bidding/list/helpers.js
  - src/views/Bidding/list/manualTenderParseHelpers.js
backlinks:
  - _index
---

# 标讯人工创建截止时间零点格式化规则与排错

> 生产案例（2026-10-10）：生产环境人工创建标讯时，用户选择截止时间为 `00:00`（如 `2026-10-31 00:00`），开标时间设为同日中午（`2026-10-31 12:00`），开标时间明显晚于截止时间，但提交时后端报错：“开标时间必须大于等于报名截止时间”。

## 1. 现象与根因

### 1.1 表面现象
前端提交报错 `开标时间必须大于等于报名截止时间`。

### 1.2 根因定位
在 `src/views/Bidding/list/helpers.js` 的 `formatManualTenderDeadline` 中，为了让纯日期输入（如 `2026-10-31`）默认在当天最后一秒截止，原实现使用了基于时间的判断：
```javascript
if (date.getHours() === 0 && date.getMinutes() === 0 && date.getSeconds() === 0) {
  date.setHours(23, 59, 59, 0)
}
```
**隐患**：当用户在 UI 时间选择器中显式选择了 `00:00`（或者传入包含 `00:00` 的完整时间）时，`date.getHours() === 0 && date.getMinutes() === 0` 同样成立，导致用户的 `00:00:00` 被强行改写为 `23:59:59`。若开标时间是当天 `12:00:00`，改写后的报名截止时间（`23:59:59`）晚于开标时间（`12:00:00`），直接触发后端 `TenderBasicInfoValidator.validateBasicInfo` 校验阻断。

## 2. 规范契约与修复原则

1. **禁止用 `Date.getHours() === 0` 识别“纯日期”**：
   - 必须在字符串输入层区分 `date-only`（`YYYY-MM-DD`）与包含时间的输入（含空格或 `T`）。
   - 只有纯日期字符串（`YYYY-MM-DD`）才补齐为 `23:59:59`。
2. **显式时间输入保持原样**：
   - 字符串中显式包含时间的输入（如 `2026-10-31 00:00`、`2026-10-31T00:00:00`），格式化后严格保留 `00:00:00`，不得改写为 `23:59:59`。
   - `Date` 对象输入保持原时分秒，禁止篡改。
3. **解析与创建双向对齐**：
   - `src/views/Bidding/list/helpers.js` 与 `src/views/Bidding/list/manualTenderParseHelpers.js` 统一按此规范处理。
