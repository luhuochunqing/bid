<template>
  <el-card class="case-card" shadow="hover" @click="$emit('view-detail', caseData.caseId || caseData.id)">
    <div class="case-card-header">
      <div class="title-row">
        <!-- 匹配度标签（悬停展开 AI 匹配依据与规则浮层） -->
        <el-popover
          v-if="hasMatchScore"
          placement="top-start"
          :width="270"
          trigger="hover"
          :show-after="120"
        >
          <template #reference>
            <span class="match-stat" :class="matchStatClass" @click.stop>
              匹配度 <strong>{{ matchScore }}%</strong>
              <span v-if="displayMatchLabel" class="match-badge">{{ displayMatchLabel }}</span>
            </span>
          </template>
          <div class="match-popover-panel">
            <div class="popover-title-row">
              <span class="popover-heading">AI 匹配度分析</span>
              <el-tag size="small" :type="matchLabelTagType" effect="dark">{{ displayMatchLabel }} {{ matchScore }}%</el-tag>
            </div>
            <div v-if="matchReasonsList.length > 0" class="popover-block">
              <div class="block-label">命中维度</div>
              <div class="reasons-wrap">
                <el-tag v-for="(reason, idx) in matchReasonsList" :key="idx" size="small" type="success" effect="plain" class="reason-tag">
                  ✓ {{ reason }}
                </el-tag>
              </div>
            </div>
            <div v-if="matchedKeywordsList.length > 0" class="popover-block">
              <div class="block-label">命中关键词</div>
              <div class="keywords-wrap">
                <span v-for="(kw, idx) in matchedKeywordsList" :key="idx" class="kw-pill">{{ kw }}</span>
              </div>
            </div>
            <div class="popover-tip">综合考量条款契合度、类别、项目与客户类型及中标记录</div>
          </div>
        </el-popover>

        <span class="scoring-title">{{ caseData.scoringTitle }}</span>
      </div>
      <div class="tag-row mt-2">
        <el-tag size="small" type="primary" effect="plain">{{ getProjectTypeLabel(caseData.projectType) }}</el-tag>
        <el-tag size="small" type="success" effect="plain" class="ml-1">{{ getCustomerTypeLabel(caseData.customerType) }}</el-tag>
        <el-tag v-if="subTopicLabel" size="small" type="warning" effect="plain" class="ml-1">{{ subTopicLabel }}</el-tag>
      </div>
    </div>
    <div class="case-card-body">
      <p class="summary-text">{{ truncatedSummary }}</p>
    </div>
    <div class="case-card-footer">
      <div class="info-meta">
        <span class="date-stat">创建时间：<DateTimeDisplay :value="caseData.createdAt" /></span>
      </div>
      <el-button size="small" type="primary" @click.stop="$emit('reuse', caseData)">
        <el-icon style="vertical-align:-.2em;margin-right:4px"><CopyDocument /></el-icon>复用
      </el-button>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { CopyDocument } from '@element-plus/icons-vue'
import { getProjectTypeLabel, getCustomerTypeLabel, getSubTopicLabel, getMatchLabel, getMatchTagType, parseStringOrArray } from '../caseLabels.js'
import DateTimeDisplay from '@/components/common/DateTimeDisplay.vue'

const props = defineProps({
  caseData: { type: Object, default: () => ({}) },
  // 可选匹配度（%）：仅 AI 智能推荐等场景传入，案例库不传即零变化
  matchScore: { type: [Number, String], default: null },
})
defineEmits(['view-detail', 'reuse'])

const subTopicLabel = computed(() => getSubTopicLabel(props.caseData?.subTopic))

const hasMatchScore = computed(() => props.matchScore !== null && props.matchScore !== undefined)
const numScore = computed(() => Number(props.matchScore) || 0)

const displayMatchLabel = computed(() => getMatchLabel(props.matchScore, props.caseData?.matchLabel))

const matchLabelTagType = computed(() => getMatchTagType(props.matchScore, props.caseData?.matchLabel))

const matchStatClass = computed(() => {
  if (numScore.value >= 80) return 'stat-high'
  if (numScore.value >= 60) return 'stat-medium'
  return 'stat-normal'
})

const matchReasonsList = computed(() => parseStringOrArray(props.caseData?.matchReasons))
const matchedKeywordsList = computed(() => parseStringOrArray(props.caseData?.matchedKeywords))

const truncatedSummary = computed(() => {
  return props.caseData?.responseTextSummary || ''
})
</script>

<style scoped lang="scss">
.case-card {
  height: 100%;
  border-radius: 8px;
  transition: all 0.25s ease;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  &:hover { transform: translateY(-4px); box-shadow: 0 8px 24px var(--el-color-primary-light-9, rgba(0, 0, 0, 0.08)); }
}
.case-card-header { border-bottom: 1px dashed var(--el-border-color-lighter); padding-bottom: 12px; }
.title-row { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.match-stat {
  flex-shrink: 0;
  font-size: 12px;
  line-height: 20px;
  padding: 0 6px;
  border-radius: 4px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  cursor: help;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: opacity 0.2s;
  &:hover { opacity: 0.85; }
  strong { font-weight: 600; }
  &.stat-high {
    color: var(--el-color-danger);
    background: var(--el-color-danger-light-9);
  }
  &.stat-medium {
    color: var(--el-color-warning);
    background: var(--el-color-warning-light-9);
  }
}
.match-badge {
  font-size: 10px;
  padding: 0 3px;
  border-radius: 2px;
  background: var(--el-fill-color);
  line-height: 14px;
}
.scoring-title { font-size: 15px; font-weight: 600; color: var(--el-text-color-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.tag-row { display: flex; align-items: center; }
.case-card-body { padding: 12px 0; flex-grow: 1; }
.summary-text { font-size: 13px; color: var(--el-text-color-regular); line-height: 1.6; margin: 0; display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden; height: 83px; white-space: pre-wrap; }
.case-card-footer { border-top: 1px dashed var(--el-border-color-lighter); padding-top: 12px; display: flex; justify-content: space-between; align-items: center; }
.info-meta { font-size: 12px; color: var(--el-text-color-placeholder); display: flex; align-items: center; }
.mt-2 { margin-top: 8px; }
.ml-1 { margin-right: 4px; }
</style>

<style lang="scss">
.match-popover-panel {
  font-size: 12px;
  line-height: 1.5;
  .popover-title-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
    padding-bottom: 6px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
  .popover-heading { font-weight: 600; color: var(--el-text-color-primary); font-size: 13px; }
  .popover-block { margin-top: 6px; }
  .block-label { color: var(--el-text-color-secondary); font-size: 11px; margin-bottom: 4px; }
  .reasons-wrap { display: flex; flex-wrap: wrap; gap: 4px; }
  .reason-tag { font-size: 11px; }
  .keywords-wrap { display: flex; flex-wrap: wrap; gap: 4px; }
  .kw-pill {
    background: var(--el-fill-color-light);
    border: 1px solid var(--el-border-color-lighter);
    padding: 1px 6px;
    border-radius: 4px;
    color: var(--el-color-primary);
    font-size: 11px;
  }
  .popover-tip {
    margin-top: 8px;
    padding-top: 6px;
    border-top: 1px dashed var(--el-border-color-lighter);
    color: var(--el-text-color-placeholder);
    font-size: 11px;
  }
}
</style>
