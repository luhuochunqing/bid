import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useCasePrecipitationTask } from './useCasePrecipitationTask.js'
import { casesApi } from '@/api/modules/knowledge.js'

vi.mock('@/api/modules/knowledge.js', () => ({
  casesApi: { getPrecipitationTask: vi.fn(), precipitateCases: vi.fn() }
}))

const Host = defineComponent({
  setup() { return useCasePrecipitationTask(1120) },
  template: '<span>{{ statusText }}</span>'
})

describe('useCasePrecipitationTask', () => {
  afterEach(() => { vi.useRealTimers(); vi.clearAllMocks() })

  it('restores active task after mount and keeps polling until terminal state', async () => {
    vi.useFakeTimers()
    casesApi.getPrecipitationTask
      .mockResolvedValueOnce({ taskId: 'task-1', status: 'PROCESSING', stage: '解析标书' })
      .mockResolvedValueOnce({ taskId: 'task-1', status: 'COMPLETED', caseCount: 0 })
    const wrapper = mount(Host)
    await vi.waitFor(() => expect(wrapper.text()).toContain('解析标书'))
    await vi.advanceTimersByTimeAsync(2000)
    await nextTick()
    expect(wrapper.text()).toContain('沉淀完成（0 条）')
    wrapper.unmount()
  })

  it('keeps POST result active instead of treating acceptance as completion', async () => {
    casesApi.getPrecipitationTask.mockResolvedValue(null)
    casesApi.precipitateCases.mockResolvedValue({ data: { taskId: 'task-2', status: 'PENDING', reused: true } })
    const wrapper = mount(Host)
    await wrapper.vm.trigger()
    expect(wrapper.vm.precipitating).toBe(true)
    expect(wrapper.text()).toContain('正在排队沉淀')
    wrapper.unmount()
  })

  it('does not restart polling when an in-flight refresh resolves after unmount', async () => {
    vi.useFakeTimers()
    let resolveRefresh
    casesApi.getPrecipitationTask.mockImplementation(() => new Promise(resolve => { resolveRefresh = resolve }))
    const wrapper = mount(Host)
    await vi.waitFor(() => expect(casesApi.getPrecipitationTask).toHaveBeenCalledTimes(1))
    wrapper.unmount()
    resolveRefresh({ taskId: 'task-3', status: 'PROCESSING' })
    await Promise.resolve()
    await vi.advanceTimersByTimeAsync(4000)
    expect(casesApi.getPrecipitationTask).toHaveBeenCalledTimes(1)
  })
})
