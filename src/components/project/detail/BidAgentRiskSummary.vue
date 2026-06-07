<template>
  <section v-if="summary" class="risk-summary">
    <header class="risk-summary-header">
      <span class="risk-summary-title">风险总览</span>
      <span class="risk-summary-total">{{ summary.total }} 项待关注</span>
    </header>
    <div class="risk-summary-cards">
      <div v-if="summary.redLineCount" class="risk-card risk-card-danger">
        <span class="risk-card-count">{{ summary.redLineCount }}</span>
        <span class="risk-card-label">废标红线</span>
      </div>
      <div v-if="summary.unsatisfiedCount" class="risk-card risk-card-unsatisfied">
        <span class="risk-card-count">{{ summary.unsatisfiedCount }}</span>
        <span class="risk-card-label">四库不满足</span>
      </div>
      <div v-if="summary.attentionCount" class="risk-card risk-card-attention">
        <span class="risk-card-count">{{ summary.attentionCount }}</span>
        <span class="risk-card-label">需关注</span>
      </div>
      <div v-if="summary.total === 0" class="risk-card risk-card-ok">
        <span class="risk-card-label">未发现风险项</span>
      </div>
    </div>
  </section>
</template>

<script setup>
defineProps({
  summary: { type: Object, default: null },
})
</script>

<style scoped>
.risk-summary {
  padding: 14px;
  border-radius: 14px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.risk-summary-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.risk-summary-title {
  font-weight: 700;
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.risk-summary-total {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.risk-summary-cards {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.risk-card {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 10px;
  font-size: 13px;
}

.risk-card-danger {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  border: 1px solid var(--el-color-danger-light-5);
}

.risk-card-unsatisfied {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning-dark-2);
  border: 1px solid var(--el-color-warning-light-5);
}

.risk-card-attention {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  border: 1px solid var(--el-border-color-lighter);
}

.risk-card-ok {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
  border: 1px solid var(--el-color-success-light-5);
}

.risk-card-count {
  font-weight: 700;
  font-size: 16px;
}
</style>
