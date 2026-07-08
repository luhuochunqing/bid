// Input: project ID, document CRUD and download operations
// Output: independent project document API requests for list, upload, delete, and download URL
// Pos: src/api/modules/ - Project document API boundary
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'
import { apiModeFailure, demoReadonlyFailure, isDemoEntityId, isNumericId } from './projectApiGuards.js'

export async function getDocuments(projectId, params = {}) {
  if (!isNumericId(projectId)) {
    return apiModeFailure('project')
  }

  if (isDemoEntityId(projectId)) {
    return { success: true, data: [] }
  }

  return httpClient.get(`/api/projects/${projectId}/documents`, { params })
}

export async function uploadDocument(projectId, formData) {
  if (!isNumericId(projectId)) return apiModeFailure('project')
  if (isDemoEntityId(projectId)) return demoReadonlyFailure()
  if (formData.get('file')) {
    return httpClient.post(`/api/projects/${projectId}/documents`, formData, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 })
  }

  return httpClient.post(`/api/projects/${projectId}/documents`, {
    name: formData.get('name') || formData.get('file')?.name || '项目文档',
    size: formData.get('size') || '1MB',
    fileType: formData.get('fileType') || formData.get('file')?.type || 'application/octet-stream',
    fileUrl: formData.get('fileUrl') || null,
    documentCategory: formData.get('documentCategory') || null,
    linkedEntityType: formData.get('linkedEntityType') || null,
    linkedEntityId: formData.get('linkedEntityId') ? Number(formData.get('linkedEntityId')) : null,
    uploaderId: formData.get('uploaderId') ? Number(formData.get('uploaderId')) : null,
    uploaderName: formData.get('uploaderName') || '' })
}

export async function deleteDocument(projectId, documentId) {
  if (!isNumericId(projectId) || !isNumericId(documentId)) {
    return apiModeFailure('document')
  }

  if (isDemoEntityId(projectId) || isDemoEntityId(documentId)) {
    return demoReadonlyFailure()
  }

  return httpClient.delete(`/api/projects/${projectId}/documents/${documentId}`)
}

export function getDocumentDownloadUrl(projectId, documentId) {
  if (!isNumericId(projectId) || !isNumericId(documentId)) return ''
  return `/api/projects/${projectId}/documents/${documentId}/download`
}
