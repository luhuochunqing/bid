/**
 * v-autosize 指令
 *
 * 用于 readonly textarea：根据内容自动调整高度，避免内容少时大片空白。
 * 保留 CSS 中 min-height 作为最小高度兜底，超过时按内容撑开。
 *
 * 用法：
 *   <textarea v-autosize readonly value="..." class="readonly-textarea" />
 *
 * 设计要点：
 * - 仅在 mounted/updated 时同步计算，无事件监听开销
 * - 先 height='auto' 重置再读 scrollHeight，确保 shrink 也生效
 * - 异步数据由 Vue 的 updated 钩子接管（prop 变化触发 re-render → updated 重新计算）
 * - 不接管 overflow/resize，与 .readonly-textarea 现有样式兼容
 */

function autosize(el) {
  if (!(el instanceof HTMLTextAreaElement)) return
  el.style.height = 'auto'
  el.style.height = el.scrollHeight + 'px'
}

export const vAutosize = {
  mounted(el) {
    autosize(el)
  },
  updated(el) {
    autosize(el)
  }
}
