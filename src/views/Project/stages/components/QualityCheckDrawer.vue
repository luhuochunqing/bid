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
    <!-- 头部标题栏 -->
    <template #header>
      <div class="drawer-header-content">
        <span class="header-title">AI 标书质量核查</span>
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
        <div class="progress-hint">全量解析真实投标文件、提取页码证据并对照 25 项合规矩阵，通常需要 1~2 分钟</div>
      </div>
    </div>

    <!-- 报告内容主体 -->
    <div v-else-if="report" class="report-main-content">
      <el-alert
        v-if="report.requiresRecheck"
        type="warning"
        :closable="false"
        show-icon
        title="这是历史核查报告，问题页码尚未校验。请点击重新核查获取当前定位结果。"
      />
      <!-- 汇总平铺卡：左上受检信息，右上操作按钮 -->
      <div class="summary-card">
        <div class="summary-left">
          <div v-if="report.bidFileName" class="summary-meta">
            <el-icon><Document /></el-icon>
            <span>投标文件：{{ report.bidFileName }}</span>
          </div>
          <div v-if="report.checkedAt" class="summary-meta">
            <el-icon><Clock /></el-icon>
            <span>核查时间：{{ formatTime(report.checkedAt) }}</span>
          </div>
        </div>
        <div class="summary-actions">
          <el-button
            size="small"
            :icon="Refresh"
            @click="handleRecheckConfirm"
          >
            重新核查
          </el-button>
          <el-button type="primary" size="small" @click="goToScoreParse">AI 评分</el-button>
        </div>
      </div>

      <!-- 统计指标看板（点击筛选状态） -->
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
          <div class="stat-label">◐ 待确认项</div>
        </div>
      </div>

      <!-- 分类定位条：点击展开并滚动到对应分组 -->
      <div class="category-tab-bar">
        <button
          v-for="tab in categoryTabs"
          :key="tab.category"
          type="button"
          class="category-tab"
          @click="scrollToCategory(tab.category)"
        >
          {{ tab.label }}（{{ tab.count }}）
        </button>
      </div>

      <!-- 3 大分类分组卡片列表 -->
      <div class="categories-container">
        <div
          v-for="group in filteredCategories"
          :key="group.category"
          :ref="(el) => registerCategoryAnchor(group.category, el)"
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
              @detail="openDetail"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 失败/异常状态卡片 -->
    <div v-else-if="errorMessage" class="error-state-container">
      <el-result
        icon="error"
        title="AI 标书质量核查未完成"
        sub-title="核查任务执行异常，未生成有效核查报告"
      >
        <template #extra>
          <div v-if="errorPresentation.scanned" class="scan-document-alert">
            <el-alert
              :title="errorPresentation.title"
              :description="errorPresentation.message"
              type="error"
              show-icon
              :closable="false"
            />
            <div v-if="errorPresentation.detail" class="error-details-text">原始错误：{{ errorPresentation.detail }}</div>
          </div>
          <div v-else class="error-details-box">
            <div class="error-details-header">
              <el-icon><WarningFilled /></el-icon>
              <span>错误详情</span>
            </div>
            <div class="error-details-text">{{ errorMessage }}</div>
          </div>
          <div class="error-action-buttons">
            <el-button
              type="primary"
              :icon="Refresh"
              :loading="polling"
              @click="handleRecheckConfirm"
            >
              重新核查（跳过缓存）
            </el-button>
            <el-button
              :loading="polling"
              @click="triggerCheck(false)"
            >
              尝试恢复（检查缓存）
            </el-button>
          </div>
        </template>
      </el-result>
    </div>

    <!-- 后台执行中：前端等待窗口到顶，任务仍在后端运行（spec 066 FR-008） -->
    <div v-else-if="backgroundRunning" class="background-running-container">
      <el-result
        icon="info"
        title="核查任务仍在后台执行"
        sub-title="大文件核查耗时较长，任务仍在后台运行；可点击刷新查看最新进度，或稍后再来查看结果。"
      >
        <template #extra>
          <el-button type="primary" :icon="Refresh" @click="loadLatestReport">
            刷新查看
          </el-button>
        </template>
      </el-result>
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

  <!-- 检查项详情弹窗（居中） -->
  <QualityCheckDetailDialog
    v-model="detailVisible"
    :item="detailItem"
    :objective-score="objectiveScore"
    @go-to-score-parse="goToScoreParse"
  />
</template>

<script setup>
import { Document, Clock, Refresh, ArrowDown, WarningFilled } from '@element-plus/icons-vue'
import QualityCheckItemCard from './QualityCheckItemCard.vue'
import QualityCheckDetailDialog from './QualityCheckDetailDialog.vue'
import { useQualityCheckDrawer } from '../composables/useQualityCheckDrawer.js'
import { formatTime } from '@/composables/projectDetail/scoreParseTask.js'

const props = defineProps({
  projectId: {
    type: [String, Number],
    required: true
  }
})

const emit = defineEmits(['go-to-score-parse'])

const {
  visible,
  polling,
  errorMessage,
  errorPresentation,
  backgroundRunning,
  progressPercent,
  currentStepText,
  progressStatus,
  report,
  activeStatusFilter,
  collapsedCategories,
  categoryTabs,
  filteredCategories,
  objectiveScore,
  detailItem,
  detailVisible,
  toggleCategory,
  registerCategoryAnchor,
  scrollToCategory,
  openDetail,
  open,
  loadLatestReport,
  handleRecheckConfirm,
  goToScoreParse,
  triggerCheck
} = useQualityCheckDrawer(props, emit)

defineExpose({
  open,
  triggerCheck
})
</script>

<style scoped>
@import './qualityCheckDrawer.css';
</style>
