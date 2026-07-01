/**
 * 任务操作 composable
 *
 * 封装 TASK 类型任务的通用操作：执行人判断、交付物判断、
 * 交付物上传 + 提交审核对话框、提交逻辑。
 *
 * 复用点：TaskBoardCard、TaskKanban 等多个组件有完全相同的
 * 交付物上传/提交流程，抽到此 composable 统一维护。
 *
 * @param {Object} options
 * @param {Function} options.getProjectId - 获取当前 projectId 的函数
 * @param {Function} [options.onSubmitted] - 提交成功后的回调（刷新列表等）
 * @param {Function} [options.api] - 自定义 API 对象（默认用 projectsApi）
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { projectsApi } from '@/api/modules/projects.js'
import { createTaskDeliverable as apiCreateTaskDeliverable } from '@/api/modules/taskDeliverables.js'
import { useUserStore } from '@/stores/user.js'
import { TASK_STATUS } from '@/constants/taskStatus.js'
import { validateSubmitForReview } from './useTaskSubmissionValidation.js'

export function useTaskActions(options = {}) {
  const { onSubmitted, api = projectsApi } = options

  const userStore = useUserStore()

  // ===== 当前用户判断 =====
  function matchesCurrentUser(id) {
    const uid = userStore?.currentUser?.id
    return uid != null && id != null && String(uid) === String(id)
  }

  function isTaskAssignee(task) {
    return matchesCurrentUser(task?.assigneeId)
  }

  // ===== 交付物判断 =====
  // 兼容两种数据形态：
  // 1. TaskKanban 等项目内视图：deliverableUrl / deliverableName / fileUrl
  // 2. TaskBoard 看板视图：deliverables 数组（异步加载）
  function hasDeliverable(task) {
    if (!task) return false
    if (Array.isArray(task.deliverables) && task.deliverables.length > 0) return true
    if (task.deliverableUrl || task.deliverableName || task.fileUrl) return true
    return false
  }

  // ===== 交付物上传 + 提交对话框状态 =====
  const showSubmitDialog = ref(false)
  const submittingTask = ref(null)
  const submittingTaskLoading = ref(false)
  const deliverableFileList = ref([])
  const deliverableUploadRef = ref(null)
  const submitNotes = ref('')

  function buildFileList(task) {
    if (task.deliverableUrl) {
      return [{ name: task.deliverableName || '已上传文件', url: task.deliverableUrl }]
    }
    return []
  }

  function openDeliverableUpload(task) {
    submittingTask.value = task
    showSubmitDialog.value = true
    deliverableFileList.value = buildFileList(task)
    submitNotes.value = task.completionNotes || ''
  }

  function openSubmitDialog(task) {
    if (!hasDeliverable(task)) {
      ElMessage.warning('请先上传交付物')
      return
    }
    submittingTask.value = task
    showSubmitDialog.value = true
    deliverableFileList.value = buildFileList(task)
    submitNotes.value = task.completionNotes || ''
  }

  function closeSubmitDialog() {
    showSubmitDialog.value = false
    submittingTask.value = null
    submitNotes.value = ''
    deliverableFileList.value = []
  }

  // ===== 提交审核 =====
  async function confirmSubmit() {
    if (!submittingTask.value) return
    const task = submittingTask.value
    const projectId = options.getProjectId ? options.getProjectId(task) : task.projectId
    if (!projectId) {
      ElMessage.error('缺少项目信息，无法提交')
      return
    }
    const validation = validateSubmitForReview({
      deliverables: task.deliverables,
      deliverableFiles: deliverableUploadRef.value?.uploadFiles,
      hasDeliverable: hasDeliverable(task),
      completionNotes: submitNotes.value
    })
    if (!validation.valid) {
      ElMessage.warning(validation.message)
      return
    }

    submittingTaskLoading.value = true
    try {
      if (deliverableUploadRef.value?.uploadFiles?.length > 0) {
        const formData = new FormData()
        formData.append('file', deliverableUploadRef.value.uploadFiles[0].raw)
        formData.append('taskId', task.id)
        await apiCreateTaskDeliverable(projectId, task.id, formData)
      }
      await api.updateTaskStatus(projectId, task.id, TASK_STATUS.REVIEW, null, submitNotes.value)
      ElMessage.success('已提交审核')
      closeSubmitDialog()
      if (onSubmitted) await onSubmitted(task)
    } catch (e) {
      ElMessage.error(e?.response?.data?.msg || '提交失败')
    } finally {
      submittingTaskLoading.value = false
    }
  }

  return {
    // 判断函数
    isTaskAssignee,
    hasDeliverable,
    matchesCurrentUser,
    // 对话框状态
    showSubmitDialog,
    submittingTask,
    submittingTaskLoading,
    deliverableFileList,
    deliverableUploadRef,
    submitNotes,
    // 操作方法
    openDeliverableUpload,
    openSubmitDialog,
    closeSubmitDialog,
    confirmSubmit,
  }
}
