<template>
  <div
    v-if="items.length > 0"
    class="summary-bar"
    role="status"
    aria-live="polite"
  >
    <div class="summary-copy">
      <span class="summary-label">当前视图</span>
      <div class="summary-tags">
        <span
          v-for="item in items"
          :key="item.key"
          class="summary-chip"
          :class="`is-${item.emphasis || 'primary'}`"
        >
          <span class="chip-key">{{ item.label }}</span>
          <span class="chip-value">{{ item.value }}</span>
        </span>
      </div>
    </div>
    <el-button text type="primary" class="clear-button" @click="$emit('clear')">
      一键清空
    </el-button>
  </div>
</template>

<script setup>
defineProps({
  items: { type: Array, default: () => [] }
})

defineEmits(['clear'])
</script>

<style scoped>
.summary-bar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  padding: 14px 18px;
  margin: 16px 0 0;
  border-radius: 16px;
  border: 1px solid #d9e7f7;
  background: linear-gradient(135deg, #f9fcff 0%, #f2f7ff 100%);
}

.summary-copy {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.summary-label {
  font-size: 13px;
  color: #52627a;
  font-weight: 600;
}

.summary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.summary-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--bg-card);
  border: 1px solid #d8e5f5;
  color: #1f2937;
}

.summary-chip.is-secondary {
  background: #f7f8fb;
  color: #5b6472;
}

.summary-chip.is-local {
  background: #f3f8f4;
  border-color: #cfe9d7;
}

.chip-key {
  font-size: 12px;
  color: var(--gray-650);
}

.chip-value {
  font-size: 13px;
  font-weight: 600;
}

.clear-button {
  min-height: 40px;
}

@media (max-width: 768px) {
  .summary-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .clear-button {
    align-self: flex-start;
  }
}
</style>
