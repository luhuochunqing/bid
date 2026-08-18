<template>
  <el-drawer
    v-model="visible"
    title="AI 标书质量核查"
    size="880px"
    direction="rtl"
    :destroy-on-close="false"
    :close-on-click-modal="false"
    class="quality-check-drawer"
  >
    <!-- 头部元信息与操作栏 -->
    <template #header>
      <div class="drawer-header-content">
        <div class="header-main">
          <div class="header-title-row">
            <span class="header-title">AI 标书质量核查</span>
            <el-tag :type="overallStatusTag.type" effect="dark" class="overall-status-tag">
              {{ overallStatusTag.label }}
            </el-tag>
          </div>
          <div class="header-sub-row">
            <span class="file-name-meta" v-if="report?.bidFileName">
              <el-icon><Document /></el-icon> {{ report.bidFileName }}
            </span>
            <span class="check-time-meta" v-if="report?.checkedAt">
              <el-icon><Clock /></el-icon> {{ formatTime(report.checkedAt) }}
            </span>
          </div>
        </div>
        <div class="header-actions">
          <el-button
            type="primary"
            size="small"
            :loading="polling"
            :icon="Refresh"
            @click="handleRecheckConfirm"
          >
            重新核查
          </el-button>
        </div>
      </div>
    </template>

    <!-- 异步轮询进度状态 -->
    <div v-if="polling" class="polling-progress-container">
      <div class="progress-box">
        <div class="ai-bot-avatar">🤖</div>
        <div class="progress-step-title">{{ currentStepText || '正在执行 AI 标书质量深度核查...' }}</div>
        <el-progress
          :percentage="progressPercent"
          :status="progressStatus"
          :stroke-width="14"
          striped
          striped-flow
          class="step-progress-bar"
        />
        <div class="progress-hint">全量解析真实投标文件、提取页码证据并对照 25 项合规矩阵，通常需要 10~30 秒</div>
      </div>
    </div>

    <!-- 报告内容主体 -->
    <div v-else-if="report" class="report-main-content">
      <!-- 统计指标看板 -->
      <div class="stats-overview-grid">
        <div class="stat-card stat-total" @click="activeStatusFilter = 'ALL'">
          <div class="stat-num">{{ report.totalCount || 25 }}</div>
          <div class="stat-label">总核查项</div>
        </div>
        <div
          class="stat-card stat-passed"
          :class="{ active: activeStatusFilter === 'PASS' }"
          @click="activeStatusFilter = 'PASS'"
        >
          <div class="stat-num">{{ report.passedCount || 0 }}</div>
          <div class="stat-label">✓ 通过项</div>
        </div>
        <div
          class="stat-card stat-failed"
          :class="{ active: activeStatusFilter === 'FAIL' }"
          @click="activeStatusFilter = 'FAIL'"
        >
          <div class="stat-num">{{ report.failedCount || 0 }}</div>
          <div class="stat-label">✗ 不通过项</div>
        </div>
        <div
          class="stat-card stat-manual"
          :class="{ active: activeStatusFilter === 'MANUAL_REVIEW' }"
          @click="activeStatusFilter = 'MANUAL_REVIEW'"
        >
          <div class="stat-num">{{ report.manualReviewCount || 0 }}</div>
          <div class="stat-label">◐ 待人工确认</div>
        </div>
      </div>

      <!-- 快速状态筛选条 -->
      <div class="filter-tab-bar">
        <el-radio-group v-model="activeStatusFilter" size="small">
          <el-radio-button label="ALL">全部 ({{ report.totalCount || 25 }})</el-radio-button>
          <el-radio-button label="FAIL">不通过 ({{ report.failedCount || 0 }})</el-radio-button>
          <el-radio-button label="MANUAL_REVIEW">待确认 ({{ report.manualReviewCount || 0 }})</el-radio-button>
          <el-radio-button label="PASS">通过 ({{ report.passedCount || 0 }})</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 3 大分类分组卡片列表 -->
      <div class="categories-container">
        <div
          v-for="group in filteredCategories"
          :key="group.category"
          class="category-section"
        >
          <div class="category-header" @click="toggleCategory(group.category)">
            <div class="cat-title-left">
              <span class="cat-name">{{ group.title }}</span>
              <span class="cat-count">({{ group.items.length }}项)</span>
            </div>
            <div class="cat-badges-right">
              <span v-if="group.failCount" class="badge-tag fail-tag">不通过 {{ group.failCount }}</span>
              <span v-if="group.manualCount" class="badge-tag manual-tag">待确认 {{ group.manualCount }}</span>
              <span v-if="group.passCount" class="badge-tag pass-tag">通过 {{ group.passCount }}</span>
              <el-icon class="collapse-arrow" :class="{ rotated: collapsedCategories[group.category] }">
                <ArrowDown />
              </el-icon>
            </div>
          </div>

          <div v-show="!collapsedCategories[group.category]" class="category-items-list">
            <QualityCheckItemCard
              v-for="item in group.items"
              :key="item.itemCode"
              :item="item"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 空白状态 -->
    <div v-else class="empty-state-container">
      <el-empty description="当前项目尚未执行标书质量核查">
        <el-button type="primary" :loading="polling" @click="triggerCheck(false)">
          立即执行 AI 质量核查
        </el-button>
      </el-empty>
    </div>
  </el-drawer>
</template>

<script setup>
import { Document, Clock, Refresh, ArrowDown } from '@element-plus/icons-vue'
import QualityCheckItemCard from './QualityCheckItemCard.vue'
import { useQualityCheckDrawer } from '../composables/useQualityCheckDrawer.js'

const props = defineProps({
  projectId: {
    type: [String, Number],
    required: true
  }
})

const {
  visible,
  polling,
  progressPercent,
  currentStepText,
  progressStatus,
  report,
  activeStatusFilter,
  collapsedCategories,
  overallStatusTag,
  filteredCategories,
  toggleCategory,
  open,
  handleRecheckConfirm,
  triggerCheck
} = useQualityCheckDrawer(props)

function formatTime(timeStr) {
  if (!timeStr) return ''
  try {
    const d = new Date(timeStr)
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return timeStr
  }
}

defineExpose({
  open,
  triggerCheck
})
</script>

<style scoped>
@import './qualityCheckDrawer.css';
</style>
