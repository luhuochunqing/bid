<!--
  Input: items (评分项列表), results (实际打分结果), mode ('est' | 'actual'), statistics
  Output: AI 评分标准 8 列结构化表格（支持阶段 1 预判与阶段 2 实际打分展示）
  Pos: src/views/Project/stages/components/ScoreParseTable.vue
-->
<template>
  <div class="score-parse-table-wrapper">
    <table class="parse-table">
      <thead>
        <tr>
          <th style="width: 46px">编号</th>
          <th style="width: 78px">维度</th>
          <th style="width: 52px">权重</th>
          <th style="width: 60px">类别</th>
          <th>评分细则与要求</th>
          <th style="width: 74px">满足状态</th>
          <th style="width: 82px">{{ mode === 'actual' ? '实际得分' : '预计得分' }}</th>
          <th style="width: 58px">详情</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.code">
          <td class="code-cell">{{ item.code }}</td>
          <td><span class="dim-badge">{{ item.dim }}</span></td>
          <td class="num">{{ item.weight }} 分</td>
          <td>
            <span class="pill" :class="item.scoreType === '客观项' ? 'info' : 'neutral'">
              {{ item.scoreType }}
            </span>
          </td>
          <td class="req-cell">{{ item.req }}</td>
          <td>
            <span class="status-cell" :class="item.status">
              {{ item.statusText }}
            </span>
          </td>
          <td class="score-cell" :class="getScoreClass(item)">
            {{ getScoreText(item) }}
          </td>
          <td>
            <button class="btn-detail" @click="$emit('open-detail', item, results[item.code] || null, mode)">
              查看
            </button>
          </td>
        </tr>
      </tbody>
      <tfoot>
        <tr class="tfoot-row">
          <td colspan="2" class="tfoot-title">
            {{ mode === 'actual' ? '实际得分合计' : '权重合计 / 预估得分' }}
          </td>
          <td class="num tfoot-weight">{{ totalWeight }} 分</td>
          <td colspan="3" class="tfoot-stats">
            <span class="stat-tag ok">✓ 满足 {{ statsOkCount }} 项</span>
            <span v-if="statsDangerCount > 0" class="stat-tag danger">✗ 不满足 {{ statsDangerCount }} 项</span>
            <span class="stat-tag neutral">待确认 {{ statsNeutralCount }} 项</span>
          </td>
          <td class="score-cell tfoot-highlight">
            <template v-if="mode === 'actual'">
              <span class="score-val">{{ highlightScore }}</span>
              <span class="score-den"> / {{ objectiveWeight }} 分</span>
            </template>
            <template v-else>
              <span class="score-val">{{ highlightScore }}</span>
              <span class="score-den"> / {{ totalWeight }} 分</span>
            </template>
          </td>
          <td class="tfoot-desc">
            {{ mode === 'actual' ? '仅客观项得分，主观项待评审' : '基于知识库预估' }}
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
    if (res.status === 'subjective' || res.actualScore === null || res.actualScore === undefined) return '待专家评审'
    return `${res.actualScore} / ${item.weight}`
  }
  if (typeof item.estScore === 'number') {
    return `${item.estScore} / ${item.weight}`
  }
  return item.estScore || '待专家评审'
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
.code-cell { font-family: monospace; font-size: 12px; font-weight: 600; color: var(--brand-xiyu-logo); }
.dim-badge { font-size: 11px; padding: 2px 6px; border-radius: var(--radius-sm); font-weight: 600; background: var(--brand-xiyu-logo-light); color: var(--brand-xiyu-logo-active); display: inline-block; }
.pill { font-size: 11px; padding: 1px 6px; border-radius: 3px; font-weight: 600; display: inline-block; }
.pill.info { background: var(--status-info-bg); color: var(--status-info-color); }
.pill.neutral { background: var(--status-neutral-bg); color: var(--status-neutral-color); }
.req-cell { line-height: 1.5; color: var(--text-primary-ui); font-size: 12px; }
.num { text-align: right; font-weight: 600; font-family: monospace; color: var(--text-primary-ui); white-space: nowrap; }
.status-cell { font-size: 12px; font-weight: 500; }
.status-cell.ok { color: var(--status-success-color); }
.status-cell.danger { color: var(--status-danger-color); }
.status-cell.neutral { color: var(--text-muted); }
.score-cell { font-weight: 600; font-family: monospace; text-align: right; font-size: 12px; white-space: nowrap; }
.score-cell.full { color: var(--status-success-color); font-weight: 700; }
.score-cell.partial { color: var(--status-warning-color); font-weight: 700; }
.score-cell.zero { color: var(--status-danger-color); font-weight: 700; }
.score-cell.subjective { color: var(--text-muted); font-size: 11px; }
.score-cell.na { color: var(--text-lighter); }
.btn-detail { padding: 2px 8px; font-size: 12px; background: none; border: 1px solid var(--border-base); border-radius: var(--radius-sm); color: var(--brand-xiyu-logo); cursor: pointer; transition: all 0.15s; }
.btn-detail:hover { background: var(--brand-xiyu-logo-light); border-color: var(--brand-xiyu-logo); }
.tfoot-row { background: var(--bg-muted); font-weight: 600; border-top: 2px solid var(--border-base); }
.tfoot-title { padding: 10px 8px; color: var(--text-primary); font-size: 12px; }
.tfoot-weight { font-size: 12px; }
.tfoot-stats { font-size: 11px; }
.stat-tag { display: inline-block; padding: 1px 6px; border-radius: 3px; margin-right: 6px; font-size: 11px; }
.stat-tag.ok { background: var(--status-success-bg); color: var(--status-success-color); }
.stat-tag.danger { background: var(--status-danger-bg); color: var(--status-danger-color); }
.stat-tag.neutral { background: var(--status-neutral-bg); color: var(--status-neutral-color); }
.tfoot-highlight { font-size: 13px; color: var(--brand-xiyu-logo); }
.tfoot-highlight .score-val { font-size: 15px; font-weight: 700; color: var(--brand-xiyu-logo); }
.tfoot-highlight .score-den { font-size: 11px; color: var(--text-muted); }
.tfoot-desc { font-size: 11px; color: var(--text-muted); font-weight: 400; }
</style>
