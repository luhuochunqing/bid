import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AiRecommendDrawer from './AiRecommendDrawer.vue'
import { casesApi } from '@/api/modules/knowledge.js'
import { projectsApi } from '@/api'

vi.mock('@/api/modules/knowledge.js', () => ({
  casesApi: {
    recommendCases: vi.fn(),
    recommendForProject: vi.fn(),
    reuseCase: vi.fn(),
  },
}))

vi.mock('@/api', () => ({
  projectsApi: {
    getScoreDrafts: vi.fn(),
  },
}))

describe('AiRecommendDrawer.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    navigator.clipboard = {
      writeText: vi.fn().mockResolvedValue(undefined),
    }
  })

  const globalStubs = {
    'el-drawer': {
      props: ['modelValue', 'title'],
      emits: ['open'],
      template: `<div class="el-drawer-mock" v-if="modelValue"><slot /></div>`,
      mounted() {
        if (this.modelValue) this.$emit('open')
      },
    },
    'el-dialog': {
      props: ['modelValue'],
      template: `<div class="el-dialog-mock" v-if="modelValue"><slot /><slot name="footer" /></div>`,
    },
    'el-select': { template: `<div><slot /></div>` },
    'el-option-group': { template: `<div><slot /></div>` },
    'el-option': { template: `<div />` },
    'el-input': { template: `<input />` },
    'el-tag': { template: `<span><slot /></span>` },
    'el-button': { template: `<button @click="$emit('click')"><slot /></button>` },
    'el-empty': { template: `<div class="el-empty-mock" />` },
    'el-alert': { template: `<div class="el-alert-mock"><slot name="title" /></div>` },
  }

  it('loads score drafts and triggers preliminary recommendations on open', async () => {
    projectsApi.getScoreDrafts.mockResolvedValue({
      data: [
        { id: 1, scoreItemTitle: '技术方案实施', category: '技术评分' },
      ],
    })
    casesApi.recommendForProject.mockResolvedValue([])
    casesApi.recommendCases.mockResolvedValue([
      {
        caseId: 101,
        scoringTitle: '技术方案实施细则',
        responseText: '我们具备成熟的技术方案实施能力',
        sourceProjectName: '国家电网信息化项目',
        score: 92,
        scoreLabel: '优质',
        bidResult: 'WON',
        reuseCount: 3,
      },
    ])

    const wrapper = mount(AiRecommendDrawer, {
      props: {
        projectId: 10,
        modelValue: true,
      },
      global: { stubs: globalStubs },
    })

    await flushPromises()

    expect(projectsApi.getScoreDrafts).toHaveBeenCalledWith(10)
    expect(casesApi.recommendCases).toHaveBeenCalledWith(10, '技术方案实施', '')
    expect(wrapper.text()).toContain('技术方案实施细则')
    expect(wrapper.text()).toContain('国家电网信息化项目')
  })

  it('copies text and calls reuseCase on handleReuse', async () => {
    projectsApi.getScoreDrafts.mockResolvedValue({ data: [] })
    casesApi.recommendForProject.mockResolvedValue([
      {
        caseId: 202,
        scoringTitle: '售后服务承诺',
        responseText: '7x24小时响应',
        sourceProjectName: '政务云运维项目',
        score: 88,
        scoreLabel: '优质',
        reuseCount: 1,
      },
    ])

    const wrapper = mount(AiRecommendDrawer, {
      props: {
        projectId: 10,
        modelValue: true,
      },
      global: { stubs: globalStubs },
    })

    await flushPromises()

    const reuseButtons = wrapper.findAll('button').filter(b => b.text().includes('复用'))
    expect(reuseButtons.length).toBeGreaterThan(0)
    await reuseButtons[0].trigger('click')

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('7x24小时响应')
    expect(casesApi.reuseCase).toHaveBeenCalledWith(202)
  })
})
