## ADDED Requirements

### Requirement: Router Configuration (REQ-KB-001)
知识库 SHALL 提供 `/knowledge` 基础路由，并且支持档案台账、案例库、资质列表、保证金看板的子路由切换。

#### Scenario: Route navigation success
- **WHEN** user navigates to `/knowledge/archive`
- **THEN** project archive page is rendered correctly

### Requirement: File Category Popover (REQ-KB-002)
文档分类气泡浮层 SHALL 在鼠标悬停 0.5 秒后展示该项目在各个分类（如：商务文件、技术文件、澄清文件等）下的文件总数。

#### Scenario: Popover hover display
- **WHEN** user hovers over the category popover trigger for 0.5 seconds
- **THEN** tooltip displaying the file counts of various categories appears

### Requirement: Project Archive Ledger View (REQ-KB-003)
项目档案台账视图 SHALL 运用 El-Table 展示项目列表，支持双轴日期选择（上传区间、结项区间独立筛选）和文档分类筛选。

#### Scenario: Ledger filter by dates and category
- **WHEN** user selects independent upload date range and project completion date range
- **AND** selects a document category filter
- **THEN** list queries and displays matching project archive items

### Requirement: Archive Detail Drawer (REQ-KB-004)
点击台账行时，系统 SHALL 右滑展示 60% 宽度的只读详情抽屉，展示项目基础信息、倒序文件列表以及文件预览/下载审计日志轴。

#### Scenario: Open detail drawer
- **WHEN** user clicks a row in the ledger table
- **THEN** a 60% width drawer slides in from the right containing project details, reversed file list, and audit timeline
