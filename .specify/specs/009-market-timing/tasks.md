# Tasks: Market Timing Prediction

**Input**: spec.md, plan.md from specs/009-market-timing/

## Tasks

### Task 1 [US1] Add market-timing config entry
- File: `src/config/ai-prompts.js`
- Add new config entry for `market-timing` in the 投标准备 (prepare) category
- Title: "商机时间预测", description about predicting tender timing

### Task 2 [US1] Add feature card to AI Center
- File: `src/views/AI/Center.vue`
- Add card in "投标准备" tab with `link: '/ai-center/market-timing'`

### Task 3 [US1] Create MarketTiming.vue page
- File: `src/views/AI/MarketTiming.vue`
- Summary stats bar: total monitored, high confidence count
- Prediction card grid with purchaser name, next date, confidence
- Search/filter for purchaser names
- Calls POST /api/market-prediction/batch
- Loading / empty / error states

### Task 4 [US1] Add route
- File: `src/router/index.js`
- Add sibling route `/ai-center/market-timing`
