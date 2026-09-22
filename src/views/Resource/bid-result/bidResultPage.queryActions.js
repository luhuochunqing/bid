// Input: bid result page refs for query-side state
// Output: loading, sync, fetch, reminder, and report actions
// Pos: src/views/Resource/bid-result/ - Bid result page query actions
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ElMessage } from 'element-plus'

import { bidResultsApi, projectsApi } from '@/api'

export function createBidResultPageQueryActions(context) {
  const {
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
  } = context

  const loadOverview = async () => {
    const result = await bidResultsApi.getOverview()
    if (!result?.success) throw new Error(result?.msg || '加载概览失败')
    overview.value = result.data
  }

  const loadFetchResults = async () => {
    const result = await bidResultsApi.getFetchResults()
    if (!result?.success) throw new Error(result?.msg || '加载外部结果失败')
    fetchResults.value = result.data
  }

  const loadReminders = async () => {
    const result = await bidResultsApi.getReminders()
    if (!result?.success) throw new Error(result?.msg || '加载提醒记录失败')
    reminderRecords.value = result.data
  }

  const loadProjects = async () => {
    const result = await projectsApi.getList()
    projects.value = result?.success ? (result.data || []) : []
  }

  const loadPage = async () => {
    pageLoading.value = true
    try {
      await Promise.all([loadOverview(), loadFetchResults(), loadReminders(), loadProjects()])
    } catch (error) {
      ElMessage.error(error?.message || '加载投标结果闭环失败')
    } finally {
      pageLoading.value = false
    }
  }

  const syncInternal = async () => {
    syncing.value = true
    try {
      const result = await bidResultsApi.sync()
      if (!result?.success) throw new Error(result?.msg || '同步失败')
      ElMessage.success(result?.data?.msg || '同步完成')
      await loadPage()
    } catch (error) {
      ElMessage.error(error?.message || '同步失败')
    } finally {
      syncing.value = false
    }
  }

  const fetchPublic = async () => {
    fetching.value = true
    try {
      const result = await bidResultsApi.fetch()
      if (!result?.success) throw new Error(result?.msg || '同步失败')
      ElMessage.success(result?.data?.msg || '同步完成')
      await loadPage()
    } catch (error) {
      ElMessage.error(error?.message || '同步失败')
    } finally {
      fetching.value = false
    }
  }

  const sendReminderAgain = async (row) => {
    if (!row?.lastResultId) return
    try {
      const result = await bidResultsApi.sendReminder(row.lastResultId, '请及时补齐结果资料')
      if (!result?.success) throw new Error(result?.msg || '发送提醒失败')
      ElMessage.success('提醒已发送')
      await loadPage()
    } catch (error) {
      ElMessage.error(error?.message || '发送提醒失败')
    }
  }

  const sendAllReminders = async () => {
    const ids = reminderRecords.value.filter((item) => item.lastResultId).map((item) => item.lastResultId)
    if (ids.length === 0) return ElMessage.info('当前没有可提醒的结果')
    sending.value = true
    try {
      const result = await bidResultsApi.sendReminderBatch(ids, '请尽快上传结果资料')
      if (!result?.success) throw new Error(result?.msg || '批量提醒失败')
      ElMessage.success(result?.data?.msg || '批量提醒完成')
      await loadPage()
    } catch (error) {
      ElMessage.error(error?.message || '批量提醒失败')
    } finally {
      sending.value = false
    }
  }

  const showReport = async (reportVisible) => {
    reportLoading.value = true
    try {
      const result = await bidResultsApi.getCompetitorReport()
      if (!result?.success) throw new Error(result?.msg || '加载报表失败')
      competitorReport.value = result.data
      reportVisible.value = true
    } catch (error) {
      ElMessage.error(error?.message || '加载报表失败')
    } finally {
      reportLoading.value = false
    }
  }

  return {
    loadPage,
    syncInternal,
    fetchPublic,
    sendReminderAgain,
    sendAllReminders,
    showReport
  }
}
