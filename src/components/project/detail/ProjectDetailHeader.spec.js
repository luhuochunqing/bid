import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// CO-330: 验证「编辑/结果闭环/智能助手」3 个按钮已隐藏
const context = {
  project: { name: '测试项目', status: 'approved' },
  canSubmit: false,
  canRecordResult: false,
  assistantPanelVisible: false,
  goBack: vi.fn(),
  handleEdit: vi.fn(),
  handleSubmitApproval: vi.fn(),
  handleRecordResult: vi.fn(),
  toggleAssistantPanel: vi.fn(),
  getStatusType: () => 'success',
  getStatusText: () => '已立项',
}

vi.mock('@/composables/projectDetail/context.js', async () => {
  const actual = await vi.importActual('@/composables/projectDetail/context.js')
  return { ...actual, useProjectDetailContext: () => context }
})

const elementStubs = {
  ElPageHeader: {
    // 模拟 EP 2.5 结构：__back 区域可点击并 emit back 事件
    emits: ['back'],
    template: `
      <section class="el-page-header">
        <div class="el-page-header__back" role="button" @click="$emit('back')">返回</div>
        <div class="el-page-header__content"><slot name="content" /></div>
        <div class="el-page-header__extra"><slot name="extra" /></div>
      </section>
    `,
  },
  ElButton: { template: '<button class="el-button"><slot /></button>' },
  ElIcon: { template: '<i><slot /></i>' },
  ElTag: { template: '<span class="el-tag"><slot /></span>' },
}

describe('ProjectDetailHeader', () => {
  // context mock 是模块级共享对象，每个用例前清理调用计数，保证断言不跨用例污染
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('hides edit / result-loop / assistant buttons per CO-330', async () => {
    const { default: ProjectDetailHeader } = await import('./ProjectDetailHeader.vue')
    const wrapper = mount(ProjectDetailHeader, { global: { stubs: elementStubs } })

    expect(wrapper.find('.project-name').text()).toBe('测试项目')
    expect(wrapper.find('.status-tag').text()).toBe('已立项')
    expect(wrapper.find('.header-actions').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('编辑')
    expect(wrapper.text()).not.toContain('结果闭环')
    expect(wrapper.text()).not.toContain('智能助手')
    expect(wrapper.text()).not.toContain('提交审批')
    expect(wrapper.text()).not.toContain('录入结果')
  })

  it('clicking the page-header back area navigates back to project list', async () => {
    const { default: ProjectDetailHeader } = await import('./ProjectDetailHeader.vue')
    const wrapper = mount(ProjectDetailHeader, { global: { stubs: elementStubs } })

    await wrapper.find('.el-page-header__back').trigger('click')

    expect(context.goBack).toHaveBeenCalledTimes(1)
  })
})
