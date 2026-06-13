# Feature Specification: Market Timing Prediction

**Feature Branch**: `009-market-timing`

**Created**: 2026-05-23

**Status**: Draft

**Input**: User description: "AI Center 新增商机时间预测能力，基于历史投标数据分析招标窗口期，自动预测下次招标时间"

## User Scenarios & Testing

### User Story 1 - View Market Timing Predictions (Priority: P1)

As a bid preparation user, I want to open the "商机时间预测" page from the AI Center and see a dashboard of predicted tender timing for monitored purchasers, so that I can proactively prepare for upcoming bidding opportunities.

**Why this priority**: This is the core feature — showing prediction data for known purchasers.

**Independent Test**: Navigate to the prediction page and verify prediction cards render with timing data.

**Acceptance Scenarios**:

1. **Given** I am on the AI Center page, **When** I click the "商机时间预测" feature card, **Then** I am navigated to the market timing prediction page
2. **Given** I am on the prediction page, **When** the page loads, **Then** prediction cards are displayed with purchaser name, predicted next tender date, and confidence level
3. **Given** prediction cards are displayed, **When** a prediction has high confidence (>80%), **Then** it is visually highlighted

---

### User Story 2 - Understand Prediction Details (Priority: P2)

As a bid preparation user, I want to see the details behind a prediction, including historical tender dates and confidence metrics, so that I can assess the reliability of the prediction.

**Why this priority**: Transparency builds trust in the AI prediction.

**Independent Test**: Click on a prediction card and verify the detail drawer shows historical data and confidence breakdown.

**Acceptance Scenarios**:

1. **Given** prediction cards are displayed, **When** I click a card, **Then** a detail view shows historical tender dates and interval analysis
2. **Given** the detail view is open, **When** I view the data, **Then** the confidence score is explained with contributing factors
3. **Given** no prediction data exists for a purchaser, **When** viewed, **Then** a "暂无足够历史数据" message is displayed

### Edge Cases

- What if no purchasers have enough data for prediction? → Show "暂无预测数据，需要至少 2 条历史标讯记录"
- What if the backend API fails? → Show error state with retry button
- What if there are many purchasers? → Show summary stats (total monitored, high-confidence count)

## Requirements

### Functional Requirements

- **FR-001**: Users MUST be able to navigate from AI Center to a dedicated "商机时间预测" page
- **FR-002**: The page MUST display prediction cards with: purchaser name, predicted next tender date, confidence percentage
- **FR-003**: Predictions MUST be fetched via `POST /api/market-prediction/batch`
- **FR-004**: High-confidence predictions (>80%) MUST be visually distinguished
- **FR-005**: Users MUST be able to view prediction details including historical tender dates
- **FR-006**: System MUST handle empty/no-data states with informative messages
- **FR-007**: The feature card in AI Center MUST be in the "投标准备" tab

### Key Entities

- **MarketTimingPrediction**: prediction result per purchaser containing next tender date, confidence, historical count, average interval
- **PredictionBatchQuery**: list of purchaser hashes to query
- **PredictionConfig**: minimum historical records required (from GET /api/market-prediction/config/min-count)

## Success Criteria

### Measurable Outcomes

- **SC-001**: Users can reach prediction results in under 3 clicks from AI Center
- **SC-002**: Prediction cards render within 3 seconds of page load
- **SC-003**: All states (loading, empty, error, results) render correctly without console errors

## Assumptions

- Backend marketprediction API is stable and returns correct DTO format
- Purchaser hashes will be derived from existing tender data
- Page will integrate into the existing AI Center sub-page pattern (like solution-reuse)
- Mobile support is out of scope for v1 (AI Center is desktop-first)
