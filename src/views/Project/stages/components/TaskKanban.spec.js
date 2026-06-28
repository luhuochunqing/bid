import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { flushPromises } from '@vue/test-utils'
import TaskKanban from './TaskKanban.vue'
import { projectsApi } from '@/api/modules/projects.js'
import { useUserStore } from '@/stores/user'

vi.mock('@/api/modules/projects.js', () => ({
  projectsApi: {
    getTasks: vi.fn(() => Promise.resolve({ data: [] })),
  },
}))

describe('TaskKanban — 新建任务按钮权限', () => {
  beforeEach(() => setActivePinia(createPinia()))

  function mountKanban(role, opts = {}) {
    const userStore = useUserStore()
    userStore.currentUser = { id: opts.currentUserId ?? 1, roleCode: role }
    return mount(TaskKanban, {
      props: {
        projectId: 1,
        canUseAI: false,
        scoreRiskCount: 0,
        primaryLeadId: opts.primaryLeadId ?? null,
        secondaryLeadId: opts.secondaryLeadId ?? null,
      },
      global: {
        stubs: {
          'el-button': { template: '<button><slot /></button>' },
          'el-card': { template: '<div><slot name="header" /><slot /></div>' },
          'el-tag': true,
          'el-empty': true,
          'el-alert': true,
          'el-input': true,
          'el-form-item': true,
          'el-upload': true,
          'el-date-picker': true,
          'el-form': true,
          'el-dialog': true,
          'el-select-v2': true,
          'user-picker': true,
        },
      },
    })
  }

  it('admin 角色显示新建任务按钮', async () => {
    const wrapper = mountKanban('admin')
    await nextTick()
    expect(wrapper.text()).toContain('新建任务')
  })

  it('/bidAdmin 角色显示新建任务按钮', async () => {
    const wrapper = mountKanban('/bidAdmin')
    await nextTick()
    expect(wrapper.text()).toContain('新建任务')
  })

  it('bid-TeamLeader 角色显示新建任务按钮', async () => {
    const wrapper = mountKanban('bid-TeamLeader')
    await nextTick()
    expect(wrapper.text()).toContain('新建任务')
  })

  it('bid-projectLeader 匹配 primaryLeadId 显示新建任务按钮', async () => {
    const wrapper = mountKanban('bid-projectLeader', { currentUserId: 10, primaryLeadId: 10 })
    await nextTick()
    expect(wrapper.text()).toContain('新建任务')
  })

  it('bid-projectLeader 不匹配 primaryLeadId 不显示新建任务按钮', async () => {
    const wrapper = mountKanban('bid-projectLeader', { currentUserId: 10, primaryLeadId: 99 })
    await nextTick()
    expect(wrapper.text()).not.toContain('新建任务')
  })

  it('bid-Team 匹配 secondaryLeadId 显示新建任务按钮', async () => {
    const wrapper = mountKanban('bid-Team', { currentUserId: 20, primaryLeadId: 10, secondaryLeadId: 20 })
    await nextTick()
    expect(wrapper.text()).toContain('新建任务')
  })

  it('bid-Team 不匹配 lead 不显示新建任务按钮', async () => {
    const wrapper = mountKanban('bid-Team', { currentUserId: 99, primaryLeadId: 10, secondaryLeadId: 20 })
    await nextTick()
    expect(wrapper.text()).not.toContain('新建任务')
  })

  it('bid-administration 角色不显示新建任务按钮', async () => {
    const wrapper = mountKanban('bid-administration')
    await nextTick()
    expect(wrapper.text()).not.toContain('新建任务')
  })
})

describe('TaskKanban — 任务加载失败展示', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(projectsApi.getTasks).mockReset()
  })

  function mountKanban(getTasksImpl) {
    vi.mocked(projectsApi.getTasks).mockImplementation(getTasksImpl)
    const userStore = useUserStore()
    userStore.currentUser = { id: 1, roleCode: 'admin' }
    return mount(TaskKanban, {
      props: { projectId: 1, canUseAI: false, scoreRiskCount: 0 },
      global: {
        stubs: {
          'el-button': { template: '<button><slot /></button>' },
          'el-card': { template: '<div><slot name="header" /><slot /></div>' },
          'el-tag': true,
          'el-empty': true,
          'el-alert': { props: ['title'], template: '<div class="el-alert">{{ title }}</div>' },
          'el-input': true,
          'el-form-item': true,
          'el-upload': true,
          'el-date-picker': true,
          'el-form': true,
          'el-dialog': true,
          'el-select-v2': true,
          'user-picker': true,
        },
      },
    })
  }

  it('加载失败时展示错误提示而非静默空看板', async () => {
    const wrapper = mountKanban(() =>
      Promise.reject({ response: { data: { msg: '权限不足' } } })
    )
    await flushPromises()
    expect(wrapper.text()).toContain('权限不足')
  })

  it('加载失败且无明确消息时使用默认文案', async () => {
    const wrapper = mountKanban(() => Promise.reject({}))
    await flushPromises()
    expect(wrapper.text()).toContain('任务加载失败')
  })
})
