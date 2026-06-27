import { ElMessage } from 'element-plus'
import { getTaskDeliverableDownloadUrl } from '@/api/modules/taskDeliverables.js'
import { downloadWithFilename } from '@/utils/download.js'

/**
 * 任务交付物/附件下载链接逻辑。
 * 负责构造后端下载 API URL，避免直接使用 doc-insight:// 协议 URL。
 *
 * @param {Object} localValue - 任务表单数据（含 deliverables/attachments 数组）
 * @returns {Object} { getDeliverableDownloadUrl, downloadDeliverable, downloadAttachment }
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

  /**
   * 通过 axios 下载交付物文件，携带认证 token 并处理 403/401 错误。
   * 避免使用 <a :href> 直接导航导致会话过期时显示空白页。
   * @param {Object} deliverable - 交付物对象
   */
  async function downloadDeliverable(deliverable) {
    const url = getDeliverableDownloadUrl(deliverable)
    if (!url) {
      ElMessage.info('文件地址不可用')
      return
    }
    await downloadWithFilename(url, deliverable?.name || 'download')
  }

  /**
   * 通过 axios 下载任务附件文件（project_documents 表中的 TASK_ATTACHMENT 记录）。
   * @param {Object} file - 附件对象（含 id、projectId、name）
   */
  function downloadAttachment(file) {
    if (!file?.id) {
      ElMessage.warning('文件信息缺失，无法下载')
      return
    }
    const projectId = file?.projectId || localValue.projectId
    downloadWithFilename(`/api/projects/${projectId}/documents/${file.id}/download`, file?.name || '附件')
  }

  return {
    getDeliverableDownloadUrl,
    downloadDeliverable,
    downloadAttachment,
  }
}
