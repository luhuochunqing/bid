// Input: project ID, reusable tender breakdown snapshot/upload lookup, and tender document file
// Output: independent project tender breakdown API requests for latest, readiness, uploaded reuse, and upload parse
// Pos: src/api/modules/ - Project tender breakdown API boundary
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'
import { apiModeFailure, demoReadonlyFailure, isDemoEntityId, isNumericId } from './projectApiGuards.js'

export async function parseTenderBreakdown(projectId, payload) {
  if (!isNumericId(projectId)) {
    return apiModeFailure('project')
  }

  if (isDemoEntityId(projectId)) {
    return demoReadonlyFailure()
  }

  // 双模式：有 file → multipart；无 file 有 fileUrl → JSON（OBS 直传）
  const formData = payload instanceof FormData ? payload : (() => {
    const fd = new FormData()
    fd.set('file', payload, payload?.name || '招标文件')
    return fd
  })()
  if (formData.get('fileUrl')) {
    return httpClient.post(`/api/projects/${projectId}/tender-breakdown`, {
      fileName: formData.get('fileName') || '招标文件',
      fileType: formData.get('fileType') || 'application/octet-stream',
      fileUrl: formData.get('fileUrl'),
    }, { timeout: 120000, silentError: true })
  }
  return httpClient.post(`/api/projects/${projectId}/tender-breakdown`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
    silentError: true,
  })
}

export async function getLatestTenderBreakdown(projectId) {
  if (!isNumericId(projectId)) {
    return apiModeFailure('project')
  }

  if (isDemoEntityId(projectId)) {
    return demoReadonlyFailure()
  }

  return httpClient.get(`/api/projects/${projectId}/tender-breakdown/latest`, { silentError: true })
}

export async function parseUploadedTenderBreakdown(projectId) {
  if (!isNumericId(projectId)) {
    return apiModeFailure('project')
  }

  if (isDemoEntityId(projectId)) {
    return demoReadonlyFailure()
  }

  return httpClient.post(`/api/projects/${projectId}/tender-breakdown/reuse-uploaded`, null, {
    timeout: 120000,
    silentError: true,
  })
}

export async function getTenderBreakdownReadiness(projectId) {
  if (!isNumericId(projectId)) {
    return apiModeFailure('project')
  }

  if (isDemoEntityId(projectId)) {
    return demoReadonlyFailure()
  }

  return httpClient.get(`/api/projects/${projectId}/tender-breakdown/readiness`, { silentError: true })
}
