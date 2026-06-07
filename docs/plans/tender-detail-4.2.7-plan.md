# 4.2.7 标讯详情 — 实现计划

## 架构设计

### 纯核心 (Pure Core)
```
src/views/Bidding/detail/actionMatrix.js
  - HEADER_ACTION_MATRIX: 头部全局操作按钮配置（role × status → buttons）
  - BOTTOM_ACTION_MATRIX: 底部操作栏按钮配置（role × status → buttons）
  - getHeaderActions(status, role) → Action[]
  - getBottomActions(status, role) → Action[]
  - shouldShowLogsTab(sourceType) → boolean
```

### 编排层 (Composables)
```
src/views/Bidding/detail/useDetailActions.js (new)
  - 包装 actionMatrix + API 调用
  - 暴露 handleAssign / handleTransfer / handleDelete 等方法
  - 管理按钮 loading 状态

src/views/Bidding/detail/useDetailTabs.js (new)
  - Tab 显隐逻辑（操作日志 Tab 条件隐藏）
  - Tab 切换管理
```

### 表现层 (Components)
```
src/views/Bidding/detail/BottomActionBar.vue (new)
  - fixed 底部操作栏
  - 按钮按角色+状态动态展示
  - 通过 emit 向父组件发送事件

src/views/Bidding/detail/DetailPage.vue (修改)
  - 头部信息卡重构：面包屑 + 标题 + 状态/优先级/来源标签 + 元信息 + 全局操作按钮
  - 集成 BottomActionBar
  - Tab 条件显隐
```

### 测试文件
```
actionMatrix.spec.js       → 纯函数测试
useDetailTabs.spec.js      → composable 测试
BottomActionBar.spec.js    → 组件测试
DetailPage.spec.js         → 集成测试 (更新)
```

## 执行波次

### Wave 1 (并行，无依赖)
- **Agent A**: actionMatrix.js + actionMatrix.spec.js
- **Agent B**: useDetailTabs.js + useDetailTabs.spec.js

### Wave 2 (依赖 Wave 1)
- **Agent C**: useDetailActions.js + useDetailActions.spec.js
- **Agent D**: BottomActionBar.vue + BottomActionBar.spec.js
- **Agent E**: CSS 更新 (detail-layout.css + detail-overrides.css)

### Wave 3 (依赖 Wave 2)
- **Agent F**: DetailPage.vue 重构 + DetailPage.spec.js 更新

### 验证
- npm run build
- npm run test:unit
