<template>
  <div class="feature-placeholder" :class="[`level-${level}`, { compact }]">
    <div class="placeholder-title">{{ title }}</div>
    <div v-if="message" class="placeholder-message">{{ message }}</div>
    <div v-if="hint" class="placeholder-hint">{{ hint }}</div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '功能暂未接入' },
  message: { type: String, default: '' },
  hint: { type: String, default: '' },
  level: { type: String, default: 'info' },
  compact: { type: Boolean, default: false },
})
</script>

<style scoped>
.feature-placeholder {
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px dashed #cbd5e1;
  background: #f8fafc;
  color: var(--sidebar-text-secondary);
}

.feature-placeholder.compact {
  padding: 10px 12px;
}

.feature-placeholder.level-warning {
  border-color: #fbbf24;
  background: #fffbeb;
}

.placeholder-title {
  font-weight: 600;
  color: #0f172a;
}

.placeholder-message,
.placeholder-hint {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.5;
}

.placeholder-hint {
  color: var(--text-slate);
}
</style>
