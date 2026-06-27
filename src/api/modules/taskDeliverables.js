// Input: task deliverable API operations
// Output: deliverable CRUD and download API requests
// Pos: src/api/modules/ - Task deliverable API boundary
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import httpClient from '../client.js'
import { apiModeFailure, isDemoEntityId, isNumericId } from './projectApiGuards.js'

export async function getTaskDeliverables(projectId, taskId) {
  if (!isNumericId(projectId) || !isNumericId(taskId)) {
    return apiModeFailure('task')
  }
  return httpClient.get(`/api/projects/${projectId}/tasks/${taskId}/deliverables`)
}

export async function createTaskDeliverable(projectId, taskId, data) {
  if (!isNumericId(projectId) || !isNumericId(taskId)) {
    return apiModeFailure('task')
  }
  return httpClient.post(`/api/projects/${projectId}/tasks/${taskId}/deliverables`, data)
}

export async function deleteTaskDeliverable(projectId, taskId, deliverableId) {
  if (!isNumericId(projectId) || !isNumericId(taskId) || !isNumericId(deliverableId)) {
    return apiModeFailure('deliverable')
  }
  if (isDemoEntityId(projectId) || isDemoEntityId(deliverableId)) {
    return { success: true, data: null }
  }
  return httpClient.delete(`/api/projects/${projectId}/tasks/${taskId}/deliverables/${deliverableId}`)
}

export async function getDeliverableCoverage(projectId, taskId) {
  if (!isNumericId(projectId) || !isNumericId(taskId)) {
    return apiModeFailure('task')
  }
  return httpClient.get(`/api/projects/${projectId}/tasks/${taskId}/deliverables/coverage`)
}

export function getTaskDeliverableDownloadUrl(projectId, taskId, deliverableId) {
  if (!isNumericId(projectId) || !isNumericId(taskId) || !isNumericId(deliverableId)) return ''
  return `/api/projects/${projectId}/tasks/${taskId}/deliverables/${deliverableId}/download`
}
