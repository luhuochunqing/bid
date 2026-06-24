<template>
  <div class="evaluation-stage">
    <!-- 评标状态 -->
    <el-card shadow="never" class="stage-section">
      <template #header><span class="section-title">评标状态</span></template>
      <div class="status-group">
        <span
          v-for="opt in statusOptions"
          :key="opt.value"
          class="status-chip"
          :class="{ 'status-chip--active': targetSubStage === opt.value, 'status-chip--clickable': editable }"
          @click="selectStatus(opt.value)"
        >
          {{ opt.label }}
        </span>
      </div>
      <div class="notes-section">
        <label class="notes-label">评标情况说明<span class="required-mark">*</span></label>
        <el-input
          v-model="evaluationNotes"
          type="textarea"
          :rows="5"
          placeholder="请在选择评标状态后填写评标情况说明..."
          :disabled="!editable"
        />
      </div>
    </el-card>

    <!-- 评标文件 -->
    <el-card shadow="never" class="stage-section">
      <template #header><span class="section-title">评标文件</span></template>
      <EvaluationEvidenceUpload
        ref="evidenceUploadRef"
        :project-id="projectId"
        :existing-doc-ids="evidenceDocIds"
        :editable="editable"
      />
    </el-card>

    <!-- 提交 -->
    <div v-if="editable" class="btn-container">
      <el-button v-if="evaluationDone" type="primary" size="large" disabled>已提交</el-button>
      <el-button v-else type="primary" size="large" :loading="submitting" @click="handleSubmit">提交</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user.js'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { STAGE_TRANSITION_MAP } from '@/constants/projectStages.js'
import EvaluationEvidenceUpload from './components/EvaluationEvidenceUpload.vue'

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const emit = defineEmits(["advanced", "switch-tab"])

const userStore = useUserStore()
const evidenceUploadRef = ref(null)
const view = ref(null)
const targetSubStage = ref('')
const evidenceDocIds = ref([])
const submitting = ref(false)
const evaluationNotes = ref('')
const evaluationDone = ref(false)

const statusOptions = [
  { label: '评标中', value: 'IN_PROGRESS' },
  { label: '评标结果已出，待上会', value: 'AWAITING_BOARD' },
  { label: '评标结果已出', value: 'RESULT_OUT' },
  { label: '评标结果公示', value: 'ANNOUNCED' }
]

const editable = computed(() => {
  if (evaluationDone.value) return false
  const role = userStore.userRole
  // 评标编辑权限：投标管理员/组长 + 投标负责人/辅助人（与蓝图权限矩阵一致）
  if (role === 'admin' || role === '/bidAdmin' || role === 'bid-TeamLeader') return true
  if (role === 'bid-projectLeader' || role === 'bid-Team') return true
  return false
})

function selectStatus(value) {
  if (!editable.value) return
  targetSubStage.value = value
}

async function load() {
  view.value = null
  targetSubStage.value = ''
  evidenceDocIds.value = []
  evaluationNotes.value = ''
  try {
    const r = await projectLifecycleApi.getEvaluation(props.projectId)
    view.value = r?.data || r
    if (view.value?.subStage) targetSubStage.value = view.value.subStage
    if (view.value?.evidenceDocIds) evidenceDocIds.value = view.value.evidenceDocIds
    if (view.value?.notes) evaluationNotes.value = view.value.notes
    if (view.value?.done) evaluationDone.value = true
  } catch (e) {
    console.warn('加载评估数据失败', e)
  }
}

async function handleSubmit() {
  if (!targetSubStage.value) return ElMessage.warning('请先选择评标状态')
  if (!(evaluationNotes.value || '').trim()) return ElMessage.warning('请填写评标情况说明')

  submitting.value = true
  try {
    const currentSubStage = view.value?.subStage
    if (targetSubStage.value !== currentSubStage) {
      await projectLifecycleApi.transitionEvaluationSubStage(props.projectId, {
        targetSubStage: targetSubStage.value, notes: evaluationNotes.value
      })
    } else if (evaluationNotes.value !== (view.value?.notes || '')) {
      await projectLifecycleApi.updateEvaluationForm(props.projectId, { notes: evaluationNotes.value })
    }
    const pendingIds = evidenceUploadRef.value?.getPendingFileIds() || []
    if (pendingIds.length > 0) {
      await projectLifecycleApi.attachEvaluationEvidence(props.projectId, { fileIds: pendingIds })
      evidenceUploadRef.value?.clearPendingFileIds()
    }
    await projectLifecycleApi.advanceEvaluation(props.projectId)
    evaluationDone.value = true
    ElMessage.success('提交成功，已推进至结果确认阶段')
    emit('advanced')
    emit('switch-tab', STAGE_TRANSITION_MAP.EVALUATING)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)

defineExpose({ load })
</script>

<style scoped>
.evaluation-stage { display: flex; flex-direction: column; gap: 16px; }
.section-title { font-size: 15px; font-weight: 600; color: #2E7659; }

.status-group { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.status-chip {
  display: inline-flex; align-items: center; justify-content: center;
  padding: 10px 24px; font-size: 15px; border-radius: 6px;
  border: 1px solid var(--el-border-color); background: var(--el-fill-color-light);
  color: var(--el-text-color-regular); user-select: none; transition: all 0.2s;
}
.status-chip--clickable { cursor: pointer; }
.status-chip--clickable:hover { border-color: var(--el-color-primary); }
.status-chip--active {
  background: var(--el-color-primary); color: #fff; border-color: var(--el-color-primary); font-weight: 600;
}

.notes-section { margin-top: 4px; }
.notes-label { display: block; font-size: 13px; font-weight: 500; color: #333; margin-bottom: 8px; }
.required-mark { color: #e65100; margin-left: 2px; }

.btn-container { display: flex; justify-content: flex-end; }
</style>
