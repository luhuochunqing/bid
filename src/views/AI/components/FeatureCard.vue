<template>
  <el-card class="b2b-feature-card feature-card" :class="{ disabled: !feature.enabled }" shadow="never">
    <!-- 卡片头部 -->
    <div class="card-header">
      <div class="header-left">
        <div class="feature-icon" :style="{ backgroundColor: getIconColor(feature.icon) }">
          <component :is="getIconComponent(feature.icon)" />
        </div>
        <span class="feature-name">{{ feature.name }}</span>
      </div>
      <div class="header-right">
        <el-switch
          :model-value="feature.enabled"
          @change="handleToggle"
          inline-prompt
          active-text="启用"
          inactive-text="禁用"
        />
        <el-button
          type="primary"
          link
          @click="handleConfigure"
        >
          <el-icon><Setting /></el-icon>
          配置
        </el-button>
      </div>
    </div>

    <el-divider />

    <!-- 卡片内容 -->
    <div class="card-content">
      <!-- 说明 -->
      <div class="content-row">
        <span class="label">说明：</span>
        <span class="value">{{ feature.description }}</span>
      </div>

      <!-- 统计 -->
      <div class="content-row">
        <span class="label">统计：</span>
        <div class="stats-tags">
          <el-tag type="info" size="small">
            本月使用 {{ feature.stats.usageCount }} 次
          </el-tag>
          <el-tag :type="getAccuracyType(feature.stats.accuracy)" size="small">
            准确率 {{ feature.stats.accuracy }}%
          </el-tag>
        </div>
      </div>

      <!-- 提示词预览 -->
      <div class="content-row prompt-row">
        <span class="label">提示词：</span>
        <div class="prompt-preview">
          <el-icon class="prompt-icon"><ChatDotRound /></el-icon>
          <span class="prompt-text">{{ truncatedPrompt }}</span>
          <el-tooltip :content="feature.promptTemplate" placement="top">
            <el-button type="info" link size="small">
              <el-icon><MoreFilled /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed, markRaw } from 'vue'
import { Setting, ChatDotRound, MoreFilled } from '@element-plus/icons-vue'
import {
  TrendCharts,
  Aim,
  View,
  TrendCharts as TrendUp,
  MagicStick,
  Lock,
  Document as DocumentIcon,
  User,
  Setting as SettingIcon
} from '@element-plus/icons-vue'

// 图标映射表
const iconMap = {
  'analysis': markRaw(TrendCharts),
  'score': markRaw(Aim),
  'intel': markRaw(View),
  'roi': markRaw(TrendUp),
  'assembly': markRaw(MagicStick),
  'compliance': markRaw(Lock),
  'version': markRaw(DocumentIcon),
  'collab': markRaw(User),
  'tasks': markRaw(SettingIcon)
}

// 图标颜色映射
const iconColorMap = {
  'analysis': '#409EFF',
  'score': '#67C23A',
  'intel': '#E6A23C',
  'roi': '#F56C6C',
  'assembly': 'var(--text-muted)',
  'compliance': '#67C23A',
  'version': '#409EFF',
  'collab': '#E6A23C',
  'tasks': 'var(--text-muted)'
}

const props = defineProps({
  feature: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['toggle', 'configure'])

// 获取图标组件
const getIconComponent = (iconName) => {
  return iconMap[iconName] || iconMap['analysis']
}

// 获取图标背景色
const getIconColor = (iconName) => {
  return iconColorMap[iconName] || '#409EFF'
}

// 截断提示词预览（前50字）
const truncatedPrompt = computed(() => {
  const prompt = props.feature.promptTemplate || ''
  return prompt.length > 50 ? prompt.substring(0, 50) + '...' : prompt
})

// 根据准确率返回标签类型
const getAccuracyType = (accuracy) => {
  if (accuracy >= 95) return 'success'
  if (accuracy >= 90) return 'info'  // 修复: 使用 'info' 替代空字符串
  if (accuracy >= 85) return 'warning'
  return 'danger'
}

const handleToggle = (val) => {
  emit('toggle', props.feature.id, val)
}

const handleConfigure = () => {
  emit('configure', props.feature.id)
}
</script>

<script>
export default {
  name: 'FeatureCard'
}
</script>

<style scoped>
/* Using B2B feature card styles - minimal custom overrides */
.feature-card {
  transition: all 0.25s ease;
}

.feature-card.disabled {
  opacity: 0.6;
}

.feature-card :deep(.el-card__body) {
  padding: var(--card-padding, 20px);
}

/* 卡片头部 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.feature-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  font-size: 20px;
  color: var(--bg-card);
}

.feature-icon :deep(svg) {
  width: 20px;
  height: 20px;
  fill: currentColor;
}

.feature-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* 分隔线 */
.el-divider {
  margin: 16px 0;
}

/* 卡片内容 */
.card-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.content-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.label {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.value {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.6;
}

.stats-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* 提示词预览 */
.prompt-row {
  align-items: center;
}

.prompt-preview {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background-color: var(--bg-subtle);
  border-radius: 6px;
  font-size: 12px;
}

.prompt-icon {
  color: var(--color-primary, var(--brand-primary));
  flex-shrink: 0;
}

.prompt-text {
  flex: 1;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 480px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .header-right {
    width: 100%;
    justify-content: space-between;
  }

  .stats-tags {
    flex-direction: column;
  }
}

/* ==================== Card Interaction Enhancements ==================== */

.feature-card {
  transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1);
  border: 1.5px solid var(--el-border-color-light);
}

.feature-card:hover {
  border-color: var(--accent-blue);
  box-shadow: 0 8px 24px rgba(3, 105, 161, 0.12);
  transform: translateY(-2px);
}

.feature-card:active {
  transform: translateY(0);
}

/* ==================== Icon Enhancement ==================== */

.feature-icon {
  transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.feature-card:hover .feature-icon {
  transform: scale(1.05) rotate(5deg);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* ==================== Switch Enhancement ==================== */

:deep(.el-switch__core) {
  transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1);
}

/* ==================== Button Enhancement ==================== */

:deep(.el-button--link) {
  font-size: 13px;
  font-weight: 500;
  transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1);
}

:deep(.el-button--link:hover) {
  transform: translateX(2px);
}

/* ==================== Tag Enhancement ==================== */

:deep(.el-tag) {
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  border: none;
  transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1);
}

:deep(.el-tag--primary) {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: var(--bg-card);
}

:deep(.el-tag--success) {
  background: linear-gradient(135deg, var(--color-success), var(--color-success-dark));
  color: var(--bg-card);
}

:deep(.el-tag--warning) {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: var(--bg-card);
}

:deep(.el-tag--danger) {
  background: linear-gradient(135deg, var(--color-danger), var(--color-danger));
  color: var(--bg-card);
}

:deep(.el-tag--info) {
  background: linear-gradient(135deg, var(--text-slate), var(--sidebar-text-secondary));
  color: var(--bg-card);
}

:deep(.el-tag:hover) {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

/* ==================== Prompt Preview Enhancement ==================== */

.prompt-preview {
  transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
}

.prompt-preview:hover {
  background: var(--el-color-info-light-9);
}

.prompt-icon {
  transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1);
}

.prompt-preview:hover .prompt-icon {
  transform: scale(1.1);
  color: var(--brand-xiyu-logo);
}
</style>
