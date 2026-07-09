// Input: 无
// Output: { isDeleting, safeDelete } — 防止同一 documentId 重复发 DELETE 请求
// Pos: src/composables/ - 删除按钮防重复点击守卫
// 修复：DELETE /documents/{id} 在网络延迟内被多次点击导致 404（后端已删，再发请求）

import { ref } from 'vue'

/**
 * 防止同一 ID 的删除请求被重复触发。
 *
 * 场景：用户点击"删除"按钮 → await deleteDocument() 期间（1-2s 网络延迟）
 * 按钮没有 disabled，用户可以连续点击，每次都用同一个 documentId 发 DELETE，
 * 导致后端返回 404（文档已删）。
 *
 * 用法：
 *   const { isDeleting, safeDelete } = useDeleteGuard()
 *   async function handleRemove(file) {
 *     const documentId = file.response?.data?.id
 *     const ok = await safeDelete(documentId, async () => {
 *       await deleteDocument(props.projectId, documentId)
 *       bidFiles.value = bidFiles.value.filter(f => f !== file)
 *       ElMessage.success('已删除')
 *     })
 *     if (!ok) return  // 正在删除中或 documentId 无效
 *   }
 *
 * @returns {{ isDeleting: import('vue').Ref<boolean>, safeDelete: Function }}
 */
export function useDeleteGuard() {
  const deletingIds = ref(new Set())
  const isDeleting = ref(false)

  /**
   * 安全删除：基于 idKey 去重，相同 idKey 在删除期间不会重复触发。
   * deleteFn 抛错时不会向上传播（由 deleteFn 自己负责错误提示），
   * 避免在 Vue 事件处理中产生 unhandled rejection。
   *
   * @param {string|number} idKey - 用于去重的唯一标识（通常是 documentId）
   * @param {Function} deleteFn - 实际执行删除的异步函数（自行处理错误 UI）
   * @returns {Promise<boolean>} true=执行成功，false=跳过（重复点击/idKey 无效/执行失败）
   */
  async function safeDelete(idKey, deleteFn) {
    if (idKey == null) return false
    if (deletingIds.value.has(idKey)) return false

    deletingIds.value.add(idKey)
    isDeleting.value = true
    try {
      await deleteFn()
      return true
    } catch {
      // deleteFn 自行处理错误 UI（如 ElMessage.error），此处仅释放锁
      return false
    } finally {
      deletingIds.value.delete(idKey)
      isDeleting.value = false
    }
  }

  return { isDeleting, safeDelete }
}
