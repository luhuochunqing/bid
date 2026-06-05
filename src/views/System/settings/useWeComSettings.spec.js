// Input: useWeComSettings composable with mocked weComIntegrationApi
// Output: load / save / test flow coverage with reactive state assertions
// Pos: src/views/System/settings/ - composable unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const getConfig = vi.fn()
const saveConfig = vi.fn()
const testConnection = vi.fn()

vi.mock('@/api/modules/systemIntegration', () => ({
  weComIntegrationApi: { getConfig, saveConfig, testConnection },
}))

const elMessage = {
  success: vi.fn(),
  error: vi.fn(),
}

vi.mock('element-plus', () => ({
  ElMessage: elMessage,
}))

const { useWeComSettings } = await import('./useWeComSettings.js')

function createHarness() {
  return defineComponent({
    template: '<div />',
    setup() {
      return useWeComSettings()
    },
  })
}

const serverConfig = () => ({
  corpId: 'ww1234567890',
  agentId: 1000001,
  ssoEnabled: true,
  messageEnabled: false,
  secretConfigured: true,
  updatedAt: '2026-04-24T10:00:00Z',
})

describe('useWeComSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getConfig.mockResolvedValue({ success: true, data: serverConfig() })
    saveConfig.mockResolvedValue({ success: true, data: { ...serverConfig(), updatedAt: '2026-04-24T11:00:00Z' } })
    testConnection.mockResolvedValue({ success: true, data: { success: true, msg: '连接成功', probedAt: '2026-04-24T12:00:00Z' } })
  })

  it('load() populates form state from API response', async () => {
    const wrapper = mount(createHarness())

    await wrapper.vm.load()
    await flushPromises()

    expect(getConfig).toHaveBeenCalled()
    expect(wrapper.vm.form.corpId).toBe('ww1234567890')
    expect(wrapper.vm.form.agentId).toBe(1000001)
    expect(wrapper.vm.form.ssoEnabled).toBe(true)
    expect(wrapper.vm.form.messageEnabled).toBe(false)
    expect(wrapper.vm.secretConfigured).toBe(true)
  })

  it('load() sets loading flag correctly', async () => {
    const wrapper = mount(createHarness())

    const loadPromise = wrapper.vm.load()
    expect(wrapper.vm.loading).toBe(true)

    await loadPromise
    await flushPromises()
    expect(wrapper.vm.loading).toBe(false)
  })

  it('load() shows error message on API failure', async () => {
    getConfig.mockResolvedValue({ success: false, msg: '获取企业微信配置失败' })
    const wrapper = mount(createHarness())

    await wrapper.vm.load()
    await flushPromises()

    expect(elMessage.error).toHaveBeenCalledWith('获取企业微信配置失败')
  })

  it('save() calls saveConfig with form payload, omitting corpSecret when blank', async () => {
    const wrapper = mount(createHarness())
    await wrapper.vm.load()
    await flushPromises()

    wrapper.vm.form.corpSecret = ''
    await wrapper.vm.save()
    await flushPromises()

    expect(saveConfig).toHaveBeenCalledWith({
      corpId: 'ww1234567890',
      agentId: 1000001,
      ssoEnabled: true,
      messageEnabled: false,
      notifyUserIds: '',
    })
    expect(elMessage.success).toHaveBeenCalledWith('企业微信配置已保存')
  })

  it('save() includes corpSecret in payload when non-empty', async () => {
    const wrapper = mount(createHarness())
    await wrapper.vm.load()
    await flushPromises()

    wrapper.vm.form.corpSecret = 'new-secret-abc'
    await wrapper.vm.save()
    await flushPromises()

    const sentPayload = saveConfig.mock.calls[0][0]
    expect(sentPayload.corpSecret).toBe('new-secret-abc')
  })

  it('save() updates secretConfigured from response', async () => {
    saveConfig.mockResolvedValue({ success: true, data: { ...serverConfig(), secretConfigured: true } })
    const wrapper = mount(createHarness())
    await wrapper.vm.load()
    await flushPromises()

    await wrapper.vm.save()
    await flushPromises()

    expect(wrapper.vm.secretConfigured).toBe(true)
  })

  it('save() shows error message on failure', async () => {
    saveConfig.mockResolvedValue({ success: false, msg: '保存失败，请重试' })
    const wrapper = mount(createHarness())
    await wrapper.vm.load()
    await flushPromises()

    await wrapper.vm.save()
    await flushPromises()

    expect(elMessage.error).toHaveBeenCalledWith('保存失败，请重试')
    expect(elMessage.success).not.toHaveBeenCalled()
  })

  it('save() sets saving flag correctly', async () => {
    const wrapper = mount(createHarness())
    await wrapper.vm.load()
    await flushPromises()

    const savePromise = wrapper.vm.save()
    expect(wrapper.vm.saving).toBe(true)

    await savePromise
    await flushPromises()
    expect(wrapper.vm.saving).toBe(false)
  })

  it('testConn() stores test result including success flag, message and probedAt', async () => {
    const wrapper = mount(createHarness())
    await wrapper.vm.load()
    await flushPromises()

    await wrapper.vm.testConn()
    await flushPromises()

    expect(testConnection).toHaveBeenCalled()
    expect(wrapper.vm.testResult).toMatchObject({
      success: true,
      msg: '连接成功',
      probedAt: '2026-04-24T12:00:00Z',
    })
  })

  it('testConn() stores failure result without throwing', async () => {
    testConnection.mockResolvedValue({ success: true, data: { success: false, msg: '认证失败', probedAt: '2026-04-24T12:05:00Z' } })
    const wrapper = mount(createHarness())

    await wrapper.vm.testConn()
    await flushPromises()

    expect(wrapper.vm.testResult.success).toBe(false)
    expect(wrapper.vm.testResult.msg).toBe('认证失败')
  })

  it('testConn() sets testing flag correctly', async () => {
    const wrapper = mount(createHarness())

    const testPromise = wrapper.vm.testConn()
    expect(wrapper.vm.testing).toBe(true)

    await testPromise
    await flushPromises()
    expect(wrapper.vm.testing).toBe(false)
  })

  it('testConn() captures network-level error as failure result without throwing', async () => {
    testConnection.mockRejectedValue(new Error('Network Error'))
    const wrapper = mount(createHarness())

    await wrapper.vm.testConn()
    await flushPromises()

    expect(wrapper.vm.testResult).toMatchObject({
      success: false,
      message: 'Network Error',
    })
    expect(wrapper.vm.testing).toBe(false)
  })

  it('does not mutate API response — form state is a copy', async () => {
    const responseData = serverConfig()
    getConfig.mockResolvedValue({ success: true, data: responseData })
    const wrapper = mount(createHarness())
    await wrapper.vm.load()
    await flushPromises()

    wrapper.vm.form.corpId = 'MODIFIED'

    expect(responseData.corpId).toBe('ww1234567890')
  })
})
