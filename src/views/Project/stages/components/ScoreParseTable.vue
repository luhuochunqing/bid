<!--
  Input: items (评分项列表), results (实际打分结果), mode ('est' | 'actual'), statistics
  Output: AI 评分标准 8 列结构化表格（完全对齐 PRD / 原型 V3 双阶段设计）
  Pos: src/views/Project/stages/components/ScoreParseTable.vue
-->
<template>
  <div class="score-parse-table-wrapper">
    <table class="parse-table">
      <thead>
        <tr>
          <th style="width: 48px">编号</th>
          <th style="width: 76px">评分项</th>
          <th>评分项详细要素</th>
          <th style="width: 52px; text-align: center">权重</th>
          <th style="width: 80px">满足状态</th>
          <th style="width: 76px; text-align: center">评分类别</th>
          <th style="width: 76px; text-align: center">{{ mode === 'actual' ? '实际得分' : '预计得分' }}</th>
          <th style="width: 68px; text-align: center">得分依据</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.code">
          <td class="code-cell">{{ item.code }}</td>
          <td class="dim-cell">{{ item.dim }}</td>
          <td class="req-cell">{{ item.req }}</td>
          <td class="weight-cell">{{ item.weight }}</td>
          <td>
            <span class="status-cell" :class="item.status">
              <span v-if="item.status === 'neutral'" class="dot">● </span>
              <span v-else-if="item.status === 'ok'">✓ </span>
              <span v-else-if="item.status === 'danger'">✕ </span>
              {{ item.statusText?.replace(/^[✓✕●\s]+/, '') || item.statusText }}
            </span>
          </td>
          <td style="text-align: center">
            <span class="pill" :class="item.scoreType === '客观项' ? 'info' : 'neutral'">
              {{ item.scoreType }}
            </span>
          </td>
          <td class="score-cell" :class="getScoreClass(item)">
            {{ getScoreText(item) }}
          </td>
          <td style="text-align: center">
            <button class="btn-detail" @click="$emit('open-detail', item, results[item.code] || null, mode)">
              详情
            </button>
          </td>
        </tr>
      </tbody>
      <tfoot>
        <tr class="tfoot-row">
          <td colspan="3" class="tfoot-title">合计</td>
          <td class="weight-cell tfoot-weight">{{ totalWeight }}</td>
          <td colspan="2" class="tfoot-stats">
            <span class="stat-tag ok">{{ statsOkCount }} 满足</span> ·
            <span class="stat-tag danger">{{ statsDangerCount }} 不满足</span> ·
            <span class="stat-tag neutral">{{ statsNeutralCount }} 待确认</span>
          </td>
          <td class="tfoot-dim-dist">
            <span class="obj-text">客观项 {{ objectiveWeight }}</span> ·
            <span class="subj-text">主观项 {{ subjectiveWeight }}</span>
          </td>
          <td class="score-cell tfoot-highlight">
            <div class="highlight-score-box">
              {{ highlightScore }}
            </div>
          </td>
        </tr>
      </tfoot>
    </table>
  </div>
</template>

<script setup>
const props = defineProps({
  items: { type: Array, required: true },
  results: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'est' },
  totalWeight: { type: Number, default: 100 },
  statsOkCount: { type: Number, default: 0 },
  statsDangerCount: { type: Number, default: 0 },
  statsNeutralCount: { type: Number, default: 0 },
  objectiveWeight: { type: Number, default: 0 },
  subjectiveWeight: { type: Number, default: 0 },
  highlightScore: { type: [Number, String], default: 0 },
})

defineEmits(['open-detail'])

function getScoreText(item) {
  if (props.mode === 'actual') {
    const res = props.results[item.code]
    if (!res) return '—'
    if (res.status === 'subjective' || res.actualScore === null || res.actualScore === undefined) return '待评审'
    return res.actualScore
  }
  if (typeof item.estScore === 'number') {
    return item.estScore
  }
  return item.estScore || '待评审'
}

function getScoreClass(item) {
  if (props.mode === 'actual') {
    const res = props.results[item.code]
    if (!res) return 'na'
    if (res.status === 'subjective' || res.actualScore === null || res.actualScore === undefined) return 'subjective'
    if (res.actualScore === item.weight) return 'full'
    return res.actualScore === 0 ? 'zero' : 'partial'
  }
  if (typeof item.estScore === 'number') {
    if (item.estScore === item.weight) return 'full'
    return item.estScore === 0 ? 'zero' : 'partial'
  }
  return 'subjective'
}
</script>

<style scoped>
.score-parse-table-wrapper { width: 100%; overflow-x: auto; }
.parse-table { width: 100%; border-collapse: collapse; font-size: 13px; table-layout: fixed; }
.parse-table th { background: var(--bg-muted); color: var(--text-primary); font-weight: 600; text-align: left; padding: 10px 8px; border-bottom: 1px solid var(--border-base); font-size: 12px; white-space: nowrap; }
.parse-table td { padding: 9px 8px; border-bottom: 1px solid var(--gray-100); vertical-align: middle; color: var(--text-primary-ui); }
.parse-table tr:hover td { background: var(--bg-muted-2); }
.code-cell { font-family: monospace; font-size: 12px; font-weight: 600; color: var(--text-primary-ui); }
.dim-cell { font-size: 12px; color: var(--text-primary-ui); }
.pill { font-size: 11px; padding: 1px 6px; border-radius: 3px; font-weight: 500; display: inline-block; }
.pill.info { background: var(--status-info-bg); color: var(--status-info-color); }
.pill.neutral { background: var(--status-neutral-bg); color: var(--status-neutral-color); }
.req-cell { line-height: 1.5; color: var(--text-primary-ui); font-size: 12px; }
.weight-cell { text-align: center; font-weight: 600; font-family: monospace; color: var(--brand-xiyu-logo); font-size: 13px; }
.status-cell { font-size: 12px; font-weight: 500; }
.status-cell.ok { color: var(--brand-xiyu-logo); }
.status-cell.danger { color: var(--status-danger-color); }
.status-cell.neutral { color: var(--status-info-color); }
.status-cell .dot { font-size: 9px; vertical-align: middle; }
.score-cell { font-weight: 600; font-family: monospace; text-align: center; font-size: 13px; white-space: nowrap; }
.score-cell.full { color: var(--brand-xiyu-logo); font-weight: 700; }
.score-cell.partial { color: var(--status-warning-color); font-weight: 700; }
.score-cell.zero { color: var(--status-danger-color); font-weight: 700; }
.score-cell.subjective { color: var(--text-muted); font-size: 11px; font-weight: 400; }
.score-cell.na { color: var(--text-lighter); }
.btn-detail { padding: 2px 10px; font-size: 12px; background: none; border: 1px solid var(--border-base); border-radius: var(--radius-sm); color: var(--brand-xiyu-logo); cursor: pointer; transition: all 0.15s; }
.btn-detail:hover { background: var(--brand-xiyu-logo-light); border-color: var(--brand-xiyu-logo); }
.tfoot-row { background: var(--bg-muted); font-weight: 600; border-top: 2px solid var(--border-base); }
.tfoot-title { padding: 10px 8px; text-align: right; color: var(--text-primary); font-size: 12px; }
.tfoot-weight { font-size: 13px; color: var(--brand-xiyu-logo); font-weight: 700; }
.tfoot-stats { font-size: 12px; color: var(--text-muted); padding: 0 8px; white-space: nowrap; }
.stat-tag.ok { color: var(--brand-xiyu-logo); font-weight: 600; }
.stat-tag.danger { color: var(--status-danger-color); font-weight: 600; }
.stat-tag.neutral { color: var(--text-muted); font-weight: 600; }
.tfoot-dim-dist { font-size: 12px; text-align: center; white-space: nowrap; }
.tfoot-dim-dist .obj-text { color: var(--status-info-color); font-weight: 600; }
.tfoot-dim-dist .subj-text { color: var(--text-muted); }
.tfoot-highlight { text-align: center; padding: 4px; }
.highlight-score-box { display: inline-flex; align-items: center; justify-content: center; width: 100%; height: 32px; background: var(--brand-xiyu-logo-light); color: var(--brand-xiyu-logo-active); font-size: 16px; font-weight: 700; border-radius: 4px; }
</style>
