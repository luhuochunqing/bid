# Feature Specification: 品牌授权 — 原厂授权核心 §4.6a

**Feature Branch**: `013-brand-auth-manufacturer`

**Created**: 2026-05-30

**Status**: Draft

**Input**: User description: "品牌授权 §4.6a — 原厂授权核心 CRUD + 作废 + 附件 + 权限。重构现有品牌授权模块中原厂授权数据模型和功能，对齐产品蓝图 §4.6 规范。"

## User Scenarios & Testing

### User Story 1 — 新增原厂授权 (Priority: P1)

投标管理员、投标组长或投标专员需要在资源管理 → 品牌授权 → 原厂授权 Tab 下，
点击"+ 新增原厂授权"按钮打开抽屉表单，按 3 段分组填写 10 个字段后保存。
保存成功后授权状态为"生效中"，列表自动刷新，操作日志自动记录。

**Why this priority**: 新增是模块的入口功能，无数据则后续所有操作无法进行。

**Independent Test**: 以 bid_admin 身份登录，打开品牌授权页，点击新增按钮，
填写必填字段，上传附件，保存后在列表中看到新记录。

**Acceptance Scenarios**:

1. **Given** 用户已登录且有新增权限 (bid_admin/bid_lead/bid_specialist)，**When** 点击"+ 新增原厂授权"按钮，**Then** 打开右侧抽屉，显示 3 段分组表单（基础信息区/授权信息区/补充信息区）
2. **Given** 抽屉表单已打开，**When** 用户未填必填项直接保存，**Then** 必填字段标红，抽屉自动滚到首个错误位置，不关闭抽屉
3. **Given** 抽屉表单已打开，**When** 授权结束时间 ≤ 开始时间，**Then** 红字阻断"结束时间须晚于开始时间"，保存按钮可点击但提交时报错
4. **Given** 抽屉表单已打开，**When** 品牌+品牌原厂名称+一级产线与已有生效记录重复，**Then** 弹出黄色警告"已存在重叠授权 [编号]，是否继续保存？"，用户确认后仍可保存
5. **Given** 所有必填字段已填且校验通过，**When** 点击保存，**Then** 记录创建成功，状态=生效中，列表刷新，操作日志记录"新增"
6. **Given** 用户无新增权限 (其他角色)，**When** 进入品牌授权页，**Then** 不显示"+ 新增原厂授权"按钮

---

### User Story 2 — 查看原厂授权列表与详情 (Priority: P1)

用户进入品牌授权 → 原厂授权 Tab，看到 12 列表格（授权编号/一级产线/品牌ID/品牌/
进口国产/品牌原厂名称/授权开始/授权结束/授权附件/状态/备注/操作），
默认按有效期升序排列。点击行或"查看"按钮打开详情抽屉，4 个分区展示全部信息，
附件支持在线预览和下载。

**Why this priority**: 列表和详情是日常使用最频繁的功能，与新增同等重要。

**Independent Test**: 以任何授权角色登录，打开品牌授权页，看到原厂授权列表，
点击某行打开详情，附件可预览/下载。

**Acceptance Scenarios**:

1. **Given** 列表中有原厂授权数据，**When** 用户打开页面，**Then** 显示 12 列表格，默认按授权结束时间升序，即将到期排最前
2. **Given** 列表已展示，**When** 用户点击某行，**Then** 打开详情抽屉，4 分区：基础信息 / 授权信息（含附件预览）/ 备注与其他附件 / 操作日志
3. **Given** 详情抽屉已打开，**When** 用户点击附件文件，**Then** 在线预览 PDF/图片（无需下载），同时提供"下载"按钮
4. **Given** 列表有大量数据，**When** 用户切换分页大小，**Then** 支持 20/50/100 条/页
5. **Given** 其他角色（无入口权限），**When** 尝试访问品牌授权路由，**Then** 菜单中不显示"品牌授权"入口，直接访问 URL 被路由守卫拦截

---

### User Story 3 — 修改原厂授权 (Priority: P2)

用户查看某条授权后，点击"编辑"按钮进入编辑模式。系统根据当前状态限制可编辑字段：
生效中/即将到期可修改全部字段；已失效仅可修改备注（其他字段只读并提示"请使用续期"）；
已作废全部只读。

**Why this priority**: 修改是日常维护操作，但依赖新增和查看功能先行完成。

**Independent Test**: 以 bid_admin 登录，查看一条"生效中"记录，点击编辑，修改品牌原厂名称后保存，列表和详情反映变更。

**Acceptance Scenarios**:

1. **Given** 授权状态=生效中或即将到期，**When** 用户点击"编辑"，**Then** 所有字段可编辑，保存后更新记录
2. **Given** 授权状态=已失效，**When** 用户点击"编辑"，**Then** 仅"备注"字段可编辑，其他字段只读且提示"请使用续期"
3. **Given** 授权状态=已作废，**When** 用户打开详情，**Then** 所有编辑入口隐藏，全部字段只读
4. **Given** 编辑模式下修改了字段值，**When** 用户保存，**Then** 操作日志记录"修改"，含变更字段 diff（前→后）

---

### User Story 4 — 作废授权 (Priority: P2)

投标管理员或投标组长可将授权作废。点击"作废"按钮弹出确认弹窗，警示"作废操作不可撤销"，
必须填写作废原因（≥10 字）才能确认。作废后：列表状态显示「已作废」灰色删除线、
所有编辑入口隐藏、关联到期提醒停止、操作日志记录作废原因。

**Why this priority**: 作废是合规要求，不提供物理删除以保护审计链完整性。

**Independent Test**: 以 bid_admin 登录，对一条授权点击作废，输入原因后确认，
列表状态变为已作废，详情页编辑按钮消失。

**Acceptance Scenarios**:

1. **Given** 用户是 bid_admin 或 bid_lead，**When** 查看某条授权详情，**Then** 底部显示"作废"按钮
2. **Given** 用户是 bid_specialist（无作废权限），**When** 查看某条授权详情，**Then** 不显示"作废"按钮
3. **Given** 用户点击"作废"，**When** 弹窗打开，**Then** 显示警示语"作废操作不可撤销"，需填写作废原因（文本框，≥10 字）才能确认
4. **Given** 用户填写原因并确认作废，**When** 提交，**Then** 状态变为"已作废"，列表显示灰色删除线徽标，编辑隐藏，到期提醒停止
5. **Given** 授权已作废，**When** 用户查看详情，**Then** 全部字段只读，作废原因可查看

---

### User Story 5 — 筛选与导出 (Priority: P3)

用户在列表页使用 11 维筛选器缩小范围（一级产线/品牌ID/品牌/进口国产/品牌原厂名称/
授权时间范围/状态/创建人等），点击查询刷新列表。点击"导出 Excel"将当前筛选结果
导出为 Excel 文件，最多 500 条，超出阻断提示缩小范围。

**Why this priority**: 筛选和导出是列表页的增强功能，核心 CRUD 完成后才有意义。

**Independent Test**: 以 bid_admin 登录，设置筛选条件（状态=生效中），点击查询，
列表过滤正确。点击导出，下载 Excel 文件包含筛选后的数据。

**Acceptance Scenarios**:

1. **Given** 筛选区已展示，**When** 用户选择一级产线（多选下拉+搜索），**Then** 列表过滤为匹配产线的记录
2. **Given** 多个筛选条件已设置，**When** 用户点击"查询"，**Then** 列表按 AND 逻辑过滤刷新
3. **Given** 筛选条件已设置，**When** 用户点击"重置"，**Then** 清空所有筛选条件，恢复全量列表
4. **Given** 筛选结果 ≤500 条，**When** 用户点击"导出 Excel"，**Then** 弹出确认弹窗（筛选摘要+导出条数+文件名），确认后下载
5. **Given** 筛选结果 >500 条，**When** 用户点击"导出 Excel"，**Then** 阻断提示"请缩小筛选范围"
6. **Given** 导出完成，**When** 查看操作日志，**Then** 记录"导出"操作，含筛选条件 JSON + 导出条数
7. **Given** 默认状态筛选，**When** 用户打开列表，**Then** 默认排除"已作废"状态，需手动勾选才能看到

### Edge Cases

- 附件上传：多文件上传时某文件失败（超大/格式不支持），其他文件正常上传，失败文件单独提示
- 并发作废：两个管理员同时作废同一条，后操作的返回"该授权已被作废"
- 重复检测：品牌ID+品牌原厂名称+一级产线三者组合判重，仅在"生效中"/"即将到期"状态中检测
- 日期校验（代理商迭代预留）：当前原厂授权的时间校验是独立的，后续代理商授权需加交叉校验
- 附件删除：编辑时可移除已上传附件并替换新文件
- 空状态：列表无数据时展示空状态插图和"暂无原厂授权记录"提示

## Requirements

### Functional Requirements

- **FR-001**: System MUST support dual-tab layout (原厂授权 | 代理商授权) with last-visited-tab memory; this iteration implements 原厂授权 tab only
- **FR-002**: System MUST provide 10-field data model: 一级产线 (39-item enum), 品牌ID (text), 品牌 (text), 进口/国产 (single select), 品牌原厂名称 (text), 原厂授权附件 (file, multi), 授权开始时间 (date), 授权结束时间 (date), 备注 (textarea), 附件-补充材料 (file, multi)
- **FR-003**: System MUST auto-manage status field (草稿/生效中/即将到期/已失效/已作废) based on dates and user actions
- **FR-004**: 新增 MUST use drawer component with 3 grouped sections: 基础信息区, 授权信息区, 补充信息区
- **FR-005**: File upload MUST support PDF/JPG/PNG formats, max 20MB per file, multi-file selection
- **FR-006**: Attachments MUST support inline preview (PDF/images) and download
- **FR-007**: 作废 MUST be soft-delete: status changes to 已作废, reason ≥10 chars required, undo not supported
- **FR-008**: 作废 permission MUST be restricted to bid_admin and bid_lead roles only
- **FR-009**: Edit field availability MUST be gated by current status: 生效中/即将到期=all editable; 已失效=only 备注 editable; 已作废=all readonly
- **FR-010**: Save validation MUST include: required fields red highlight + auto-scroll to first error; end date > start date blocking; duplicate detection (品牌+原厂+产线) as yellow warning
- **FR-011**: List MUST display 12 columns with default sort by end date ascending; pagination 20/50/100
- **FR-012**: Detail drawer MUST have 4 sections: 基础信息, 授权信息 (with attachment preview), 备注与附件, 操作日志 (last 5 entries, expandable)
- **FR-013**: Filters MUST include 11 dimensions: 一级产线 (multi-select+search), 品牌ID (fuzzy), 品牌 (multi-select), 进口/国产 (single), 品牌原厂名称 (fuzzy), 授权开始时间范围, 授权结束时间范围, 状态 (multi-select, exclude 已作废 by default), 创建人 (dropdown), 关键词 (fuzzy on 编号/备注)
- **FR-014**: Export MUST generate Excel file with all list columns + audit fields; max 500 records; async notification for large exports
- **FR-015**: Export action MUST be logged in operation log with filter conditions JSON and record count
- **FR-016**: Operation log MUST record 8 types: 新增 (full snapshot), 修改 (before/after diff), 作废 (reason), 续期 (linked new record), 状态自动变更 (system operator), 附件操作 (file name/size/action), 导出 (filter JSON/count), 敏感查看 (download)
- **FR-017**: System MUST register `knowledge-brand-auth` permission key in RoleProfileCatalog with proper Flyway migration for existing roles
- **FR-018**: Menu entry MUST be under 资源管理 → 品牌授权; non-authorized roles MUST have no menu visibility and route guard blocking
- **FR-019**: Brand ID + 品牌原厂名称 + 一级产线 combination MUST be unique among active (生效中/即将到期) records
- **FR-020**: All 10 fields except 备注 MUST be required when creating; 备注 is the only optional field

### Key Entities

- **原厂授权 (ManufacturerAuthorization)**: Core entity with 10 business fields + system status + audit fields. Key attributes: 一级产线 (from 39-item enum), 品牌ID (internal brand code), 品牌 (brand name), 进口/国产 (import/domestic flag), 品牌原厂名称 (manufacturer legal name), 原厂授权附件 (authorization document files), 授权开始时间/结束时间 (validity period), 备注 (optional notes), 附件 (supplementary files), 状态 (lifecycle state)
- **授权附件 (AuthorizationAttachment)**: File metadata entity: file name, size, MIME type, storage path, upload timestamp, association to 原厂授权
- **操作日志 (BrandAuthOperationLog)**: Audit trail: operation type enum, operator (name+role+employee ID), timestamp, target record ID, change summary, change detail (before/after values), remarks (e.g., 作废 reason)
- **一级产线 (ProductLine)**: Enumeration of 39 values: 工具/工具耗材/刀具/量具/焊接/机床/磨具/润滑/胶粘/车间化学品/劳保/安全/消防/搬运/存储/工位/包材/清洁/办公/制冷/暖通/工控/低压/电工/照明/轴承/皮带/机械/气动/液压/管阀/泵/紧固/密封/工业检测/实验室产品/企业福礼/紧急救护/建工材料

## Success Criteria

### Measurable Outcomes

- **SC-001**: Users with authorization can create a new 原厂授权 record in under 3 minutes (including file upload)
- **SC-002**: List page loads and renders 100 records in under 2 seconds
- **SC-003**: 100% of role-based permission rules (view/add/edit/invalidate) are correctly enforced across all 4 roles
- **SC-004**: File upload handles up to 5 files per field, each up to 20MB, without timeout
- **SC-005**: All 10 required-field validations and business rule checks fire before save, with zero data corruption on constraint violation
- **SC-006**: 作废 operation records reason in audit log and hides all edit UI within the same request
- **SC-007**: Export generates correct Excel file with filtered data in under 60 seconds for up to 500 records
- **SC-008**: Every blueprint-defined operation (新增/修改/作废/导出/查看附件) is traceable in operation log

## Assumptions

- The existing file storage infrastructure (local filesystem or object storage) can be reused for brand authorization attachments
- The 39-item 一级产线 enum is static and does not need database-backed CRUD management in v1
- 品牌字典 (brand dictionary) linkage for 品牌ID is a future integration; current iteration accepts free-text input
- Agent authorization tab (代理商授权) is out of scope for this iteration and will display a placeholder
- The existing `brandauth` DDD package structure can be refactored rather than rewritten from scratch
- 到期提醒 (expiry alerts) for 4-tier (60/30/7/0 day) will be implemented in a follow-up iteration (§4.6d)
- Batch import/export is a separate iteration (§4.6c)
- 授权链可视化 (authorization chain visualization) applies only to 代理商授权 and is out of scope for §4.6a
