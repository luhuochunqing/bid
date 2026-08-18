<template>
  <el-dialog
    v-model="dialogVisible"
    title="推荐案例详情"
    width="760px"
    destroy-on-close
    append-to-body
    class="ai-case-detail-dialog"
  >
    <div v-if="caseDetail" class="detail-container">
      <div class="header-section">
        <div class="title-row">
          <h3 class="case-title">{{ caseDetail.scoringPointTitle }}</h3>
          <div class="score-badge" :class="getScoreClass(caseDetail.matchScore)">
            <span class="score-num">{{ caseDetail.matchScore }}%</span>
            <span class="score-label">{{ caseDetail.matchLabel || '高匹配' }}</span>
          </div>
        </div>

        <div class="meta-tags">
          <el-tag size="small" type="info" effect="plain">{{ caseDetail.scoringCategory || '技术方案' }}</el-tag>
          <el-tag v-if="caseDetail.bidResult === 'WON'" size="small" type="success" effect="plain">历史中标</el-tag>
          <el-tag v-if="caseDetail.customerType" size="small" type="warning" effect="plain">{{ customerTypeLabel(caseDetail.customerType) }}</el-tag>
          <span class="source-info">来源项目：{{ caseDetail.sourceProjectName || '历史归档标书' }}</span>
        </div>

        <div v-if="caseDetail.matchReasons && caseDetail.matchReasons.length" class="reason-bar">
          <span class="reason-title">🎯 匹配特征：</span>
          <el-tag
            v-for="(reason, idx) in caseDetail.matchReasons"
            :key="idx"
            size="small"
            class="reason-tag"
          >
            {{ reason }}
          </el-tag>
        </div>
      </div>

      <!-- 案例元数据描述表格 (PRD 3.6 契约) -->
      <el-descriptions :column="2" border size="small" class="case-meta-table">
        <el-descriptions-item label="项目类型">{{ projectTypeLabel(caseDetail.projectType) }}</el-descriptions-item>
        <el-descriptions-item label="客户类型">{{ customerTypeLabel(caseDetail.customerType) }}</el-descriptions-item>
        <el-descriptions-item label="投标结果">
          <el-tag :type="caseDetail.bidResult === 'WON' ? 'success' : 'info'" size="small">
            {{ caseDetail.bidResult === 'WON' ? '已中标' : (caseDetail.bidResult || '归档') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="复用次数">{{ caseDetail.reuseCount || 0 }} 次</el-descriptions-item>
        <el-descriptions-item label="来源项目" :span="2">{{ caseDetail.sourceProjectName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="招标主体" :span="2">{{ caseDetail.bidEntity || caseDetail.sourceProjectName || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider class="custom-divider" />

      <div class="content-tabs">
        <div class="section-block">
          <h4 class="section-title">📌 招标原始要求</h4>
          <div class="raw-requirement-box">
            {{ caseDetail.requirementRaw || '暂无原始要求记录' }}
          </div>
        </div>

        <div class="section-block">
          <h4 class="section-title">💡 推荐应答原文</h4>
          <div class="response-text-box" v-html="highlightedResponseText"></div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button type="primary" class="reuse-btn" @click="handleReuse">
          ⚡ 立即复用到标书
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { customerTypeLabel, projectTypeLabel } from '@/views/Project/utils/projectListFormatters.js'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  caseDetail: {
    type: Object,
    default: () => null
  }
})

const emit = defineEmits(['update:modelValue', 'reuse'])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const getScoreClass = (score) => {
  if (score >= 80) return 'score-high'
  if (score >= 60) return 'score-medium'
  return 'score-normal'
}

const highlightedResponseText = computed(() => {
  if (!props.caseDetail || !props.caseDetail.responseText) {
    return '暂无应答内容'
  }
  let text = props.caseDetail.responseText
  const keywords = props.caseDetail.matchedKeywords || []
  if (keywords.length > 0) {
    keywords.forEach((kw) => {
      if (kw && kw.length >= 2) {
        const reg = new RegExp(`(${kw})`, 'gi')
        text = text.replace(reg, '<mark class="highlight-kw">$1</mark>')
      }
    })
  }
  return text.replace(/\n/g, '<br/>')
})

const handleReuse = () => {
  emit('reuse', props.caseDetail)
}
</script>

<style scoped>
.detail-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.case-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
}

.score-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 16px;
  font-weight: 600;
  font-size: 13px;
}

.score-high {
  background: var(--brand-xiyu-logo-light);
  color: var(--brand-xiyu-logo);
  border: 1px solid var(--brand-xiyu-logo-soft);
}

.score-medium {
  background: var(--color-info-bg);
  color: var(--brand-primary);
  border: 1px solid var(--color-info-bg);
}

.score-normal {
  background: var(--gray-50);
  color: var(--gray-650);
}

.meta-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
}

.source-info {
  color: var(--text-muted);
  font-size: 12px;
  margin-left: 4px;
}

.reason-bar {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--gray-600);
}

.reason-tag {
  background: var(--gray-50);
  border: none;
  color: var(--gray-700);
}

.custom-divider {
  margin: 12px 0;
}

.section-block {
  margin-bottom: 16px;
}

.section-title {
  margin: 0 0 8px 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.raw-requirement-box {
  background: var(--bg-muted);
  border: 1px solid var(--gray-200);
  border-radius: 6px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--gray-600);
  max-height: 150px;
  overflow-y: auto;
}

.response-text-box {
  background: var(--bg-subtle);
  border: 1px solid var(--gray-150);
  border-radius: 6px;
  padding: 14px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--text-primary);
  max-height: 240px;
  overflow-y: auto;
}

:deep(.highlight-kw) {
  background-color: var(--color-warning-light);
  color: var(--color-warning);
  padding: 1px 3px;
  border-radius: 3px;
  font-weight: 600;
}

.reuse-btn {
  background-color: var(--brand-xiyu-logo);
  border-color: var(--brand-xiyu-logo);
}

.reuse-btn:hover {
  background-color: var(--brand-xiyu-logo-hover);
  border-color: var(--brand-xiyu-logo-hover);
}
</style>
