# B2B UI 专业风格改进实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将西域投标管理平台从业余 UI 风格升级为专业 B2B 企业级界面，符合 ui-ux-pro-max 技能规范。

**Architecture:**
1. 更新全局设计系统变量（颜色、字体、焦点状态）
2. 逐个组件修复表情符号图标、头像显示
3. 统一交互状态（hover、focus、cursor）
4. 完善移动端适配和无障碍支持

**Tech Stack:** Vue 3 + Element Plus + Vite + SCSS

---

## Task 1: 全局设计系统更新

**Files:**
- Modify: `src/styles/variables.css`
- Modify: `src/styles/common.css`
- Modify: `src/App.vue`

**Step 1: 更新 variables.css - 添加 B2B 专业字体和改进对比度**

```css
/* 在 src/styles/variables.css 顶部添加 Google Fonts 导入 */
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap');

:root {
  /* ========== B2B 专业配色系统 ========== */
  /* 品牌色 - B2B 科技蓝（保持原色但增强对比度） */
  --brand-primary: #0066CC;
  --brand-primary-light: #3388DD;
  --brand-primary-dark: #0052A3;
  --brand-primary-bg: rgba(0, 102, 204, 0.1);  /* 新增：图标背景色 */

  /* 功能色 */
  --color-success: #00AA44;
  --color-success-bg: rgba(0, 170, 68, 0.1);
  --color-warning: #FF8800;
  --color-warning-bg: rgba(255, 136, 0, 0.1);
  --color-danger: #DD2200;
  --color-danger-bg: rgba(221, 34, 0, 0.1);
  --color-info: #0066CC;
  --color-info-bg: rgba(0, 102, 204, 0.1);

  /* 中性色 */
  --gray-50: #F5F7FA;
  --gray-100: #E8E8E8;
  --gray-200: #D0D0D0;
  --gray-300: #B0B0B0;
  --gray-400: #999999;
  --gray-500: #666666;
  --gray-600: #444444;
  --gray-700: #333333;
  --gray-800: #222222;
  --gray-900: #1A1A1A;

  /* 文字颜色 */
  --text-primary: #1A1A1A;
  --text-secondary: #666666;
  --text-tertiary: #999999;
  --text-disabled: var(--gray-300);
  --text-placeholder: #BBBBBB;
  --text-link: var(--brand-primary);

  /* ========== 侧边栏专用颜色（提升对比度） ========== */
  --sidebar-bg: #001529;
  --sidebar-text: rgba(255, 255, 255, 0.85);  /* 从 0.65 提升到 0.85 */
  --sidebar-text-active: #FFFFFF;
  --sidebar-hover-bg: rgba(255, 255, 255, 0.08);
  --sidebar-active-bg: #409EFF;

  /* ========== 焦点状态颜色（无障碍） ========== */
  --focus-ring: var(--brand-primary);
  --focus-ring-offset: 2px;
  --focus-width: 2px;

  /* ========== 间距系统 - 8px 基础单位 ========== */
  --space-xs: 4px;
  --space-sm: 8px;
  --space-md: 16px;
  --space-lg: 24px;
  --space-xl: 32px;
  --space-2xl: 48px;

  /* 向后兼容的间距变量 */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-8: 32px;

  /* ========== 组件尺寸 ========== */
  --header-height: 56px;
  --sidebar-width: 220px;
  --sidebar-collapsed-width: 64px;
  --menu-item-height: 48px;

  /* ========== 圆角 ========== */
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-xl: 16px;
  --radius-full: 50%;

  /* ========== 阴影（分层系统） ========== */
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.04);
  --shadow-md: 0 1px 3px rgba(0, 0, 0, 0.08), 0 1px 2px rgba(0, 0, 0, 0.04);
  --shadow-lg: 0 4px 12px rgba(0, 0, 0, 0.10), 0 2px 6px rgba(0, 0, 0, 0.05);
  --shadow-xl: 0 10px 25px rgba(0, 0, 0, 0.12), 0 4px 10px rgba(0, 0, 0, 0.06);

  /* ========== 字体 ========== */
  --font-family: 'Plus Jakarta Sans', 'PingFang SC', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  --font-size-xs: 12px;
  --font-size-sm: 14px;
  --font-size-base: 16px;
  --font-size-lg: 18px;
  --font-size-xl: 20px;

  /* ========== z-index ========== */
  --z-dropdown: 1000;
  --z-sticky: 1020;
  --z-fixed: 1030;
  --z-modal: 1050;
  --z-popover: 1060;
  --z-tooltip: 1070;
  --z-sidebar: 100;
}
```

**Step 2: 更新 common.css - 添加全局焦点状态和 cursor 类**

```css
/* 在 src/styles/common.css 中添加以下内容 */

/* ========== 全局焦点状态（无障碍 CRITICAL） ========== */
*:focus-visible {
  outline: var(--focus-width) solid var(--focus-ring);
  outline-offset: var(--focus-ring-offset);
}

/* Element Plus 组件焦点增强 */
:deep(.el-button:focus),
:deep(.el-input__wrapper:focus),
:deep(.el-textarea__inner:focus),
:deep(.el-select .el-input__wrapper:focus),
:deep(.el-checkbox__input:focus-visible .el-checkbox__inner),
:deep(.el-radio__input:focus-visible .el-radio__inner) {
  outline: var(--focus-width) solid var(--focus-ring);
  outline-offset: var(--focus-ring-offset);
}

/* 菜单项焦点状态 */
:deep(.el-menu-item:focus),
:deep(.el-sub-menu__title:focus) {
  background: rgba(64, 158, 255, 0.15) !important;
  outline: 2px solid var(--brand-primary);
  outline-offset: -2px;
}

/* ========== 可点击元素 cursor 类 ========== */
.cursor-pointer {
  cursor: pointer;
}

.cursor-default {
  cursor: default;
}

/* ========== 减少动画（尊重用户偏好） ========== */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

**Step 3: 更新 App.vue - 导入 Google Fonts**

```vue
<template>
  <router-view />
</template>

<script setup>
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap');

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  font-family: 'Plus Jakarta Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
</style>
```

**Step 4: 验证修改**

Run: `npm run dev`
Expected: 开发服务器启动，字体加载正常

**Step 5: Commit**

```bash
git add src/styles/variables.css src/styles/common.css src/App.vue
git commit -m "feat(ui): update global design system with B2B professional fonts and focus states"
```

---

## Task 2: 侧边栏对比度和焦点状态修复

**Files:**
- Modify: `src/components/layout/Sidebar.vue`

**Step 1: 更新侧边栏颜色变量**

将 `text-color="rgba(255, 255, 255, 0.65)"` 改为 `text-color="rgba(255, 255, 255, 0.85)"`

找到以下两处（移动端抽屉和 PC 端侧边栏）：

```vue
<!-- 第 21-24 行：移动端抽屉菜单 -->
<el-menu
  :default-active="activeMenu"
  :collapse="false"
  class="sidebar-menu"
  background-color="#001529"
  text-color="rgba(255, 255, 255, 0.85)"  <!-- 从 0.65 改为 0.85 -->
  active-text-color="#FFFFFF"  <!-- 改为纯白 -->
  router
  @select="handleMenuSelect"
>

<!-- 第 67-75 行：PC 端菜单 -->
<el-menu
  :default-active="activeMenu"
  :collapse="collapse"
  :collapse-transition="false"
  class="sidebar-menu"
  background-color="#001529"
  text-color="rgba(255, 255, 255, 0.85)"  <!-- 从 0.65 改为 0.85 -->
  active-text-color="#FFFFFF"  <!-- 改为纯白 -->
  router
>
```

**Step 2: 更新激活状态样式**

在 `<style scoped>` 中更新 `.el-menu-item.is-active` 样式：

```css
:deep(.el-menu-item.is-active) {
  background: var(--brand-primary) !important;  /* 使用品牌色而非硬编码 */
  border-right: 3px solid var(--brand-primary);
  color: #fff !important;
}

:deep(.el-sub-menu .el-menu-item.is-active) {
  background: rgba(0, 102, 204, 0.3) !important;  /* 使用品牌色的半透明 */
  color: #fff !important;
}
```

**Step 3: 验证修改**

Run: `npm run dev`
Expected: 侧边栏文字对比度提升，激活状态更清晰

**Step 4: Commit**

```bash
git add src/components/layout/Sidebar.vue
git commit -m "fix(ui): improve sidebar text contrast for accessibility"
```

---

## Task 3: Header 组件 - 头像显示修复

**Files:**
- Modify: `src/components/layout/Header.vue`

**Step 1: 修改用户头像计算逻辑**

将表情符号头像改为首字母：

```vue
<script setup>
// ... 其他 import 保持不变

// ✅ 修改：使用姓名首字母而非表情符号
const userAvatar = computed(() => {
  const name = userStore.currentUser?.name || '游客'
  // 取姓名的第一个字符
  return name.charAt(0).toUpperCase()
})

const userName = computed(() => userStore.currentUser?.name || '游客')
// ... 其他代码保持不变
</script>
```

**Step 2: 更新头像样式以支持渐变背景**

更新 `.user-avatar` 和 `.dropdown-avatar` 样式：

```css
.user-avatar {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  background: linear-gradient(135deg, #0066CC, #3388DD);
  color: #fff;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}

.dropdown-avatar {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
  background: linear-gradient(135deg, #0066CC, #3388DD);
  color: #fff;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}

/* 移动端头像样式调整 */
@media (max-width: 768px) {
  .user-avatar {
    width: 28px;
    height: 28px;
    font-size: 12px;
  }
}
```

**Step 3: 验证修改**

Run: `npm run dev`
Expected: 用户头像显示为姓名首字母，带渐变背景

**Step 4: Commit**

```bash
git add src/components/layout/Header.vue
git commit -m "fix(ui): replace emoji avatar with professional initials + gradient"
```

---

## Task 4: AI Center - 移除表情符号图标

**Files:**
- Modify: `src/views/AI/Center.vue`
- Modify: `src/views/AI/components/FeatureCard.vue`

**Step 1: 创建图标映射配置**

在 `src/views/AI/Center.vue` 的 `<script setup>` 中，定义图标映射：

```vue
<script setup>
import { ref, computed } from 'vue'
import { Document, RefreshLeft, Download } from '@element-plus/icons-vue'
// ✅ 新增：导入所有需要的图标
import {
  TrendCharts, Aim, View, DataLine,
  Lightning, Shield, Document as DocIcon,
  User, Setting
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import FeatureCard from './components/FeatureCard.vue'
import ConfigDialog from '@/components/ai/ConfigDialog.vue'
import { aiConfigs, getConfigById } from '@/config/ai-prompts'

// ✅ 图标映射：将表情符号映射到 Element Plus 图标
const iconMap = {
  '📊': TrendCharts,   // AI 分析
  '🎯': Aim,           // 评分点覆盖
  '🕵️': View,          // 竞争情报
  '📈': DataLine,      // ROI 核算
  '⚡': Lightning,     // 智能装配
  '🛡️': Shield,        // 合规雷达
  '📝': DocIcon,       // 版本管理
  '👥': User,          // 协作中心
  '⚙️': Setting,       // 自动化任务
}

// ✅ 函数：获取图标组件
const getIconComponent = (emojiIcon) => {
  return iconMap[emojiIcon] || Document
}

// ... 其他代码保持不变

// 投标准备功能列表 - 保持 icon 字段用于显示，但会被 FeatureCard 转换
const prepareFeatures = ref([
  {
    id: 'ai-analysis',
    icon: '📊',  // 保持原样，由 FeatureCard 处理
    name: 'AI 分析',
    description: '智能分析招标文件，提取关键信息和风险点',
    enabled: true,
    stats: { usageCount: 128, accuracy: 94.5 },
    promptTemplate: '请分析以下招标文件，提取项目概况、资格要求、评分标准等核心要素...'
  },
  // ... 其他功能保持不变
])
```

**Step 2: 移除标题中的表情符号**

```vue
<template>
  <div class="ai-center-page">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <!-- ❌ 删除：🤖 -->
        <h2 class="page-title">AI 智能中心</h2>
      </div>
      <!-- ... 其他内容保持不变 -->
    </div>
    <!-- ... 其他内容保持不变 -->
  </div>
</template>
```

**Step 3: 更新 FeatureCard 组件以使用 Element Plus 图标**

修改 `src/views/AI/components/FeatureCard.vue`：

```vue
<template>
  <div class="b2b-feature-card" :class="{ disabled: !feature.enabled }">
    <div class="b2b-feature-header">
      <div class="b2b-feature-title">
        <!-- ✅ 使用动态图标组件 -->
        <div class="b2b-feature-icon-wrapper" :class="`icon-${getIconColor()}`">
          <el-icon :size="24">
            <component :is="getIconComponent()" />
          </el-icon>
        </div>
        <span class="b2b-feature-name">{{ feature.name }}</span>
      </div>
      <el-switch
        :model-value="feature.enabled"
        @update:model-value="$emit('toggle', feature.id, $event)"
      />
    </div>
    <div class="b2b-feature-description">{{ feature.description }}</div>
    <div v-if="feature.stats" class="b2b-feature-stats">
      <div class="stat-item">
        <span class="stat-label">使用次数</span>
        <span class="stat-value">{{ feature.stats.usageCount }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">准确率</span>
        <span class="stat-value">{{ feature.stats.accuracy }}%</span>
      </div>
    </div>
    <div class="b2b-feature-actions">
      <el-button size="small" @click="$emit('configure', feature.id)">
        <el-icon><Setting /></el-icon>
        配置
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import {
  TrendCharts, Aim, View, DataLine,
  Lightning, Shield, Document, User
} from '@element-plus/icons-vue'

const props = defineProps({
  feature: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['toggle', 'configure'])

// 图标映射（与 Center.vue 保持一致）
const iconMap = {
  '📊': TrendCharts,
  '🎯': Aim,
  '🕵️': View,
  '📈': DataLine,
  '⚡': Lightning,
  '🛡️': Shield,
  '📝': Document,
  '👥': User,
  '⚙️': Setting
}

const getIconComponent = () => {
  return iconMap[props.feature.icon] || Document
}

const getIconColor = () => {
  const colorMap = {
    '📊': 'primary',
    '🎯': 'primary',
    '🕵️': 'warning',
    '📈': 'success',
    '⚡': 'primary',
    '🛡️': 'danger',
    '📝': 'primary',
    '👥': 'info',
    '⚙️': 'default'
  }
  return colorMap[props.feature.icon] || 'primary'
}
</script>

<style scoped>
.b2b-feature-card {
  background: #fff;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--gray-200);
  padding: var(--space-md);
  transition: all 0.25s ease;
  cursor: pointer;
}

.b2b-feature-card:hover:not(.disabled) {
  box-shadow: var(--shadow-md);
  border-color: var(--brand-primary);
}

.b2b-feature-card.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.b2b-feature-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-sm);
}

.b2b-feature-title {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.b2b-feature-icon-wrapper {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.b2b-feature-icon-wrapper.icon-primary {
  background: var(--color-primary-bg);
  color: var(--brand-primary);
}

.b2b-feature-icon-wrapper.icon-success {
  background: var(--color-success-bg);
  color: var(--color-success);
}

.b2b-feature-icon-wrapper.icon-warning {
  background: var(--color-warning-bg);
  color: var(--color-warning);
}

.b2b-feature-icon-wrapper.icon-danger {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}

.b2b-feature-icon-wrapper.icon-info {
  background: var(--color-info-bg);
  color: var(--color-info);
}

.b2b-feature-icon-wrapper.icon-default {
  background: var(--gray-100);
  color: var(--gray-600);
}

.b2b-feature-name {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary);
}

.b2b-feature-description {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: var(--space-sm);
}

.b2b-feature-stats {
  display: flex;
  gap: var(--space-md);
  padding: var(--space-sm) 0;
  border-top: 1px solid var(--gray-100);
  border-bottom: 1px solid var(--gray-100);
  margin-bottom: var(--space-sm);
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-label {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

.stat-value {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-primary);
}

.b2b-feature-actions {
  display: flex;
  gap: var(--space-sm);
}
</style>
```

**Step 4: 验证修改**

Run: `npm run dev`
Expected: AI 中心页面所有表情符号替换为 Element Plus 图标

**Step 5: Commit**

```bash
git add src/views/AI/Center.vue src/views/AI/components/FeatureCard.vue
git commit -m "fix(ui): replace emoji icons with Element Plus icons in AI Center"
```

---

## Task 5: 工作台页面 - hover 状态修复

**Files:**
- Modify: `src/views/Dashboard/Workbench.vue`

**Step 1: 修复 hover 状态引起的布局偏移**

将 `transform: translateY(-2px)` 改为仅使用阴影和边框变化：

```css
.b2b-stat-card:hover {
  /* ❌ 移除：transform: translateY(-2px); */
  box-shadow: var(--shadow-lg);
  border-color: var(--brand-primary);
}
```

**Step 2: 添加 cursor-pointer 类**

确保所有可点击元素都有正确的 cursor：

```css
.b2b-stat-card,
.project-item,
.todo-item,
.activity-item,
.process-item-card {
  cursor: pointer;
}
```

**Step 3: 添加平滑过渡**

```css
.b2b-stat-card,
.project-item,
.todo-item {
  transition: box-shadow 0.25s ease, border-color 0.25s ease, background 0.25s ease;
}
```

**Step 4: 验证修改**

Run: `npm run dev`
Expected: hover 时无布局偏移，交互更流畅

**Step 5: Commit**

```bash
git add src/views/Dashboard/Workbench.vue
git commit -m "fix(ui): remove layout shift on hover, add proper cursor states"
```

---

## Task 6: 创建全局通用样式文件

**Files:**
- Create: `src/styles/accessibility.css`
- Create: `src/styles/interactions.css`

**Step 1: 创建无障碍样式文件**

```css
/* src/styles/accessibility.css */
/* ========== 无障碍增强样式 ========== */

/* 焦点可见性（WCAG 2.4.7） */
*:focus-visible {
  outline: var(--focus-width, 2px) solid var(--focus-ring, #0066CC);
  outline-offset: var(--focus-ring-offset, 2px);
}

/* 跳过导航链接（键盘用户快速跳过） */
.skip-to-content {
  position: absolute;
  top: -40px;
  left: 0;
  background: var(--brand-primary, #0066CC);
  color: #fff;
  padding: 8px 16px;
  z-index: 9999;
  transition: top 0.3s;
}

.skip-to-content:focus {
  top: 0;
}

/* 屏幕阅读器专用内容 */
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border-width: 0;
}

/* 颜色对比度检查 - 确保文字对比度 >= 4.5:1 */
.text-contrast-safe {
  color: var(--text-primary, #1A1A1A);
}

.text-contrast-secondary {
  color: var(--text-secondary, #666666);
}

/* 减少动画尊重用户偏好 */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

**Step 2: 创建交互状态样式文件**

```css
/* src/styles/interactions.css */
/* ========== 交互状态统一样式 ========== */

/* 可点击元素 cursor */
.clickable,
.cursor-pointer {
  cursor: pointer;
}

/* 禁用状态 cursor */
.disabled,
.cursor-not-allowed {
  cursor: not-allowed;
}

/* 平滑过渡（统一的过渡时长）*/
.transition-smooth {
  transition: all 0.25s ease;
}

.transition-fast {
  transition: all 0.15s ease;
}

/* hover 状态（不影响布局的 hover 效果）*/
.hover-lift:hover {
  box-shadow: var(--shadow-lg);
  border-color: var(--brand-primary);
}

.hover-bg:hover {
  background-color: var(--gray-50);
}

/* active 状态（触摸设备反馈）*/
@media (hover: none) and (pointer: coarse) {
  .clickable:active {
    background-color: var(--gray-100);
    transform: scale(0.98);
  }
}

/* loading 状态占位 */
.loading-skeleton {
  background: linear-gradient(
    90deg,
    var(--gray-100) 25%,
    var(--gray-50) 50%,
    var(--gray-100) 75%
  );
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
}

@keyframes skeleton-loading {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
```

**Step 3: 在 App.vue 中导入新样式文件**

```vue
<style>
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap');
@import './styles/accessibility.css';
@import './styles/interactions.css';

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  font-family: 'Plus Jakarta Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
</style>
```

**Step 4: 验证修改**

Run: `npm run dev`
Expected: 新样式文件加载正常

**Step 5: Commit**

```bash
git add src/styles/accessibility.css src/styles/interactions.css src/App.vue
git commit -m "feat(ui): add global accessibility and interaction utility styles"
```

---

## Task 7: Bidding List 表格移动端适配

**Files:**
- Modify: `src/views/Bidding/List.vue`

**Step 1: 添加表格包装器样式**

在 `<style scoped>` 中添加：

```css
/* 表格移动端适配 */
@media (max-width: 768px) {
  .table-container {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    border-radius: var(--radius-md);
  }

  .el-table {
    min-width: 800px; /* 确保表格不会过小 */
  }

  /* 自定义滚动条样式 */
  .table-container::-webkit-scrollbar {
    height: 6px;
  }

  .table-container::-webkit-scrollbar-thumb {
    background: var(--gray-300);
    border-radius: 3px;
  }

  .table-container::-webkit-scrollbar-thumb:hover {
    background: var(--gray-400);
  }

  /* 卡片视图作为替代方案 */
  .mobile-card-view {
    display: flex;
    flex-direction: column;
    gap: var(--space-md);
  }

  .mobile-card-item {
    background: #fff;
    border-radius: var(--radius-md);
    padding: var(--space-md);
    border: 1px solid var(--gray-200);
  }

  .mobile-card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--space-sm);
  }

  .mobile-card-title {
    font-size: var(--font-size-base);
    font-weight: 600;
    color: var(--text-primary);
  }

  .mobile-card-body {
    display: flex;
    flex-direction: column;
    gap: var(--space-xs);
    font-size: var(--font-size-sm);
    color: var(--text-secondary);
  }

  .mobile-card-actions {
    display: flex;
    gap: var(--space-sm);
    margin-top: var(--space-sm);
  }
}
```

**Step 2: 更新模板以支持移动端卡片视图**

```vue
<template>
  <div class="bidding-list-page">
    <!-- ... 其他内容保持不变 ... -->

    <!-- 表格区域 -->
    <el-card class="table-card" shadow="never">
      <!-- PC 端表格视图 -->
      <div class="table-container" v-if="!isMobile">
        <el-table :data="filteredTenders" style="width: 100%">
          <!-- ... 表格列保持不变 ... -->
        </el-table>
      </div>

      <!-- 移动端卡片视图 -->
      <div class="mobile-card-view" v-else>
        <div
          v-for="tender in filteredTenders"
          :key="tender.id"
          class="mobile-card-item"
          @click="handleViewDetail(tender)"
        >
          <div class="mobile-card-header">
            <span class="mobile-card-title">{{ tender.title }}</span>
            <el-tag :type="getStatusType(tender.status)" size="small">
              {{ tender.status }}
            </el-tag>
          </div>
          <div class="mobile-card-body">
            <div><strong>客户：</strong>{{ tender.customer }}</div>
            <div><strong>地区：</strong>{{ tender.region }}</div>
            <div><strong>预算：</strong>{{ tender.budget }}</div>
            <div><strong>截止：</strong>{{ tender.deadline }}</div>
          </div>
          <div class="mobile-card-actions">
            <el-button size="small" type="primary" @click.stop="handleViewDetail(tender)">
              查看详情
            </el-button>
            <el-button size="small" @click.stop="handleFollow(tender)">
              关注
            </el-button>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
// ... 其他代码保持不变 ...

// 添加移动端检测
import { ref, computed, onMounted, onUnmounted } from 'vue'

const isMobile = ref(false)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

// ... 其他代码保持不变 ...
</script>
```

**Step 3: 验证修改**

Run: `npm run dev`
Expected: 移动端显示卡片视图，PC 端显示表格视图

**Step 4: Commit**

```bash
git add src/views/Bidding/List.vue
git commit -m "feat(ui): add mobile card view for bidding list table"
```

---

## Task 8: 仪表盘数据趋势展示优化

**Files:**
- Modify: `src/views/Dashboard/Workbench.vue`

**Step 1: 更新统计数据结构，添加趋势信息**

```vue
<script setup>
// 统计数据 - 添加趋势信息
const stats = ref([
  {
    key: 'tenders',
    label: '标讯数量',
    value: '128',
    icon: Document,
    color: '#E6F7FF',
    trend: {
      value: '+12.5%',
      direction: 'up',  // 'up' | 'down' | 'neutral'
      label: '较上月'
    }
  },
  {
    key: 'projects',
    label: '进行中项目',
    value: '12',
    icon: Briefcase,
    color: '#F6FFED',
    trend: {
      value: '+2',
      direction: 'up',
      label: '较上月'
    }
  },
  {
    key: 'winRate',
    label: '中标率',
    value: '68%',
    icon: TrendCharts,
    color: '#FFF7E6',
    trend: {
      value: '-3.2%',
      direction: 'down',
      label: '较上月'
    }
  },
  {
    key: 'tasks',
    label: '待处理任务',
    value: '23',
    icon: Check,
    color: '#FFF1F0',
    trend: {
      value: '-5',
      direction: 'neutral',  // 减少是好方向
      label: '较上月'
    }
  }
])
</script>
```

**Step 2: 更新模板以显示趋势**

```vue
<template>
  <div class="workbench-page">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6" v-for="stat in stats" :key="stat.key">
        <div class="b2b-stat-card">
          <div class="b2b-stat-icon" :style="{ background: stat.color }">
            <el-icon :size="24">
              <component :is="stat.icon" />
            </el-icon>
          </div>
          <div class="b2b-stat-content">
            <div class="b2b-stat-value">{{ stat.value }}</div>
            <div class="b2b-stat-label">{{ stat.label }}</div>
            <!-- ✅ 新增：趋势显示 -->
            <div v-if="stat.trend" class="b2b-stat-trend" :class="'trend-' + stat.trend.direction">
              <el-icon v-if="stat.trend.direction === 'up'">
                <ArrowTop />
              </el-icon>
              <el-icon v-else-if="stat.trend.direction === 'down'">
                <ArrowBottom />
              </el-icon>
              <span>{{ stat.trend.value }}</span>
              <span class="trend-label">{{ stat.trend.label }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
    <!-- ... 其他内容保持不变 ... -->
  </div>
</template>

<script setup>
import {
  List, Warning, Briefcase, Bell, View, Clock, Calendar, User,
  Document, Plus, TrendCharts, DataAnalysis, Message, Check,
  CircleCheck, CircleClose, Loading, ArrowTop, ArrowBottom  // ✅ 新增
} from '@element-plus/icons-vue'
// ... 其他代码保持不变 ...
</script>
```

**Step 3: 添加趋势样式**

```css
.b2b-stat-trend {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-xs);
  margin-top: 4px;
}

.b2b-stat-trend.trend-up {
  color: var(--color-success);
}

.b2b-stat-trend.trend-down {
  color: var(--color-danger);
}

.b2b-stat-trend.trend-neutral {
  color: var(--color-success);  /* 任务减少是好事，用绿色 */
}

.trend-label {
  color: var(--text-tertiary);
  margin-left: 2px;
}
```

**Step 4: 验证修改**

Run: `npm run dev`
Expected: 统计卡片显示趋势指标

**Step 5: Commit**

```bash
git add src/views/Dashboard/Workbench.vue
git commit -m "feat(ui): add trend indicators to dashboard stats"
```

---

## Task 9: 全局样式变量统一

**Files:**
- Modify: `src/styles/card-b2b.css`

**Step 1: 将硬编码颜色替换为 CSS 变量**

```css
/**
 * B2B Professional Card Styles
 * Unified card component styling for bid management platform
 */

:root {
  /* Professional B2B Color Palette - 使用 variables.css 中的变量 */
  --card-border-radius: var(--radius-md, 8px);
  --card-shadow: var(--shadow-sm);
  --card-shadow-hover: var(--shadow-md);
  --card-border: 1px solid var(--gray-200);
  --card-padding: var(--space-lg, 20px);
  --card-gap: var(--space-md, 16px);

  /* Icon Backgrounds - 使用变量 */
  --icon-bg-primary: var(--color-primary-bg);
  --icon-bg-success: var(--color-success-bg);
  --icon-bg-warning: var(--color-warning-bg);
  --icon-bg-danger: var(--color-danger-bg);
}

/* ... 其他样式保持不变，但将硬编码颜色替换为变量 ... */

/* 示例：修改 .b2b-stat-icon.primary */
.b2b-stat-icon.primary {
  background: var(--icon-bg-primary);
  color: var(--color-primary);
}

/* 修改 .b2b-stat-value 颜色 */
.b2b-stat-value {
  font-size: 32px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
  margin-bottom: 4px;
}

/* 修改 .b2b-stat-label 颜色 */
.b2b-stat-label {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 400;
}
```

**Step 2: 验证修改**

Run: `npm run dev`
Expected: 样式保持一致，但使用统一变量

**Step 3: Commit**

```bash
git add src/styles/card-b2b.css
git commit -m "refactor(ui): replace hardcoded colors with CSS variables"
```

---

## Task 10: 添加跳过导航链接（无障碍）

**Files:**
- Modify: `src/components/layout/MainLayout.vue`

**Step 1: 在布局顶部添加跳过导航链接**

```vue
<template>
  <el-container class="main-layout" :class="{ 'mobile': isMobile }">
    <!-- ✅ 新增：跳过导航链接 -->
    <a href="#main-content" class="skip-to-content">
      跳转到主内容
    </a>

    <!-- PC端侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside" v-if="!isMobile">
      <Sidebar :collapse="isCollapse" />
    </el-aside>

    <!-- ... 其他内容保持不变 ... -->

    <el-container>
      <el-header height="56px" class="layout-header">
        <Header
          @toggle-collapse="toggleCollapse"
          @mobile-menu-click="mobileDrawerVisible = true"
        />
      </el-header>

      <!-- ✅ 新增：给主内容区域添加 id -->
      <el-main id="main-content" class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
/* ... 现有样式保持不变 ... */

/* ✅ 新增：跳过导航链接样式 */
.skip-to-content {
  position: absolute;
  top: -40px;
  left: 0;
  background: var(--brand-primary, #0066CC);
  color: #fff;
  padding: 8px 16px;
  z-index: 9999;
  transition: top 0.3s;
  text-decoration: none;
  font-size: var(--font-size-sm);
}

.skip-to-content:focus {
  top: 0;
}
</style>
```

**Step 2: 验证修改**

Run: `npm run dev`
Expected: Tab 键聚焦时显示"跳转到主内容"链接

**Step 3: Commit**

```bash
git add src/components/layout/MainLayout.vue
git commit -m "feat(a11y): add skip to content link for keyboard navigation"
```

---

## 验收测试清单

完成所有任务后，运行以下验证：

### 视觉验证
- [ ] 无表情符号图标，全部使用 Element Plus 图标
- [ ] 用户头像显示为姓名首字母 + 渐变背景
- [ ] 侧边栏文字对比度足够（深色背景上清晰可见）
- [ ] 统计卡片显示趋势指标

### 交互验证
- [ ] Tab 键导航可见焦点环
- [ ] hover 状态无布局偏移
- [ ] 可点击元素有正确 cursor: pointer
- [ ] 点击跳过导航链接直接跳到主内容

### 移动端验证
- [ ] 标讯列表在小屏幕上显示卡片视图
- [ ] 表格可横向滚动
- [ ] 触摸目标最小 44x44px

### 浏览器测试
- [ ] Chrome/Edge (最新版)
- [ ] Safari (最新版)
- [ ] Firefox (最新版)

### 无障碍验证
- [ ] 使用键盘可以完成所有操作
- [ ] 颜色对比度 >= 4.5:1
- [ ] 尊重 `prefers-reduced-motion` 设置

---

## 最终提交

完成所有任务后：

```bash
# 检查修改
git status

# 创建总结提交
git commit --allow-empty -m "chore: complete B2B UI professional style improvements

- Replace all emoji icons with Element Plus icons
- Update user avatar to show initials with gradient
- Improve sidebar text contrast for accessibility
- Add global focus states for keyboard navigation
- Update font system to Plus Jakarta Sans
- Fix hover states to prevent layout shift
- Add mobile card view for tables
- Add trend indicators to dashboard stats
- Unify color system with CSS variables
- Add skip to content link for accessibility

Follows ui-ux-pro-max skill guidelines for B2B applications."
```

---

**Plan complete and saved to `docs/plans/2025-02-27-ui-b2b-improvements.md`.**
