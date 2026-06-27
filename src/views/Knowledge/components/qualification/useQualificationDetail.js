// Input: http client, qualifications ref (来自 useQualificationList), fetchQualifications
// Output: 资质详情抽屉状态、附件操作（替换/删除/上传/下载）、行下载逻辑
// Pos: src/views/Knowledge/components/qualification/ - 从 Qualification.vue 抽取的详情/附件逻辑
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/client'

export function useQualificationDetail({ qualifications, fetchQualifications }) {
  // 4.1.3.6 资质详情抽屉
  const detailDrawerVisible = ref(false)
  const detailQualification = ref(null)
  const detailAttachments = ref([])

  // 4.2.1.3 编辑资质 - 附件管理（替换/上传共用弹窗）
  const replaceDialogVisible = ref(false)
  const replaceQualificationId = ref(null)
  const replaceAttachmentId = ref(null)
  const replaceCurrentFileName = ref('')

  const openDetailDrawer = (row) => {
    detailQualification.value = row
    detailAttachments.value = Array.isArray(row?.attachments) ? row.attachments : []
    detailDrawerVisible.value = true
  }

  const refreshDetailFromList = () => {
    const id = detailQualification.value?.id
    if (!id) return
    const updated = qualifications.value.find((q) => q.id === id)
    if (updated) {
      detailQualification.value = updated
      detailAttachments.value = Array.isArray(updated.attachments) ? updated.attachments : []
    }
  }

  const handleAttachmentActionSuccess = () => {
    fetchQualifications()
    refreshDetailFromList()
  }

  const handleAttachmentReplace = (att) => {
    replaceQualificationId.value = detailQualification.value?.id
    replaceAttachmentId.value = att?.id || null
    replaceCurrentFileName.value = att?.fileName || att?.name || ''
    replaceDialogVisible.value = true
  }

  const handleAttachmentUpload = () => {
    replaceQualificationId.value = detailQualification.value?.id
    replaceAttachmentId.value = null
    replaceCurrentFileName.value = ''
    replaceDialogVisible.value = true
  }

  const handleAttachmentDelete = async (att) => {
    const fileName = att?.fileName || att?.name || '该附件'
    // CO-368 fix: 文案区分是否最后一个附件，避免误导用户
    const isLastAttachment = detailAttachments.value.length <= 1
    const warningText = isLastAttachment
      ? `确认删除附件 ${fileName}？\n\n删除后证书将处于"无附件"状态，可能影响后续投标资质佐证。建议尽快上传新附件。\n\n该操作将被记录在操作日志中。`
      : `确认删除附件 ${fileName}？\n\n删除后无法恢复，请谨慎操作。\n\n该操作将被记录在操作日志中。`
    try {
      await ElMessageBox.confirm(
        warningText,
        '删除附件',
        { confirmButtonText: '确认删除', confirmButtonClass: 'el-button--danger', type: 'warning' }
      )
      const id = detailQualification.value?.id
      const attId = att?.id
      if (!id || !attId) {
        ElMessage.warning('附件信息不完整，请刷新后重试')
        return
      }
      await http.delete(`/api/knowledge/qualifications/${id}/attachments/${attId}`)
      ElMessage.success('附件已删除')
      handleAttachmentActionSuccess()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error('删除失败')
    }
  }

  // 附件下载（来自详情抽屉）
  const handleDetailDownload = async (att) => {
    const qId = detailQualification.value?.id
    const attId = att?.id
    if (!qId || !attId) {
      ElMessage.warning('附件信息不完整，请刷新后重试')
      return
    }
    try {
      const res = await http.get(`/api/knowledge/qualifications/${qId}/attachments/${attId}`, { responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([res.data]))
      const a = document.createElement('a')
      a.href = url
      a.download = att.fileName || '附件'
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    } catch {
      ElMessage.error('下载失败')
    }
  }

  // 附件预览（来自详情抽屉）- 在新窗口打开
  // CO-368 fix: 加 ?inline=true 让浏览器内显示 PDF/图片，而非下载
  const handleDetailPreview = (att) => {
    const qId = detailQualification.value?.id
    const attId = att?.id
    if (!qId || !attId) {
      ElMessage.warning('附件信息不完整，请刷新后重试')
      return
    }
    const url = `/api/knowledge/qualifications/${qId}/attachments/${attId}?inline=true`
    window.open(url, '_blank')
  }

  // 行下载：优先走 attachments[0] 的 attachment-id 接口，兜底走旧 fileUrl
  const handleDownloadFile = async (row) => {
    const att = row.attachments?.[0]
    const qId = row.id
    const attId = att?.id
    if (qId && attId) {
      try {
        const res = await http.get(`/api/knowledge/qualifications/${qId}/attachments/${attId}`, { responseType: 'blob' })
        const url = window.URL.createObjectURL(new Blob([res.data]))
        const a = document.createElement('a')
        a.href = url
        a.download = att.fileName || row.name || '资质附件'
        document.body.appendChild(a)
        a.click()
        a.remove()
        window.URL.revokeObjectURL(url)
        return
      } catch {
        /* fall through to legacy fallback */
      }
    }
    // 兜底：旧数据直接使用 fileUrl
    if (row.fileUrl) {
      try {
        const res = await http.get(row.fileUrl, { responseType: 'blob' })
        const url = window.URL.createObjectURL(new Blob([res.data]))
        const a = document.createElement('a')
        a.href = url
        a.download = row.name || '资质附件'
        document.body.appendChild(a)
        a.click()
        a.remove()
        window.URL.revokeObjectURL(url)
      } catch {
        ElMessage.error('下载失败')
      }
    } else {
      ElMessage.error('下载失败')
    }
  }

  return {
    detailDrawerVisible,
    detailQualification,
    detailAttachments,
    replaceDialogVisible,
    replaceQualificationId,
    replaceAttachmentId,
    replaceCurrentFileName,
    openDetailDrawer,
    refreshDetailFromList,
    handleAttachmentActionSuccess,
    handleAttachmentReplace,
    handleAttachmentUpload,
    handleAttachmentDelete,
    handleDetailPreview,
    handleDetailDownload,
    handleDownloadFile
  }
}
