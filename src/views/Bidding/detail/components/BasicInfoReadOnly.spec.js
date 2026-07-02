import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import BasicInfoReadOnly from './BasicInfoReadOnly.vue'
import BasicFieldsSection from './BasicFieldsSection.vue'
import ProjectPlanGapUpload from './ProjectPlanGapUpload.vue'
import TenderEvaluationForm from '../TenderEvaluationForm.vue'
import { vAutosize } from '@/directives/autosize.js'

function mountWithElementPlus(component, options = {}) {
  return mount(component, {
    ...options,
    global: {
      ...(options.global || {}),
      plugins: [ElementPlus, ...(options.global?.plugins || [])],
      directives: { autosize: vAutosize, ...(options.global?.directives || {}) },
    },
  })
}

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ({ token: 'test-token' }),
}))

function expectTextareaAutosize(textarea, expected) {
  expect(textarea.props('type')).toBe('textarea')
  expect(textarea.props('autosize')).toEqual(expected)
}

describe('BasicInfoReadOnly — 只读详情多行字段绕过 Element Plus 滚动条 bug', () => {
  it('标讯描述和标讯信息应使用原生 <textarea readonly> 而非 el-input', async () => {
    const wrapper = mountWithElementPlus(BasicInfoReadOnly, {
      props: {
        tender: {
          description: '第一行\n第二行\n第三行\n第四行\n第五行',
          tenderInfo: '信息第一行\n信息第二行\n信息第三行\n信息第四行',
        },
      },
    })
    await wrapper.vm.$nextTick()

    // 原生 textarea 渲染，内容正确显示
    const nativeTextareas = wrapper.findAll('textarea.readonly-textarea')
    expect(nativeTextareas).toHaveLength(2)
    expect(nativeTextareas[0].element.value).toContain('第一行')
    expect(nativeTextareas[1].element.value).toContain('信息第一行')

    // el-input type=textarea 不应存在（已替换为原生）
    const elTextareas = wrapper.findAllComponents({ name: 'ElInput' }).filter(c => c.props('type') === 'textarea')
    expect(elTextareas).toHaveLength(0)

    // 验证原生 textarea 的属性：readonly 启用，不再固定 rows=10
    // （改由 v-autosize 指令根据内容自适应高度，详见 src/directives/autosize.js）
    expect(nativeTextareas[0].attributes('readonly')).toBeDefined()
    expect(nativeTextareas[0].attributes('rows')).toBeUndefined()
    expect(nativeTextareas[1].attributes('readonly')).toBeDefined()
    expect(nativeTextareas[1].attributes('rows')).toBeUndefined()
  })
})

describe('BasicInfoReadOnly — 附件下载链接应拼接 API_BASE_URL', () => {
  async function mountComponentWithBaseUrl(baseUrl, tender) {
    vi.resetModules()
    vi.doMock('@/api/config.js', () => ({
      API_BASE_URL: baseUrl,
      API_CONFIG: {
        baseURL: baseUrl,
        timeout: 30000,
        headers: { 'Content-Type': 'application/json' }
      }
    }))
    const { default: BasicInfoReadOnlyDynamic } = await import('./BasicInfoReadOnly.vue')
    return mountWithElementPlus(BasicInfoReadOnlyDynamic, { props: { tender } })
  }

  it('doc-insight:// 协议 URL 应显示文件名链接', async () => {
    const wrapper = await mountComponentWithBaseUrl('http://127.0.0.1:18089', {
      attachments: [
        { fileName: '招标文件.pdf', fileType: 'application/pdf', fileUrl: 'doc-insight://TENDER_INTAKE/create/hash-file.pdf' },
      ],
    })
    await wrapper.vm.$nextTick()

    const links = wrapper.findAll('.el-link')
    expect(links.length).toBeGreaterThan(0)
    expect(links[0].text()).toContain('招标文件.pdf')
  })

  it('后端已转换的 /api/... 相对路径也应显示文件名链接', async () => {
    const wrapper = await mountComponentWithBaseUrl('http://127.0.0.1:18089', {
      sourceDocumentName: '原始标讯.pdf',
      sourceDocumentFileUrl: '/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2FTENDER%2Fhash-file.pdf',
    })
    await wrapper.vm.$nextTick()

    const links = wrapper.findAll('.el-link')
    expect(links.length).toBeGreaterThan(0)
    expect(links[0].text()).toContain('原始标讯.pdf')
  })

  it('后端返回绝对内部 API URL 时点击下载应改为同源相对路径', async () => {
    vi.resetModules()
    const downloadWithFilename = vi.fn()
    vi.doMock('@/utils/download.js', () => ({
      downloadWithFilename,
      normalizeApiDownloadUrl: (url) => {
        const parsed = new URL(url, 'http://172.16.38.78:8080')
        return parsed.pathname.startsWith('/api/') ? `${parsed.pathname}${parsed.search}${parsed.hash}` : ''
      }
    }))
    const { default: BasicInfoReadOnlyDynamic } = await import('./BasicInfoReadOnly.vue')
    const wrapper = mountWithElementPlus(BasicInfoReadOnlyDynamic, {
      props: {
        tender: {
          attachments: [
            {
              fileName: '招标文件.pdf',
              fileUrl: 'https://winbid-test.ehsy.com/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2FTENDER%2Fhash-file.pdf'
            }
          ]
        }
      }
    })
    await wrapper.vm.$nextTick()

    await wrapper.find('.el-link').trigger('click')

    expect(downloadWithFilename).toHaveBeenCalledWith(
      '/api/doc-insight/download?fileUrl=doc-insight%3A%2F%2FTENDER%2Fhash-file.pdf',
      '招标文件.pdf'
    )
  })

  it('空 URL 应不显示链接', async () => {
    const wrapper = await mountComponentWithBaseUrl('http://127.0.0.1:18089', {
      attachments: [],
    })
    await wrapper.vm.$nextTick()

    const links = wrapper.findAll('.el-link')
    expect(links).toHaveLength(0)
  })
})

describe('BasicFieldsSection — 评估表基础段多行字段启用 autosize', () => {
  it('招标文件不利项、风险预判、了解流程、支持备注应启用 autosize', async () => {
    const wrapper = mountWithElementPlus(BasicFieldsSection, {
      props: {
        modelValue: {
          plannedShortlistedCount: null,
          mroOfficeFlowAmount: null,
          customerRevenue: null,
          unfavorableItems: '不利项1\n不利项2',
          riskAssessment: '风险1\n风险2',
          contingencyPlan: '否',
          processKnowledge: '了解',
          supportNotes: '备注1\n备注2',
        },
        disabled: false,
        errors: {},
      },
    })
    await wrapper.vm.$nextTick()

    const textareas = wrapper.findAllComponents({ name: 'ElInput' }).filter(c => c.props('type') === 'textarea')
    expect(textareas).toHaveLength(4)
    textareas.forEach(t => expectTextareaAutosize(t, { minRows: 3, maxRows: 10 }))
  })
})

describe('ProjectPlanGapUpload — GAP 多行字段启用 autosize', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('项目计划差距应启用 autosize', async () => {
    const wrapper = mountWithElementPlus(ProjectPlanGapUpload, {
      props: {
        modelValue: {
          projectPlanGap: '差距1\n差距2',
          projectPlanGapFiles: [],
        },
        tenderId: 9001,
        disabled: false,
      },
    })
    await wrapper.vm.$nextTick()

    const textareas = wrapper.findAllComponents({ name: 'ElInput' }).filter(c => c.props('type') === 'textarea')
    expect(textareas).toHaveLength(1)
    expectTextareaAutosize(textareas[0], { minRows: 3, maxRows: 10 })
  })
})

describe('TenderEvaluationForm — 建议段理由字段启用 autosize', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('选择不投标时理由字段应启用 autosize', async () => {
    const wrapper = mountWithElementPlus(TenderEvaluationForm, {
      props: {
        tenderId: 9001,
        evaluation: {
          evaluationStatus: 'DRAFT',
          basic: {
            plannedShortlistedCount: 3,
            mroOfficeFlowAmount: 500000,
            customerRevenue: 120000,
            unfavorableItems: '不利项',
            riskAssessment: '风险',
            contingencyPlan: '否',
            processKnowledge: '了解',
            supportNotes: '备注',
            projectPlanGap: '',
            projectPlanGapFiles: [],
          },
          customerInfo: [],
          recommendation: { shouldBid: false, reason: '预算不足\n周期紧张\n资源不够' },
        },
        canFill: true,
        canDecide: false,
        canFillRecommendation: true,
      },
    })
    await wrapper.vm.$nextTick()

    const textareas = wrapper.findAllComponents({ name: 'ElInput' }).filter(c => c.props('type') === 'textarea')
    const reasonTextarea = textareas.find(t => t.props('placeholder')?.includes('理由'))
    expect(reasonTextarea).toBeTruthy()
    expectTextareaAutosize(reasonTextarea, { minRows: 4, maxRows: 10 })
  })
})
