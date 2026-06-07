// Input: CRM 集成配置卡片 — form, save, test connection
// Output: coverage for CrmIntegrationCard.vue form rendering and interactions
// Pos: src/views/System/settings/ — component tests

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import CrmIntegrationCard from '../integration/CrmIntegrationCard.vue'

describe('CrmIntegrationCard', () => {
  const stubs = {
    'el-button': { template: '<button><slot /></button>' },
    'el-form': { template: '<form><slot /></form>' },
    'el-form-item': { template: '<div><label /><slot /></div>' },
    'el-input': { template: '<input />' },
  }

  it('renders form fields', () => {
    const wrapper = mount(CrmIntegrationCard, {
      global: { stubs },
    })

    expect(wrapper.find('.card-title').text()).toBe('CRM 系统')
    expect(wrapper.find('.card-kicker').text()).toBe('CRM')
    expect(wrapper.text()).toContain('保存配置')
    expect(wrapper.text()).toContain('测试连接')
  })

  it('starts with empty form', async () => {
    const wrapper = mount(CrmIntegrationCard, {
      global: { stubs },
    })

    expect(wrapper.vm.form.baseUrl).toBe('')
    expect(wrapper.vm.form.authToken).toBe('')
    expect(wrapper.vm.form.clientId).toBe('')
  })

  it('saves config via API', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      json: () => Promise.resolve({ success: true }),
    }))

    const wrapper = mount(CrmIntegrationCard, {
      global: { stubs },
    })

    wrapper.vm.form.baseUrl = 'https://crm.example.com'
    wrapper.vm.form.authToken = 'test-token'
    wrapper.vm.form.clientId = 'client-123'

    await wrapper.vm.handleSave()
    await wrapper.vm.$nextTick()

    expect(global.fetch).toHaveBeenCalledWith(
      '/api/settings',
      expect.objectContaining({ method: 'PUT' })
    )
  })

  it('tests connection and shows result', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }))

    const wrapper = mount(CrmIntegrationCard, {
      global: { stubs },
    })

    await wrapper.vm.handleTest()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.testResult.ok).toBe(true)
  })

  it('handles connection failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 502 }))

    const wrapper = mount(CrmIntegrationCard, {
      global: { stubs },
    })

    await wrapper.vm.handleTest()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.testResult.ok).toBe(false)
    expect(wrapper.vm.testResult.message).toContain('502')
  })
})
