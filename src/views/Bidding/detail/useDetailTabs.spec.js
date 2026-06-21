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

  it('所有来源类型均显示全部 Tab', async () => {
    const wrapper = mount(createHarness(tenderRef))
    for (const sourceType of ['MANUAL_SINGLE', '人工录入', 'EXTERNAL_PLATFORM', '第三方平台', 'CRM_OPPORTUNITY', 'CRM创建', 'BULK_IMPORT', '批量导入']) {
      tenderRef.value = { sourceType }
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.visibleTabs).toHaveLength(3)
      expect(wrapper.vm.visibleTabs.find(t => t.name === 'logs')).toBeDefined()
    }
  })
})
