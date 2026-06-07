// Input: bidAgent API module with mocked HTTP client
// Output: endpoint coverage for bid writing agent run lifecycle
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

import httpClient from '@/api/client'
import { bidAgentApi, normalizeBidAgentRun } from './bidAgent.js'

describe('bidAgentApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('createRun(): posts to the project bid-agent run endpoint', async () => {
    httpClient.post.mockResolvedValue({ success: true, data: { runId: 'run-1', status: 'QUEUED' } })

    const result = await bidAgentApi.createRun(12, { mode: 'fullDraft' })

    expect(httpClient.post).toHaveBeenCalledWith('/api/projects/12/bid-agent/runs', { mode: 'fullDraft' })
    expect(result.data).toMatchObject({ id: 'run-1', runId: 'run-1', status: 'QUEUED' })
  })

  it('importTenderDocument(): uploads tender file through the bid-agent multipart endpoint', async () => {
    const formData = new FormData()
    formData.set('file', new Blob(['招标文件正文']), 'tender.docx')
    httpClient.post.mockResolvedValue({
      success: true,
      data: { document: { id: 55, snapshotId: 601 } },
    })

    const result = await bidAgentApi.importTenderDocument(12, formData)

    expect(httpClient.post).toHaveBeenCalledWith(
      '/api/projects/12/bid-agent/tender-documents',
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    )
    expect(result.data.document).toMatchObject({ id: 55, snapshotId: 601 })
  })

  it('getRun(): fetches a single backend run without local fallback', async () => {
    httpClient.get.mockResolvedValue({ success: true, data: { id: 3, state: 'COMPLETED' } })

    const result = await bidAgentApi.getRun('12', '3')

    expect(httpClient.get).toHaveBeenCalledWith('/api/projects/12/bid-agent/runs/3')
    expect(result.data).toMatchObject({ id: 3, runId: 3, status: 'COMPLETED' })
  })

  it('applyRun(): writes generated content through the apply endpoint', async () => {
    const payload = { sectionIds: ['overview'] }
    httpClient.post.mockResolvedValue({ success: true, data: { documentId: 88 } })

    await bidAgentApi.applyRun(12, 'run-1', payload)

    expect(httpClient.post).toHaveBeenCalledWith('/api/projects/12/bid-agent/runs/run-1/apply', payload)
  })

  it('createReview(): posts run-scoped review requests when runId is present', async () => {
    const payload = { runId: 'run-1', reviewerIds: [5] }
    httpClient.post.mockResolvedValue({ success: true, data: { reviewId: 9 } })

    await bidAgentApi.createReview(12, payload)

    expect(httpClient.post).toHaveBeenCalledWith('/api/projects/12/bid-agent/runs/run-1/reviews', { reviewerIds: [5] })
  })

  it('createReview(): keeps the project-level review endpoint for compatibility', async () => {
    const payload = { reviewerIds: [5] }
    httpClient.post.mockResolvedValue({ success: true, data: { reviewId: 9 } })

    await bidAgentApi.createReview(12, payload)

    expect(httpClient.post).toHaveBeenCalledWith('/api/projects/12/bid-agent/reviews', payload)
  })

  it('normalizeBidAgentRun(): maps backend artifacts and review fields into drawer data', () => {
    const run = normalizeBidAgentRun({
      id: 100,
      status: 'DRAFTED',
      artifacts: [
        { id: 200, artifactType: 'DRAFT_TEXT', title: '自动生成投标草稿', content: 'draft text' },
        { id: 201, artifactType: 'HANDOFF_CHECKLIST', title: '文档写手交接清单', content: 'checklist' },
      ],
      gapCheck: { gaps: ['材料覆盖度不足'] },
      manualConfirmation: { reasons: ['价格与报价口径需要人工复核'] },
    })

    expect(run.draft.sections).toHaveLength(2)
    expect(run.draft.sections[0]).toMatchObject({
      id: 200,
      title: '自动生成投标草稿',
      content: 'draft text',
      source: 'bid-agent-artifact:200',
    })
    expect(run.gaps).toEqual(['材料覆盖度不足'])
    expect(run.manualConfirmations).toEqual(['价格与报价口径需要人工复核'])
  })
})
