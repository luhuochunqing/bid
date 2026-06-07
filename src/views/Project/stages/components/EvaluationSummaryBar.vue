<template>
  <div class="project-summary-bar">
    <div class="summary-card">
      <div class="summary-item">
        <span class="summary-label">项目名称</span>
        <span class="summary-value" :title="projectName">{{ projectName || '-' }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">招标主体</span>
        <span class="summary-value" :title="initiation?.ownerUnit">{{ initiation?.ownerUnit || '-' }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">项目负责人</span>
        <span class="summary-value">{{ initiation?.projectLeaderName || '-' }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">投标负责人</span>
        <span class="summary-value" :title="initiation?.biddingLeaderName">{{ initiation?.biddingLeaderName || '-' }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">开标时间</span>
        <span class="summary-value">{{ formatDateTime(initiation?.bidOpenTime) || '-' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  projectName: { type: String, default: '' },
  initiation: { type: Object, default: null }
})

const formatDateTime = (dateStr) => {
  if (!dateStr || typeof dateStr !== 'string') return ''
  return dateStr.replace('T', ' ')
}
</script>

<style scoped>
.project-summary-bar {
  margin-top: 16px;
  margin-bottom: 8px;
}

.summary-card {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 16px 24px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.summary-item:not(:last-child) {
  border-right: 1px solid var(--el-border-color-extra-light);
  padding-right: 16px;
}

.summary-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.summary-value {
  font-size: 14px;
  color: var(--el-text-color-primary);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .summary-card {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .summary-item:not(:last-child) {
    border-right: none;
    border-bottom: 1px solid var(--el-border-color-extra-light);
    padding-right: 0;
    padding-bottom: 12px;
  }
}
</style>
