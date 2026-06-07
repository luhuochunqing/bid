<template>
  <div class="audit-timeline-container">
    <el-empty v-if="logs.length === 0" description="暂无操作记录" />
    <el-timeline v-else class="audit-timeline">
      <el-timeline-item
        v-for="(log, idx) in logs"
        :key="idx"
        :type="getTimelineItemType(log.actionType)"
        :hollow="true"
        :timestamp="formatLogTime(log.time)"
        placement="top"
      >
        <el-card class="timeline-card">
          <div class="log-header">
            <span class="operator-name">{{ log.operator }}</span>
            <el-tag :type="getLogActionTagType(log.actionType)" size="small">{{ getActionLabel(log.actionType) }}</el-tag>
          </div>
          <div class="log-content">{{ log.content }}</div>
        </el-card>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup>
import { getTimelineItemType } from '../archiveLabels.js'

defineProps({
  logs: { type: Array, default: () => [] }
})

const formatLogTime = (time) => {
  if (!time) return '-'
  const d = new Date(time)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const getActionLabel = (action) => {
  const map = { PREVIEW: '预览', DOWNLOAD: '下载', EXPORT: '导出', VIEW: '查看' }
  return map[action] || action || '-'
}

const getLogActionTagType = (action) => {
  const act = String(action || '').toUpperCase()
  if (act.includes('DOWNLOAD') || act.includes('EXPORT')) return 'success'
  if (act.includes('PREVIEW')) return 'primary'
  return 'info'
}
</script>

<style scoped lang="scss">
.audit-timeline-container {
  padding-top: 16px;
}

.audit-timeline {
  padding-left: 8px;
}

.timeline-card {
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: none !important;
  background-color: var(--el-fill-color-blank);
  margin-bottom: 4px;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.operator-name {
  font-weight: 600;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.log-content {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 6px;
}
</style>
