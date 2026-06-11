<template>
  <div v-if="actions.length > 0" class="form-action-bar">
    <div class="form-action-bar-inner">
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
  }
})

defineEmits(['action'])

const icons = { edit: Edit }
</script>

<style scoped>
.form-action-bar {
  position: sticky;
  bottom: 20px;
  z-index: 10;
  margin-top: 16px;
  padding: 8px 24px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  border-top: 1px solid var(--gray-150);
}
.form-action-bar-inner {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}
</style>
