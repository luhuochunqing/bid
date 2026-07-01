import { ElMessageBox } from 'element-plus'
import { taskTemplates } from './constants.js'
import { normalizeProjectTaskList, openScoreDraftDialogWhenTenderSourceMissing } from './projectDetailTaskGeneration.js'
import { createTaskAssigneePayload, uploadTaskFilesWithFallback } from './taskAssigneePayload.js'
import { normalizeTaskStatusForApi, taskFormDtoToBackend, taskBackendToCard } from '@/views/Project/project-utils'
import { tasksApi } from '@/api/modules/tasks.js'
export function useProjectDetailTaskActions(context) {
  const { route, userStore, projectStore, projectsApi, tenderBreakdownApi = projectsApi, isApiProject, message, state, workflow } = context
  const pushActivity = (action) => state.activities.value.unshift({ id: Date.now(), user: userStore.userName, action, time: new Date().toLocaleString('zh-CN', { hour12: false }) })
  const ensureTaskList = () => {
    if (!state.project.value) {
      return []
    }
    if (!Array.isArray(state.project.value.tasks)) {
      state.project.value.tasks = []
    }
    return state.project.value.tasks
  }
  const resolveErrorMessage = (error, fallback) => error?.response?.data?.msg || error?.message || fallback
  const resolveTenderBreakdownReadinessMessage = (readiness = {}) => (
    readiness.message
    || 'DeepSeek API Key 未配置。请管理员到系统设置 → AI 模型配置中填写 DeepSeek provider key，或在服务端设置 DEEPSEEK_API_KEY 后重启。'
  )
  const hasReusableTenderBreakdown = (result = {}) => Boolean(result?.document?.snapshotId)
  const isNotFoundResponse = (error) => Number(error?.response?.status || error?.status || error?.code) === 404
  const resolveTenderBreakdownReuseMessage = (result = {}, uploaded = false) => {
    const documentName = result?.document?.name
    const sourceText = uploaded ? '项目已上传的招标文件' : '已解析的招标文件'
    return documentName
      ? `已复用${sourceText}「${documentName}」，可直接拆解任务或生成标书初稿`
      : `已复用${sourceText}，可直接拆解任务或生成标书初稿`
  }
  const ensureTenderBreakdownReady = async () => {
    const readinessResult = await tenderBreakdownApi.getTenderBreakdownReadiness(route.params.id)
    const readiness = readinessResult?.data || {}
    if (!readinessResult?.success) throw new Error(readinessResult?.msg || '无法检查 DeepSeek 配置状态')
    if (readiness.ready === false) {
      message.warning(resolveTenderBreakdownReadinessMessage(readiness))
      return false
    }
    return true
  }
  const notifyTenderBreakdownReused = (result, uploaded = false) => {
    const documentName = result?.document?.name || '招标文件'
    pushActivity(uploaded ? `复用了项目已上传招标文件「${documentName}」` : `复用了已解析招标文件「${documentName}」`)
    message.success(resolveTenderBreakdownReuseMessage(result, uploaded))
  }
  const tryParseUploadedTenderBreakdown = async () => {
    if (typeof tenderBreakdownApi.parseUploadedTenderBreakdown !== 'function') {
      return false
    }
    const ready = await ensureTenderBreakdownReady()
    if (!ready) return true
    state.tenderBreakdownParsing.value = true
    try {
      const result = await tenderBreakdownApi.parseUploadedTenderBreakdown(route.params.id)
      if (result?.success && hasReusableTenderBreakdown(result.data)) {
        notifyTenderBreakdownReused(result.data, true)
        return true
      }
      return false
    } finally {
      state.tenderBreakdownParsing.value = false
    }
  }

  const getTaskTemplateByProject = (project) => {
    const industry = project?.industry?.toLowerCase() || ''
    if (industry.includes('政府') || industry.includes('gov')) return taskTemplates.government
    if (industry.includes('能源') || industry.includes('电力') || industry.includes('energy')) return taskTemplates.energy
    if (industry.includes('交通') || industry.includes('地铁') || industry.includes('traffic')) return taskTemplates.traffic
    return taskTemplates.default
  }

  const handleGenerateTasks = async () => {
    if (!state.project.value) return message.warning('项目信息未加载')
    if (isApiProject.value) {
      try {
        const result = await projectsApi.decomposeTasks(route.params.id)
        const tasks = Array.isArray(result?.data) ? result.data : result?.data?.tasks
        if (!result?.success || !Array.isArray(tasks)) throw new Error(result?.msg || '任务拆解失败')
        state.project.value.tasks = normalizeProjectTaskList(tasks)
        pushActivity(`自动拆解生成了 ${state.project.value.tasks.length} 个任务`)
        message.success(`已拆解生成 ${state.project.value.tasks.length} 个任务`)
      } catch (error) {
        if (await openScoreDraftDialogWhenTenderSourceMissing({ error, projectsApi, projectId: route.params.id, state, message, resolveErrorMessage })) return
        message.error(resolveErrorMessage(error, '任务拆解失败'))
      }
      return
    }
    const deadline = new Date(state.project.value.deadline)
    state.project.value.tasks = getTaskTemplateByProject(state.project.value).map((taskTemplate, index) => {
      const taskDeadline = new Date(deadline)
      taskDeadline.setDate(taskDeadline.getDate() - taskTemplate.deadlineOffset)
      return { id: `${state.project.value.id}_T${String(index + 1).padStart(3, '0')}`, name: taskTemplate.name, description: taskTemplate.description, owner: taskTemplate.owner, status: 'TODO', priority: taskTemplate.priority, deadline: taskDeadline.toISOString().split('T')[0], hasDeliverable: taskTemplate.needsDeliverable, deliverableType: taskTemplate.deliverableType || 'other', deliverables: [] }
    })
    pushActivity(`根据项目模板自动生成了 ${state.project.value.tasks.length} 个任务`)
    message.success(`已自动生成 ${state.project.value.tasks.length} 个任务`)
  }

  const handleScoreDraftGenerated = (tasks) => {
    if (!state.project.value) return
    state.project.value.tasks = normalizeProjectTaskList(tasks)
    pushActivity(`根据评分标准生成了 ${tasks.length} 个正式任务`)
  }

  const handleOpenScoreDraftDecompose = () => { state.scoreDraftDialogVisible.value = true }

  const handleOpenTenderBreakdown = async () => {
    if (!state.project.value) { message.warning('项目信息未加载'); return }
    if (!isApiProject.value) {
      message.warning('当前项目不支持解析招标文件')
      return
    }
    if (state.tenderBreakdownParsing.value) {
      message.warning('正在解析招标文件，请稍候')
      return
    }
    if (typeof tenderBreakdownApi.getLatestTenderBreakdown === 'function') {
      try {
        const latestResult = await tenderBreakdownApi.getLatestTenderBreakdown(route.params.id)
        if (latestResult?.success && hasReusableTenderBreakdown(latestResult.data)) {
          notifyTenderBreakdownReused(latestResult.data)
          return
        }
      } catch (error) {
        if (!isNotFoundResponse(error)) {
          message.error(resolveErrorMessage(error, '读取已解析招标文件失败'))
          return
        }
      }
    }
    try {
      if (await tryParseUploadedTenderBreakdown()) {
        return
      }
    } catch (error) {
      if (!isNotFoundResponse(error)) {
        message.error(resolveErrorMessage(error, '复用已上传招标文件失败'))
        return
      }
    }
    state.tenderBreakdownDialogVisible.value = true
  }

  const handleTenderBreakdownUpload = async (file) => {
    if (!state.project.value) {
      message.warning('项目信息未加载')
      return false
    }
    if (!file) {
      message.warning('请先选择招标文件')
      return false
    }
    if (!isApiProject.value) {
      message.warning('当前项目不支持解析招标文件')
      return false
    }
    if (state.tenderBreakdownParsing.value) {
      message.warning('正在解析招标文件，请稍候')
      return false
    }

    try {
      const ready = await ensureTenderBreakdownReady()
      if (!ready) {
        return false
      }

      state.tenderBreakdownParsing.value = true
      const result = await tenderBreakdownApi.parseTenderBreakdown(route.params.id, file)
      if (!result?.success || !result?.data?.document?.snapshotId) {
        throw new Error(result?.msg || '招标文件解析失败')
      }
      state.tenderBreakdownDialogVisible.value = false
      pushActivity(`解析了招标文件「${file.name || '招标文件'}」`)
      message.success('招标文件已拆解，可继续生成任务或标书初稿')
    } catch (error) {
      message.error(resolveErrorMessage(error, '招标文件解析失败'))
    } finally {
      state.tenderBreakdownParsing.value = false
    }
    return false
  }

  const handleAddTask = () => {
    if (!state.project.value) return
    const nextIndex = (state.project.value.tasks?.length || 0) + 1
    const dueDate = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000)
    const newTask = { name: `新增任务 ${nextIndex}`, owner: userStore.userName, assignee: userStore.userName, department: '投标管理部', dueDate: dueDate.toISOString().split('T')[0], priority: 'medium', status: 'TODO', deliverables: [], hasDeliverable: false }

    if (!isApiProject.value) {
      state.project.value.tasks = Array.isArray(state.project.value.tasks) ? state.project.value.tasks : []
      state.project.value.tasks.unshift({ id: `TASK_${Date.now()}`, ...newTask })
      pushActivity(`新增了任务「${newTask.name}」`)
      message.success('任务已新增')
      return
    }

    projectsApi.createTask(route.params.id, { title: newTask.name, description: '', assigneeId: userStore.currentUser?.id || null, assigneeName: userStore.userName, priority: 'MEDIUM', dueDate: dueDate.toISOString() }).then((result) => {
      if (!result?.success || !result?.data) throw new Error(result?.msg || '新增任务失败')
      ensureTaskList().unshift(taskBackendToCard({ ...result.data, deliverables: [] }))
      pushActivity(`新增了任务「${result.data.name}」`)
      message.success('任务已新增')
    }).catch((error) => message.error(error.message || '新增任务失败'))
  }

  const handleResetTasks = () => {
    ElMessageBox.confirm('确认重置所有任务？这将清空当前项目的所有任务数据。', '重置确认', { confirmButtonText: '确认重置', cancelButtonText: '取消', type: 'warning' }).then(() => {
      state.project.value.tasks = []
      pushActivity('重置了项目任务')
      message.success('任务已重置，可以重新拆解任务')
    }).catch(() => {})
  }

  const handleTaskClick = async (task) => {
    state.currentTask.value = task
    try {
      const res = await tasksApi.getTaskById(task.id)
      const taskData = res?.data?.data || res?.data || {}
      if (state.currentTask.value?.id === task.id) {
        state.currentTask.value = { ...taskBackendToCard(taskData), deliverableFiles: [] }
      }
    } catch (e) {
      // ignore
    }
  }

  const handleSaveTask = async (payload = {}) => {
    const { mode = 'create', data = {}, done } = payload
    if (!state.project.value) { message.warning('项目信息未加载'); return }

    if (mode === 'edit' && data?.id != null) {
      const tasks = ensureTaskList()
      const target = tasks.find((t) => String(t.id) === String(data.id))
      if (!target) return
      try {
        const dto = taskFormDtoToBackend(data)
        const updated = await projectStore.updateTask(state.project.value.id, target.id, dto)
        Object.assign(target, taskBackendToCard(updated))
        const uploadOk = await uploadTaskFilesWithFallback(target, data, { projectStore, projectId: route.params.id, userStore }, {
          attachments: '任务已更新，但附件上传失败，请重试', deliverables: '任务已更新，但交付物上传失败，请重试',
        }, message)
        if (!uploadOk) return
        pushActivity(`更新了任务「${target.name}」`)
        message.success('任务已更新')
        done?.()
      } catch (error) {
        message.error(resolveErrorMessage(error, '任务更新失败'))
      }
      return
    }

    const title = data?.name || `新增任务 ${(state.project.value.tasks?.length || 0) + 1}`
    const dueDate = data?.deadline ? new Date(data.deadline) : new Date(Date.now() + 3 * 24 * 60 * 60 * 1000)

    if (!isApiProject.value) {
      const list = ensureTaskList()
      list.unshift({
        id: `TASK_${Date.now()}`, name: title,
        owner: data?.owner || userStore.userName, assignee: data?.owner || userStore.userName,
        department: '投标管理部', dueDate: dueDate.toISOString().split('T')[0],
        priority: data?.priority || 'medium', status: data?.status || 'todo',
        content: data?.content || '', extendedFields: data?.extendedFields || {},
        deliverables: [], hasDeliverable: false,
      })
      pushActivity(`新增了任务「${title}」`)
      message.success('任务已新增')
      done?.()
      return
    }

    try {
      const result = await projectsApi.createTask(route.params.id, {
        title,
        description: '', content: data?.content || '',
        extendedFields: data?.extendedFields || {},
        ...createTaskAssigneePayload(data, userStore),
        priority: (data?.priority || 'medium').toUpperCase(),
        dueDate: dueDate.toISOString(),
      })
      if (!result?.success || !result?.data) throw new Error(result?.msg || '新增任务失败')
      const createdTask = taskBackendToCard({ ...result.data, deliverables: [] })
      const uploadOk = await uploadTaskFilesWithFallback(createdTask, data, { projectStore, projectId: route.params.id, userStore }, {
        attachments: '任务已新增，但附件上传失败，请重试', deliverables: '任务已新增，但交付物上传失败，请重试',
      }, message)
      ensureTaskList().unshift(createdTask)
      pushActivity(`新增了任务「${createdTask.name}」`)
      if (!uploadOk) return
      message.success('任务已新增')
      done?.()
    } catch (error) {
      message.error(resolveErrorMessage(error, '新增任务失败'))
    }
  }
  const handleTaskStatusChange = async (task, newStatus, reviewComment) => {
    // CO-411: 传入的 task 可能是浅拷贝（CO-397 抽屉），按 id 查原引用再改，确保看板刷新
    const tasks = ensureTaskList()
    const taskRef = (task?.id != null && tasks.find((t) => String(t.id) === String(task.id))) || task
    if (taskRef) {
      taskRef.status = newStatus
      // CO-413: 驳回原因乐观写入 extendedFields，供任务详情即时展示
      if (reviewComment) {
        taskRef.extendedFields = { ...(taskRef.extendedFields || {}), lastRejectReason: reviewComment }
      }
      pushActivity(`将任务"${taskRef.name}"状态更新为${({ todo: '待办', review: '待审核', done: '已完成' }[newStatus] || newStatus)}`)
    }
    if (!isApiProject.value) {
      await projectStore.updateTaskStatus(route.params.id, taskRef?.id, newStatus)
      message.success('任务状态已更新')
      return
    }
    try {
      const result = await projectsApi.updateTaskStatus(route.params.id, taskRef?.id, normalizeTaskStatusForApi(newStatus), reviewComment)
      if (!result?.success || !result?.data) throw new Error(result?.msg || '任务状态更新失败')
      const keepDeliverables = taskRef.deliverables
      const keepAttachments = taskRef.attachments
      Object.assign(taskRef, taskBackendToCard(result.data))
      taskRef.deliverables = keepDeliverables || taskRef.deliverables
      taskRef.attachments = keepAttachments || taskRef.attachments
      taskRef.hasDeliverable = (taskRef.deliverables || []).length > 0
      message.success('任务状态已更新')
    } catch (error) {
      message.error(error.message || '任务状态更新失败')
    }
  }
  const handleAddDeliverable = (taskId, deliverable) => {
    const task = state.project.value?.tasks?.find((item) => item.id === taskId)
    if (!task) return
    task.deliverables = task.deliverables || []
    const exists = deliverable?.id != null && task.deliverables.some((item) => String(item.id) === String(deliverable.id))
    if (!exists) task.deliverables.push(deliverable)
    task.hasDeliverable = true
    pushActivity(`为任务"${task.name}"上传了交付物: ${deliverable.name}`)
    message.success('交付物已添加')
  }
  const handleRemoveDeliverable = (taskId, deliverableId) => {
    const task = state.project.value?.tasks?.find((item) => item.id === taskId)
    if (!task?.deliverables) return
    const deliverable = task.deliverables.find((item) => item.id === deliverableId)
    task.deliverables = task.deliverables.filter((item) => item.id !== deliverableId)
    task.hasDeliverable = task.deliverables.length > 0
    pushActivity(`删除了任务"${task.name}"的交付物: ${deliverable?.name}`)
    message.success('交付物已删除')
  }

  const handleSubmitReview = async (data) => {
    const task = state.project.value?.tasks?.find((item) => item.id === data.id)
    if (!task) {
      message.warning('未找到对应任务')
      return
    }
    const newStatus = data.status || 'REVIEW'
    task.status = newStatus
    Object.assign(task, data)
    pushActivity(`任务"${task.name}"已提交审核`)
    message.success('任务已提交审核，等待审批')
    if (!isApiProject.value) {
      await projectStore.updateTaskStatus(route.params.id, task.id, newStatus)
      return
    }
    try {
      if (!await uploadTaskFilesWithFallback(task, data, { projectStore, projectId: route.params.id, userStore }, { attachments: '任务已提交审核，但附件上传失败，请重试', deliverables: '任务已提交审核，但交付物上传失败，请重试' }, message)) return
      const completionNotes = data.completionNotes
      const result = await projectsApi.updateTaskStatus(route.params.id, task.id, normalizeTaskStatusForApi(newStatus), undefined, completionNotes)
      if (!result?.success) throw new Error(result?.msg || '提交审核失败')
      const keepDeliverables = task.deliverables
      const keepAttachments = task.attachments
      Object.assign(task, taskBackendToCard(result.data))
      task.deliverables = keepDeliverables || task.deliverables
      task.attachments = keepAttachments || task.attachments
      task.hasDeliverable = (task.deliverables || []).length > 0
      message.success('任务已提交审核')
    } catch (error) {
      message.error(resolveErrorMessage(error, '提交审核失败'))
    }
  }

  const handleSubmitToDocument = async () => {
    const tasks = state.project.value?.tasks || []
    if (!tasks.length) return

    const allCompleted = tasks.every((task) => projectStore.isTerminalStatus(task.status))
    if (!allCompleted) {
      message.warning('请先完成所有任务后再提交至标书编写流程')
      return
    }

    const tasksWithDeliverables = tasks.filter((task) => Array.isArray(task.deliverables) && task.deliverables.length > 0)
    if (!tasksWithDeliverables.length) {
      message.warning('请至少上传一个任务的交付物后再提交')
      return
    }

    try {
      await ElMessageBox.confirm(
        `所有任务已完成，确认提交至标书编写流程？\n\n已完成任务数: ${tasks.length}\n已上传交付物: ${tasksWithDeliverables.length} 个任务`,
        '提交确认',
        {
          confirmButtonText: '确认提交',
          cancelButtonText: '取消',
          type: 'success',
        },
      )
      await workflow.handleInitiateProcess()
      pushActivity('所有任务已完成，提交至标书编写流程')
      message.success('已提交至标书编写流程，可开始编制标书')
    } catch {
      return
    }
  }

  return { handleGenerateTasks, handleScoreDraftGenerated, handleOpenScoreDraftDecompose, handleOpenTenderBreakdown, handleTenderBreakdownUpload, handleAddTask, handleResetTasks, handleTaskClick, handleSaveTask, handleTaskStatusChange, handleAddDeliverable, handleRemoveDeliverable, handleSubmitReview, handleSubmitToDocument }
}
