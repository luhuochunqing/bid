<template>
  <el-dialog v-model="internalVisible" :title="caseData?.scoringPointTitle || caseData?.scoringTitle || '案例详情'" width="640px" align-center destroy-on-close>
    <div class="dialog-container" v-loading="loading">
      <template v-if="caseData">
      <!-- 0. 匹配度分析提示条（仅在推荐抽屉等带有匹配信息时展示） -->
      <div v-if="hasMatchInfo" class="match-detail-banner">
        <div class="match-detail-header">
          <div class="match-score-text">
            <span>AI 推荐匹配度</span>
            <strong class="score-num">{{ displayMatchScore }}%</strong>
            <el-tag size="small" :type="matchLabelTagType" effect="dark" class="ml-2">{{ displayMatchLabel }}</el-tag>
          </div>
          <div v-if="matchReasonsList.length > 0" class="match-reasons-chips">
            <el-tag v-for="(reason, idx) in matchReasonsList" :key="idx" size="small" type="success" effect="plain" class="reason-tag">
              ✓ {{ reason }}
            </el-tag>
          </div>
        </div>
        <div v-if="matchedKeywordsList.length > 0" class="match-keywords-line">
          <span class="kw-title">命中关键词：</span>
          <span v-for="(kw, idx) in matchedKeywordsList" :key="idx" class="kw-badge">{{ kw }}</span>
        </div>
      </div>

      <!-- 1. 评分项详细要素原文（浅色背景展示区） -->
      <div class="section-title">评分项详细要素原文</div>
      <div class="content-box requirement-box">{{ caseData.requirementRaw || '暂无要求' }}</div>

      <!-- 2. 应答片段摘要（标注总字数，操作按钮在标题行右侧） -->
      <div class="section-title response-title mt-4">
        <span>应答片段摘要</span>
        <div class="title-actions">
          <el-button plain type="primary" size="small" @click="$emit('go-source', caseData)"><el-icon><Link /></el-icon>查看源项目</el-button>
          <el-button plain type="primary" size="small" @click="$emit('open-bid', caseData)"><el-icon><Document /></el-icon>标书原文</el-button>
        </div>
      </div>
      <div class="word-count-hint">共 {{ (caseData?.responseText || '').length }} 字</div>
      <div v-if="hasResponseText" class="content-box response-box" v-html="responseHtml"></div>
      <div v-else class="content-box response-box">暂无应答内容</div>

      <!-- 3. 案例元信息（双列网格布局展示） -->
      <div class="section-title mt-4">案例元信息</div>
      <el-descriptions :column="2" border class="case-desc">
        <el-descriptions-item label="项目名称">{{ caseData.sourceProjectName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="招标主体">{{ caseData.ownerUnit || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间"><DateTimeDisplay :value="caseData.createdAt" /></el-descriptions-item>
        <el-descriptions-item label="项目状态"><el-tag size="small" :type="bidResultType">{{ bidResultLabel }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="客户类型"><el-tag size="small" type="success">{{ getCustomerTypeLabel(caseData.customerType) || '-' }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="项目类型"><el-tag size="small" type="primary">{{ getProjectTypeLabel(caseData.projectType) || '-' }}</el-tag></el-descriptions-item>
      </el-descriptions>

      <!-- 底部操作栏 -->
      <div class="dialog-actions mt-6">
        <el-button type="primary" @click="$emit('reuse', caseData)">
          <el-icon style="vertical-align:-.2em;margin-right:4px"><CopyDocument /></el-icon>复用
        </el-button>
      </div>
      </template>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { Link, Document, CopyDocument } from '@element-plus/icons-vue'
import DateTimeDisplay from '@/components/common/DateTimeDisplay.vue'
import { renderSafeMarkdown } from '@/components/common/doc-insight/markdownSanitizer.js'
import { getProjectTypeLabel, getCustomerTypeLabel, getMatchLabel, getMatchTagType, parseStringOrArray } from '../caseLabels.js'

const props = defineProps({
  modelValue: Boolean,
  caseData: Object,
  loading: Boolean
})
const emit = defineEmits(['update:modelValue', 'reuse', 'go-source', 'open-bid'])

const internalVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const displayMatchScore = computed(() => props.caseData?.matchScore ?? props.caseData?.score ?? null)
const hasMatchInfo = computed(() => displayMatchScore.value !== null && displayMatchScore.value !== undefined)

const displayMatchLabel = computed(() => {
  if (!hasMatchInfo.value && !props.caseData?.matchLabel) return ''
  return getMatchLabel(displayMatchScore.value, props.caseData?.matchLabel)
})

const matchLabelTagType = computed(() => getMatchTagType(displayMatchScore.value, props.caseData?.matchLabel))

const matchReasonsList = computed(() => parseStringOrArray(props.caseData?.matchReasons))
const matchedKeywordsList = computed(() => parseStringOrArray(props.caseData?.matchedKeywords))

const hasResponseText = computed(() => {
  const raw = props.caseData?.responseText
  return typeof raw === 'string' && raw.trim().length > 0
})

const escapeHtml = (str) => String(str)
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#039;')

const isMarkdownStructureLine = (line) => {
  const trimmed = line.trimStart()
  return trimmed.startsWith('#') || trimmed.startsWith('>') || trimmed.startsWith('```') ||
    /^[-*+]\s/.test(trimmed) || (trimmed.match(/\|/g) || []).length >= 2
}

const isMarkdownBlock = (block) => block.split('\n').some(isMarkdownStructureLine)

const SEQUENCE_RE = /^(?:\d+\.\s*|[一二三四五六七八九十]+、|[①-⑩]\s*|[(（]\d+[)）]\s*|[(（][一二三四五六七八九十]+[)）]\s*)/

const insertBrBetweenNonListLines = (lines) => {
  const tagPattern = /^<\/?ol>|^<li>/
  const output = []

  for (const line of lines) {
    if (line === '') {
      output.push('')
    } else if (tagPattern.test(line)) {
      output.push(line)
    } else if (output.length > 0 && output[output.length - 1] !== '' && !tagPattern.test(output[output.length - 1])) {
      output.push(`<br>${line}`)
    } else {
      output.push(line)
    }
  }

  return output.join('\n')
}

const processPlainTextBlock = (block) => {
  const lines = block.split('\n')
  const output = []
  let inList = false

  for (const line of lines) {
    const trimmed = line.trim()
    const sequence = trimmed.match(SEQUENCE_RE)

    if (sequence) {
      if (!inList) {
        output.push('<ol>')
        inList = true
      }
      output.push(`<li>${escapeHtml(trimmed.slice(sequence[0].length))}</li>`)
      continue
    }

    if (inList) {
      output.push('</ol>')
      inList = false
    }
    output.push(trimmed === '' ? '' : escapeHtml(line))
  }

  if (inList) output.push('</ol>')
  return insertBrBetweenNonListLines(output)
}

const preprocessPlainText = (text) => {
  if (!text) return ''
  const normalized = text.replace(/\r\n/g, '\n')
  return normalized.split(/\n{2,}/).map((block) => {
    if (block.trim() === '') return ''
    return isMarkdownBlock(block) ? block : processPlainTextBlock(block)
  }).join('\n\n')
}

defineExpose({ preprocessPlainText })

const responseHtml = computed(() => {
  if (!hasResponseText.value) return ''
  return renderSafeMarkdown(preprocessPlainText(props.caseData.responseText))
})

const bidResultLabel = computed(() => {
  const r = props.caseData?.bidResult
  return r === 'WON' ? '已中标' : r === 'LOST' ? '未中标' : r === 'FAILED' ? '已流标' : '未知'
})
const bidResultType = computed(() => {
  const r = props.caseData?.bidResult
  return r === 'WON' ? 'success' : r === 'LOST' ? 'danger' : r === 'FAILED' ? 'warning' : 'info'
})
</script>

<style scoped>
.dialog-container { padding: 0 4px 8px 4px; min-height: 120px; }
.match-detail-banner {
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 14px;
}
.match-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.match-score-text {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: var(--el-text-color-primary);
}
.score-num {
  font-size: 16px;
  color: var(--el-color-primary);
  margin-left: 6px;
  font-weight: 700;
}
.match-reasons-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.reason-tag { font-size: 11px; }
.match-keywords-line {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed var(--el-color-primary-light-7);
  font-size: 12px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}
.kw-title { color: var(--el-text-color-secondary); font-size: 11px; }
.kw-badge {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  padding: 0 6px;
  border-radius: 3px;
  font-size: 11px;
  color: var(--el-color-primary);
}
.section-title { font-size: 15px; font-weight: 600; color: var(--el-text-color-primary); margin-bottom: 12px; border-left: 4px solid var(--el-color-primary); padding-left: 8px; }
.case-desc { margin-bottom: 16px; }
.content-box { background-color: var(--el-fill-color-light); border-radius: 6px; padding: 12px; font-size: 13px; line-height: 1.6; color: var(--el-text-color-regular); white-space: pre-wrap; border: 1px solid var(--el-border-color-lighter); }
.requirement-box { max-height: 150px; overflow-y: auto; font-family: 'Courier New', Courier, monospace; }
.response-box { max-height: 240px; overflow: auto; }
.response-box :deep(table) { width: 100%; border-collapse: collapse; margin: 8px 0; font-size: 12px; white-space: normal; }
.response-box :deep(th),
.response-box :deep(td) { border: 1px solid var(--el-border-color); padding: 6px 8px; text-align: left; }
.response-box :deep(th) { background: var(--el-fill-color); font-weight: 600; }
.word-count-hint { font-size: 12px; color: var(--el-text-color-secondary); text-align: right; margin-bottom: 6px; padding-right: 4px; }
.response-title { display: flex; align-items: center; justify-content: space-between; }
.response-title .title-actions { display: flex; align-items: center; }
.dialog-actions { display: flex; justify-content: flex-end; }
.mt-4 { margin-top: 16px; }
.mt-6 { margin-top: 24px; }
.ml-2 { margin-left: 8px; }
</style>
