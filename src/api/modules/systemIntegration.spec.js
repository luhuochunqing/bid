// Input: systemIntegration API module with mocked HTTP client
// Output: endpoint and payload coverage for WeChat Work and organization integrations
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}))

import httpClient from '@/api/client'
import { organizationIntegrationApi, weComIntegrationApi } from './systemIntegration.js'

describe('weComIntegrationApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getConfig(): calls GET endpoint and returns normalized response', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: {
        corpId: 'ww1234567890',
        agentId: 1000001,
        ssoEnabled: true,
        messageEnabled: false,
        secretConfigured: true,
        updatedAt: '2026-04-24T10:00:00Z',
      },
    })

    const result = await weComIntegrationApi.getConfig()

    expect(httpClient.get).toHaveBeenCalledWith('/api/admin/integrations/wecom')
    expect(result).toMatchObject({
      success: true,
      data: {
        corpId: 'ww1234567890',
        agentId: 1000001,
        ssoEnabled: true,
        messageEnabled: false,
        secretConfigured: true,
        updatedAt: '2026-04-24T10:00:00Z',
      },
    })
  })

  it('getConfig(): handles missing optional fields gracefully', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: { corpId: '', agentId: null, ssoEnabled: false, messageEnabled: false, secretConfigured: false, updatedAt: null },
    })

    const result = await weComIntegrationApi.getConfig()

    expect(result.data.secretConfigured).toBe(false)
    expect(result.data.updatedAt).toBeNull()
  })

  it('saveConfig(): forwards payload to PUT endpoint unchanged', async () => {
    httpClient.put.mockResolvedValue({
      success: true,
      data: {
        corpId: 'ww9999',
        agentId: 2000001,
        ssoEnabled: false,
        messageEnabled: true,
        secretConfigured: true,
        updatedAt: '2026-04-24T11:00:00Z',
      },
    })

    const payload = {
      corpId: 'ww9999',
      agentId: 2000001,
      corpSecret: 'new-secret-value',
      ssoEnabled: false,
      messageEnabled: true,
    }

    const result = await weComIntegrationApi.saveConfig(payload)

    expect(httpClient.put).toHaveBeenCalledWith('/api/admin/integrations/wecom', payload)
    expect(result.data.secretConfigured).toBe(true)
  })

  it('saveConfig(): forwards payload as-is without re-stripping corpSecret', async () => {
    httpClient.put.mockResolvedValue({ success: true, data: { corpId: 'ww9999', agentId: 1, ssoEnabled: false, messageEnabled: false, secretConfigured: true, updatedAt: null } })

    const payload = {
      corpId: 'ww9999',
      agentId: 1,
      ssoEnabled: false,
      messageEnabled: false,
    }

    await weComIntegrationApi.saveConfig(payload)

    expect(httpClient.put).toHaveBeenCalledWith('/api/admin/integrations/wecom', payload)
  })

  it('testConnection(): calls POST /test and returns test result', async () => {
    httpClient.post.mockResolvedValue({
      success: true,
      data: {
        success: true,
        message: '连接成功',
        probedAt: '2026-04-24T12:00:00Z',
      },
    })

    const result = await weComIntegrationApi.testConnection()

    expect(httpClient.post).toHaveBeenCalledWith('/api/admin/integrations/wecom/test')
    expect(result).toMatchObject({
      success: true,
      data: {
        success: true,
        message: '连接成功',
        probedAt: '2026-04-24T12:00:00Z',
      },
    })
  })

  it('testConnection(): returns failure result when connection fails', async () => {
    httpClient.post.mockResolvedValue({
      success: true,
      data: {
        success: false,
        message: '认证失败，请检查 CorpID 和 Secret',
        probedAt: '2026-04-24T12:05:00Z',
      },
    })

    const result = await weComIntegrationApi.testConnection()

    expect(result.data.success).toBe(false)
    expect(result.data.message).toBe('认证失败，请检查 CorpID 和 Secret')
  })
})

describe('organizationIntegrationApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getOperationsStatus(): calls real organization status endpoint', async () => {
    httpClient.get.mockResolvedValue({ enabled: true, pendingRetryCount: 0 })

    const result = await organizationIntegrationApi.getOperationsStatus()

    expect(httpClient.get).toHaveBeenCalledWith('/api/integrations/organization/operations/status')
    expect(result.enabled).toBe(true)
  })

  it('startSyncRun(): posts reconciliation payload unchanged', async () => {
    const payload = { sourceApp: 'oss', runType: 'RECONCILIATION' }
    httpClient.post.mockResolvedValue({ runId: 1 })

    await organizationIntegrationApi.startSyncRun(payload)

    expect(httpClient.post).toHaveBeenCalledWith('/api/integrations/organization/sync-runs', payload)
  })

  it('resyncUser(): posts user id path', async () => {
    httpClient.post.mockResolvedValue({ runId: 2 })

    await organizationIntegrationApi.resyncUser('10001')

    expect(httpClient.post).toHaveBeenCalledWith('/api/integrations/organization/resync/users/10001')
  })

  it('resyncDepartment(): posts department id path', async () => {
    httpClient.post.mockResolvedValue({ runId: 3 })

    await organizationIntegrationApi.resyncDepartment('D001')

    expect(httpClient.post).toHaveBeenCalledWith('/api/integrations/organization/resync/departments/D001')
  })

  it('replayDeadLetter(): posts encoded event key path', async () => {
    httpClient.post.mockResolvedValue({ code: '200' })

    await organizationIntegrationApi.replayDeadLetter('event key')

    expect(httpClient.post)
      .toHaveBeenCalledWith('/api/integrations/organization/operations/dead-letters/event%20key/replay')
  })
})
