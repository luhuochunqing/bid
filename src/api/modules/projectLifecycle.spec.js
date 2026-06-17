// Input: projectLifecycleApi with mocked HTTP client
// Output: 6-stage tender lifecycle endpoint pass-through coverage
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  },
}))

import httpClient from '@/api/client'
import { projectLifecycleApi } from './projectLifecycle.js'

describe('projectLifecycleApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('getStage()', async () => {
    httpClient.get.mockResolvedValue({ success: true })
    await projectLifecycleApi.getStage(7)
    expect(httpClient.get).toHaveBeenCalledWith('/api/projects/7/stage')
  })

  it('initiation get/submit/update', async () => {
    httpClient.get.mockResolvedValue({ success: true })
    httpClient.post.mockResolvedValue({ success: true })
    httpClient.patch.mockResolvedValue({ success: true })

    await projectLifecycleApi.getInitiation(1)
    await projectLifecycleApi.submitInitiation(1, { ownerUnit: 'A' })
    await projectLifecycleApi.updateInitiation(1, { ownerUnit: 'B' })

    expect(httpClient.get).toHaveBeenCalledWith('/api/projects/1/initiation', { skipGlobalErrorMessage: true })
    expect(httpClient.post).toHaveBeenCalledWith('/api/projects/1/initiation', { ownerUnit: 'A' })
    expect(httpClient.patch).toHaveBeenCalledWith('/api/projects/1/initiation', { ownerUnit: 'B' })
  })

  it('drafting leads + advance', async () => {
    httpClient.patch.mockResolvedValue({ success: true })
    httpClient.post.mockResolvedValue({ success: true })

    await projectLifecycleApi.assignDraftingLeads(2, { primary: 9, secondary: 10 })
    await projectLifecycleApi.advanceDrafting(2)

    expect(httpClient.patch).toHaveBeenCalledWith('/api/projects/2/drafting/leads', {
      primary: 9,
      secondary: 10,
    })
    expect(httpClient.post).toHaveBeenCalledWith('/api/projects/2/drafting/advance', {})
  })

  it('evaluation sub-stage + evidence', async () => {
    httpClient.patch.mockResolvedValue({ success: true })
    httpClient.post.mockResolvedValue({ success: true })

    await projectLifecycleApi.transitionEvaluationSubStage(3, { target: 'AWAITING_BOARD' })
    await projectLifecycleApi.attachEvaluationEvidence(3, { documentId: 100 })

    expect(httpClient.patch).toHaveBeenCalledWith('/api/projects/3/evaluation/sub-stage', {
      target: 'AWAITING_BOARD',
    })
    expect(httpClient.post).toHaveBeenCalledWith('/api/projects/3/evaluation/evidence', {
      documentId: 100,
    })
  })

  it('result register + retrospective submit', async () => {
    httpClient.post.mockResolvedValue({ success: true })

    await projectLifecycleApi.registerResult(4, { resultType: 'WON' })
    await projectLifecycleApi.submitRetrospective(4, { summary: 'ok' })

    expect(httpClient.post).toHaveBeenCalledWith('/api/projects/4/result', { resultType: 'WON' })
    expect(httpClient.post).toHaveBeenCalledWith('/api/projects/4/retrospective', { summary: 'ok' })
  })

  it('closure preview + submit', async () => {
    httpClient.get.mockResolvedValue({ success: true })
    httpClient.post.mockResolvedValue({ success: true })

    await projectLifecycleApi.getClosurePreview(5)
    await projectLifecycleApi.submitClosure(5, { depositReturned: true })

    expect(httpClient.get).toHaveBeenCalledWith('/api/projects/5/closure/preview')
    expect(httpClient.post).toHaveBeenCalledWith('/api/projects/5/closure', {
      depositReturned: true,
    })
  })
})
