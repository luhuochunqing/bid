// Input: SystemInfoPanel with mocked settingsApi system info
// Output: product name display normalization, fallback versioning, and changelog translation coverage
// Pos: src/views/System/settings/ - component tests

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  getSystemInfo: vi.fn(),
}))

vi.mock('@/api', () => ({
  settingsApi: {
    getSystemInfo: mocks.getSystemInfo,
  },
}))

import SystemInfoPanel from './SystemInfoPanel.vue'

const stubs = {
  ElAlert: {
    props: ['title'],
    template: '<div role="alert">{{ title }}</div>',
  },
  ElButton: {
    props: ['loading'],
    emits: ['click'],
    template: '<button @click="$emit(\'click\')"><slot /></button>',
  },
  ElCard: {
    template: '<section><header><slot name="header" /></header><slot /></section>',
  },
  ElDescriptions: {
    props: ['column', 'border'],
    template: '<div class="el-descriptions"><slot /></div>',
  },
  ElDescriptionsItem: {
    props: ['label', 'span'],
    template: '<div class="desc-item"><span class="label">{{ label }}</span><span class="content"><slot /></span></div>',
  },
  ElTag: {
    props: ['type', 'effect'],
    template: '<span class="el-tag"><slot /></span>',
  },
  ElCollapse: {
    template: '<div class="el-collapse"><slot /></div>',
  },
  ElCollapseItem: {
    props: ['name'],
    template: '<div class="el-collapse-item"><slot name="title" /><slot /></div>',
  },
  ElTimeline: {
    template: '<ul class="el-timeline"><slot /></ul>',
  },
  ElTimelineItem: {
    props: ['timestamp', 'type', 'hollow'],
    template: '<li class="el-timeline-item"><span>{{ timestamp }}</span><slot /></li>',
  },
  ElIcon: {
    template: '<i class="el-icon"><slot /></i>',
  },
}

function mountPanel() {
  return mount(SystemInfoPanel, {
    global: {
      stubs,
      directives: {
        loading: {},
      },
    },
  })
}

describe('SystemInfoPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads and displays system info successfully', async () => {
    mocks.getSystemInfo.mockResolvedValue({
      success: true,
      data: {
        build: {
          name: 'XiYu Bid POC',
          version: '1.0.3',
          artifact: 'xiyu-bid-poc',
          description: '投标平台',
          buildTime: '2026-05-30T07:00:00Z',
        },
        git: {
          branch: 'feature/some-branch',
          commitIdAbbrev: 'abc1234',
          commitTime: '2026-05-30 14:50:00',
          commitAuthorName: 'Developer',
          commitMessage: 'fix: something',
        },
        changelog: [
          { version: '1.0.3', date: '2026-05-30', content: '当前版本', type: 'current' },
          { version: '1.0.2', date: '2026-04-01', content: '历史版本记录可通过 CHANGELOG.md 维护', type: 'history' },
          { version: '1.0.1', date: '2026-03-15', content: '初始版本发布', type: 'history' },
        ],
      },
    })

    const wrapper = mountPanel()
    await flushPromises()

    expect(mocks.getSystemInfo).toHaveBeenCalled()
    
    // 验证产品名称已被翻译
    expect(wrapper.text()).toContain('西域数智化投标管理平台')
    expect(wrapper.text()).not.toContain('XiYu Bid POC')

    // 验证版本及构建组件
    expect(wrapper.text()).toContain('1.0.3')
    expect(wrapper.text()).toContain('西域')
    expect(wrapper.text()).toContain('feature/some-branch')
    expect(wrapper.text()).toContain('abc1234')

    // 验证更新日志的占位符翻译
    expect(wrapper.text()).toContain('重构系统信息面板并完善开发诊断数据')
    expect(wrapper.text()).toContain('优化标讯详情评估管理，重构权限控制机制')
    expect(wrapper.text()).toContain('西域数智化投标管理平台初始版本发布')

    // 验证平台作者及联系方式
    expect(wrapper.text()).toContain('卢文融')
    expect(wrapper.text()).toContain('13761778461')
  })

  it('falls back to default version and project name when data is missing or unknown', async () => {
    mocks.getSystemInfo.mockResolvedValue({
      success: true,
      data: {
        build: {
          name: null,
          version: 'unknown',
          artifact: null,
          description: null,
          buildTime: null,
        },
        git: null,
        changelog: [],
      },
    })

    const wrapper = mountPanel()
    await flushPromises()

    // 验证缺省名称和版本回退
    expect(wrapper.text()).toContain('西域数智化投标管理平台')
    expect(wrapper.text()).toContain('v1.0.3')
  })

  it('displays error alert when API call fails', async () => {
    mocks.getSystemInfo.mockRejectedValue(new Error('网络连接超时'))

    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.text()).toContain('网络连接超时')
  })
})
