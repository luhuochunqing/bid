<template>
  <div class="operation-log-timeline">
    <div v-if="loading" class="timeline-loading">
      <el-skeleton :rows="5" animated />
    </div>
    <div v-else-if="logs.length === 0" class="timeline-empty">
      <el-empty description="暂无操作记录" />
    </div>
    <el-timeline v-else>
      <el-timeline-item
        v-for="log in logs"
        :key="log.id"
        :timestamp="formatTimestamp(log.timestamp)"
        :type="getTimelineType(log.action)"
        placement="top"
      >
        <div class="timeline-item-content">
          <div class="timeline-action">
            <el-tag :type="getActionTagType(log.action)" size="small">{{ getActionText(log.action) }}</el-tag>
            <span class="timeline-operator">— {{ log.username }}</span>
          </div>
          <div class="timeline-desc">{{ log.description }}</div>
          <div v-if="log.oldValue || log.newValue" class="timeline-change">
            <span v-if="log.oldValue" class="old-value">旧: {{ log.oldValue }}</span>
            <el-icon v-if="log.oldValue && log.newValue"><ArrowRight /></el-icon>
            <span v-if="log.newValue" class="new-value">新: {{ log.newValue }}</span>
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import api from '@/api'

const props = defineProps({
  tenderId: { type: [Number, String], required: true },
})

const logs = ref([])
const loading = ref(true)

const ACTION_LABELS = {
  CREATE: '创建',
  UPDATE: '编辑',
  ASSIGN: '分配',
  REASSIGN: '转派',
  EVALUATION_SUBMIT: '评估提交',
  PARTICIPATE: '立即投标',
  ABANDON: '放弃投标',
  STATUS_CHANGE: '状态变更',
  DELETE: '删除',
}

const ACTION_TAG_TYPES = {
  CREATE: 'success',
  UPDATE: 'warning',
  ASSIGN: 'primary',
  REASSIGN: 'warning',
  EVALUATION_SUBMIT: 'success',
  PARTICIPATE: 'success',
  ABANDON: 'danger',
  STATUS_CHANGE: 'info',
  DELETE: 'danger',
}

const ACTION_TIMELINE_TYPES = {
  CREATE: 'success',
  UPDATE: 'warning',
  ASSIGN: 'primary',
  REASSIGN: 'warning',
  EVALUATION_SUBMIT: 'success',
  PARTICIPATE: 'success',
  ABANDON: 'danger',
  STATUS_CHANGE: 'info',
  DELETE: 'danger',
}

const getActionText = (action) => ACTION_LABELS[action] || action
const getActionTagType = (action) => ACTION_TAG_TYPES[action] || 'info'
const getTimelineType = (action) => ACTION_TIMELINE_TYPES[action] || 'info'

const formatTimestamp = (val) => {
  if (!val) return ''
  const d = new Date(val)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}

onMounted(async () => {
  try {
    loading.value = true
    const res = await api.get(`/tenders/${props.tenderId}/audit-logs`)
    logs.value = res?.data?.data || []
  } catch {
    logs.value = []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.operation-log-timeline { padding: 16px 0; }
.timeline-loading { padding: 24px; }
.timeline-empty { padding: 40px 0; }
.timeline-item-content { padding-bottom: 8px; }
.timeline-action { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.timeline-operator { color: var(--text-muted); font-size: 13px; }
.timeline-desc { color: var(--gray-750); font-size: 14px; margin-bottom: 4px; }
.timeline-change { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--text-muted); }
.old-value { background: #fef0f0; padding: 2px 6px; border-radius: 3px; }
.new-value { background: #f0f9eb; padding: 2px 6px; border-radius: 3px; }
</style>
