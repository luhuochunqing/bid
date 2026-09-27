import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useScoreExtraction } from './useScoreExtraction.js'
import { scoreParseApi } from '@/api/modules/scoreParse.js'

vi.mock('@/api/modules/scoreParse.js', () => ({
  scoreParseApi: {
    getLatestExtraction: vi.fn(), getExtraction: vi.fn(), getItems: vi.fn(), getResults: vi.fn(),
    activateExtraction: vi.fn(),
  },
}))

const latest = (fingerprint = 'v1') => ({ data: {
  taskId: 'latest', taskStatus: 'COMPLETED', payloadVersion: 1, snapshotStatus: 'FROZEN',
  active: { taskId: 'latest', fingerprint },
} })

describe('useScoreExtraction', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    scoreParseApi.getLatestExtraction.mockResolvedValue(latest())
    scoreParseApi.getItems.mockResolvedValue({ data: { items: [] } })
    scoreParseApi.getResults.mockResolvedValue({ data: { results: [] } })
  })

  it('publishes only a stable read bundle', async () => {
    const refresh = vi.fn()
    const extraction = useScoreExtraction({ projectId: 1382 }, refresh)
    const bundle = await extraction.readBundle()
    expect(bundle.resultsData).toEqual({ results: [] })
    expect(extraction.latestExtraction.value.taskId).toBe('latest')
    expect(extraction.activationBlocked.value).toBe(false)
  })

  it('requires a reason and selected items before activation', async () => {
    const extraction = useScoreExtraction({ projectId: 1382 }, vi.fn())
    await extraction.readBundle()
    await extraction.activateExtraction({ schemeId: 's', selectedItemIds: [], reason: '' })
    expect(scoreParseApi.activateExtraction).not.toHaveBeenCalled()
    expect(extraction.extractionWarning.value).toContain('请选择')
  })
})
