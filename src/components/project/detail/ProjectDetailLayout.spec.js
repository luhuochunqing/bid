import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const longProjectName = '中国石油天然气集团有限公司2026年电商采购项目'

const context = {
  project: {
    name: longProjectName,
    status: 'approved',
  },
  canSubmit: false,
  canRecordResult: false,
  canRunAICheck: true,
  canManageProjectDocuments: true,
  canSetProjectReminder: true,
  assistantPanelVisible: false,
  aiChecking: false,
  activeAITab: 'compliance',
  aiResult: {},
  activities: [{ id: 1, time: '2026-04-30', user: '小王', action: '创建项目' }],
  goBack: vi.fn(),
  handleEdit: vi.fn(),
  handleSubmitApproval: vi.fn(),
  handleRecordResult: vi.fn(),
  goToResultPage: vi.fn(),
  toggleAssistantPanel: vi.fn(),
  runAICheck: vi.fn(),
  handleAddDocument: vi.fn(),
  handleShare: vi.fn(),
  handleExport: vi.fn(),
  handleSetReminder: vi.fn(),
  getStatusType: () => 'success',
  getStatusText: () => '已立项',
  getBadgeType: () => 'danger',
  getProgressColor: () => '#f56c6c',
  formatScore: (score) => score.toFixed(2),
  getScoreLevel: () => '不合格',
  getOverallScoreType: () => 'danger',
  showAICheckCard: true,
}

vi.mock('@/composables/projectDetail/context.js', async () => {
  const actual = await vi.importActual('@/composables/projectDetail/context.js')
  return {
    ...actual,
    useProjectDetailContext: () => context,
  }
})

const elementStubs = {
  ElPageHeader: {
    emits: ['back'],
    template: `
      <section class="el-page-header">
        <button class="el-page-header__back" @click="$emit('back')">返回</button>
        <div class="el-page-header__header">
          <div class="el-page-header__content"><slot name="content" /></div>
          <div class="el-page-header__extra"><slot name="extra" /></div>
        </div>
      </section>
    `,
  },
  ElButton: {
    props: ['icon', 'type', 'loading'],
    emits: ['click'],
    template: '<button class="el-button" @click="$emit(\'click\')"><slot /></button>',
  },
  ElIcon: { template: '<i><slot /></i>' },
  ElTag: { template: '<span class="el-tag"><slot /></span>' },
  ElAside: { template: '<aside class="el-aside"><slot /></aside>' },
  ElCard: { template: '<section class="el-card"><header><slot name="header" /></header><slot /></section>' },
  ElTabs: { template: '<div class="el-tabs"><slot /></div>' },
  ElTabPane: { template: '<section class="el-tab-pane"><slot name="label" /><slot /></section>' },
  ElBadge: { template: '<span class="el-badge"><slot /></span>' },
  ElProgress: { template: '<div class="el-progress"><slot :percentage="0" /></div>' },
  ElEmpty: { template: '<div class="el-empty" />' },
  ElTimeline: { template: '<ol class="el-timeline"><slot /></ol>' },
  ElTimelineItem: { props: ['timestamp'], template: '<li class="el-timeline-item"><slot /></li>' },
}

describe('ProjectDetail layout', () => {
  it('keeps long project names in a shrinkable title area separate from actions', async () => {
    const { default: ProjectDetailHeader } = await import('./ProjectDetailHeader.vue')
    const wrapper = mount(ProjectDetailHeader, {
      global: {
        stubs: elementStubs,
      },
    })

    expect(wrapper.find('.page-header').classes()).toContain('project-detail-toolbar')
    expect(wrapper.find('.header-title').exists()).toBe(true)
    expect(wrapper.find('.project-name').attributes('title')).toBe(longProjectName)
    expect(wrapper.find('.header-actions').attributes('aria-label')).toBe('项目操作')
  })

  it('keeps the right rail identifiable as a responsive assistant deck', async () => {
    const { default: ProjectDetailSidebar } = await import('./ProjectDetailSidebar.vue')
    const wrapper = mount(ProjectDetailSidebar, {
      global: {
        stubs: elementStubs,
      },
    })

    expect(wrapper.find('.right-sidebar').attributes('aria-label')).toBe('项目辅助信息')
    expect(wrapper.find('.ai-check-card').exists()).toBe(true)
    expect(wrapper.find('.action-card').exists()).toBe(true)
    expect(wrapper.find('.timeline-card').exists()).toBe(true)
  })

  it('documents the responsive layout rules that prevent horizontal clipping', () => {
    const shellCss = readFileSync(
      resolve(process.cwd(), 'src/components/project/detail/project-detail-shell.css'),
      'utf8',
    )

    expect(shellCss).toContain('grid-template-columns: minmax(0, 1fr) 320px')
    expect(shellCss).toContain('@media (max-width: 1280px)')
    expect(shellCss).toContain('grid-template-columns: minmax(0, 1fr)')
    expect(shellCss).toContain('width: 100% !important')
  })

  it('documents aligned secondary action controls', () => {
    const shellCss = readFileSync(
      resolve(process.cwd(), 'src/components/project/detail/project-detail-shell.css'),
      'utf8',
    )
    const sidebarCss = readFileSync(
      resolve(process.cwd(), 'src/components/project/detail/project-detail-sidebar.css'),
      'utf8',
    )

    expect(shellCss).toContain('.workflow-header-actions')
    expect(shellCss).toContain('margin-left: auto')
    expect(sidebarCss).toContain('.action-list .el-button')
    expect(sidebarCss).toContain('margin-left: 0')
  })
})
