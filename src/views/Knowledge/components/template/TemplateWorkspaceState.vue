<template>
  <el-card class="workspace-state el-empty" shadow="never">
    <div class="state-badge">
      {{ state.type === 'initial' ? '模板资产工作台' : '筛选结果' }}
    </div>
    <h3 class="state-title">{{ state.title }}</h3>
    <p class="state-description">{{ state.description }}</p>
    <p class="state-hint">{{ state.hint }}</p>
    <div class="state-actions">
      <el-button
        v-if="state.type === 'initial'"
        type="primary"
        @click="$emit('create')"
      >
        新建模板
      </el-button>
      <el-button
        v-else
        @click="$emit('clear')"
      >
        清空条件
      </el-button>
    </div>
  </el-card>
</template>

<script setup>
defineProps({
  state: { type: Object, required: true }
})

defineEmits(['clear', 'create'])
</script>

<style scoped>
.workspace-state {
  margin-top: 16px;
  border-radius: 20px;
  border: 1px dashed #c6d7ea;
  background:
    radial-gradient(circle at top left, rgba(64, 158, 255, 0.08), transparent 35%),
    linear-gradient(180deg, var(--bg-card) 0%, #f8fbff 100%);
}

.state-badge {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  padding: 0 12px;
  border-radius: 999px;
  background: #eef5ff;
  color: #335d92;
  font-size: 12px;
  font-weight: 600;
}

.state-title {
  margin: 18px 0 8px;
  font-size: 24px;
  color: #1f2937;
}

.state-description,
.state-hint {
  margin: 0;
  max-width: 640px;
  line-height: 1.7;
  color: #52627a;
}

.state-hint {
  margin-top: 8px;
}

.state-actions {
  margin-top: 20px;
}

.state-actions :deep(.el-button) {
  min-height: 44px;
}
</style>
