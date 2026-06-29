<template>
  <div class="password-cell">
    <div class="password-row">
      <span class="password-text">{{ password.displayText(row.id) }}</span>
    </div>
    <button
      v-if="canReveal"
      class="password-toggle-btn"
      :disabled="password.isLoading(row.id)"
      @click.stop="password.toggle(row.id)">
      <el-icon size="14">
        <component :is="password.isVisible(row.id) ? Hide : View" />
      </el-icon>
    </button>
  </div>
</template>

<script setup>
import { Hide, View } from '@element-plus/icons-vue'

defineProps({
  row: { type: Object, required: true },
  password: { type: Object, required: true },
  canReveal: { type: Boolean, default: true }
})
</script>

<style scoped>
.password-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
}
.password-row {
  display: flex;
  align-items: center;
  width: 100%;
}
.password-text {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: var(--text-secondary);
}
.password-toggle-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 150ms ease;
}
.password-toggle-btn:hover {
  background: var(--surface-hover);
  color: var(--brand-xiyu-logo);
}
</style>
