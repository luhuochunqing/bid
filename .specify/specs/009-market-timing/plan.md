# Implementation Plan: Market Timing Prediction

**Branch**: `009-market-timing` | **Date**: 2026-05-23 | **Spec**: [spec.md](spec.md)

## Summary

在 AI Center 新增"商机时间预测"功能入口，复用已有后端 `marketprediction` 模块的 3 个 API 端点，参考 `MarketPredictionPage.vue` 的 UI 模式，以 AI Center 子页面形式集成。

## Implementation

### Task 1: AI config entry
- `src/config/ai-prompts.js` — Add `market-timing` entry in "投标准备" category

### Task 2: AI Center feature card
- `src/views/AI/Center.vue` — Add "商机时间预测" card in "投标准备" tab with `link: '/ai-center/market-timing'`

### Task 3: MarketTiming page
- `src/views/AI/MarketTiming.vue` — New page, reusing `MarketPredictionPage.vue` logic:
  - Summary stats bar (监测业主数, 高置信度, 近期招标)
  - Prediction card grid
  - Search filter
  - Calls `POST /api/market-prediction/batch`

### Task 4: Route
- `src/router/index.js` — Add `/ai-center/market-timing` route

## Verification
1. `npm run build`
2. `npm run check:line-budgets`
3. AI Center → "投标准备" tab → "商机时间预测" card → prediction page loads
