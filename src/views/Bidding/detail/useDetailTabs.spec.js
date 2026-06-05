import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { ref } from 'vue'

const { useDetailTabs } = await import('./useDetailTabs.js')

function createHarness(tenderRef) {
  return defineComponent({
    template: '<div />',
    setup() {
      return useDetailTabs(tenderRef)
    },
  })
}

describe('useDetailTabs', () => {
  let tenderRef

  beforeEach(() => {
    tenderRef = ref(null)
  })

  it('默认显示所有 3 个 Tab', () => {
    const wrapper = mount(createHarness(tenderRef))
    expect(wrapper.vm.visibleTabs).toHaveLength(3)
    expect(wrapper.vm.visibleTabs.map(t => t.name)).toEqual(['basic', 'evaluation', 'logs'])
    expect(wrapper.vm.activeTab).toBe('basic')
  })

  it('tender 为 null 时显示所有 Tab', () => {
    const wrapper = mount(createHarness(tenderRef))
    expect(wrapper.vm.visibleTabs).toHaveLength(3)
  })

  it('MANUAL_SINGLE 时隐藏操作日志 Tab', async () => {
    const wrapper = mount(createHarness(tenderRef))
    tenderRef.value = { sourceType: 'MANUAL_SINGLE' }
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.visibleTabs).toHaveLength(2)
    expect(wrapper.vm.visibleTabs.find(t => t.name === 'logs')).toBeUndefined()
    expect(wrapper.vm.visibleTabs.map(t => t.name)).toEqual(['basic', 'evaluation'])
  })

  it('非 MANUAL_SINGLE 时显示所有 Tab', async () => {
    const wrapper = mount(createHarness(tenderRef))

    tenderRef.value = { sourceType: 'EXTERNAL_PLATFORM' }
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.visibleTabs).toHaveLength(3)

    tenderRef.value = { sourceType: 'CRM_OPPORTUNITY' }
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.visibleTabs).toHaveLength(3)

    tenderRef.value = { sourceType: 'BULK_IMPORT' }
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.visibleTabs).toHaveLength(3)
  })

  it('activeTab 自动修正（当当前 tab 被隐藏时切到第一个）', async () => {
    const wrapper = mount(createHarness(tenderRef))
    wrapper.vm.switchTab('logs')
    expect(wrapper.vm.activeTab).toBe('logs')

    tenderRef.value = { sourceType: 'MANUAL_SINGLE' }
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.activeTab).toBe('basic')
  })
})
