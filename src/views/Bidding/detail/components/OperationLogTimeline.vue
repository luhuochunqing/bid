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
        :timestamp="log.time"
        :type="getTimelineType(log.actionType)"
        placement="top"
      >
        <div class="timeline-item-content">
          <div class="timeline-action">
            <el-tag :type="getActionTagType(log.actionType)" size="small">{{ getActionText(log.actionType) }}</el-tag>
            <span class="timeline-operator">— {{ log.operator }}</span>
          </div>
          <div class="timeline-desc">{{ log.detail }}</div>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { auditApi } from '@/api/modules/audit.js'

const props = defineProps({
  tenderId: { type: [Number, String], required: true },
})

const logs = ref([])
const loading = ref(true)

const ACTION_LABELS = {
  create: '创建',
  update: '编辑',
  assign: '分配',
  reassign: '转派',
  transfer: '转派',
  evaluation_submit: '评估提交',
  participate: '立即投标',
  abandon: '放弃投标',
  status_change: '状态变更',
  delete: '删除',
  link_crm: '关联商机',
  ai_analyze: 'AI分析',
  review: '评估决策',
  proceed_to_bid: '投标立项',
  evaluation_review: '评估审核',
}

const ACTION_TAG_TYPES = {
  create: 'success',
  update: 'warning',
  assign: 'primary',
  reassign: 'warning',
  transfer: 'warning',
  evaluation_submit: 'success',
  participate: 'success',
  abandon: 'danger',
  status_change: 'info',
  delete: 'danger',
  link_crm: 'primary',
  ai_analyze: 'info',
  review: 'primary',
  proceed_to_bid: 'success',
  evaluation_review: 'primary',
}

const ACTION_TIMELINE_TYPES = {
  create: 'success',
  update: 'warning',
  assign: 'primary',
  reassign: 'warning',
  transfer: 'warning',
  evaluation_submit: 'success',
  participate: 'success',
  abandon: 'danger',
  status_change: 'info',
  delete: 'danger',
  link_crm: 'primary',
  ai_analyze: 'info',
  review: 'primary',
  proceed_to_bid: 'success',
  evaluation_review: 'primary',
}

const getActionText = (action) => ACTION_LABELS[action] || '操作'
const getActionTagType = (action) => ACTION_TAG_TYPES[action] || 'info'
const getTimelineType = (action) => ACTION_TIMELINE_TYPES[action] || 'info'

onMounted(async () => {
  try {
    loading.value = true
    const res = await auditApi.getTenderAuditLogs(props.tenderId)
    logs.value = res?.data?.data || res?.data || []
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
</style>
