<!--
  Input: item (评分项基础信息), result (实际打分结果), mode ('est' | 'actual')
  Output: 评分标准详细要素、依据、标书引用、缺失说明与优化建议弹窗
  Pos: src/views/Project/stages/components/ScoreItemDetailModal.vue
-->
<template>
  <el-dialog
    v-model="dialogVisible"
    :title="modalTitle"
    width="560px"
    :append-to-body="true"
    :close-on-click-modal="true"
    class="score-item-detail-dialog"
    @closed="handleClosed"
  >
    <div class="detail-modal-body">
      <div class="detail-row">
        <div class="detail-label">详细评分项要素</div>
        <div class="detail-value">{{ item?.detail || item?.req || '-' }}</div>
      </div>

      <div class="detail-row">
        <div class="detail-label">权重 / 评分类别</div>
        <div class="detail-value">
          权重 {{ item?.weight ?? 0 }} 分 ｜
          <span class="pill" :class="item?.scoreType === '客观项' ? 'info' : 'neutral'">{{ item?.scoreType || '客观项' }}</span>
        </div>
      </div>

      <div class="detail-row">
        <div class="detail-label">满足状态</div>
        <div class="detail-value status-cell" :class="item?.status || 'neutral'">
          {{ item?.statusText || '待确认' }}
        </div>
      </div>

      <div class="detail-row">
        <div class="detail-label">{{ mode === 'actual' ? '实际得分' : '预计得分' }}</div>
        <div class="detail-value" :class="scoreClass">{{ scoreDisplay }}</div>
      </div>

      <div class="detail-row">
        <div class="detail-label">{{ mode === 'actual' ? '评分依据' : '得分依据' }}</div>
        <div class="detail-quote">{{ basisText }}</div>
      </div>

      <div v-if="mode === 'actual' && result?.quote" class="detail-row">
        <div class="detail-label">标书引用</div>
        <div class="detail-quote">{{ result.quote }}</div>
      </div>

      <div v-if="mode === 'actual' && result?.missedReason" class="detail-row">
        <div class="detail-label">缺失说明</div>
        <div class="detail-miss">{{ result.missedReason }}</div>
      </div>

      <div v-if="suggestionText" class="detail-row">
        <div class="detail-label">修改建议</div>
        <div class="detail-suggestion">
          <div class="suggestion-title">
            💡 {{ item?.status === 'danger' ? '不满足项改进建议' : '待确认项补充建议' }}
          </div>
          {{ suggestionText }}
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  mode: { type: String, default: 'est' },
  item: { type: Object, default: () => ({}) },
  result: { type: Object, default: () => null },
})

const emit = defineEmits(['update:visible', 'close'])

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

function handleClosed() {
  emit('close')
}

const modalTitle = computed(() => {
  const code = props.item?.code || ''
  const dim = props.item?.dim || ''
  const suffix = props.mode === 'actual' ? '实际评分详情' : '预计评分详情'
  return `${code} · ${dim} — ${suffix}`
})

const scoreDisplay = computed(() => {
  const weight = props.item?.weight ?? 0
  if (props.mode === 'actual') {
    if (!props.result) return '—'
    const actual = props.result.score ?? props.result.actualScore
    if (props.result.status === 'subjective' || props.result.status === 'PENDING_EXPERT' || actual === null || actual === undefined) return '待评审'
    return `${actual} / ${weight}`
  }
  return typeof props.item?.estScore === 'number' ? `${props.item.estScore} / ${weight}` : props.item?.estScore || '待评审'
})

const scoreClass = computed(() => {
  if (props.mode === 'actual') {
    if (!props.result) return 'na'
    const actual = props.result.score ?? props.result.actualScore
    if (props.result.status === 'subjective' || props.result.status === 'PENDING_EXPERT' || actual === null || actual === undefined) return 'subjective'
    if (actual === props.item?.weight) return 'full'
    return actual === 0 ? 'zero' : 'partial'
  }
  if (typeof props.item?.estScore === 'number') {
    if (props.item.estScore === props.item?.weight) return 'full'
    return props.item.estScore === 0 ? 'zero' : 'partial'
  }
  return 'subjective'
})

const basisText = computed(() => {
  if (props.mode === 'actual') return props.result?.basis || props.result?.evidence || props.item?.estBasis || '暂无评分依据'
  return props.item?.estBasis || '需评标专家根据标书描述人工评审'
})

const suggestionText = computed(() => {
  if (props.result?.suggestion) return props.result.suggestion
  if (props.item?.suggestion) return props.item.suggestion
  if (['danger', 'warn', 'neutral', 'PARTIALLY_SATISFIED', 'NOT_SATISFIED'].includes(props.item?.status) ||
      ['danger', 'warn', 'neutral', 'PARTIALLY_SATISFIED', 'NOT_SATISFIED'].includes(props.result?.status)) {
    return props.item?.status === 'danger' || props.result?.status === 'NOT_SATISFIED'
      ? '建议针对此项要求补充证明材料或替代响应方案'
      : '建议在标书中详细补充相关阐述，降低评审不确定性'
  }
  return ''
})
</script>

<style scoped>
.detail-modal-body { font-size: 13px; color: var(--text-primary-ui); line-height: 1.7; }
.detail-row { margin-bottom: 14px; }
.detail-label { font-size: 12px; font-weight: 600; color: var(--brand-xiyu-logo-active); margin-bottom: 4px; padding-left: 8px; border-left: 3px solid var(--brand-xiyu-logo); line-height: 1.4; }
.detail-value { font-size: 13px; color: var(--text-primary-ui); }
.pill { font-size: 11px; padding: 1px 6px; border-radius: 3px; font-weight: 600; display: inline-block; }
.pill.info { background: var(--status-info-bg); color: var(--status-info-color); }
.pill.neutral { background: var(--status-neutral-bg); color: var(--status-neutral-color); }
.status-cell { font-size: 12px; font-weight: 500; }
.status-cell.ok { color: var(--status-success-color); }
.status-cell.warn { color: var(--status-warning-color); }
.status-cell.danger { color: var(--status-danger-color); }
.status-cell.neutral { color: var(--text-muted); }
.detail-value.full { color: var(--status-success-color); font-weight: 700; }
.detail-value.partial { color: var(--status-warning-color); font-weight: 700; }
.detail-value.zero { color: var(--status-danger-color); font-weight: 700; }
.detail-value.subjective { color: var(--text-muted); font-size: 12px; }
.detail-value.na { color: var(--text-lighter); }
.detail-quote { background: var(--status-success-bg-soft); border-left: 3px solid var(--status-success-border); padding: 8px 12px; margin-top: 6px; border-radius: 0 4px 4px 0; color: var(--gray-700); font-size: 12px; }
.detail-miss { background: var(--status-danger-bg-soft); border-left: 3px solid var(--status-danger-border); padding: 8px 12px; margin-top: 6px; border-radius: 0 4px 4px 0; color: var(--status-danger-color); font-size: 12px; }
.detail-suggestion { background: var(--status-info-bg); border-left: 3px solid var(--status-info-color); padding: 10px 12px; margin-top: 6px; border-radius: 0 4px 4px 0; color: var(--status-info-color); font-size: 12px; line-height: 1.7; }
.suggestion-title { font-weight: 600; margin-bottom: 4px; display: flex; align-items: center; gap: 4px; }
</style>
