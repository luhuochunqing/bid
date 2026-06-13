# Tasks: AI Solution Reuse Center

**Input**: spec.md, plan.md from specs/007-solution-reuse/

## Format: `[ID] [P?] [Story] Description`

## Tasks

### Task 1 [US1] Add solution-reuse config entry
- File: `src/config/ai-prompts.js`
- Add new config entry for `solution-reuse` capability in the 标书编制 (DRAFTING) category
- Include feature title "历史方案提取与复用", description, and placeholder prompt template

### Task 2 [US1] Add feature card to AI Center
- File: `src/views/AI/Center.vue`
- Add 3rd FeatureCard in the "标书编制" tab panel
- Key: `solution-reuse`, click navigates to `/ai-center/solution-reuse`
- Icon: document + search style

### Task 3 [US1] Create SolutionReuse.vue page
- File: `src/views/AI/SolutionReuse.vue`
- Search bar with keyword input and search button
- Filters: industry dropdown, project type dropdown
- Results list showing: solution name, source project, industry, date
- Loading / empty / error states
- Click result → emits event to open detail drawer

### Task 4 [US1] Add route for solution-reuse
- File: `src/router/index.js`
- Add child route `/ai-center/solution-reuse` under the ai-center parent (or as sibling)
- Route name: `SolutionReuse`, component: `@/views/AI/SolutionReuse.vue`

### Task 5 [US2] Create SolutionReuseDrawer.vue
- File: `src/views/AI/components/SolutionReuseDrawer.vue`
- Drawer shows full solution content with metadata header
- Sections labeled with original project context
- "复制内容" button copies section text to clipboard
- Close button and click-outside-to-close
