import { mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { markRaw, ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

import ProjectDetailMainColumn from './ProjectDetailMainColumn.vue'
import ProjectDetailMainContent from './ProjectDetailMainContent.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

const mainColumnContext = {
  project: {
    id: 12,
    tasks: [{ id: 501, name: '商务标：商务响应', status: 'TODO' }],
    documents: [],
  },
  approvalHistory: [],
  projectExpenses: [],
  expenseSummary: {},
  expenseLoading: false,
  expenseError: '',
  canApproveCurrent: false,
  canManageProjectTasks: true,
  canManageProjectDocuments: false,
  isDemoMode: false,
  handleQuickApprove: vi.fn(),
  handleQuickReject: vi.fn(),
  goToExpensePage: vi.fn(),
  handleAddTask: vi.fn(),
  handleResetTasks: vi.fn(),
  handleTaskClick: vi.fn(),
  handleTaskStatusChange: vi.fn(),
  handleGenerateTasks: vi.fn(),
  handleOpenScoreDraftDecompose: vi.fn(),
  handleAddDeliverable: vi.fn(),
  handleRemoveDeliverable: vi.fn(),
  handleSubmitToDocument: vi.fn(),
  handleArchiveDocuments: vi.fn(),
  handleUpload: vi.fn(),
  handleDownload: vi.fn(),
  handleDeleteDoc: vi.fn(),
}

vi.mock('@/composables/projectDetail/context.js', async () => {
  const actual = await vi.importActual('@/composables/projectDetail/context.js')
  return {
    ...actual,
    useProjectDetailContext: () => mainColumnContext,
  }
})

const taskBoardCardStub = {
  name: 'ProjectTaskBoardCard',
  props: ['tasks'],
  emits: ['status-change'],
  template: '<button data-test="status-change" @click="$emit(\'status-change\', tasks[0], \'doing\')">change</button>',
}

const shallowStubs = {
  ProjectBasicInfoCard: true,
  ProjectApprovalStatusCard: true,
  ProjectExpenseSummaryCard: true,
  ProjectDetailWorkflowCard: true,
  ProjectDetailDocumentsCard: true,
  ProjectTaskBoardCard: markRaw(taskBoardCardStub),
  DraftingStage: true,
  InitiationStage: true,
  EvaluationStage: true,
  ResultConfirmStage: true,
  RetrospectiveStage: true,
  ClosureStage: true,
  ElMain: { template: '<main><slot /></main>' },
  ElCard: { template: '<section><slot /><slot name="header" /></section>' },
  ElTable: true,
  ElTableColumn: true,
  ElEmpty: true,
  ElButton: true,
  ElUpload: true,
  ElIcon: true,
  ElTabs: { template: '<div><slot /></div>' },
  ElTabPane: { template: '<div><slot /></div>' },
}

describe('ProjectDetail task status event wiring', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('MainColumn forwards task board status changes to the context handler', async () => {
    const wrapper = mount(ProjectDetailMainColumn, {
      global: {
        stubs: shallowStubs,
      },
    })

    await wrapper.get('[data-test="status-change"]').trigger('click')

    expect(mainColumnContext.handleTaskStatusChange).toHaveBeenCalledWith(
      mainColumnContext.project.tasks[0],
      'doing',
    )
  })

  it('MainContent forwards task board status changes to the injected detail handler', async () => {
    const detail = {
      project: ref({
        id: 12,
        tasks: [{ id: 601, name: '技术标：技术方案', status: 'TODO' }],
      }),
      approvalHistory: ref([]),
      projectExpenses: ref([]),
      expenseSummary: ref({}),
      expenseLoading: ref(false),
      expenseError: ref(''),
      canApproveCurrent: ref(false),
      canManageProjectTasks: ref(true),
      isDemoMode: false,
      handleQuickApprove: vi.fn(),
      handleQuickReject: vi.fn(),
      goToExpensePage: vi.fn(),
      handleAddTask: vi.fn(),
      handleResetTasks: vi.fn(),
      handleTaskClick: vi.fn(),
      handleTaskStatusChange: vi.fn(),
      handleGenerateTasks: vi.fn(),
      handleOpenScoreDraftDecompose: vi.fn(),
      handleAddDeliverable: vi.fn(),
      handleRemoveDeliverable: vi.fn(),
      handleSubmitToDocument: vi.fn(),
    }
    const wrapper = mount(ProjectDetailMainContent, {
      global: {
        provide: {
          [projectDetailKey]: detail,
        },
        stubs: shallowStubs,
      },
    })

    await wrapper.get('[data-test="status-change"]').trigger('click')

    expect(detail.handleTaskStatusChange).toHaveBeenCalledWith(
      detail.project.value.tasks[0],
      'doing',
    )
  })
})
