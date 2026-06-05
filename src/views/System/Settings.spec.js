// Input: Settings page shell with mocked user role and settings composables
// Output: audit log tab privileged visibility coverage
// Pos: src/views/System/ - page tests

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

const state = vi.hoisted(() => ({
  role: 'admin',
  query: {},
  loadOrganizationSettings: vi.fn(),
  loadAiSettings: vi.fn(),
  loadBidMatchScoringSettings: vi.fn(),
}))

const rolePermissions = {
  admin: ['all'],
  auditor: ['audit-logs', 'operation-logs', 'dashboard'],
  manager: ['settings', 'dashboard', 'bidding', 'project', 'knowledge', 'resource', 'analytics'],
  staff: ['dashboard', 'operation-logs', 'bidding', 'project', 'knowledge', 'resource'],
}

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    get userRole() {
      return state.role
    },
    get menuPermissions() {
      return rolePermissions[state.role] || []
    },
    hasPermission(key) {
      return (rolePermissions[state.role] || []).includes(key)
    },
  }),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal()),
  useRoute: () => ({ query: state.query }),
}))

vi.mock('./settings/useOrganizationSettings', async () => {
  const { computed, ref } = await vi.importActual('vue')
  return {
    useOrganizationSettings: () => ({
      loading: ref(false),
      deptTree: ref([{ deptCode: 'sales', deptName: '销售部' }]),
      deptOptions: computed(() => []),
      users: ref([]),
      roles: ref([]),
      enabledRoles: computed(() => []),
      load: state.loadOrganizationSettings,
      saveDepartments: vi.fn(),
      saveUserOrganization: vi.fn(),
      saveRole: vi.fn(),
      toggleRole: vi.fn(),
      resetRole: vi.fn(),
    }),
  }
})

vi.mock('./settings/useAiModelSettings', async () => {
  const { ref } = await vi.importActual('vue')
  return {
    useAiModelSettings: () => ({
      loading: ref(false),
      saving: ref(false),
      testingProvider: ref(''),
      systemConfig: ref({}),
      aiModelConfig: ref({ providers: [] }),
      load: state.loadAiSettings,
      save: vi.fn(),
      testProvider: vi.fn(),
    }),
  }
})

vi.mock('./settings/useBidMatchScoringSettings', async () => {
  const { computed, ref } = await vi.importActual('vue')
  return {
    EVIDENCE_KEY_OPTIONS: [],
    RULE_TYPE_OPTIONS: [],
    useBidMatchScoringSettings: () => ({
      loading: ref(false),
      saving: ref(false),
      activating: ref(false),
      currentModel: ref({ dimensions: [] }),
      weightValidation: computed(() => ({ valid: true, message: '' })),
      enabledDimensionCount: computed(() => 0),
      load: state.loadBidMatchScoringSettings,
      save: vi.fn(),
      activateCurrentModel: vi.fn(),
      addDimension: vi.fn(),
      removeDimension: vi.fn(),
      addRule: vi.fn(),
      removeRule: vi.fn(),
    }),
  }
})

// TODO(PR #440): remove this mock once @/api/modules/systemSettings.js is merged to main
vi.mock('./settings/useOrganizationIntegrationSettings', async () => {
  const { ref } = await vi.importActual('vue')
  return {
    useOrganizationIntegrationSettings: () => ({
      loading: ref(false),
      loaded: ref(true),
      saving: ref(false),
      form: {},
      load: vi.fn(),
      save: vi.fn(),
    }),
  }
})

import Settings from './Settings.vue'

const childStub = { template: '<div />' }
const stubs = {
  ElAlert: childStub,
  ElButton: {
    props: ['loading'],
    emits: ['click'],
    template: '<button @click="$emit(\'click\')"><slot /></button>',
  },
  ElTabs: {
    props: ['modelValue'],
    template: '<div class="settings-tabs" :data-active="modelValue"><slot /></div>',
  },
  ElTabPane: {
    props: ['label', 'name'],
    template: '<section class="tab-pane" :data-name="name"><span>{{ label }}</span><slot /></section>',
  },
  DepartmentTreePanel: childStub,
  RoleManagementPanel: childStub,
  InterfacePermissionMatrixPanel: childStub,
  UserOrganizationPanel: childStub,
  AiModelSettingsPanel: childStub,
  BidMatchScoringSettingsPanel: childStub,
  SystemIntegrationPanel: childStub,
  TaskStatusDictPanel: { template: '<div>任务状态字典面板</div>' },
  TaskExtendedFieldPanel: { template: '<div>任务扩展字段面板</div>' },
  AuditLogPanel: { template: '<div>审计日志面板</div>' },
}

function mountSettings({ role = 'admin', query = {} } = {}) {
  state.role = role
  state.query = query
  vi.clearAllMocks()

  return mount(Settings, {
    global: {
      stubs,
      directives: {
        loading: {},
      },
    },
  })
}

describe('Settings', () => {
  it('shows audit log tab only for privileged roles', () => {
    const adminWrapper = mountSettings({ role: 'admin' })
    expect(adminWrapper.text()).toContain('审计日志')
    expect(adminWrapper.text()).toContain('审计日志面板')

    const auditorWrapper = mountSettings({ role: 'auditor' })
    expect(auditorWrapper.text()).toContain('审计日志')
    expect(auditorWrapper.text()).toContain('审计日志面板')

    const managerWrapper = mountSettings({ role: 'manager' })
    expect(managerWrapper.text()).not.toContain('审计日志')
    expect(managerWrapper.text()).not.toContain('审计日志面板')
  })

  it('does not keep audit log active when a non-privileged role requests the audit tab', () => {
    const wrapper = mountSettings({ role: 'staff', query: { tab: 'audit' } })

    expect(wrapper.find('.settings-tabs').attributes('data-active')).toBe('departments')
  })

  it('shows task status dict tab only for admin', () => {
    const adminWrapper = mountSettings({ role: 'admin' })
    expect(adminWrapper.text()).toContain('任务状态字典')
    expect(adminWrapper.text()).toContain('任务状态字典面板')

    const managerWrapper = mountSettings({ role: 'manager' })
    expect(managerWrapper.text()).not.toContain('任务状态字典')
    expect(managerWrapper.text()).not.toContain('任务状态字典面板')
  })

  it('redirects task-status-dict query tab away for non-admin', () => {
    const wrapper = mountSettings({ role: 'staff', query: { tab: 'task-status-dict' } })

    expect(wrapper.find('.settings-tabs').attributes('data-active')).toBe('departments')
  })

  it('shows task extended fields tab only for admin', () => {
    const adminWrapper = mountSettings({ role: 'admin' })
    expect(adminWrapper.text()).toContain('任务扩展字段')
    expect(adminWrapper.text()).toContain('任务扩展字段面板')

    const managerWrapper = mountSettings({ role: 'manager' })
    expect(managerWrapper.text()).not.toContain('任务扩展字段')
    expect(managerWrapper.text()).not.toContain('任务扩展字段面板')
  })

  it('redirects task-extended-fields query tab away for non-admin', () => {
    const wrapper = mountSettings({ role: 'staff', query: { tab: 'task-extended-fields' } })

    expect(wrapper.find('.settings-tabs').attributes('data-active')).toBe('departments')
  })
})
