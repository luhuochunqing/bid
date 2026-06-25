import { projectsApi } from '@/api/modules/projects.js'

/**
 * 提交任务审核 — 封装交付物上传、完成说明保存、状态更新三条 API 调用
 *
 * 供给两个调用方：
 * - TaskBoardCard.confirmSubmit — 卡片内"提交"按钮
 * - TaskBoardPage.handleSubmitForReview — 抽屉内"提交审核"按钮
 */
export function useTaskSubmitReview() {
  /**
   * @param {Object} options
   * @param {number|string} options.projectId
   * @param {number|string} options.taskId
   * @param {Array}  [options.deliverableFiles=[]] - 待上传的交付物（含 .raw 属性）
   * @param {string} [options.completionNote]       - 完成情况说明
   */
  async function submitForReview({ projectId, taskId, deliverableFiles = [], completionNote } = {}) {
    // 上传新增交付物
    for (const file of deliverableFiles) {
      if (file?.raw) {
        const formData = new FormData()
        formData.append('file', file.raw)
        formData.append('taskId', taskId)
        await projectsApi.createTaskDeliverable(projectId, taskId, formData)
      }
    }

    // 保存完成情况说明
    if (completionNote) {
      await projectsApi.updateTask(taskId, { completionNotes: completionNote })
    }

    // 更新状态为 REVIEW
    await projectsApi.updateTaskStatus(projectId, taskId, 'REVIEW')
  }

  return { submitForReview }
}