<template>
  <div class="change-diff-card" role="region" aria-label="字段变更">
    <div class="change-diff-header">字段变更</div>
    <div v-if="!changes || changes.length === 0" class="change-diff-empty">
      <el-empty description="无变更" :image-size="40" />
    </div>
    <el-table v-else :data="changes" size="small" class="change-diff-table">
      <el-table-column label="字段" prop="field" width="140" />
      <el-table-column label="原值">
        <template #default="{ row }">
          <span class="change-diff-before">{{ formatValue(row.before) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="新值">
        <template #default="{ row }">
          <span class="change-diff-after">{{ formatValue(row.after) }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
defineProps({
  changes: { type: Array, default: () => [] }
})

const formatValue = (v) => {
  if (v === null || v === undefined) return '—'
  if (typeof v === 'object') {
    try {
      return JSON.stringify(v)
    } catch {
      return String(v)
    }
  }
  return String(v)
}
</script>

<style scoped>
.change-diff-card {
  border: 1px solid var(--border-color, #f1f5f9);
  border-radius: 8px;
  padding: 12px 16px;
  background: var(--bg-card);
  margin-top: 12px;
}

.change-diff-header {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
  margin-bottom: 8px;
}

.change-diff-empty {
  padding: 16px 0;
}

.change-diff-before {
  color: var(--color-danger);
  text-decoration: line-through;
  word-break: break-all;
}

.change-diff-after {
  color: var(--brand-xiyu-logo);
  font-weight: 500;
  word-break: break-all;
}
</style>
