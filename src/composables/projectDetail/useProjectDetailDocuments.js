export function useProjectDetailDocuments(context) {
  const { project, route, userStore, projectsApi, collaborationApi, isApiProject } = context

  const downloadTextFile = (filename, content, mimeType = 'text/plain;charset=utf-8') => {
    const blob = new Blob([content], { type: mimeType })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(link.href)
  }

  const handleUpload = async (file) => {
    if (!project.value) return false
    const docPayload = { name: file.name, uploader: userStore.userName, time: new Date().toLocaleString('zh-CN', { hour12: false }), size: `${Math.max(1, Math.round((file.size || 1024 * 1024) / 1024 / 1024))}MB` }
    if (!isApiProject.value) {
      context.message.error('当前项目仅支持通过 API 上传文档')
      return false
    }
    try {
      const formData = new FormData()
      formData.set('file', file); formData.set('name', file.name); formData.set('size', docPayload.size); formData.set('fileType', file.type || 'application/octet-stream'); formData.set('uploaderId', String(userStore.currentUser?.id || '')); formData.set('uploaderName', userStore.userName)
      const result = await projectsApi.uploadDocument(route.params.id, formData)
      if (!result?.success || !result?.data) throw new Error(result?.msg || '上传文档失败')
      if (!Array.isArray(project.value.documents)) project.value.documents = []
      project.value.documents.unshift(result.data)
      context.activities.value.unshift({ id: Date.now(), user: userStore.userName, action: `上传了文档「${result.data.name}」`, time: new Date().toLocaleString('zh-CN', { hour12: false }) })
      context.message.success(`已上传文档：${result.data.name}`)
    } catch (error) {
      context.message.error(error.message || '上传文档失败')
    }
    return false
  }

  const handleDownload = (doc) => { downloadTextFile(doc.name, `文档：${doc.name}\n项目：${project.value?.name || ''}\n上传者：${doc.uploader || ''}`); context.message.success(`已下载 ${doc.name}`) }
  const handleDeleteDoc = async (doc) => {
    try {
      await context.confirm('确认删除该文档？', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
      if (isApiProject.value && /^\d+$/.test(String(doc?.id))) {
        const result = await projectsApi.deleteDocument(route.params.id, doc.id)
        if (!result?.success) throw new Error(result?.msg || '删除文档失败')
      }
      if (Array.isArray(project.value?.documents)) project.value.documents = project.value.documents.filter((item) => String(item.id) !== String(doc.id))
      context.message.success('删除成功')
    } catch {
      // 用户在 confirm 中点击了取消；无需处理。
    }
  }
  const handleCreateDocument = async (docName, size = '1.2MB') => {
    const formData = new FormData()
    formData.set('name', docName); formData.set('size', size); formData.set('fileType', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'); formData.set('uploaderId', String(userStore.currentUser?.id || '')); formData.set('uploaderName', userStore.userName)
    const result = await projectsApi.uploadDocument(route.params.id, formData)
    if (!result?.success || !result?.data) throw new Error(result?.msg || '新增项目文档失败')
    if (!Array.isArray(project.value.documents)) project.value.documents = []
    project.value.documents.unshift(result.data)
    context.activities.value.unshift({ id: Date.now(), user: userStore.userName, action: `新增了项目文档「${result.data.name}」`, time: new Date().toLocaleString('zh-CN', { hour12: false }) })
    context.message.success('项目文档已新增')
  }
  const handleAddDocument = async () => {
    if (!project.value) return
    const docName = `项目文档_${new Date().toLocaleDateString('zh-CN').replaceAll('/', '')}.docx`
    if (!isApiProject.value) {
      return context.message.error('当前项目仅支持通过 API 添加文档')
    }
    try { await handleCreateDocument(docName) } catch (error) { context.message.error(error.message || '新增项目文档失败') }
  }
  const handleShare = async () => {
    const fallbackLink = `${window.location.origin}/project/${route.params.id}`
    if (!isApiProject.value) return context.message.error('当前项目仅支持通过 API 生成分享链接')
    try {
      const result = await projectsApi.createShareLink(route.params.id, { createdBy: userStore.currentUser?.id || null, createdByName: userStore.userName, baseUrl: window.location.origin })
      await navigator.clipboard.writeText(result?.data?.url || fallbackLink)
      context.activities.value.unshift({ id: Date.now(), user: userStore.userName, action: '生成了项目分享链接', time: new Date().toLocaleString('zh-CN', { hour12: false }) })
      context.message.success('项目分享链接已复制到剪贴板')
    } catch (error) { context.message.error(error.message || '生成分享链接失败') }
  }
  const handleExport = () => {
    if (isApiProject.value) {
      collaborationApi.exports.createExport(route.params.id, { format: 'json', exportedBy: userStore.currentUser?.id || null, exportedByName: userStore.userName }).then((result) => {
        if (!result?.success || !result?.data) return context.message.error(result?.msg || '导出资料失败')
        downloadTextFile(result.data.fileName, result.data.content || '', result.data.contentType || 'application/json;charset=utf-8')
        context.activities.value.unshift({ id: Date.now(), user: userStore.userName, action: `导出了项目资料「${result.data.fileName}」`, time: new Date().toLocaleString('zh-CN', { hour12: false }) })
        context.message.success(`已导出 ${result.data.fileName}`)
      }).catch(() => context.message.error('导出资料失败'))
      return
    }
    context.message.error('当前项目仅支持通过 API 导出资料')
  }
  const handleArchiveDocuments = async () => {
    if (!project.value) return
    if (!isApiProject.value) {
      return context.message.error('当前项目仅支持通过 API 归档资料')
    }
    try {
      const result = await collaborationApi.exports.archive(route.params.id, { archivedBy: userStore.currentUser?.id || null, archivedByName: userStore.userName, archiveReason: '项目资料整理完成，归档留存' })
      if (!result?.success || !result?.data) throw new Error(result?.msg || '归档资料失败')
      if (project.value) project.value.status = 'archived'
      context.activities.value.unshift({ id: Date.now(), user: userStore.userName, action: `归档了项目资料（${result.data.archiveReason}）`, time: new Date().toLocaleString('zh-CN', { hour12: false }) })
      context.message.success('项目资料归档成功')
    } catch (error) { context.message.error(error.message || '归档资料失败') }
  }
  const handleSetReminder = async () => {
    const remindAt = new Date(); remindAt.setDate(remindAt.getDate() + 1); remindAt.setHours(9, 0, 0, 0)
    if (!isApiProject.value) {
      return context.message.error('当前项目仅支持通过 API 设置提醒')
    }
    try {
      const result = await projectsApi.createReminder(route.params.id, { title: '项目跟进提醒', message: `请跟进项目「${project.value?.name || ''}」`, remindAt: remindAt.toISOString(), createdBy: userStore.currentUser?.id || null, createdByName: userStore.userName, recipient: '项目负责人' })
      if (!result?.success || !result?.data) throw new Error(result?.msg || '设置提醒失败')
      context.activities.value.unshift({ id: Date.now(), user: userStore.userName, action: `设置了项目提醒：${result.data.title}`, time: new Date().toLocaleString('zh-CN', { hour12: false }) })
      context.message.success('项目提醒已创建')
    } catch (error) { context.message.error(error.message || '设置提醒失败') }
  }

  return { handleUpload, handleDownload, handleDeleteDoc, handleAddDocument, handleShare, handleExport, handleArchiveDocuments, handleSetReminder, handleCreateDocument }
}
