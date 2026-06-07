// Input: 审批历史 ref（approvalApi.getProjectApprovals 归一化后的数组）、标书编制流程 ref（bidProcess）
// Output: 派生"标书评审"相关状态，并在通过时同步写回 bidProcess.steps.review.completed
// Pos: src/composables/useBidReviewStatus.js
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// FP 边界说明：
//   - `isApprovedBidReview` / `isPendingBidReview` 是纯函数：仅基于入参判定，无副作用。
//   - `useBidReviewStatus` 是命令式外壳：watch 里对传入的 bidProcess ref 做原地写入，
//     刻意由此承担状态同步职责，使 Detail.vue 模板只消费派生值。
//     如需纯函数版本（例如在测试或服务端复用），直接使用上面两个纯谓词即可。

import { computed, watch } from 'vue'

export const BID_REVIEW_TYPE_CODE = 'bid_review'
export const BID_REVIEW_TYPE_NAME = '标书评审'

function recordTypeCode(record) {
  if (!record) return ''
  return record.approvalType || record.type || ''
}

function recordStatusUpper(record) {
  return String(record?.status || '').toUpperCase()
}

export function isApprovedBidReview(record) {
  return recordTypeCode(record) === BID_REVIEW_TYPE_CODE && recordStatusUpper(record) === 'APPROVED'
}

export function isPendingBidReview(record) {
  return recordTypeCode(record) === BID_REVIEW_TYPE_CODE && recordStatusUpper(record) === 'PENDING'
}

/**
 * 从审批历史派生"标书评审"相关状态。
 *
 * 只在审批链路产出 APPROVED 的 bid_review 时把 review.completed 置 true；
 * 不会把已经为 true 的状态回退为 false，避免覆盖其它来源（比如数据初始化）的标记。
 *
 * @param {import('vue').Ref<Array>} approvalHistoryRef 审批历史 ref
 * @param {import('vue').Ref<object>} bidProcessRef bidProcess ref（至少包含 steps.review）
 * @param {object} [options]
 * @param {() => string} [options.nowFn] 注入当前时间格式化函数，便于测试
 * @param {(step: string) => void} [options.onAdvance] review 刚完成时的回调（例如推进 currentStep）
 */
export function useBidReviewStatus(approvalHistoryRef, bidProcessRef, options = {}) {
  const nowFn = options.nowFn || (() => new Date().toLocaleString('zh-CN'))
  const onAdvance = options.onAdvance

  const approvedBidReview = computed(() => {
    const list = approvalHistoryRef.value
    if (!Array.isArray(list)) return null
    return list.find(isApprovedBidReview) || null
  })

  const isBidReviewApproved = computed(() => Boolean(approvedBidReview.value))

  const hasPendingBidReview = computed(() => {
    const list = approvalHistoryRef.value
    if (!Array.isArray(list)) return false
    return list.some(isPendingBidReview)
  })

  watch(
    isBidReviewApproved,
    (approved) => {
      if (!approved) return
      const bidProcess = bidProcessRef.value
      if (!bidProcess || !bidProcess.steps || !bidProcess.steps.review) return
      if (bidProcess.steps.review.completed) return
      bidProcess.steps.review.completed = true
      bidProcess.steps.review.time = bidProcess.steps.review.time || nowFn()
      if (typeof onAdvance === 'function') {
        onAdvance('review')
      }
    },
    { immediate: true },
  )

  return {
    approvedBidReview,
    isBidReviewApproved,
    hasPendingBidReview,
  }
}

export default useBidReviewStatus
