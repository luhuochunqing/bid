<template>
  <div class="knowledge-float-panel">
    <div class="panel-header">
      <el-icon><MagicStick /></el-icon>
      <span>知识库推荐</span>
    </div>
    <div class="knowledge-list">
      <div
        v-for="match in matches"
        :key="match.id"
        class="knowledge-item"
        @click="$emit('insert', match)"
      >
        <div class="knowledge-type">
          <el-tag :type="match.type === 'case' ? 'success' : 'primary'" size="small">
            {{ match.type === 'case' ? '案例' : '模板' }}
          </el-tag>
          <span class="relevance">匹配度: {{ match.relevance }}%</span>
        </div>
        <div class="knowledge-title">{{ match.title }}</div>
        <div class="knowledge-summary">{{ match.summary }}</div>
        <div class="insert-hint">点击插入</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { MagicStick } from '@element-plus/icons-vue'

defineProps({
  matches: { type: Array, default: () => [] }
})

defineEmits(['insert'])
</script>

<style scoped>
.knowledge-float-panel {
  position: absolute;
  right: 20px;
  top: 20px;
  width: 280px;
  max-height: 400px;
  background: var(--bg-card);
  border: 1px solid var(--gray-250);
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  z-index: 10;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: var(--bg-card);
  font-weight: 600;
}

.knowledge-list {
  max-height: 320px;
  overflow-y: auto;
}

.knowledge-item {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1);
  border: 1.5px solid #e5e7eb;
}

.knowledge-item:hover {
  background: var(--bg-subtle);
  border-color: var(--accent-blue);
  box-shadow: 0 4px 12px rgba(3, 105, 161, 0.1);
  transform: translateY(-1px);
}

.knowledge-item:active {
  transform: translateY(0);
}

.knowledge-item:last-child {
  border-bottom: none;
}

.knowledge-type {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.relevance {
  font-size: 12px;
  color: #67c23a;
  font-weight: 600;
}

.knowledge-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--gray-750);
  margin-bottom: 4px;
}

.knowledge-summary {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.insert-hint {
  font-size: 12px;
  color: #409eff;
}

:deep(.el-tag) {
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  border: none;
}

:deep(.el-tag--primary) {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: var(--bg-card);
}

:deep(.el-tag--success) {
  background: linear-gradient(135deg, var(--color-success), var(--color-success-dark));
  color: var(--bg-card);
}
</style>
