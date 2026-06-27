import { computed } from 'vue'
import { getTaskDeliverableDownloadUrl } from '@/api/modules/taskDeliverables.js'

/**
 * 任务交付物下载链接逻辑。
 * 负责构造交付物的后端下载 API URL，避免直接使用 doc-insight:// 协议 URL。
 *
 * @param {Object} localValue - 任务表单数据（含 deliverables 数组）
 * @returns {Object} { getDeliverableDownloadUrl } - 交付物下载链接构造函数
 */
export function useTaskDeliverableDownload(localValue) {
  /**
   * 构造单个交付物的后端下载 API URL。
   * @param {Object} deliverable - 交付物对象
   * @returns {string} 下载 API URL
   */
  function getDeliverableDownloadUrl(deliverable) {
    if (!deliverable?.id) return ''
    const projectId = deliverable.projectId || localValue.projectId
    const taskId = deliverable.taskId || localValue.id
    return getTaskDeliverableDownloadUrl(projectId, taskId, deliverable.id)
  }

  return {
    getDeliverableDownloadUrl,
  }
}
