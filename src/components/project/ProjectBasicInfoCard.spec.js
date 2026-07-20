import { mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises } from '@vue/test-utils'

// Mock httpClient 避免组件 watch tenderId 时发起真实请求
const httpClientGet = vi.fn()
vi.mock('@/api', () => ({
  httpClient: { get: (...a) => httpClientGet(...a) },
}))

// Mock ElMessage（保留 element-plus actual 让 ElMessage 之外的导出可用）
const messageWarning = vi.fn()
vi.mock('element-plus', async () => {
  const actual = await vi.importActual('element-plus')
  return {
    ...actual,
    ElMessage: { warning: (...a) => messageWarning(...a) },
  }
})

import ProjectBasicInfoCard from './ProjectBasicInfoCard.vue'

// el-* 组件 stubs：el-descriptions-item 暴露 data-label 方便断言
const stubs = {
  'el-card': { template: '<div class="card-stub"><slot name="header" /><slot /></div>' },
  'el-descriptions': { template: '<div class="desc-stub"><slot /></div>' },
  'el-descriptions-item': {
    props: ['label'],
    template: '<div class="desc-item" :data-label="label"><slot /></div>',
  },
  'el-tag': { template: '<span class="tag-stub"><slot /></span>' },
  'el-icon': { template: '<i class="icon-stub"><slot /></i>' },
}

// PR !1571 回归修复：客户类型展示位需通过 customerTypeLabel 翻译回中文
describe('ProjectBasicInfoCard customerTypeLabel 渲染', () => {
  beforeEach(() => {
    httpClientGet.mockReset()
    messageWarning.mockReset()
  })

  it.each([
    ['GOVERNMENT', '政府机关/事业单位/高校'],
    ['CENTRAL_SOE', '央企'],
    ['LOCAL_SOE', '地方国企'],
    ['PRIVATE', '民企'],
    ['FOREIGN', '港澳台及外企'],
    ['OTHER', '其他'],
  ])('renders customerType enum "%s" as localized label "%s"', (enumValue, expectedLabel) => {
    // 不传 tenderId 避免 fetchTender 触发；watch immediate 会调 ElMessage.warning（已被 mock）
    const wrapper = mount(ProjectBasicInfoCard, {
      props: { project: { customerType: enumValue } },
      global: { stubs },
    })
    const item = wrapper.find('.desc-item[data-label="客户类型"]')
    expect(item.exists()).toBe(true)
    expect(item.text()).toBe(expectedLabel)
  })

  it('renders "-" for null customerType', () => {
    const wrapper = mount(ProjectBasicInfoCard, {
      props: { project: { customerType: null } },
      global: { stubs },
    })
    const item = wrapper.find('.desc-item[data-label="客户类型"]')
    expect(item.text()).toBe('-')
  })

  it('falls back to raw value for unknown customerType (历史中文数据兼容)', () => {
    const wrapper = mount(ProjectBasicInfoCard, {
      props: { project: { customerType: '央企' } },
      global: { stubs },
    })
    const item = wrapper.find('.desc-item[data-label="客户类型"]')
    expect(item.text()).toBe('央企')
  })
})

// 项目类型展示位需通过 projectTypeLabel 翻译回中文（PR !1571 同款遗漏）
describe('ProjectBasicInfoCard projectTypeLabel 渲染', () => {
  beforeEach(() => {
    httpClientGet.mockReset()
    messageWarning.mockReset()
  })

  it.each([
    ['OFFICE', '办公'],
    ['COMPREHENSIVE', '综合'],
    ['COLLECTIVE', '集采'],
    ['INDUSTRIAL', '工业品'],
    ['OTHER', '其他'],
  ])('renders projectType enum "%s" as localized label "%s"', (enumValue, expectedLabel) => {
    const wrapper = mount(ProjectBasicInfoCard, {
      props: { project: { projectType: enumValue } },
      global: { stubs },
    })
    const item = wrapper.find('.desc-item[data-label="项目类型"]')
    expect(item.exists()).toBe(true)
    expect(item.text()).toBe(expectedLabel)
  })

  it('renders "-" for null projectType', () => {
    const wrapper = mount(ProjectBasicInfoCard, {
      props: { project: { projectType: null } },
      global: { stubs },
    })
    const item = wrapper.find('.desc-item[data-label="项目类型"]')
    expect(item.text()).toBe('-')
  })

  it('falls back to raw value for unknown projectType (历史中文数据兼容)', () => {
    const wrapper = mount(ProjectBasicInfoCard, {
      props: { project: { projectType: '集采' } },
      global: { stubs },
    })
    const item = wrapper.find('.desc-item[data-label="项目类型"]')
    expect(item.text()).toBe('集采')
  })
})

// 项目负责人显示需统一为 "姓名 (工号)" 格式，工号缺失时仅显示姓名
describe('ProjectBasicInfoCard 项目负责人工号显示', () => {
  beforeEach(() => {
    httpClientGet.mockReset()
    messageWarning.mockReset()
  })

  it('renders "姓名 (工号)" when projectLeaderName and projectLeaderEmployeeNumber both exist', () => {
    const wrapper = mount(ProjectBasicInfoCard, {
      props: { project: { projectLeaderName: '王亮', projectLeaderEmployeeNumber: '05972' } },
      global: { stubs },
    })
    const item = wrapper.find('.desc-item[data-label="项目负责人"]')
    expect(item.exists()).toBe(true)
    expect(item.text()).toBe('王亮 (05972)')
  })

  it('renders only name when projectLeaderEmployeeNumber is empty', () => {
    const wrapper = mount(ProjectBasicInfoCard, {
      props: { project: { projectLeaderName: '王亮', projectLeaderEmployeeNumber: '' } },
      global: { stubs },
    })
    const item = wrapper.find('.desc-item[data-label="项目负责人"]')
    expect(item.text()).toBe('王亮')
  })

  it('renders only name when projectLeaderEmployeeNumber is null', () => {
    const wrapper = mount(ProjectBasicInfoCard, {
      props: { project: { projectLeaderName: '王亮', projectLeaderEmployeeNumber: null } },
      global: { stubs },
    })
    const item = wrapper.find('.desc-item[data-label="项目负责人"]')
    expect(item.text()).toBe('王亮')
  })

  it('renders only name when projectLeaderEmployeeNumber is undefined (后端未传字段)', () => {
    const wrapper = mount(ProjectBasicInfoCard, {
      props: { project: { projectLeaderName: '王亮' } },
      global: { stubs },
    })
    const item = wrapper.find('.desc-item[data-label="项目负责人"]')
    expect(item.text()).toBe('王亮')
  })

  // 回归测试：projectLeaderName 缺失时回退到标讯联系人 projectManagerName（通过 fetchTender 拉取）
  it('falls back to tf(projectManagerName) when projectLeaderName is missing', async () => {
    httpClientGet.mockResolvedValueOnce({ data: { projectManagerName: '李四' } })
    const wrapper = mount(ProjectBasicInfoCard, {
      props: {
        project: { projectLeaderName: null, tenderId: 999 },
      },
      global: { stubs },
    })
    // 等待 watch immediate 触发的 fetchTender + httpClient.get Promise 完成
    await flushPromises()
    const item = wrapper.find('.desc-item[data-label="项目负责人"]')
    expect(item.text()).toBe('李四')
  })
})
