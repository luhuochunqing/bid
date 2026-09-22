// Input: ProjectDetailMainContent after 067 removal (expense manage wiring removed)
// Output: assertion that children render without dead manage listener
// Pos: src/components/project/detail/ - project detail main content spec
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import ProjectDetailMainContent from './ProjectDetailMainContent.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

const ExpenseCardStub = {
  name: 'ProjectExpenseSummaryCard',
  props: ['expenses', 'summary', 'loading', 'error'],
  template: '<div class="expense-card-stub" />',
}

const detail = {
  project: ref({ id: 1, name: '项目A', status: 'approved', tasks: [] }),
  approvalHistory: ref([]),
  projectExpenses: ref([]),
  expenseSummary: ref({ totalAmount: 0 }),
  expenseLoading: ref(false),
  expenseError: ref(''),
  canApproveCurrent: ref(false),
  canManageProjectTasks: ref(false),
  isDemoMode: false,
  handleQuickApprove: () => {},
  handleQuickReject: () => {},
  handleAddTask: () => {},
  handleResetTasks: () => {},
  handleTaskClick: () => {},
  handleSaveTask: () => {},
  handleTaskStatusChange: () => {},
  handleGenerateTasks: () => {},
  handleOpenTenderBreakdown: () => {},
  handleOpenScoreDraftDecompose: () => {},
  handleAddDeliverable: () => {},
  handleRemoveDeliverable: () => {},
  handleSubmitToDocument: () => {},
  handleSubmitReview: () => {},
}

const stubs = {
  ElMain: { template: '<main><slot /></main>' },
  ProjectBasicInfoCard: true,
  ProjectApprovalStatusCard: true,
  ProjectTaskBoardCard: true,
  ProjectDetailWorkflowCard: true,
  ProjectDetailDocumentsCard: true,
  ProjectExpenseSummaryCard: ExpenseCardStub,
}

describe('ProjectDetailMainContent', () => {
  it('renders expense card without the removed manage listener (067 验收导出剔除)', () => {
    const wrapper = mount(ProjectDetailMainContent, {
      global: { stubs, provide: { [projectDetailKey]: detail } },
    })

    const expenseCard = wrapper.findComponent(ExpenseCardStub)
    expect(expenseCard.exists()).toBe(true)
    // 067: @manage="detail.goToExpensePage" 已移除，子组件不应再收到 manage 监听
    expect(expenseCard.props('onManage')).toBeUndefined()
    expect(expenseCard.attributes('onmanage')).toBeUndefined()
  })
})
