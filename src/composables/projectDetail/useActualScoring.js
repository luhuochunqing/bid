// Input: drawer state and refresh callback, actual-scoring API
// Output: explicit scoring requests and cancellable GET-only task observation
// Pos: src/composables/projectDetail/ - Presentation domain composable
import { watch, getCurrentScope, onScopeDispose } from 'vue'
import { ElMessage } from 'element-plus'
import { scoreParseApi } from '@/api/modules/scoreParse.js'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import { formatTime, pollTask } from './scoreParseTask.js'
import { scoringBody, scoringSkipHint } from './scoreParseSpendGuard.js'

export function useActualScoring({ props, visible, loading, currentStage, scored, scoringOverlayVisible, scoringHint, scoreTime, scoringQuality, error: readError, refresh }) {
  let controller, generation = 0
  const stop = () => { generation++; controller?.abort(); controller = null; scoringOverlayVisible.value = false }
  watch(visible, value => { if (!value) stop() }, { flush: 'sync' })
  watch(() => props.projectId, stop, { flush: 'sync' })
  if (getCurrentScope()) onScopeDispose(stop)

  function showFailure(error, { submission = false } = {}) {
    if (error?.name === 'AbortError') return
    const message = error?.task?.errorMessage || error?.response?.data?.msg || error?.message || ''
    if (error?.task?.errorCode === 'SCORING_REQUIRES_ALL' || message.includes('SCORING_REQUIRES_ALL')) {
      scoringHint.value = '原件已变化或来源未知，请点击「重新打分」全量重新评分；本次局部请求未扩大范围'
      ElMessage.warning(scoringHint.value)
    } else if (error?.backendFailed) {
      scoringHint.value = error?.task?.taskId ? `评分任务 ${error.task.taskId}：${message || '后台评分失败'}` : (message || '后台评分失败')
      notifyErrorUnlessRateLimit(error, '后台评分失败')
    } else if (submission && [400, 409].includes(error?.response?.status)) {
      scoringHint.value = `评分未提交：${message || '请求被拒绝，请检查评分前提'}`
      ElMessage.warning(scoringHint.value)
    } else {
      scoringHint.value = '暂未获取结果，可稍后查看；关闭抽屉不影响后台任务'
      ElMessage.info(scoringHint.value)
    }
  }

  async function observe(taskId, { notify = false } = {}) {
    const active = controller = new AbortController()
    scoringOverlayVisible.value = true
    let done, failure
    try {
      done = await pollTask(() => scoreParseApi.getScoringStatus(props.projectId), '打分', {
        taskId, signal: active.signal,
        onTaskChanged: next => { scoringHint.value = `已切换观察最新评分任务 ${next}，原请求完成情况请稍后核对` },
      })
    } catch (error) { failure = error }
    if (active.signal.aborted) return
    try {
      if (done?.completedAt) scoreTime.value = formatTime(done.completedAt)
      await refresh({ isCurrent: () => controller === active && !active.signal.aborted })
      if (active.signal.aborted) return
      if (done?.observedReplacement) scoringHint.value = `已切换观察最新评分任务 ${done.taskId}，原请求完成情况请稍后核对`
      if (failure) showFailure(failure)
      else if (readError.value) showFailure(new Error('评分结果读取失败'))
      else if (notify && !done?.observedReplacement) {
        if (scoringQuality.value.success) ElMessage.success('AI 实际打分完成')
        else ElMessage.info(scoringQuality.value.text)
      }
    } finally {
      if (controller === active) { controller = null; scoringOverlayVisible.value = false }
    }
  }

  async function restoreObservation() {
    if (!visible.value || currentStage.value !== 2 || scoringOverlayVisible.value) return
    const session = generation, projectId = props.projectId
    try {
      const task = (await scoreParseApi.getScoringStatus(projectId))?.data
      if (!visible.value || session !== generation || scoringOverlayVisible.value) return true
      if (task?.status === 'PENDING' || task?.status === 'PROCESSING') {
        scoringHint.value = `正在观察评分任务 ${task.taskId}`
        void observe(task.taskId).catch(showFailure)
      } else if (task?.status === 'FAILED') showFailure({ task, backendFailed: true, message: task.errorMessage })
      return task != null
    } catch (error) { if (session === generation && visible.value) showFailure(error); return true }
  }

  async function runScoring(options = {}) {
    const isAuto = typeof options === 'boolean' ? options : !!options?.auto
    if ((loading.value && !isAuto) || scoringOverlayVisible.value) return
    if (currentStage.value !== 2) { ElMessage.warning('请先进入阶段 2（上传投标文件后）'); return }
    const session = generation
    scoringOverlayVisible.value = true
    try {
      if (typeof options.runner === 'function') {
        await options.runner()
        scored.value = true
        ElMessage.success(isAuto ? 'AI 自动打分完成' : 'AI 实际打分完成')
        return
      }
      const triggered = await scoreParseApi.triggerScoring(props.projectId, scoringBody({
        source: isAuto ? 'AUTO' : 'MANUAL', scope: options.scope || 'ALL', itemIds: options.itemIds || [],
      }))
      if (!visible.value || session !== generation) return
      const skipHint = scoringSkipHint(triggered?.data)
      if (skipHint) scoringHint.value = skipHint
      if (triggered?.data?.outcome === 'SKIPPED') {
        await refresh({ isCurrent: () => visible.value && session === generation })
        if (!visible.value || session !== generation) return
        ElMessage.info(skipHint || '文件未变化')
      } else if (triggered?.data?.taskId != null && visible.value) {
        await observe(triggered.data.taskId, { notify: true })
      } else if (visible.value) showFailure(new Error('评分任务状态未知'))
    } catch (error) { if (visible.value && session === generation) showFailure(error, { submission: true }) }
    finally { if (session === generation && !controller) scoringOverlayVisible.value = false }
  }
  return { runScoring, restoreObservation }
}
