<!--
  Input: items (评分项列表), results (实际打分结果), mode ('est' | 'actual'), statistics
  Output: 生效评分项表格；声明总分同源比较，历史评分不计当前总分；AI 评分标准 8 列结构化表格（完全对齐 PRD / 原型 V3 双阶段设计）
  Pos: src/views/Project/stages/components/ScoreParseTable.vue
-->
<template>
  <div class="score-parse-table-wrapper">
    <table class="parse-table">
      <thead>
        <tr>
          <th style="width: 68px">编号</th>
          <th>{{ versioned ? '当前规则文本' : '标书原文' }}</th>
          <th style="width: 110px">评分项</th>
          <th style="width: 52px; text-align: center">权重</th>
          <th style="width: 88px">满足状态</th>
          <th style="width: 116px; text-align: center">评分类别</th>
          <th style="width: 92px; text-align: center">{{ mode === 'actual' ? '实际得分' : '预计得分' }}</th>
          <th style="width: 68px; text-align: center">得分依据</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(item, index) in items" :key="item.id != null ? 'id-' + item.id : 'row-' + index" :data-item-id="item.id">
          <td class="code-cell">{{ item.code }}</td>
          <td class="req-cell original-text-cell">{{ item.detail || item.req }}<span v-if="versioned && item.validationStatus !== 'AUTO_APPROVED'" class="history-label">来源未核验，请结合提取快照核对</span></td>
          <td class="dim-cell">{{ item.dim }}</td>
          <td class="weight-cell">{{ item.weight == null ? '待确认' : item.weight }}</td>
          <td>
            <span class="status-cell" :class="getItemStatus(item)">
              <span v-if="getItemStatus(item) === 'neutral'" class="dot">● </span>
              <span v-else-if="getItemStatus(item) === 'ok'">✓ </span>
              <span v-else-if="getItemStatus(item) === 'danger'">✗ </span>
              {{ getItemStatusText(item) }}
            </span>
          </td>
          <td style="text-align: center">
            <span class="pill" :class="item.scoreType === '客观项' ? 'info' : 'neutral'">
              {{ item.scoreType }}
            </span>
          </td>
          <td class="score-cell" :class="getScoreClass(item)">
            {{ getScoreText(item) }}
            <span v-if="mode === 'actual' && resultOf(item)?.reuseKind === 'LEGACY_UNVERIFIED'" class="history-label">历史评分，需重新评分</span>
            <span v-if="mode === 'actual' && resultOf(item)?.reuseKind === 'REUSED'" class="pill neutral">沿用</span>
          </td>
          <td style="text-align: center">
            <button type="button" class="btn-detail" :aria-label="'查看' + (item.dim || item.code || '') + '得分依据'" @click="$emit('open-detail', item, resultOf(item), mode)">
              详情
            </button>
          </td>
        </tr>
      </tbody>
      <tfoot>
        <tr class="tfoot-row">
          <td colspan="3" class="tfoot-total-cell">
            <span class="tfoot-label">合计</span>
            <span v-if="declaredTotal != null && totalWeight != null && totalWeight !== declaredTotal" class="weight-warning">权重合计与原文声明 {{ declaredTotal }} 分不符</span>
          </td>
          <td class="weight-cell tfoot-weight">{{ totalWeight == null ? '待确认' : totalWeight }}</td>
          <td class="tfoot-stack-cell">
            <span class="stat-tag ok">{{ currentStats.ok }} 满足</span>
            <span class="stat-tag danger">{{ currentStats.danger }} 不满足</span>
            <span class="stat-tag neutral">{{ currentStats.neutral }} 待确认</span>
          </td>
          <td class="tfoot-stack-cell">
            <span class="obj-text">客观项权重 {{ displayObjectiveWeight }}</span>
            <span class="subj-text">主观项权重 {{ displaySubjectiveWeight }}</span>
          </td>
          <td class="tfoot-score-cell">
            <span class="highlight-score-box" :class="{ 'is-text': typeof currentHighlightScore === 'string' }">{{ currentHighlightScore }}</span>
          </td>
          <td></td>
        </tr>
      </tfoot>
    </table>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { lookupScoreResult } from '@/composables/projectDetail/scoreParseTask.js'

const props = defineProps({
  items: { type: Array, required: true },
  results: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'est' },
  versioned: { type: Boolean, default: false },
  totalWeight: { type: Number, default: null },
  declaredTotal: { type: Number, default: null },
  statsOkCount: { type: Number, default: 0 },
  statsDangerCount: { type: Number, default: 0 },
  statsNeutralCount: { type: Number, default: 0 },
  objectiveWeight: { type: Number, default: 0 },
  subjectiveWeight: { type: Number, default: 0 },
  highlightScore: { type: [Number, String], default: null },
})

defineEmits(['open-detail'])

function resultOf(item) {
  return lookupScoreResult(props.results, item)
}

function getItemStatus(item) {
  if (props.mode === 'actual') {
    const res = resultOf(item)
    return res?.status || 'neutral'
  }
  return item.status || 'neutral'
}

function getItemStatusText(item) {
  const status = getItemStatus(item)
  if (status === 'ok') return '满足'
  if (status === 'danger') return '不满足'
  return '待确认'
}

function getScoreText(item) {
  if (props.mode === 'actual') {
    const res = resultOf(item)
    if (!res) return '待确认'
    const actual = res.actualScore ?? res.score
    if (res.status === 'subjective' || item.scoreType === '主观项' || actual === null || actual === undefined) return '待确认'
    return actual
  }
  if (typeof item.estScore === 'number') {
    return item.estScore
  }
  if (item.scoreType === '主观项') return '待确认'
  return item.estScore || '待确认'
}

function getScoreClass(item) {
  if (props.mode === 'actual') {
    const res = resultOf(item)
    if (!res) return 'na'
    const actual = res.actualScore ?? res.score
    if (res.status === 'subjective' || item.scoreType === '主观项' || actual === null || actual === undefined) return 'subjective'
    if (actual === item.weight) return 'full'
    return actual === 0 ? 'zero' : 'partial'
  }
  if (typeof item.estScore === 'number') {
    if (item.estScore === item.weight) return 'full'
    return item.estScore === 0 ? 'zero' : 'partial'
  }
  return 'subjective'
}

const currentStats = computed(() => {
  if (props.mode === 'actual') {
    let ok = 0, danger = 0, neutral = 0
    for (const item of props.items) {
      const status = getItemStatus(item)
      if (status === 'ok') ok++
      else if (status === 'danger') danger++
      else neutral++
    }
    return { ok, danger, neutral }
  }
  return {
    ok: props.statsOkCount || props.items.filter((i) => i.status === 'ok').length,
    danger: props.statsDangerCount || props.items.filter((i) => i.status === 'danger').length,
    neutral: props.statsNeutralCount || props.items.filter((i) => i.status === 'neutral').length,
  }
})

const currentHighlightScore = computed(() => {
  if (props.mode === 'actual') {
    let score = 0, known = false
    for (const item of props.items) {
      if (item.scoreType === '客观项') {
        const res = resultOf(item)
        const val = res?.actualScore ?? res?.score
        if (res?.reuseKind !== 'LEGACY_UNVERIFIED' && typeof val === 'number') {
          known = true
          score += val
        }
      }
    }
    return known ? score : '待确认'
  }
  if (typeof props.highlightScore === 'number' || typeof props.highlightScore === 'string') {
    return props.highlightScore
  }
  let score = 0, known = false
  for (const item of props.items) {
    if (item.scoreType === '客观项' && typeof item.estScore === 'number') {
      known = true
      score += item.estScore
    }
  }
  return known ? score : '待确认'
})

const isTotal100 = computed(() => Math.round(Number(props.totalWeight) || 0) === 100)

const displayObjectiveWeight = computed(() => {
  const rounded = Math.round(Number(props.objectiveWeight) || 0)
  return isTotal100.value ? Math.max(0, Math.min(100, rounded)) : rounded
})

const displaySubjectiveWeight = computed(() => {
  return isTotal100.value
    ? Math.max(0, 100 - displayObjectiveWeight.value)
    : Math.round(Number(props.subjectiveWeight) || 0)
})
</script>

<style scoped>
.score-parse-table-wrapper { width: 100%; overflow-x: auto; }
.parse-table { width: 100%; border-collapse: collapse; font-size: 13px; table-layout: fixed; }
.parse-table th { background: var(--bg-muted); color: var(--text-primary); font-weight: 600; text-align: left; padding: 10px 8px; border-bottom: 1px solid var(--border-base); font-size: 12px; white-space: nowrap; }
.parse-table td { padding: 9px 8px; border-bottom: 1px solid var(--gray-100); vertical-align: middle; color: var(--text-primary-ui); }
.parse-table tr:hover td { background: var(--bg-muted-2); }
.code-cell { font-family: monospace; font-size: 12px; font-weight: 600; color: var(--text-primary-ui); word-break: break-all; }
.dim-cell { font-size: 12px; color: var(--text-primary-ui); font-weight: 500; word-break: break-word; }
.pill { font-size: 11px; padding: 1px 6px; border-radius: 3px; font-weight: 500; display: inline-block; }
.pill.info { background: var(--status-info-bg); color: var(--status-info-color); }
.pill.neutral { background: var(--status-neutral-bg); color: var(--status-neutral-color); }
.req-cell { line-height: 1.6; color: var(--text-primary-ui); font-size: 12px; }
.original-text-cell { white-space: pre-wrap; word-break: break-word; }
.weight-cell { text-align: center; font-weight: 600; font-family: monospace; color: var(--brand-xiyu-logo); font-size: 13px; }
.weight-warning { display: block; font-size: 10px; color: var(--status-danger-color); font-weight: normal; margin-top: 2px; line-height: 1.2; }
.status-cell { font-size: 12px; font-weight: 500; }
.status-cell.ok { color: var(--brand-xiyu-logo); }
.status-cell.danger { color: var(--status-danger-color); }
/* PRD 6.5：待确认 = 灰色文字 + 蓝色圆点前缀 */
.status-cell.neutral { color: var(--text-muted); }
.status-cell .dot { font-size: 9px; vertical-align: middle; color: var(--brand-primary); }
.score-cell { font-weight: 600; font-family: monospace; text-align: center; font-size: 13px; white-space: nowrap; }
.history-label { display: block; white-space: normal; font-weight: 400; color: var(--text-muted); }
.score-cell.full { color: var(--brand-xiyu-logo); font-weight: 700; }
.score-cell.partial { color: var(--status-warning-color); font-weight: 700; }
.score-cell.zero { color: var(--status-danger-color); font-weight: 700; }
.score-cell.subjective { color: var(--text-muted); font-size: 11px; font-weight: 400; }
.score-cell.na { color: var(--text-lighter); }
.btn-detail { padding: 2px 10px; font-size: 12px; background: none; border: 1px solid var(--border-base); border-radius: var(--radius-sm); color: var(--brand-xiyu-logo); cursor: pointer; transition: all 0.15s; }
.btn-detail:hover { background: var(--brand-xiyu-logo-light); border-color: var(--brand-xiyu-logo); }
.tfoot-row { background: var(--bg-muted); font-weight: 600; border-top: 2px solid var(--border-base); }
.tfoot-total-cell { text-align: right; }
.tfoot-label { color: var(--text-primary); font-size: 12px; }
.tfoot-weight { font-weight: 700; }
.tfoot-stack-cell { font-size: 12px; white-space: nowrap; }
.tfoot-stack-cell span { display: block; line-height: 1.6; }
.stat-tag.ok { color: var(--brand-xiyu-logo); font-weight: 600; }
.stat-tag.danger { color: var(--status-danger-color); font-weight: 600; }
.stat-tag.neutral { color: var(--text-muted); font-weight: 600; }
.tfoot-stack-cell .obj-text { color: var(--status-info-color); font-weight: 600; }
.tfoot-stack-cell .subj-text { color: var(--text-muted); }
.tfoot-score-cell { text-align: center; }
.highlight-score-box { display: inline-flex; align-items: center; justify-content: center; min-width: 56px; height: 30px; padding: 0 10px; background: var(--brand-xiyu-logo-light); color: var(--brand-xiyu-logo-active); font-size: 15px; font-weight: 700; border-radius: 4px; box-sizing: border-box; white-space: nowrap; }
.highlight-score-box.is-text { font-size: 12px; font-weight: 500; min-width: 48px; padding: 0 8px; }
</style>
