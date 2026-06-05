<template>
  <div>
    <div v-if="assemblyHistory.length > 0" class="history-section">
      <el-divider>装配历史</el-divider>
      <div class="history-list">
        <div v-for="item in assemblyHistory" :key="item.id" class="history-item">
          <el-icon color="#67c23a"><CircleCheckFilled /></el-icon>
          <div class="history-content">
            <div class="history-title">{{ item.templateName }}</div>
            <div class="history-time">{{ item.time }}</div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="exportHistory.length > 0" class="history-section">
      <el-divider>导出历史</el-divider>
      <div class="history-list">
        <div v-for="item in exportHistory" :key="item.id" class="history-item">
          <el-icon color="#409eff"><Download /></el-icon>
          <div class="history-content">
            <div class="history-title">{{ item.fileName }}</div>
            <div class="history-time">{{ item.exportedAt }}</div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="archiveHistory.length > 0" class="history-section">
      <el-divider>归档记录</el-divider>
      <div class="history-list">
        <div v-for="item in archiveHistory" :key="item.id" class="history-item">
          <el-icon color="#67c23a"><DocumentChecked /></el-icon>
          <div class="history-content">
            <div class="history-title">{{ item.archiveReason }}</div>
            <div class="history-time">{{ item.archivedAt }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Download, DocumentChecked, CircleCheckFilled } from '@element-plus/icons-vue'

defineProps({
  assemblyHistory: { type: Array, default: () => [] },
  exportHistory: { type: Array, default: () => [] },
  archiveHistory: { type: Array, default: () => [] }
})
</script>

<style scoped>
.history-section { margin-top: 16px; }

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: var(--bg-subtle);
  border-radius: 8px;
  transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
}

.history-item:hover {
  background: #edf2f7;
  transform: translateX(4px);
}

.history-content { flex: 1; }

.history-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--gray-750);
  margin-bottom: 4px;
}

.history-time { font-size: 12px; color: var(--text-muted); }
</style>
