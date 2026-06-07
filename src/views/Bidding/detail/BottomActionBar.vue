<template>
  <div v-if="actions.length > 0" class="bottom-action-bar">
    <div class="bottom-action-bar-inner">
      <div class="action-bar-right">
        <el-button
          v-for="action in actions"
          :key="action.key"
          :type="action.type"
          :icon="action.icon ? icons[action.icon] : undefined"
          size="large"
          @click="$emit('action', action.key)"
        >
          {{ action.label }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { h } from 'vue'
import { Edit } from '@element-plus/icons-vue'

const props = defineProps({
  actions: {
    type: Array,
    default: () => [],
    // [{ key: string, label: string, type: string, icon: string|null }]
  }
})

defineEmits(['action'])

// Map icon names to Element Plus icon components
const icons = { edit: Edit }
</script>

<style scoped>
.bottom-action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--bg-card);
  border-top: 1px solid var(--gray-250);
  padding: 12px 24px;
  z-index: 100;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.08);
}
.bottom-action-bar-inner {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}
</style>
