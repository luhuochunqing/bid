import { ElMessage, ElMessageBox } from 'element-plus'
import { crmApi } from '@/api/modules/crm.js'
import { tendersApi } from '@/api'
import { safeTenderUrl } from '../bidding-utils.js'

export function useTenderActions(tenderRef, loadTenderDetailFn) {
  const handleParticipate = async () => {
    if (!tenderRef.value) return
    try {
      await ElMessageBox.confirm(
        '确认要投标此标讯吗？投标后将生成项目立项待办。',
        '确认投标',
        { confirmButtonText: '确认投标', cancelButtonText: '取消', type: 'warning' }
      )
    } catch { return }
    try {
      const result = await tendersApi.participate(tenderRef.value.id)
      if (result?.success && result?.data?.accepted) {
        // 参与成功后，触发项目立项
        try {
          await tendersApi.proceedToBid(tenderRef.value.id)
        } catch { /* 立项创建失败不影响投标状态 */ }
        // CRM标讯回传：投标参与
        try {
          const tender = tenderRef.value
          if (tender?.crmOpportunityCode) {
            await crmApi.bidInfoSync([{
              name: tender.title || tender.name || '',
              code: tender.crmOpportunityCode || '',
              status: 2, // 投标
              statusEditor: '',
              statusEditTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
              feedback: JSON.stringify({ action: 'participate', time: new Date().toISOString() }),
            }])
          }
        } catch { /* 非关键路径，静默处理 */ }
        ElMessage.success('投标成功，已生成项目立项待办')
        await loadTenderDetailFn()
      } else {
        ElMessage.warning(result?.data?.msg || '投标失败')
      }
    } catch (e) {
      ElMessage.error(e?.response?.data?.msg || '投标失败')
    }
  }

  const handleAbandon = async () => {
    if (!tenderRef.value) return
    try {
      const { value: reason } = await ElMessageBox.prompt(
        '请填写弃标原因（必填）',
        '弃标确认',
        {
          confirmButtonText: '确认弃标', cancelButtonText: '取消', inputType: 'textarea',
          inputPlaceholder: '请输入弃标原因...', inputErrorMessage: '弃标原因不能为空',
          distinguishCancelAndClose: true,
        }
      )
      if (!reason?.trim()) { ElMessage.warning('弃标原因不能为空'); return }
      const result = await tendersApi.abandon(tenderRef.value.id, { reason: reason.trim() })
      if (result?.success && result?.data?.accepted) {
        // CRM标讯回传：弃标
        try {
          const tender = tenderRef.value
          if (tender?.crmOpportunityCode) {
            await crmApi.bidInfoSync([{
              name: tender.title || tender.name || '',
              code: tender.crmOpportunityCode || '',
              status: 1, // 弃标
              statusEditor: '',
              statusEditTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
              feedback: JSON.stringify({ action: 'abandon', reason: reason?.trim() || '', time: new Date().toISOString() }),
            }])
          }
        } catch { /* 非关键路径，静默处理 */ }
        ElMessage.success(result.data.msg || '已放弃该标讯')
        await loadTenderDetailFn()
      } else {
        ElMessage.warning(result?.data?.msg || '弃标失败')
      }
    } catch (e) {
      if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.response?.data?.msg || '弃标失败')
    }
  }

  const handleViewOriginal = () => {
    const url = safeTenderUrl(tenderRef.value?.originalUrl)
    if (url) {
      window.open(url, '_blank', 'noopener,noreferrer')
    } else {
      ElMessage.warning('该标讯暂无官网公告链接')
    }
  }

  return { handleParticipate, handleAbandon, handleViewOriginal }
}
