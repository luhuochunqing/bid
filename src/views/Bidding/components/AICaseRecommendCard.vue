<template>
  <div
    class="case-card"
    :class="{ 'pinned-card': caseItem.isPinned }"
  >
    <div class="card-header">
      <div class="title-wrap">
        <span v-if="caseItem.isPinned" class="pin-tag">📌 置顶</span>
        <span class="case-title" :title="caseItem.scoringPointTitle">{{ caseItem.scoringPointTitle }}</span>
      </div>
      <div class="match-score-badge" :class="getScoreBadgeClass(caseItem.matchScore)">
        <span class="score-num">{{ caseItem.matchScore }}%</span>
        <span class="score-label">{{ caseItem.matchLabel || '匹配' }}</span>
      </div>
    </div>

    <div class="card-meta">
      <span class="meta-tag category-tag">{{ caseItem.scoringCategory || '技术方案' }}</span>
      <span v-if="caseItem.bidResult === 'WON'" class="meta-tag won-tag">🏆 中标案例</span>
      <span v-if="caseItem.customerType" class="meta-tag customer-tag">{{ customerTypeLabel(caseItem.customerType) }}</span>
      <span class="source-proj">来源：{{ caseItem.sourceProjectName || '历史标书' }}</span>
    </div>

    <div v-if="caseItem.matchReasons && caseItem.matchReasons.length" class="reasons-wrap">
      <span
        v-for="(reason, idx) in caseItem.matchReasons"
        :key="idx"
        class="reason-pill"
      >
        ✓ {{ reason }}
      </span>
    </div>

    <!-- 原始要求概览 -->
    <div v-if="caseItem.requirementRaw" class="requirement-preview">
      <span class="preview-label">招标要求：</span>
      <span class="preview-content">{{ caseItem.requirementRaw }}</span>
    </div>

    <!-- 应答摘要 -->
    <div class="response-summary-box">
      <span class="summary-label">应答参考：</span>
      <span class="summary-content" v-html="highlightText(caseItem.responseTextSummary || caseItem.responseText, caseItem.matchedKeywords)"></span>
    </div>

    <!-- 底部操作 -->
    <div class="card-footer">
      <span class="reuse-stat">📊 {{ caseItem.reuseCount || 0 }} 次复用</span>
      <div class="footer-actions">
        <el-button size="small" @click="$emit('view-detail', caseItem)">
          查看详情
        </el-button>
        <el-button size="small" type="primary" class="quick-reuse-btn" @click="$emit('quick-reuse', caseItem)">
          ⚡ 一键复用
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { customerTypeLabel } from '@/views/Project/utils/projectListFormatters.js'

defineProps({
  caseItem: {
    type: Object,
    required: true
  }
})

defineEmits(['view-detail', 'quick-reuse'])

const getScoreBadgeClass = (score) => {
  if (score >= 80) return 'badge-high'
  if (score >= 60) return 'badge-medium'
  return 'badge-normal'
}

const escapeHtml = (str) => {
  if (!str) return ''
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

const escapeRegExp = (str) => {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

const highlightText = (text, keywords) => {
  if (!text) return ''
  let sanitized = escapeHtml(text)
  if (keywords && keywords.length > 0) {
    keywords.forEach((kw) => {
      if (kw && kw.length >= 2) {
        const escapedKw = escapeHtml(kw)
        const reg = new RegExp(`(${escapeRegExp(escapedKw)})`, 'gi')
        sanitized = sanitized.replace(reg, '<span class="kw-hl">$1</span>')
      }
    })
  }
  return sanitized
}
</script>

<style scoped>
.case-card {
  background: var(--bg-card);
  border: 1px solid var(--gray-200);
  border-radius: 8px;
  padding: 14px;
  transition: all 0.2s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.case-card:hover {
  border-color: var(--brand-xiyu-logo);
  box-shadow: 0 4px 8px var(--brand-xiyu-logo-shadow);
}
.pinned-card {
  border-left: 3px solid var(--brand-xiyu-logo);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
}
.title-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
}
.pin-tag {
  font-size: 11px;
  background: var(--color-warning-bg);
  color: var(--color-warning);
  padding: 1px 4px;
  border-radius: 4px;
  font-weight: 500;
}
.case-title {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.4;
}
.match-score-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.badge-high { background: var(--brand-xiyu-logo-light); color: var(--brand-xiyu-logo); }
.badge-medium { background: var(--color-info-bg); color: var(--brand-primary); }
.badge-normal { background: var(--gray-50); color: var(--gray-650); }
.card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
  font-size: 12px;
}
.meta-tag {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11.5px;
}
.category-tag { background: var(--gray-50); color: var(--gray-600); }
.won-tag { background: var(--color-success-bg); color: var(--color-success-dark); }
.customer-tag { background: var(--color-warning-bg); color: var(--color-warning); }
.source-proj {
  color: var(--text-lighter);
  margin-left: auto;
  font-size: 11.5px;
}
.reasons-wrap {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.reason-pill {
  font-size: 11px;
  background: var(--bg-subtle);
  color: var(--brand-primary);
  padding: 1px 6px;
  border-radius: 4px;
  border: 1px solid var(--color-info-bg);
}
.requirement-preview {
  background: var(--bg-muted-2);
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--gray-650);
  margin-bottom: 8px;
  line-height: 1.4;
}
.preview-label { font-weight: 500; color: var(--gray-600); }
.response-summary-box {
  background: var(--bg-subtle);
  border-left: 2px solid var(--brand-xiyu-logo);
  padding: 8px 10px;
  border-radius: 0 4px 4px 0;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--text-primary-ui);
  margin-bottom: 10px;
}
.summary-label { font-weight: 600; color: var(--text-primary); }
:deep(.kw-hl) {
  background: var(--color-warning-light);
  color: var(--color-warning);
  font-weight: 600;
  padding: 0 2px;
  border-radius: 2px;
}
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px dashed var(--gray-150);
  padding-top: 8px;
}
.reuse-stat { font-size: 12px; color: var(--text-tertiary); }
.footer-actions { display: flex; gap: 8px; }
.quick-reuse-btn {
  background-color: var(--brand-xiyu-logo);
  border-color: var(--brand-xiyu-logo);
}
.quick-reuse-btn:hover {
  background-color: var(--brand-xiyu-logo-hover);
  border-color: var(--brand-xiyu-logo-hover);
}
</style>
