---
name: 标讯中心产品需求开发计划
overview: 基于西域数智化投标管理平台产品蓝图V1.1中4.2标讯中心的需求，与当前代码实现进行逐项对比，形成完整的开发计划。
todos:
  - id: task-1
    content: "Task 1: 标讯源配置增强 - 测试连接功能"
    status: completed
  - id: task-2
    content: "Task 2: AI解析增强 - 招标文件解析"
    status: completed
  - id: task-3
    content: "Task 3: 提醒功能开发"
    status: completed
  - id: task-4
    content: "Task 4: CRM自动分配集成"
    status: completed
  - id: task-5
    content: "Task 5: 移动端小程序开发"
    status: completed
  - id: task-6
    content: "Task 6: 商机时间预测"
    status: completed
  - id: tech-debt
    content: 技术债务清理
    status: completed
isProject: false
---

# 标讯中心（4.2）产品需求开发计划

## 一、需求与实现对比总览

### 1.1 已完全实现的功能

| 需求模块 | 产品要求 | 当前状态 | 说明 |
|---------|---------|---------|------|
| 标讯状态流转 | 7状态完整流程 | **已实现** | TenderStatusTransitionPolicy 完整覆盖 |
| 评估表基础字段 | 8个字段(7必填+1选填) | **已实现** | V119 评估表重设计已实施 |
| 客户信息表格 | 13角色×14字段 | **已实现** | TenderEvaluation 实体含完整字段 |
| 权限控制 | 4角色×多状态矩阵 | **已实现** | ProjectAccessGuard + TenderAssignmentPermissions |
| 标讯列表 | 18列展示+多维度筛选 | **已实现** | TenderTable + TenderSearchCard |
| 人工录入 | 分步表单+AI解析 | **已实现** | ManualTenderDialog + AI分析 |
| 批量导入 | Excel模板导入 | **已实现** | TenderImportService |
| 标讯源配置 | 配置弹窗+获取 | **已实现** | SourceConfigDialog |
| 分配/转派 | 手动分配+智能分发 | **已实现** | 3种分配规则 |
| 操作日志 | 时间轴倒序展示 | **已实现** | 通过审计表实现 |
| 导出功能 | Excel跨页导出 | **已实现** | BatchOperationController |
| 表单校验 | 完整校验规则 | **已实现** | 必填/格式/范围校验 |

### 1.2 已实现但需增强的功能

| 功能 | 当前实现 | 增强需求 | 工作量 |
|------|---------|---------|--------|
| 标讯源配置 | 仅配置保存 | 增加API端点测试连接功能 | 小 |
| AI解析 | 粘贴识别 | 增加招标文件(PDF/Word)解析 | 中 |
| CRM自动分配 | 标记待分配 | 实现真实CRM接口调用 | 中 |
| 客户信息表格 | 固定13行 | 增加行级编辑权限控制 | 小 |
| 通知机制 | 缺少企微通知 | 增加企微消息提醒 | 中 |
| 报名截止时间 | 仅日期 | 增加截止前提醒功能 | 小 |

### 1.3 缺失功能

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 标讯源第三方API联调 | P1 | cebpubservice / 招标网 API |
| 报名截止提醒 | P1 | 定时任务+企微通知 |
| 开标时间提醒 | P2 | 定时任务+企微通知 |
| 移动端适配 | P2 | 企业微信小程序 |
| 商机时间预测 | P3 | AI预测模型 |

---

## 二、详细开发任务分解

### 阶段一：核心功能增强 (P1)

#### 2.1 标讯源配置增强

**前端改动:**
- `SourceConfigDialog.vue` - 增加【测试连接】按钮
- 测试连接成功后显示成功/失败提示

**后端改动:**
- `TenderSourceConfigController.java` - 新增测试连接接口
- `TenderSourceConfigService.java` - 实现API连通性校验

**数据库改动:**
- 无

**API接口:**
```
POST /api/tender-sources/test-connection
Body: { "platform": "第三方商机服务", "apiEndpoint": "...", "apiKey": "..." }
Response: { "success": true/false, "message": "..." }
```

---

#### 2.2 AI解析功能增强 - 招标文件解析

**前端改动:**
- `ManualTenderDialog.vue` - 增加文件上传区域
- 支持 PDF/Word 文件上传
- 上传后触发 AI 解析流程

**后端改动:**
- `TenderAiParsingService.java` - 增强解析逻辑
- 支持 PDF/Word 文档内容提取
- 调用 DeepSeek 接口解析关键字段

**数据库改动:**
- 无

**API接口:**
```
POST /api/tenders/{id}/ai-parse-file
Body: multipart/form-data (file)
Response: { "fields": { "title": "...", ... } }
```

---

#### 2.3 报名截止/开标提醒功能

**前端改动:**
- `TenderSearchCard.vue` - 增加提醒设置入口
- 新增 `ReminderSettingsDialog.vue` 提醒设置对话框

**后端改动:**
- 新增 `TenderReminderService.java` 提醒服务
- 新增 `TenderReminderJob.java` 定时任务
- `TenderReminderRepository.java` 提醒规则存储

**数据库改动:**
- 新增 `tender_reminder_settings` 表

```sql
CREATE TABLE tender_reminder_settings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tender_id BIGINT NOT NULL,
    reminder_type ENUM('REGISTRATION_DEADLINE', 'BID_OPENING') NOT NULL,
    remind_before_hours INT DEFAULT 24,
    reminder_targets JSON COMMENT '通知对象列表',
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**定时任务:**
- 每小时执行一次
- 查询即将到期(24小时内)的标讯
- 发送企微消息提醒

---

### 阶段二：CRM集成 (P1)

#### 2.4 CRM自动分配

**前端改动:**
- 无需改动前端
- 后端自动执行

**后端改动:**
- `TenderAutoAssignmentService.java` - 新增CRM自动分配服务
- `CrmClient.java` - CRM接口客户端
- `TenderAssignmentPolicy.java` - 分配策略(按业主单位匹配)

**数据库改动:**
- 新增 `crm_project_mapping` 表(可选,用于缓存)

```sql
CREATE TABLE crm_project_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    purchaser_name VARCHAR(500) NOT NULL COMMENT '业主单位名称',
    crm_project_id VARCHAR(100) COMMENT 'CRM项目ID',
    project_manager_id VARCHAR(100) COMMENT 'CRM项目负责人ID',
    project_manager_name VARCHAR(100) COMMENT 'CRM项目负责人姓名',
    department_id VARCHAR(100) COMMENT '部门ID',
    department_name VARCHAR(200) COMMENT '部门名称',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_purchaser (purchaser_name)
);
```

**API接口:**
```
GET /api/crm/projects/by-purchaser?purchaserName=xxx
Response: { "projectId": "...", "managerId": "...", "managerName": "...", "department": {...} }
```

---

### 阶段三：移动端 (P2)

#### 2.5 企业微信小程序

**功能范围:**
- 标讯列表查看
- 标讯详情查看
- 待办提醒查看
- 审批入口(跳转)

**技术选型:**
- 企业微信小程序框架
- 调用现有后端 API

---

### 阶段四：高级功能 (P3)

#### 2.6 商机时间预测

**前端改动:**
- 新增 `MarketPredictionPage.vue` 预测页面

**后端改动:**
- `MarketPredictionService.java` - 预测服务
- 基于历史数据分析招标周期
- 预测下次招标时间

**数据库改动:**
- 新增 `market_prediction_cache` 表(可选)

---

## 三、开发任务清单

### Task 1: 标讯源配置增强 - 测试连接功能

| 属性 | 内容 |
|------|------|
| **Type** | Feature |
| **Priority** | P1 |
| **Estimated Hours** | 8h |
| **Files to Change** | `TenderSourceConfigController.java`, `TenderSourceConfigService.java`, `SourceConfigDialog.vue` |

**Acceptance Criteria:**
- [ ] 填写API端点和密钥后,【测试连接】按钮可点击
- [ ] 点击后显示加载状态
- [ ] 连接成功显示绿色提示
- [ ] 连接失败显示红色提示+错误原因

---

### Task 2: AI解析增强 - 招标文件解析

| 属性 | 内容 |
|------|------|
| **Type** | Feature |
| **Priority** | P1 |
| **Estimated Hours** | 24h |
| **Files to Change** | `TenderAiParsingService.java`, `ManualTenderDialog.vue`, `useManualTenderCreate.js` |

**Acceptance Criteria:**
- [ ] 支持上传PDF文件(≤50MB)
- [ ] 支持上传Word文件(≤50MB)
- [ ] 上传后自动调用AI解析
- [ ] 解析完成后自动回填表单字段
- [ ] 解析失败显示友好提示

---

### Task 3: 提醒功能开发

| 属性 | 内容 |
|------|------|
| **Type** | Feature |
| **Priority** | P1 |
| **Estimated Hours** | 16h |
| **Files to Change** | `TenderReminderService.java`, `TenderReminderJob.java`, `ReminderSettingsDialog.vue`, `tender_reminder_settings` 表 |

**Acceptance Criteria:**
- [ ] 用户可为标讯设置报名截止提醒
- [ ] 用户可为标讯设置开标提醒
- [ ] 提醒可设置提前时间(12h/24h/48h)
- [ ] 到期前自动发送企微消息
- [ ] 用户可查看已设置的提醒列表

---

### Task 4: CRM自动分配

| 属性 | 内容 |
|------|------|
| **Type** | Integration |
| **Priority** | P1 |
| **Estimated Hours** | 32h |
| **Files to Change** | `TenderAutoAssignmentService.java`, `CrmClient.java`, `crm_project_mapping` 表 |

**Acceptance Criteria:**
- [ ] 标讯创建后自动调用CRM接口
- [ ] 根据业主单位匹配项目负责人
- [ ] 匹配成功自动分配,状态变为"跟踪中"
- [ ] 匹配失败保持"待分配",等待手动分配
- [ ] CRM接口异常时记录日志,不影响标讯创建

---

### Task 5: 移动端小程序开发

| 属性 | 内容 |
|------|------|
| **Type** | Feature |
| **Priority** | P2 |
| **Estimated Hours** | 80h |
| **Files to Change** | 新建 `wechat-miniprogram/` 目录 |

**Acceptance Criteria:**
- [ ] 标讯列表移动端适配
- [ ] 标讯详情移动端查看
- [ ] 待办提醒移动端推送
- [ ] 审批快捷入口

---

### Task 6: 商机时间预测

| 属性 | 内容 |
|------|------|
| **Type** | Feature |
| **Priority** | P3 |
| **Estimated Hours** | 40h |
| **Files to Change** | `MarketPredictionService.java`, `MarketPredictionPage.vue` |

**Acceptance Criteria:**
- [ ] 基于≥2条历史数据生成预测
- [ ] 显示预测下次招标时间
- [ ] 显示预测置信度
- [ ] 生成跟进任务提醒

---

## 四、使用 Speckit Constitution 执行

### 4.1 Constitution 适用性分析

当前 Constitution (v1.0.0) 的五大原则与本次开发任务的对应关系:

| Constitution 原则 | 相关任务 | 对齐度 |
|------------------|---------|--------|
| **I. FP-Java Architecture** | 所有后端服务遵循 Pure Core 分离 | 高 |
| **II. Real-API Only** | 使用真实 API,无 Mock | 高 |
| **III. TDD** | 核心业务逻辑需要单元测试覆盖 | 高 |
| **IV. Split-First** | 拆分 Policy/Service/Controller | 高 |
| **V. Boring Proven Patterns** | 遵循已有代码风格 | 高 |

### 4.2 Speckit 工作流建议

**推荐使用 Full SDD Cycle:**

```
/speckit-specify     → 为每个 Task 生成 spec.md
         ↓
/speckit-plan         → 设计数据模型和 API 契约
         ↓
/speckit-tasks        → 拆解具体实现任务
         ↓
/speckit-implement    → 执行实现(遵循 TDD)
         ↓
/speckit-checklist   → 质量验证
```

### 4.3 执行顺序建议

```
第1步: /speckit-specify "标讯提醒功能" (Task 3)
第2步: /speckit-plan (基于 spec 生成 plan)
第3步: /speckit-tasks (生成 tasks.md)
第4步: /speckit-implement (执行 Task 3)
第5步: 循环 1-4 执行其他任务
```

---

## 五、技术债务清理

### 5.1 待清理项

| 项 | 说明 | 优先级 |
|----|------|--------|
| 遗留 Mock 代码 | 检查是否有残留 mock | P1 |
| 过期注释 | 代码注释中 POC 相关表述 | P2 |
| 重复代码 | TenderCommandService 中重复逻辑 | P2 |

### 5.2 架构验证

每次提交前需通过:
```bash
cd backend && mvn test -Dtest=ArchitectureTest
```

---

## 六、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| CRM接口不稳定 | 分配功能不可用 | 本地缓存+降级处理 |
| AI解析延迟高 | 用户体验差 | 异步处理+进度提示 |
| 企微通知被拦截 | 提醒失效 | 多通道通知(APP+企微) |
| 移动端开发周期长 | 影响整体进度 | 使用 UniApp 跨平台框架 |

---

## 七、里程碑

| 里程碑 | 包含任务 | 目标日期 |
|---------|---------|---------|
| **M1: 核心功能完成** | Task 1, 2 | 第1周 |
| **M2: CRM集成完成** | Task 4 | 第2周 |
| **M3: 提醒功能上线** | Task 3 | 第2周 |
| **M4: 移动端Beta** | Task 5 | 第4周 |
| **M5: 高级功能** | Task 6 | 第6周 |

---

## 八、总结

当前代码库**已完整实现**产品需求中标讯中心的核心功能(状态流转、评估表、权限控制、CRUD、导入导出等)。主要工作是:

1. **增强已有功能** - AI解析文件上传、测试连接
2. **新增提醒功能** - 报名截止/开标提醒
3. **集成外部系统** - CRM自动分配、企微通知
4. **移动端支持** - 企业微信小程序
5. **高级预测** - 商机时间预测

建议使用 **Speckit Constitution** 的 Full SDD Cycle 严格执行,确保:
- 每个功能有完整的 spec
- 遵循 FP-Java 架构
- TDD 测试覆盖
- 代码审查通过