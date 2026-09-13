<!--
  Input: facts/evidence review snapshot + audits + loading/submitting state
  Output: REVIEW_REQUIRED / STRUCTURE_INCOMPLETE human review closure panel
  Pos: Project score parse drawer child component
-->
<template>
  <div class="review-panel" data-test="score-parse-review-panel">
    <div class="review-panel-head">
      <div>
        <div class="review-panel-title">人工复核闭环</div>
        <div class="review-panel-sub">本次解析未自动覆盖正式评分项，请按 facts/evidence 复核后留痕。</div>
      </div>
      <button class="btn-tool" :disabled="submitting || submitted" data-test="score-parse-review-retain" @click="$emit('retain')">
        {{ submitting ? '保存中...' : submitted ? '已保留' : '记录已复核，保留现有项' }}
      </button>
    </div>
    <div v-if="loading" class="review-panel-muted">正在加载复核证据...</div>
    <div v-else-if="error" class="review-panel-error">{{ error }}</div>
    <template v-else>
      <div class="review-panel-meta">
        <span>事实 {{ facts.length }} 条</span>
        <span>审计 {{ audits.length }} 条</span>
        <span v-if="pipelineVersion">Pipeline {{ pipelineVersion }}</span>
        <span v-if="artifactSha256">Artifact {{ artifactSha256.slice(0, 12) }}</span>
      </div>
      <div v-if="facts.length === 0" class="review-panel-muted">暂无 facts/evidence 快照，请重新解析或检查 Sidecar 输出。</div>
      <div v-else class="review-fact-list">
        <div v-for="fact in facts.slice(0, 5)" :key="fact.factId" class="review-fact">
          <div class="review-fact-main">
            <span class="review-fact-code">{{ fact.code || fact.factId }}</span>
            <span class="review-fact-dim">{{ fact.dimension }}</span>
            <span class="review-fact-weight">{{ fact.weight }} 分</span>
          </div>
          <div class="review-fact-source">{{ fact.sourceText }}</div>
          <div v-if="fact.evidences?.length" class="review-evidence">
            证据 {{ fact.evidences.length }} 条：
            <span>{{ fact.evidences[0].quotedText || fact.evidences[0].explanation || fact.evidences[0].cellId }}</span>
          </div>
        </div>
        <div v-if="facts.length > 5" class="review-panel-muted">其余 {{ facts.length - 5 }} 条 facts 已收起。</div>
      </div>
    </template>
  </div>
</template>

<script setup>
defineProps({
  facts: { type: Array, default: () => [] },
  audits: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  submitted: { type: Boolean, default: false },
  error: { type: String, default: '' },
  pipelineVersion: { type: String, default: '' },
  artifactSha256: { type: String, default: '' },
})

defineEmits(['retain'])
</script>

<style scoped>
.review-panel { margin: 8px 0 12px; padding: 12px; border: 1px solid var(--status-warning-color, #fa8c16); border-radius: var(--radius-md); background: var(--status-warning-bg, #fff7e6); }
.review-panel-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.review-panel-title { font-size: 13px; font-weight: 700; color: var(--text-primary-ui); margin-bottom: 3px; }
.review-panel-sub, .review-panel-muted, .review-panel-error, .review-panel-meta { font-size: 12px; color: var(--text-muted); }
.review-panel-error { color: var(--status-danger-color, #cf1322); }
.review-panel-meta { display: flex; flex-wrap: wrap; gap: 10px; margin: 10px 0; }
.review-fact-list { display: flex; flex-direction: column; gap: 8px; }
.review-fact { padding: 8px 10px; border: 1px solid rgba(250, 140, 22, 0.26); border-radius: var(--radius-sm); background: rgba(255, 255, 255, 0.72); }
.review-fact-main { display: flex; align-items: center; gap: 8px; font-size: 12px; font-weight: 600; color: var(--text-primary-ui); }
.review-fact-code { color: var(--brand-xiyu-logo); }
.review-fact-dim { flex: 1; }
.review-fact-weight { color: var(--status-warning-color, #d46b08); }
.review-fact-source { margin-top: 5px; font-size: 12px; color: var(--gray-650); line-height: 1.5; }
.review-evidence { margin-top: 5px; font-size: 12px; color: var(--text-muted); }
.btn-tool { padding: 5px 12px; background: var(--bg-white); border: 1px solid var(--gray-200); border-radius: var(--radius-sm); font-size: 12px; cursor: pointer; color: var(--border-focus); transition: all 0.16s ease; display: inline-flex; align-items: center; gap: 4px; }
.btn-tool:hover { border-color: var(--brand-xiyu-logo); color: var(--brand-xiyu-logo); }
.btn-tool:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
