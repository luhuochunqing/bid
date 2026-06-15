import { ElMessageBox } from 'element-plus'
import { collaborationApi } from '@/api'

function downloadTextFile(filename, content, mimeType = 'text/plain;charset=utf-8') {
  const blob = new Blob([content], { type: mimeType })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(link.href)
}

export function useProjectDetailDocumentActions(context) {
  const {
    route,
    project,
    projectExpenses,
    userStore,
    projectsApi,
    collaborationApi: collaborationApiOverride,
    isApiProject,
    message,
    state,
  } = context
  const collaboration = collaborationApiOverride || collaborationApi
  const pushActivity = (action) => state.activities.value.unshift({ id: Date.now(), user: userStore.userName, action, time: new Date().toLocaleString('zh-CN', { hour12: false }) })
  const ensureDocumentList = () => {
    if (!project.value) {
      return []
    }
    if (!Array.isArray(project.value.documents)) {
      project.value.documents = []
    }
    return project.value.documents
  }

  const handleUpload = async (file) => {
    if (!project.value) return false
    const docPayload = { name: file.name, uploader: userStore.userName, time: new Date().toLocaleString('zh-CN', { hour12: false }), size: `${Math.max(1, Math.round((file.size || 1024 * 1024) / 1024 / 1024))}MB` }
    if (!isApiProject.value) {
      project.value.documents = Array.isArray(project.value.documents) ? project.value.documents : []
      project.value.documents.unshift({ id: `DOC_${Date.now()}`, ...docPayload })
      pushActivity(`上传了文档「${file.name}」`)
      message.success(`已上传演示文档：${file.name}`)
      return false
    }

    try {
      const formData = new FormData()
      formData.set('file', file)
      formData.set('name', file.name)
      formData.set('size', docPayload.size)
      formData.set('fileType', file.type || 'application/octet-stream')
      formData.set('uploaderId', String(userStore.currentUser?.id || ''))
      formData.set('uploaderName', userStore.userName)
      const result = await projectsApi.uploadDocument(route.params.id, formData)
      if (!result?.success || !result?.data) throw new Error(result?.msg || '上传文档失败')
      ensureDocumentList().unshift(result.data)
      pushActivity(`上传了文档「${result.data.name}」`)
      message.success(`已上传文档：${result.data.name}`)
    } catch (error) {
      message.error(error.message || '上传文档失败')
    }
    return false
  }

  const handleDownload = (doc) => {
    downloadTextFile(doc.name, `演示文档：${doc.name}\n项目：${project.value?.name || ''}\n上传者：${doc.uploader || ''}`)
    message.success(`已下载 ${doc.name}`)
  }

  const handleDeleteDoc = async (doc) => {
    try {
      await ElMessageBox.confirm('确认删除该文档？', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
      if (isApiProject.value && /^\d+$/.test(String(doc?.id))) {
        const result = await projectsApi.deleteDocument(route.params.id, doc.id)
        if (!result?.success) throw new Error(result?.msg || '删除文档失败')
      }
      project.value.documents = project.value.documents.filter((item) => String(item.id) !== String(doc.id))
      message.success('删除成功')
    } catch {
      return
    }
  }

  const handleAddDocument = async () => {
    if (!project.value) return
    const docName = `项目文档_${new Date().toLocaleDateString('zh-CN').replaceAll('/', '')}.docx`
    if (!isApiProject.value) {
      project.value.documents.unshift({ id: `DOC_${Date.now()}`, name: docName, uploader: userStore.userName, time: new Date().toLocaleString('zh-CN', { hour12: false }), size: '1.2MB' })
      pushActivity(`新增了项目文档「${docName}」`)
      message.success('已新增演示文档')
      return
    }
    try {
      const formData = new FormData()
      formData.set('name', docName)
      formData.set('size', '1.2MB')
      formData.set('fileType', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document')
      formData.set('uploaderId', String(userStore.currentUser?.id || ''))
      formData.set('uploaderName', userStore.userName)
      const result = await projectsApi.uploadDocument(route.params.id, formData)
      if (!result?.success || !result?.data) throw new Error(result?.msg || '新增项目文档失败')
      ensureDocumentList().unshift(result.data)
      pushActivity(`新增了项目文档「${result.data.name}」`)
      message.success('项目文档已新增')
    } catch (error) {
      message.error(error.message || '新增项目文档失败')
    }
  }

  const handleShare = async () => {
    const fallbackLink = `${window.location.origin}/project/${route.params.id}`
    if (!isApiProject.value) {
      navigator.clipboard.writeText(fallbackLink).then(() => message.success('项目链接已复制到剪贴板')).catch(() => message.success(`分享链接：${fallbackLink}`))
      return
    }
    try {
      const result = await projectsApi.createShareLink(route.params.id, { createdBy: userStore.currentUser?.id || null, createdByName: userStore.userName, baseUrl: window.location.origin })
      await navigator.clipboard.writeText(result?.data?.url || fallbackLink)
      pushActivity('生成了项目分享链接')
      message.success('项目分享链接已复制到剪贴板')
    } catch (error) {
      message.error(error.message || '生成分享链接失败')
    }
  }

  const handleExport = () => {
    if (!isApiProject.value) {
      downloadTextFile(`${project.value?.name || '项目资料'}_导出.json`, JSON.stringify({ project: project.value, expenses: projectExpenses.value, activities: state.activities.value, exportedAt: new Date().toISOString() }, null, 2), 'application/json;charset=utf-8')
      message.success(`已生成演示导出包：${project.value?.name || '项目资料'}_导出.json`)
      return
    }
    collaboration.exports.createExport(route.params.id, { format: 'json', exportedBy: userStore.currentUser?.id || null, exportedByName: userStore.userName }).then((result) => {
      if (!result?.success || !result?.data) return message.error(result?.msg || '导出资料失败')
      downloadTextFile(result.data.fileName, result.data.content || '', result.data.contentType || 'application/json;charset=utf-8')
      pushActivity(`导出了项目资料「${result.data.fileName}」`)
      message.success(`已导出 ${result.data.fileName}`)
    }).catch(() => message.error('导出资料失败'))
  }

  const handleArchiveDocuments = async () => {
    if (!project.value) return

    if (!isApiProject.value) {
      pushActivity('归档了项目资料')
      message.success('已完成本地归档记录')
      return
    }

    try {
      const result = await collaboration.exports.archive(route.params.id, {
        archivedBy: userStore.currentUser?.id || null,
        archivedByName: userStore.userName,
        archiveReason: '项目资料整理完成，归档留存',
      })
      if (!result?.success || !result?.data) {
        throw new Error(result?.msg || '归档资料失败')
      }
      project.value.status = 'archived'
      pushActivity(`归档了项目资料（${result.data.archiveReason}）`)
      message.success('项目资料归档成功')
    } catch (error) {
      message.error(error.message || '归档资料失败')
    }
  }

  const handleSetReminder = async () => {
    const remindAt = new Date()
    remindAt.setDate(remindAt.getDate() + 1)
    remindAt.setHours(9, 0, 0, 0)

    if (!isApiProject.value) {
      pushActivity('设置了项目跟进提醒')
      message.success('已设置本地提醒，默认明天 09:00 提醒')
      return
    }

    try {
      const result = await projectsApi.createReminder(route.params.id, {
        title: '项目跟进提醒',
        message: `请跟进项目「${project.value?.name || ''}」`,
        remindAt: remindAt.toISOString(),
        createdBy: userStore.currentUser?.id || null,
        createdByName: userStore.userName,
        recipient: '项目负责人',
      })
      if (!result?.success || !result?.data) {
        throw new Error(result?.msg || '设置提醒失败')
      }
      pushActivity(`设置了项目提醒：${result.data.title}`)
      message.success('项目提醒已创建')
    } catch (error) {
      message.error(error.message || '设置提醒失败')
    }
  }

  const loadProjectWorkflowData = async (projectId) => {
    if (!project.value || !isApiProject.value) return
    const [taskResult, documentResult] = await Promise.all([projectsApi.getTasks(projectId), projectsApi.getDocuments(projectId)])
    project.value.tasks = taskResult?.success && Array.isArray(taskResult.data)
      ? taskResult.data
          .filter((task) => !task.title?.startsWith('【待立项】'))
          .map((task) => ({ ...task, deliverables: task.deliverables || [], hasDeliverable: Boolean(task.hasDeliverable) }))
      : []
    project.value.documents = documentResult?.success && Array.isArray(documentResult.data) ? documentResult.data : []
  }

  return { handleUpload, handleDownload, handleDeleteDoc, handleAddDocument, handleShare, handleExport, handleArchiveDocuments, handleSetReminder, loadProjectWorkflowData }
}
