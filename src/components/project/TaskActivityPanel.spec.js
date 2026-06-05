import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TaskActivityPanel from './TaskActivityPanel.vue'
import { taskActivityApi } from '@/api/modules/taskActivity.js'

vi.mock('@/api/modules/taskActivity.js', () => ({
  taskActivityApi: {
    getActivity: vi.fn(),
    createComment: vi.fn(),
  },
}))

const stubs = {
  ElButton: {
    emits: ['click'],
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
  },
  ElAlert: { props: ['title'], template: '<div class="alert">{{ title }}</div>' },
  ElEmpty: { props: ['description'], template: '<div class="empty">{{ description }}</div>' },
  ElInput: {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElTimeline: { template: '<ol><slot /></ol>' },
  ElTimelineItem: { props: ['timestamp'], template: '<li><slot /><small>{{ timestamp }}</small></li>' },
  ElTag: { template: '<span><slot /></span>' },
}

describe('TaskActivityPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads activity for existing task id', async () => {
    taskActivityApi.getActivity.mockResolvedValueOnce({
      success: true,
      data: [
        {
          type: 'HISTORY',
          id: 2,
          actorName: 'Alice',
          action: 'UPDATE',
          snapshot: { title: '准备商务标 V2' },
          createdAt: '2026-05-04T10:00:00',
        },
      ],
    })

    const wrapper = mount(TaskActivityPanel, {
      props: { taskId: 99 },
      global: { stubs },
    })
    await flushPromises()

    expect(taskActivityApi.getActivity).toHaveBeenCalledWith(99)
    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('准备商务标 V2')
  })

  it('submits comments and reloads activity', async () => {
    taskActivityApi.getActivity.mockResolvedValue({ success: true, data: [] })
    taskActivityApi.createComment.mockResolvedValueOnce({ success: true, data: { id: 3 } })

    const wrapper = mount(TaskActivityPanel, {
      props: { taskId: 99 },
      global: { stubs },
    })
    await flushPromises()
    await wrapper.find('textarea').setValue('请 @[Bob](8) 看一下')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(taskActivityApi.createComment).toHaveBeenCalledWith(99, {
      content: '请 @[Bob](8) 看一下',
    })
    expect(taskActivityApi.getActivity).toHaveBeenCalledTimes(2)
  })
})
