// Input: bid result page refs/reactive state
// Output: command-side actions for bid result workflows
// Pos: src/views/Resource/bid-result/ - Bid result page command actions
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ElMessage } from 'element-plus'
import { crmApi } from '@/api/modules/crm.js'

import { bidResultsApi } from '@/api'

import {
  addCompetitorRow,
  assignFormValues,
  buildResultPayload,
  createUploadForm,
  persistCompetitors
} from './bidResultPage.helpers.js'
import { createBidResultPageQueryActions } from './bidResultPage.queryActions.js'

export function createBidResultPageActions(state) {
  const {
    initialProjectId,
    pageLoading,
    saving,
    syncing,
    fetching,
    sending,
    reportLoading,
    ignoreSubmitting,
    uploadSaving,
    confirmSaving,
    overview,
    projects,
    fetchResults,
    reminderRecords,
    competitorReport,
    selectedFetchIds,
    registerForm,
    confirmForm,
    uploadForm,
    confirmDialogVisible,
    ignoreDialogVisible,
    reportVisible,
    uploadDialogVisible,
    currentFetchRecord,
    currentReminderRecord,
    ignoreReason
  } = state

  const assignForm = (target, source = {}) => assignFormValues(target, source, initialProjectId)

  const resetRegisterForm = () => assignForm(registerForm, { projectId: initialProjectId, result: 'won' })

  const removeCompetitor = (form, index) => {
    form.competitors.splice(index, 1)
  }

  const queryActions = createBidResultPageQueryActions({
    pageLoading,
    syncing,
    fetching,
    sending,
    reportLoading,
    overview,
    projects,
    fetchResults,
    reminderRecords,
    competitorReport
  })

  const persistAttachment = async (projectId, resultId, form, reminderId = null) => {
    if (!form.attachmentFile || !projectId || !resultId) return

    const documentCategory = form.attachmentType === 'LOSS_REPORT' ? 'BID_RESULT_ANALYSIS' : 'BID_RESULT_NOTICE'
    const uploadResult = await bidResultsApi.uploadProjectDocument(projectId, {
      file: form.attachmentFile,
      name: form.attachmentFile?.name,
      uploaderName: '',
      documentCategory,
      linkedEntityType: 'BID_RESULT',
      linkedEntityId: resultId
    })

    if (!uploadResult?.success) {
      throw new Error(uploadResult?.message || '上传结果附件失败')
    }

    const documentId = uploadResult?.data?.id
    await bidResultsApi.bindAttachment(resultId, {
      documentId,
      attachmentType: form.attachmentType
    })

    if (reminderId) {
      await bidResultsApi.markReminderUploaded(reminderId, {
        documentId,
        attachmentType: form.attachmentType
      })
    }
  }

  const submitRegister = async () => {
    saving.value = true
    try {
      const response = registerForm.id
        ? await bidResultsApi.update(registerForm.id, buildResultPayload(registerForm))
        : await bidResultsApi.register(buildResultPayload(registerForm))
      if (!response?.success) {
        throw new Error(response?.msg || '保存结果失败')
      }
      const saved = response.data
      await persistAttachment(saved.projectId, saved.id, registerForm)
      await persistCompetitors(saved.projectId, registerForm.competitors)
      // CRM标讯回传：登记结果
      try {
        const statusMap = { won: 2, lost: 3, abandoned: 1, failed: 4 }
        const resultStatus = statusMap[registerForm.result] || 0
        if (resultStatus > 0 && saved?.tenderCode) {
          await crmApi.bidInfoSync([{
            name: saved.projectName || registerForm.projectName || '',
            code: saved.tenderCode || '',
            status: resultStatus,
            statusEditor: '',
            statusEditTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
            feedback: JSON.stringify({
              result: registerForm.result,
              winningVendor: registerForm.winningVendor || '',
              bidAmount: registerForm.bidAmount || '',
              remark: registerForm.remark || '',
              time: new Date().toISOString(),
            }),
          }])
        }
      } catch { /* 非关键路径，静默处理 */ }
      ElMessage.success(registerForm.id ? '结果已更新' : '结果已登记')
      resetRegisterForm()
      await queryActions.loadPage()
    } catch (error) {
      ElMessage.error(error?.message || '保存结果失败')
    } finally {
      saving.value = false
    }
  }

  const openConfirmDialog = (row) => {
    currentFetchRecord.value = row
    assignForm(confirmForm, row)
    confirmDialogVisible.value = true
  }

  const submitConfirm = async () => {
    if (!currentFetchRecord.value?.id) return
    confirmSaving.value = true
    try {
      const result = await bidResultsApi.confirmWithData(currentFetchRecord.value.id, buildResultPayload(confirmForm))
      if (!result?.success) {
        throw new Error(result?.msg || '确认失败')
      }
      await persistAttachment(result.data.projectId, result.data.id, confirmForm)
      await persistCompetitors(result.data.projectId, confirmForm.competitors)
      // CRM标讯回传：确认外部结果
      try {
        const statusMap = { won: 2, lost: 3, abandoned: 1, failed: 4 }
        const resultStatus = statusMap[confirmForm.result] || 0
        if (resultStatus > 0 && result.data?.tenderCode) {
          await crmApi.bidInfoSync([{
            name: result.data.projectName || confirmForm.projectName || '',
            code: result.data.tenderCode || '',
            status: resultStatus,
            statusEditor: '',
            statusEditTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
            feedback: JSON.stringify({
              result: confirmForm.result,
              winningVendor: confirmForm.winningVendor || '',
              bidAmount: confirmForm.bidAmount || '',
              remark: confirmForm.remark || '',
              time: new Date().toISOString(),
            }),
          }])
        }
      } catch { /* 非关键路径，静默处理 */ }
      ElMessage.success('外部结果已确认')
      confirmDialogVisible.value = false
      await queryActions.loadPage()
    } catch (error) {
      ElMessage.error(error?.message || '确认失败')
    } finally {
      confirmSaving.value = false
    }
  }

  const openIgnoreDialog = (row) => {
    currentFetchRecord.value = row
    ignoreReason.value = ''
    ignoreDialogVisible.value = true
  }

  const submitIgnore = async () => {
    if (!currentFetchRecord.value?.id || !ignoreReason.value.trim()) {
      ElMessage.warning('请填写忽略原因')
      return
    }
    ignoreSubmitting.value = true
    try {
      const result = await bidResultsApi.ignore(currentFetchRecord.value.id, ignoreReason.value.trim())
      if (!result?.success) throw new Error(result?.msg || '忽略失败')
      ElMessage.success('已忽略该外部结果')
      ignoreDialogVisible.value = false
      await queryActions.loadPage()
    } catch (error) {
      ElMessage.error(error?.message || '忽略失败')
    } finally {
      ignoreSubmitting.value = false
    }
  }

  const submitConfirmBatch = async () => {
    if (selectedFetchIds.value.length === 0) return
    try {
      const result = await bidResultsApi.confirmBatch(selectedFetchIds.value)
      if (!result?.success) throw new Error(result?.msg || '批量确认失败')
      ElMessage.success(result?.data?.msg || '批量确认完成')
      selectedFetchIds.value = []
      await queryActions.loadPage()
    } catch (error) {
      ElMessage.error(error?.message || '批量确认失败')
    }
  }

  const openUploadDialog = (row) => {
    currentReminderRecord.value = row
    Object.assign(uploadForm, createUploadForm(), {
      attachmentType: row.type === 'notice' ? 'WIN_NOTICE' : 'LOSS_REPORT'
    })
    uploadDialogVisible.value = true
  }

  const submitUpload = async () => {
    if (!currentReminderRecord.value?.projectId || !currentReminderRecord.value?.lastResultId) {
      ElMessage.warning('当前提醒记录缺少关联结果')
      return
    }
    if (!uploadForm.file) {
      ElMessage.warning('请选择上传文件')
      return
    }
    uploadSaving.value = true
    try {
      uploadForm.attachmentFile = uploadForm.file
      await persistAttachment(
        currentReminderRecord.value.projectId,
        currentReminderRecord.value.lastResultId,
        uploadForm,
        currentReminderRecord.value.id
      )
      ElMessage.success('资料已上传并回写状态')
      uploadDialogVisible.value = false
      await queryActions.loadPage()
    } catch (error) {
      ElMessage.error(error?.message || '上传失败')
    } finally {
      uploadSaving.value = false
    }
  }

  return {
    addCompetitor: addCompetitorRow,
    removeCompetitor,
    assignForm,
    loadPage: queryActions.loadPage,
    resetRegisterForm,
    submitRegister,
    openConfirmDialog,
    submitConfirm,
    openIgnoreDialog,
    submitIgnore,
    submitConfirmBatch,
    syncInternal: queryActions.syncInternal,
    fetchPublic: queryActions.fetchPublic,
    sendReminderAgain: queryActions.sendReminderAgain,
    sendAllReminders: queryActions.sendAllReminders,
    openUploadDialog,
    submitUpload,
    showReport: () => queryActions.showReport(reportVisible)
  }
}
