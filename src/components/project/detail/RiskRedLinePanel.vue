<template>
  <section class="agent-section">
    <header>
      风险与废标红线
      <el-badge :value="redLineCount" :hidden="!fetched || redLineCount === 0" type="danger" class="risk-badge">
        <span />
      </el-badge>
    </header>
    <div v-if="items?.length" class="risk-list">
      <div v-for="(item, idx) in items" :key="idx" :class="['risk-item', riskClass(item.level)]">
        <el-tag v-if="item.level === 'RED_LINE'" type="danger" size="small" effect="dark">废标红线</el-tag>
        <el-tag v-else-if="item.level === 'WARNING'" type="warning" size="small" effect="plain">一般风险</el-tag>
        <el-tag v-else size="small" effect="plain">信息</el-tag>
        <span class="risk-text">{{ item.text }}</span>
      </div>
    </div>
    <div v-else-if="fetched" class="risk-empty">
      <el-empty description="未解析出风险点" :image-size="48" />
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({ items: { type: Array, default: () => [] }, fetched: { type: Boolean, default: false } })
const redLineCount = computed(() => (props.items || []).filter(i => i.level === 'RED_LINE').length)

const riskClass = (level) => ({
  RED_LINE: 'risk-red-line',
  WARNING: 'risk-warning',
  INFO: 'risk-info',
}[level] || '')
</script>

<style scoped>
.risk-list { display: grid; gap: 8px; }
.risk-item {
  display: flex; align-items: flex-start; gap: 8px;
  padding: 10px; border-radius: 10px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}
.risk-red-line {
  border-color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}
.risk-warning {
  border-color: var(--el-color-warning-light-3);
  background: var(--el-color-warning-light-9);
}
.risk-info {
  border-color: var(--el-border-color-lighter);
}
.risk-text { line-height: 1.5; color: var(--el-text-color-primary); font-size: 13px; flex: 1; }
.risk-empty { padding: 8px 0; }
.risk-badge { display: inline-flex; vertical-align: middle; }
</style>
