import { flushPromises, mount } from '@vue/test-utils'
import { vi } from 'vitest'

const elementComponentStubs = vi.hoisted(() => {
  const passthrough = (template, props = [], emits = []) => ({ props, emits, template })
  return {
    'el-button': passthrough('<button type="button" :disabled="loading" @click="$emit(\'click\', $event)"><slot /></button>', ['type', 'size', 'text', 'loading', 'icon'], ['click']),
    'el-link': passthrough('<button type="button" class="el-link-stub" @click="$emit(\'click\', $event)"><slot /></button>', ['type', 'underline'], ['click']),
    'el-tag': passthrough('<span class="el-tag-stub"><slot /></span>', ['type', 'size', 'effect']),
    'el-icon': passthrough('<span class="el-icon-stub"><slot /></span>', ['size']),
    'el-form': passthrough('<form><slot /></form>', ['model', 'labelWidth']),
    'el-form-item': passthrough('<label><span>{{ label }}</span><slot /></label>', ['label', 'required']),
    'el-option': passthrough('<option :value="value">{{ label }}</option>', ['label', 'value']),
    'el-select': {
      props: ['modelValue', 'placeholder', 'filterable'],
      emits: ['update:modelValue'],
      template: '<select class="el-select-stub" :value="modelValue ?? \'\'" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
    },
    'el-date-picker': {
      props: ['modelValue', 'type', 'placeholder', 'valueFormat'],
      emits: ['update:modelValue'],
      template: '<input class="el-date-picker-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    },
    'el-input': {
      props: ['modelValue', 'type', 'rows', 'maxlength', 'showWordLimit', 'placeholder'],
      emits: ['update:modelValue'],
      template: '<textarea v-if="type === \'textarea\'" class="el-input-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" /><input v-else class="el-input-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    },
    'el-checkbox': {
      props: ['modelValue'],
      emits: ['update:modelValue', 'change'],
      template: '<input type="checkbox" class="el-checkbox-stub" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked); $emit(\'change\', $event.target.checked)" />',
    },
    'el-segmented': {
      props: ['modelValue', 'options'],
      emits: ['update:modelValue'],
      template: '<select class="el-segmented-stub" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><option v-for="item in options" :key="item.value" :value="item.value">{{ item.label }}</option></select>',
    },
    'el-input-number': {
      props: ['modelValue', 'min', 'precision'],
      emits: ['update:modelValue'],
      template: '<input class="el-input-number-stub" type="number" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
    },
    'el-dialog': {
      props: ['modelValue', 'title', 'width', 'destroyOnClose'],
      emits: ['update:modelValue'],
      template: '<section v-if="modelValue" class="el-dialog-stub"><h2>{{ title }}</h2><slot /><footer><slot name="footer" /></footer></section>',
    },
    'el-calendar': {
      props: ['modelValue'],
      emits: ['update:modelValue'],
      template: '<div class="el-calendar-stub"><slot name="date-cell" :data="{ date: modelValue || new Date(), day: \'2026-04-22\', viewType: \'month\' }" /></div>',
    },
    ApprovalDialog: passthrough('<div class="approval-dialog-stub" />', ['visible', 'mode', 'approvalInfo']),
  }
})

vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    resolveComponent: (name) => elementComponentStubs[name] || actual.resolveComponent(name),
    resolveDirective: (name) => (name === 'loading' ? {} : actual.resolveDirective(name)),
  }
})

import { nextTick } from 'vue'

const mockState = vi.hoisted(() => ({
  routerPush: vi.fn(),
  currentUser: { id: 7, name: '小王', role: 'staff' },
  setCalendar: vi.fn(),
  dashboardGetSummary: vi.fn(),
  tasksGetMine: vi.fn(),
  tasksComplete: vi.fn(),
  alertGetUnresolved: vi.fn(),
  alertAcknowledge: vi.fn(),
  approvalGetPendingApprovals: vi.fn(),
  approvalGetMyApprovals: vi.fn(),
  approvalSubmitApproval: vi.fn(),
  projectsGetList: vi.fn(),
  qualificationsGetList: vi.fn(),
  qualificationsCreateBorrow: vi.fn(),
  contractBorrowCreate: vi.fn(),
  expenseCreate: vi.fn(),
  scheduleGetOverview: vi.fn(),
  tendersGetList: vi.fn(),
  workbenchGetDeadlineStats: vi.fn().mockResolvedValue({ success: true, data: {} }),
  messageSuccess: vi.fn(),
  messageWarning: vi.fn(),
  messageError: vi.fn(),
  messageInfo: vi.fn(),
}))
export const mocks = mockState

vi.mock('vue-router', () => ({ useRouter: () => ({ push: mockState.routerPush }) }))
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    get currentUser() { return mockState.currentUser },
    get menuPermissions() { return mockState.currentUser?.menuPermissions || [] },
    hasPermission: (key) => (mockState.currentUser?.menuPermissions || []).includes('all') || (mockState.currentUser?.menuPermissions || []).includes(key),
  })
}))
vi.mock('@/stores/bidding', () => ({ useBiddingStore: () => ({ setCalendar: mockState.setCalendar }) }))
vi.mock('@/api', () => ({
  dashboardApi: { 
    getSummary: mockState.dashboardGetSummary,
    getRuntimeMode: vi.fn().mockResolvedValue({ data: { modeCode: 'mock', demoFusionEnabled: false } }),
    getLayout: vi.fn().mockResolvedValue({ success: true, data: { layoutJson: '[]' } })
  },
  approvalApi: {
    getPendingApprovals: mockState.approvalGetPendingApprovals,
    getMyApprovals: mockState.approvalGetMyApprovals,
    submitApproval: mockState.approvalSubmitApproval,
  },
  projectsApi: { getList: mockState.projectsGetList },
  qualificationsApi: {
    getList: mockState.qualificationsGetList,
    createBorrow: mockState.qualificationsCreateBorrow,
  },
  resourcesApi: {
    expenses: { create: mockState.expenseCreate },
  },
  workbenchApi: {
    getScheduleOverview: mockState.scheduleGetOverview,
    getDeadlineStats: mockState.workbenchGetDeadlineStats,
  },
}))
vi.mock('@/api/modules/dashboard.js', () => ({ tasksApi: { getMine: mockState.tasksGetMine, complete: mockState.tasksComplete } }))
vi.mock('@/api/modules/alerts.js', () => ({ alertHistoryApi: { getUnresolved: mockState.alertGetUnresolved, acknowledge: mockState.alertAcknowledge } }))
vi.mock('@/api/modules/tenders.js', () => ({ tendersApi: { getList: mockState.tendersGetList } }))
vi.mock('@/api/modules/workbench.js', () => ({ workbenchApi: { getScheduleOverview: mockState.scheduleGetOverview, getDeadlineStats: mockState.workbenchGetDeadlineStats } }))
vi.mock('@/api/modules/contractBorrow.js', () => ({ contractBorrowApi: { create: mockState.contractBorrowCreate } }))
vi.mock('element-plus', () => ({
  ElMessage: {
    success: mockState.messageSuccess,
    warning: mockState.messageWarning,
    error: mockState.messageError,
    info: mockState.messageInfo,
  },
}))

import Workbench from '@/views/Dashboard/Workbench.vue'

const globalMountOptions = {
  directives: { loading: {} },
  stubs: elementComponentStubs,
  components: elementComponentStubs,
}

export const users = {
  sales: {
    id: 7,
    name: '小王',
    role: 'staff',
    dept: '销售一部',
    menuPermissions: [
      'dashboard', 'dashboard.quickStart', 'bidding', 'project', 'project.create',
      'dashboard:view_welcome_banner', 'dashboard:view_metric_cards', 'dashboard:view_calendar',
      'dashboard:view_tender_list', 'dashboard:view_project_list', 'dashboard:view_active_projects',
      'dashboard:view_activity_list', 'dashboard:view_priority_todos'
    ]
  },
  manager: {
    id: 8,
    name: '张经理',
    role: 'manager',
    menuPermissions: [
      'dashboard', 'bidding', 'project', 'task.assign', 'task.review',
      'dashboard:view_welcome_banner', 'dashboard:view_metric_cards', 'dashboard:view_calendar',
      'dashboard:view_tender_list', 'dashboard:view_technical_task', 'dashboard:view_review_list',
      'dashboard:view_customer_followup', 'dashboard:view_project_list', 'dashboard:view_team_task',
      'dashboard:view_global_projects', 'dashboard:view_active_projects', 'dashboard:view_team_performance',
      'dashboard:view_approval_list', 'dashboard:view_process_timeline', 'dashboard:view_activity_list',
      'dashboard:view_priority_todos'
    ]
  },
  staff: {
    id: 9,
    name: '李工',
    role: 'staff',
    menuPermissions: [
      'dashboard', 'bidding', 'project', 'task.review',
      'dashboard:view_welcome_banner', 'dashboard:view_metric_cards', 'dashboard:view_calendar',
      'dashboard:view_tender_list', 'dashboard:view_technical_task', 'dashboard:view_review_list',
      'dashboard:view_customer_followup', 'dashboard:view_project_list', 'dashboard:view_active_projects',
      'dashboard:view_process_timeline', 'dashboard:view_activity_list', 'dashboard:view_priority_todos'
    ]
  },
  admin: { id: 1, name: '管理员', role: 'admin', menuPermissions: ['all'] },
}

export function resetApiMocks() {
  mockState.dashboardGetSummary.mockResolvedValue({
    success: true,
    data: { totalBudget: 2000000, successRate: 48.6, totalTenders: 12, activeProjects: 4, pendingTasks: 3 },
  })
  mockState.tasksGetMine.mockResolvedValue({ data: [{ id: 501, title: 'API任务：完善技术方案', priority: 'HIGH', status: 'TODO', dueDate: '2026-04-23T10:30:00' }] })
  mockState.tasksComplete.mockResolvedValue({ success: true, data: { status: 'COMPLETED' } })
  mockState.alertGetUnresolved.mockResolvedValue({ data: [{ id: 601, severity: 'CRITICAL', message: '保证金即将到期', status: 'ACTIVE', createdAt: '2026-04-21T09:00:00' }] })
  mockState.alertAcknowledge.mockResolvedValue({ success: true })
  mockState.approvalGetPendingApprovals.mockResolvedValue({
    totalCount: 2,
    data: [{ id: 701, title: '数字政府项目 - 预算审批', approvalType: 'expense', applicantDept: '销售一部', submitTime: '2026-04-22 09:00' }],
  })
  mockState.approvalGetMyApprovals.mockResolvedValue({
    data: [{ id: 801, title: '数字政府项目 - 标书支持申请', status: 'PENDING', description: '等待技术支持排期', submitTime: '2026-04-22 08:30' }],
  })
  mockState.approvalSubmitApproval.mockResolvedValue({ success: true, data: { id: 901 } })
  mockState.projectsGetList.mockResolvedValue({
    success: true,
    data: [{ id: '101', name: '数字政府项目' }, { id: '102', projectName: '智慧园区项目' }],
  })
  mockState.qualificationsGetList.mockResolvedValue({
    success: true,
    data: [{ id: 201, name: '高新技术企业证书' }],
  })
  mockState.qualificationsCreateBorrow.mockResolvedValue({ success: true, data: { id: 301 } })
  mockState.contractBorrowCreate.mockResolvedValue({ success: true, data: { id: 401 } })
  mockState.expenseCreate.mockResolvedValue({ success: true, data: { id: 501 } })
  mockState.scheduleGetOverview.mockResolvedValue({
    data: { events: [{ id: 301, eventDate: '2026-04-23', eventType: 'DEADLINE', title: '数字政府项目截标', projectId: 101, isUrgent: true }] },
  })
  mockState.tendersGetList.mockResolvedValue({ success: true, data: [{ id: 'B001', title: '某央企智慧办公平台采购项目', aiScore: 92 }] })
}

export async function mountWorkbench(user = users.sales) {
  Object.keys(mockState.currentUser).forEach((key) => {
    delete mockState.currentUser[key]
  })
  Object.assign(mockState.currentUser, user)
  const wrapper = mount(Workbench, { global: globalMountOptions })
  await flushPromises()
  await nextTick()
  wrapper.vm.$forceUpdate()
  await nextTick()
  return wrapper
}

export async function refreshWorkbench(wrapper) {
  await flushPromises()
  wrapper.vm.$forceUpdate()
  await nextTick()
}

export function findByText(wrapper, selector, text) {
  return wrapper.findAll(selector).find((item) => item.text().includes(text))
}
