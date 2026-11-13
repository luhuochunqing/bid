// Input: real extraction/items/results endpoints and drawer refresh callback
// Output: coherent read bundle and explicit full-replacement CAS activation
// Pos: projectDetail presentation; reads may retry, writes never retry
import { ref, computed } from 'vue'
import { scoreParseApi } from '@/api/modules/scoreParse.js'

const messageOf = error => error?.response?.data?.msg || error?.message || '提取结果读取失败'
async function latest(projectId) {
  try { return (await scoreParseApi.getLatestExtraction(projectId))?.data || null }
  catch (error) {
    const status = error?.response?.status
    const code = messageOf(error)
    if ((status === 409 && code === 'EXTRACTION_FEATURE_DISABLED')
      || (status === 404 && ['EXTRACTION_TASK_NOT_FOUND', 'Not Found'].includes(code))) return null
    throw error
  }
}

export function useScoreExtraction(props, refresh) {
  const latestExtraction = ref(null)
  const activeSource = ref(null)
  const extractionWarning = ref('')
  const activationBlocked = ref(true)
  const activating = ref(false)
  const activeDeclaredTotal = computed(() => {
    const source = activeSource.value
    if (!source || source.taskId !== latestExtraction.value?.active?.taskId) return null
    const scheme = source.schemes?.find(s => s.activeCount > 0)
    return scheme?.declaredTotalStatus === 'KNOWN' ? scheme.declaredTotal : null
  })

  async function readBundle(isCurrent = () => true) {
    activationBlocked.value = true
    for (let attempt = 0; attempt < 3; attempt++) {
      let before
      try { before = await latest(props.projectId) }
      catch (error) {
        // Without a token only the single /items response is publishable. Never combine unguarded results.
        if (latestExtraction.value || error?.response?.status === 403) throw error
        const itemsResponse = await scoreParseApi.getItems(props.projectId)
        return { itemsResponse, resultsData: { results: [] }, readError: messageOf(error) }
      }
      if (!isCurrent()) return
      const [itemsResponse, resultsResponse] = await Promise.all([
        scoreParseApi.getItems(props.projectId), scoreParseApi.getResults(props.projectId),
      ])
      if (!isCurrent()) return
      if (!before) {
        latestExtraction.value = null
        activeSource.value = null
        extractionWarning.value = ''
        return { itemsResponse, resultsData: resultsResponse?.data }
      }
      let source = before, readError = ''
      if (before.active?.taskId && before.active.taskId !== before.taskId) {
        try { source = (await scoreParseApi.getExtraction(props.projectId, before.active.taskId))?.data }
        catch (error) {
          if ([401, 403].includes(error?.response?.status)) throw error
          // The source declaration is optional; active items/results still require the after-token check.
          source = null
          readError = messageOf(error)
        }
      }
      const after = await latest(props.projectId)
      if (!isCurrent()) return
      if (before.active?.fingerprint !== after?.active?.fingerprint || before.taskId !== after?.taskId
        || (source && source.active?.fingerprint !== before.active?.fingerprint)) continue
      latestExtraction.value = after
      activeSource.value = source
      extractionWarning.value = ''
      activationBlocked.value = Boolean(readError) || !after.active?.fingerprint || after.snapshotStatus !== 'FROZEN' || after.taskStatus !== 'COMPLETED' || after.payloadVersion === 0
      return { itemsResponse, resultsData: resultsResponse?.data, readError }
    }
    if (!isCurrent()) return
    extractionWarning.value = '生效版本正在变化，已保留上次一致结果。请刷新后再切换。'
    throw new Error(extractionWarning.value)
  }

  async function activateExtraction(selection) {
    if (activationBlocked.value || activating.value) return
    const snapshot = latestExtraction.value
    if (!selection.reason?.trim() || !selection.selectedItemIds?.length) {
      extractionWarning.value = '请选择计分项并填写切换原因。'
      return
    }
    activating.value = true
    try {
      await scoreParseApi.activateExtraction(props.projectId, snapshot.taskId, {
        schemeId: selection.schemeId, selectedItemIds: [...selection.selectedItemIds],
        reason: selection.reason.trim(), expectedActiveFingerprint: snapshot.active.fingerprint,
      })
      activationBlocked.value = true
      await refresh({ autoScore: false, autoParse: false })
    } catch (error) {
      activationBlocked.value = true
      extractionWarning.value = error?.response?.status === 409
        ? '生效版本或候选状态已变化，请刷新后重新核对差异并提交。'
        : `切换失败：${messageOf(error)}。请刷新后重试。`
    } finally { activating.value = false }
  }
  return { latestExtraction, activeDeclaredTotal, extractionWarning, activationBlocked, activating,
    readBundle, activateExtraction, refreshExtraction: () => refresh({ autoScore: false, autoParse: false }) }
}
