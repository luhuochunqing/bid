<template>
  <div>
    <el-alert
      title="AI智能分析"
      type="success"
      :closable="false"
      show-icon
      class="mb-16"
    >
      <template #default>
        <div v-if="analyzing" class="ai-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>AI正在分析项目数据...</span>
        </div>
        <div v-else class="ai-summary">
          <div class="summary-header">
            <div class="win-score">
              <span class="score-label">赢面评分</span>
              <span class="score-value" :class="getWinScoreClass(aiSummary.winScore)">
                {{ aiSummary.winScore }}
              </span>
              <span class="score-max">/100</span>
            </div>
            <div class="win-level">
              <el-tag :type="getWinLevelType(aiSummary.winLevel)" size="large">
                {{ getWinLevelText(aiSummary.winLevel) }}
              </el-tag>
            </div>
          </div>
        </div>
      </template>
    </el-alert>

    <el-divider content-position="left">
      <el-icon><Warning /></el-icon>
      关键风险
    </el-divider>
    <div class="risk-list">
      <div
        v-for="(risk, index) in aiSummary.risks"
        :key="index"
        class="risk-item"
        :class="'risk-' + risk.level"
      >
        <el-icon class="risk-icon">
          <WarningFilled v-if="risk.level === 'high'" />
          <Warning v-else />
        </el-icon>
        <span class="risk-content">{{ risk.content }}</span>
        <el-tag :type="risk.level === 'high' ? 'danger' : 'warning'" size="small">
          {{ risk.level === 'high' ? '高风险' : '中风险' }}
        </el-tag>
      </div>
    </div>

    <el-divider content-position="left">
      <el-icon><MagicStick /></el-icon>
      AI建议
    </el-divider>
    <FeaturePlaceholder
      v-if="scorePreviewPlaceholder"
      compact
      :title="scorePreviewPlaceholder.title"
      :message="scorePreviewPlaceholder.message"
      :hint="scorePreviewPlaceholder.hint"
    />
    <ul class="suggestion-list">
      <li v-for="(suggestion, index) in aiSummary.suggestions" :key="index">
        {{ suggestion }}
      </li>
    </ul>

    <el-divider content-position="left">
      <el-icon><DataAnalysis /></el-icon>
      评分点覆盖率
    </el-divider>
    <ScoreCoverage
      :score-categories="scoreAnalysis.scoreCategories"
      :gap-items="scoreAnalysis.gapItems"
    />

    <el-divider content-position="left">
      <el-icon><List /></el-icon>
      AI生成任务清单
    </el-divider>
    <div class="ai-tasks">
      <el-alert
        title="以下任务由AI根据评分缺口自动生成，您可以编辑调整"
        type="info"
        :closable="false"
        show-icon
        class="mb-12"
      />
      <div v-for="(aiTask, index) in aiGeneratedTasks" :key="index" class="ai-task-item">
        <el-checkbox v-model="aiTask.selected" :label="aiTask.name" />
        <div class="ai-task-meta">
          <el-tag size="small" :type="getPriorityType(aiTask.priority)">
            {{ getPriorityText(aiTask.priority) }}
          </el-tag>
          <span class="ai-task-suggest">{{ aiTask.suggestion }}</span>
        </div>
      </div>
    </div>

    <el-alert
      title="确认以上信息无误后，点击下方按钮完成项目创建"
      type="success"
      :closable="false"
      show-icon
      class="mt-16"
    />
  </div>
</template>

<script setup>
import { Loading, Warning, WarningFilled, MagicStick, DataAnalysis, List } from '@element-plus/icons-vue'
import ScoreCoverage from '@/components/ai/ScoreCoverage.vue'
import FeaturePlaceholder from '@/components/common/FeaturePlaceholder.vue'

defineProps({
  analyzing: { type: Boolean, default: false },
  aiSummary: { type: Object, required: true },
  scoreAnalysis: { type: Object, required: true },
  aiGeneratedTasks: { type: Array, default: () => [] },
  scorePreviewPlaceholder: { type: Object, default: null }
})

function getWinScoreClass(score) {
  if (score >= 80) return 'score-high'
  if (score >= 60) return 'score-medium'
  return 'score-low'
}

function getWinLevelType(level) {
  return { high: 'success', medium: 'warning', low: 'danger' }[level] || ''
}

function getWinLevelText(level) {
  return { high: '赢面较高', medium: '赢面中等', low: '赢面较低' }[level] || ''
}

function getPriorityType(priority) {
  return { high: 'danger', medium: 'warning', low: 'info' }[priority] || ''
}

function getPriorityText(priority) {
  return { high: '高', medium: '中', low: '低' }[priority] || ''
}

async function validate() { return true }

defineExpose({ validate })
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }
.mb-12 { margin-bottom: 12px; }
.mt-16 { margin-top: 16px; }

.ai-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
}

.ai-summary { width: 100%; }

.summary-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
}

.win-score {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.score-label { font-size: 14px; color: var(--text-secondary-ui); }
.score-value { font-size: 32px; font-weight: 600; }
.score-high { color: #67c23a; }
.score-medium { color: #e6a23c; }
.score-low { color: #f56c6c; }
.score-max { font-size: 16px; color: var(--text-muted); }
.win-level { flex-shrink: 0; }

.risk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.risk-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background-color: #fef0f0;
  border-radius: 8px;
  border-left: 4px solid #f56c6c;
}

.risk-item.risk-medium {
  background-color: #fdf6ec;
  border-left-color: #e6a23c;
}

.risk-icon { font-size: 20px; }
.risk-item.risk-high .risk-icon { color: #f56c6c; }
.risk-item.risk-medium .risk-icon { color: #e6a23c; }
.risk-content { flex: 1; color: var(--text-secondary-ui); }

.suggestion-list {
  margin: 0;
  padding-left: 20px;
  margin-bottom: 16px;
}

.suggestion-list li {
  margin-bottom: 8px;
  color: var(--text-secondary-ui);
  line-height: 1.6;
}

.ai-tasks {
  padding: 16px;
  background-color: var(--bg-subtle);
  border-radius: 8px;
}

.ai-task-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background-color: var(--bg-card);
  border-radius: 6px;
  margin-bottom: 8px;
}

.ai-task-item:last-child { margin-bottom: 0; }

.ai-task-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-left: 24px;
}

.ai-task-suggest { font-size: 12px; color: var(--text-muted); }

@media (max-width: 768px) {
  .summary-header { flex-direction: column; align-items: flex-start; gap: 12px; }
  .risk-item { flex-direction: column; align-items: flex-start; gap: 8px; }
  .ai-task-meta { flex-direction: column; align-items: flex-start; gap: 4px; padding-left: 0; }
}
</style>
