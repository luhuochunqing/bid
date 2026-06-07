<template>
  <div class="evaluation-status">
    <el-card shadow="never" class="status-card">
      <template #header>
        <span>评估状态信息</span>
      </template>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="当前子阶段">
          <el-tag v-if="view?.subStage" :type="tagType(view?.subStage)" size="small">{{ subStageLabel(view?.subStage) }}</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="评标开始时间">
          {{ formatDate(view?.evaluationStartedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="收到定标材料时间">
          {{ formatDate(view?.boardReceivedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="公示时间">
          {{ formatDate(view?.announcedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="建议是否投标">
          {{ formatRecommendation(view?.recommendation) }}
        </el-descriptions-item>
        <el-descriptions-item label="最后更新人">
          {{ view?.updatedBy || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="最后更新时间">
          {{ formatDate(view?.updatedAt) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="status-card" style="margin-top: 16px">
      <template #header>
        <span>评标状态</span>
      </template>
      <div class="status-tags">
        <el-tag
          v-for="opt in statusOptions"
          :key="opt.value"
          :type="localTargetSubStage === opt.value ? 'primary' : 'info'"
          :effect="localTargetSubStage === opt.value ? 'dark' : 'plain'"
          class="status-tag"
          @click="selectStatus(opt.value)"
        >
          {{ opt.label }}
        </el-tag>
      </div>
    </el-card>

    <!-- 状态变更记录 -->
    <el-card v-if="statusLogs.length" shadow="never" class="status-card" style="margin-top: 16px">
      <template #header>
        <span>操作日志</span>
      </template>
      <el-timeline>
        <el-timeline-item
          v-for="log in statusLogs"
          :key="log.id || log.timestamp"
          :timestamp="formatDate(log.createdAt) || formatDate(log.timestamp)"
          placement="top"
        >
          <p>{{ log.notes || '状态切换' }}</p>
          <p style="color: var(--el-text-color-secondary); font-size: 12px;">
            {{ subStageLabel(log.fromSubStage) }} → {{ subStageLabel(log.toSubStage || log.subStage) }}
          </p>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  view: { type: Object, default: null },
  transitioning: { type: Boolean, default: false },
  targetSubStage: { type: String, default: '' },
  statusLogs: { type: Array, default: () => [] }
})

const emit = defineEmits(['transition', 'update:targetSubStage'])

const localTargetSubStage = computed({
  get: () => props.targetSubStage,
  set: (val) => emit('update:targetSubStage', val)
})

const statusOptions = [
  { label: '评标中', value: 'IN_PROGRESS' },
  { label: '评标结果已出，待上会', value: 'AWAITING_BOARD' },
  { label: '评标结果已出', value: 'RESULT_OUT' },
  { label: '评标结果公示', value: 'ANNOUNCED' }
]

function selectStatus(value) {
  if (props.transitioning) return
  emit('update:targetSubStage', value)
  emit('transition', value)
}

function subStageLabel(val) {
  const opt = statusOptions.find(o => o.value === val)
  return opt ? opt.label : (val || '-')
}

function tagType(val) {
  const map = { IN_PROGRESS: 'primary', AWAITING_BOARD: 'warning', RESULT_OUT: 'info', ANNOUNCED: 'success' }
  return map[val] || 'info'
}

function formatDate(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function formatRecommendation(val) {
  if (val === true) return '建议投标'
  if (val === false) return '不建议投标'
  return '-'
}
</script>

<style scoped>
.evaluation-status {
  width: 320px;
  flex-shrink: 0;
}

.status-card {
  border: 1px solid #ebeef5;
}

.status-tags {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.status-tag {
  cursor: pointer;
  justify-content: center;
  padding: 8px 16px;
  font-size: 13px;
}
</style>
