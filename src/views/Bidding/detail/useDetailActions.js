import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tendersApi } from '@/api'
import { getHeaderActions, getBottomActions } from './actionMatrix'

/**
 * useDetailActions - 标讯详情页操作按钮 Composable
 *
 * 封装 actionMatrix 的操作按钮矩阵和删除 API 逻辑，
 * 通过 injected handlers 将所有 Action 分派到父组件的实际处理函数。
 * 不依赖 router、不向外暴露 return-value 协议，调用方只需绑定 handleAction。
 *
 * @param {import('vue').Ref<object|null>} tenderRef - 标讯详情响应式引用
 * @param {import('vue').Ref<string|null>}  roleRef  - 当前用户角色
 * @param {Function} loadDetailFn - 详情刷新函数
 * @param {object}   handlers     - 从外部注入的操作处理函数
 * @param {boolean|Ref<string>} [evaluationTabActive=false] - 当前激活 tab（支持 'basic'|'evaluation' 字符串或 boolean）
 * @param {boolean|Ref<boolean>} [evaluationSubmitted=false] - 评估表是否已提交
 * @param {Function} [handlers.bid]            - 立即投标
 * @param {Function} [handlers.abandon]        - 放弃投标
 * @param {Function} [handlers.viewAnnouncement] - 查看官网公告
 * @param {Function} [handlers.assign]         - 分配
 * @param {Function} [handlers.transfer]       - 转派
 * @param {Function} [handlers.edit]           - 编辑基本信息
 * @param {Function} [handlers.editEvaluation] - 编辑评估表
 * @param {Function} [handlers.save]           - 保存编辑
 * @param {Function} [handlers.cancel]         - 取消编辑
 * @param {Function} [handlers.nextStep]       - 下一步（切换到评估表 tab）
 * @param {Function} [handlers.viewProject]    - 查看投标项目
 * @param {Function} [handlers.afterDelete]    - 删除成功后的回调（用于导航）
 */
export function useDetailActions(tenderRef, roleRef, loadDetailFn, handlers = {}, evaluationTabActive = false, evaluationSubmitted = false) {
  // 支持传入 Ref（响应式）或原始值，并做字符串→布尔转换
  const evaluationTabActiveRef = computed(() => {
    const raw = evaluationTabActive && typeof evaluationTabActive === 'object' && 'value' in evaluationTabActive
      ? evaluationTabActive.value
      : evaluationTabActive
    return raw === 'evaluation' || raw === true
  })

  const evaluationSubmittedRef = computed(() =>
    evaluationSubmitted && typeof evaluationSubmitted === 'object' && 'value' in evaluationSubmitted
      ? evaluationSubmitted.value
      : evaluationSubmitted
  )

  // ---------------------------------------------------------------------------
  // Computed: 操作按钮列表
  // ---------------------------------------------------------------------------

  const headerActions = computed(() => {
    if (!tenderRef.value || !roleRef.value) return []
    const actions = getHeaderActions(
      tenderRef.value.status,
      roleRef.value,
      Boolean(tenderRef.value.originalUrl),
    )
    // 当评估表 tab 激活且正在编辑（非管理员）时，隐藏删除按钮
    if (evaluationTabActiveRef.value) {
      return actions.filter(a => a.key !== 'delete')
    }
    return actions
  })

  const bottomActions = computed(() => {
    if (!tenderRef.value || !roleRef.value) return []
    return getBottomActions(
      tenderRef.value.status,
      roleRef.value,
      !!tenderRef.value.requiresReview,
      evaluationTabActiveRef.value,
      evaluationSubmittedRef.value
    )
  })

  // ---------------------------------------------------------------------------
  // 删除标讯（内部处理 — 弹窗确认 + API 调用 + afterDelete 回调）
  // ---------------------------------------------------------------------------

  async function handleDelete() {
    if (!tenderRef.value) return
    try {
      await ElMessageBox.confirm(
        '确认要删除此标讯吗？删除后不可恢复。',
        '确认删除',
        { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }

    try {
      await tendersApi.delete(tenderRef.value.id)
      ElMessage.success('删除成功')
      handlers.afterDelete?.()
    } catch (e) {
      ElMessage.error(e?.response?.data?.msg || '删除失败')
    }
  }

  // ---------------------------------------------------------------------------
  // 统一操作派发 — 所有 Action 在此 switch 内分发，不再返回值给调用方
  // ---------------------------------------------------------------------------

  function handleAction(actionKey) {
    switch (actionKey) {
      case 'viewAnnouncement': handlers.viewAnnouncement?.(); break
      case 'delete':           handleDelete(); break
      case 'bid':              handlers.bid?.(); break
      case 'abandon':          handlers.abandon?.(); break
      case 'assign':           handlers.assign?.(); break
      case 'transfer':         handlers.transfer?.(); break
      case 'edit':
      case 'editBasic':        handlers.edit?.(); break
      case 'editEvaluation':    handlers.editEvaluation?.(); break
      case 'save':             handlers.save?.(); break
      case 'cancel':           handlers.cancel?.(); break
      case 'nextStep':         handlers.nextStep?.(); break
      case 'prevStep':         handlers.prevStep?.(); break
      case 'submit':           handlers.submit?.(); break
      case 'viewProject':      handlers.viewProject?.(); break
      case 'reviewConfirm':   handlers.reviewConfirm?.(); break
    }
  }

  return {
    headerActions,
    bottomActions,
    handleAction,
  }
}
