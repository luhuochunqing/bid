<template>
  <div class="project-stage-timeline" :aria-busy="loading || undefined">
    <el-steps
      :active="activeIndex"
      align-center
      finish-status="success"
      class="stage-steps"
    >
      <el-step
        v-for="stage in stages"
        :key="stage.code"
        :title="stage.title"
        :description="describe(stage)"
        :status="stepStatus(stage)"
        @click="onStageClick(stage)"
      />
    </el-steps>
    <div v-if="snapshot?.locked" class="stage-locked-hint">
      <el-tag type="warning" size="small">阶段已锁定</el-tag>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'

import { PROJECT_STAGES } from '@/constants/projectStages.js'

const STAGES = PROJECT_STAGES

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  initialStage: { type: String, default: '' },
})
const emit = defineEmits(['stage-click', 'snapshot'])

const stages = STAGES
const snapshot = ref(null)
const loading = ref(false)

const currentCode = computed(() => snapshot.value?.currentStage || props.initialStage || 'INITIATED')

const activeIndex = computed(() => {
  const idx = stages.findIndex((s) => s.code === currentCode.value)
  return idx < 0 ? 0 : idx
})

function stepStatus(stage) {
  const idx = stages.findIndex((s) => s.code === stage.code)
  if (idx < activeIndex.value) return 'success'
  if (idx === activeIndex.value) return 'process'
  return 'wait'
}

function describe(stage) {
  const completed = snapshot.value?.completedStages || []
  if (completed.includes(stage.code)) return '已完成'
  if (stage.code === currentCode.value) return '进行中'
  return '待进入'
}

function isUnlocked(stage) {
  const idx = stages.findIndex((s) => s.code === stage.code)
  return idx <= activeIndex.value
}

function onStageClick(stage) {
  if (!isUnlocked(stage)) {
    ElMessage.info('该阶段尚未到达，无法进入')
    return
  }
  emit('stage-click', stage)
}

async function loadStage() {
  if (!props.projectId) return
  loading.value = true
  try {
    const resp = await projectLifecycleApi.getStage(props.projectId)
    snapshot.value = resp?.data || resp
    emit('snapshot', snapshot.value)
  } catch (e) {
    // 静默失败，组件仍渲染默认状态
    console.warn('[ProjectStageTimeline] load stage failed', e)
  } finally {
    loading.value = false
  }
}

watch(() => props.projectId, loadStage)
onMounted(loadStage)

defineExpose({ reload: loadStage, stages: STAGES, snapshot })
</script>

<style scoped>
.project-stage-timeline {
  padding: 12px 8px;
  background: var(--bg-card);
  border: 1px solid #ebeef5;
  border-radius: 8px;
}
.stage-steps :deep(.el-step__title),
.stage-steps :deep(.el-step__description) {
  cursor: pointer;
}
.stage-locked-hint {
  margin-top: 8px;
  text-align: right;
}
</style>
