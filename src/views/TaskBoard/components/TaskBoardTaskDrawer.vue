<template>
  <el-drawer
    v-model="drawerVisible"
    title="任务详情"
    size="520px"
    direction="rtl"
    :destroy-on-close="true"
  >
    <div v-loading="loadingTaskDetail">
      <TaskForm
        v-if="selectedTask"
        ref="taskFormRef"
        v-model="selectedTask"
        mode="view"
      />
    </div>
    <template #footer>
      <div class="drawer-footer">
        <el-button @click="drawerVisible = false">关闭</el-button>
        <el-button
          v-if="canSubmitForReview"
          type="primary"
          :loading="submitting"
          @click="handleSubmitForReview"
        >
          提交审核
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import TaskForm from '@/components/project/TaskForm.vue'
import { projectsApi } from '@/api/modules/projects.js'
import { tasksApi } from '@/api/modules/tasks.js'
import { taskBackendToCard } from '@/views/Project/project-utils.js'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import { validateSubmitForReview } from '@/composables/useTaskSubmissionValidation.js'
import { uploadTaskFilesWithFallback } from '@/composables/projectDetail/taskAssigneePayload'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  taskId: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:modelValue', 'submitted'])

const drawerVisible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const selectedTask = ref(null)
const taskFormRef = ref(null)
const submitting = ref(false)
const loadingTaskDetail = ref(false)

const canSubmitForReview = computed(() => taskFormRef.value?.canDeliver === true)

async function loadTask(taskId) {
  loadingTaskDetail.value = true
  try {
    const res = await tasksApi.getTaskById(taskId)
    const taskData = res?.data?.data || res?.data || {}
    selectedTask.value = { ...taskBackendToCard(taskData), deliverableFiles: [] }
    await nextTick()
    drawerVisible.value = true
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '加载任务详情失败')
  } finally {
    loadingTaskDetail.value = false
  }
}

async function handleSubmitForReview() {
  if (!selectedTask.value?.id || !selectedTask.value?.projectId) {
    ElMessage.warning('任务数据不完整')
    return
  }
  submitting.value = true
  try {
    const form = taskFormRef.value
    const result = form?.submitForReview?.()
    if (!result || result.valid === false) {
      if (result?.message) ElMessage.warning(result.message)
      return
    }

    const data = result.data
    const projectId = data.projectId
    const taskId = data.id

    const validation = validateSubmitForReview({
      deliverables: data.deliverables,
      deliverableFiles: data.deliverableFiles,
      completionNotes: data.completionNotes
    })
    if (!validation.valid) {
      ElMessage.warning(validation.message)
      return
    }

    const projectStore = useProjectStore()
    const userStore = useUserStore()
    const uploadOk = await uploadTaskFilesWithFallback(
      selectedTask.value,
      { attachments: [], deliverableFiles: data.deliverableFiles || [] },
      { projectStore, projectId, userStore },
      {
        attachments: '已提交审核，但附件上传失败，请重试',
        deliverables: '已提交审核，但交付物上传失败，请重试',
      },
      ElMessage,
    )
    if (!uploadOk) return

    // CO-448: 保证金任务 4 个执行人填写字段需在提交审核时持久化到 extendedFields，
    // updateTaskStatus 只更新 status，不保存 extendedFields，故需先调 updateTask
    if (data.extendedFields) {
      await projectsApi.updateTask(taskId, { extendedFields: data.extendedFields })
    }

    await projectsApi.updateTaskStatus(projectId, taskId, 'REVIEW', null, data.completionNotes)

    ElMessage.success('已提交审核')
    drawerVisible.value = false
    selectedTask.value = null
    emit('submitted')
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '提交审核失败')
  } finally {
    submitting.value = false
  }
}

defineExpose({ loadTask })
</script>
