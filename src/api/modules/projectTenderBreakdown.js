// Input: project ID, reusable tender breakdown snapshot/upload lookup, and tender document file
// Output: independent project tender breakdown API requests for latest, readiness, uploaded reuse, and upload parse
// Pos: src/api/modules/ - Project tender breakdown API boundary
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'
import { apiModeFailure, demoReadonlyFailure, isDemoEntityId, isNumericId } from './projectApiGuards.js'

export async function parseTenderBreakdown(projectId, file) {
  if (!isNumericId(projectId)) {
    return apiModeFailure('project')
  }

  if (isDemoEntityId(projectId)) {
    return demoReadonlyFailure()
  }

  const formData = new FormData()
  formData.set('file', file, file?.name || '招标文件')
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
