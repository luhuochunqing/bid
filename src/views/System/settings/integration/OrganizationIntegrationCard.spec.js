import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import OrganizationIntegrationCard from './OrganizationIntegrationCard.vue'
import { useOrganizationIntegrationOperations } from '../useOrganizationIntegrationOperations.js'
import { useOrganizationIntegrationSettings } from '../useOrganizationIntegrationSettings.js'

vi.mock('../useOrganizationIntegrationOperations.js', () => ({
  useOrganizationIntegrationOperations: vi.fn(),
}))

vi.mock('../useOrganizationIntegrationSettings.js', () => ({
  useOrganizationIntegrationSettings: vi.fn(),
}))

const stubs = {
  ElAlert: true,
  ElTag: { template: '<span><slot /></span>' },
  ElInput: { props: ['placeholder'], template: '<label><input :placeholder="placeholder" /></label>' },
  ElButton: { template: '<button><slot /></button>' },
  ElCollapse: { template: '<div class="el-collapse"><slot /></div>' },
  ElCollapseItem: { props: ['title'], template: '<div class="el-collapse-item">{{ title }}<slot /></div>' },
  ElForm: { template: '<div><slot /></div>' },
  ElFormItem: { props: ['label'], template: '<div><slot />{{ label ? " " + label : "" }}</div>' },
  ElSwitch: { template: '<div class="el-switch"><slot /></div>' },
}

const directives = {
  loading: {},
}

describe('OrganizationIntegrationCard', () => {
  beforeEach(() => {
    useOrganizationIntegrationOperations.mockReturnValue({
      loading: ref(false),
      syncing: ref(false),
      resyncingUser: ref(false),
      resyncingDepartment: ref(false),
      replayingDeadLetter: ref(false),
      status: ref({ enabled: true, eventSdkEnabled: false, pendingRetryCount: 0, deadLetterCount: 0 }),
      loaded: ref(true),
      errorText: ref(''),
      canOperate: ref(true),
      userId: ref(''),
      deptId: ref(''),
      deadLetterEventKey: ref(''),
      load: vi.fn(),
      startSyncRun: vi.fn(),
      resyncUser: vi.fn(),
      resyncDepartment: vi.fn(),
      replayDeadLetter: vi.fn(),
    })

    useOrganizationIntegrationSettings.mockReturnValue({
      loading: ref(false),
      saving: ref(false),
      testing: ref(false),
      form: ref({
        orgDirectoryBaseUrl: '',
        orgDirectoryAuthClientId: '',
        orgDirectoryAuthClientSecret: '',
        orgDirectoryUserDetailPath: '/users/{userId}',
        orgDirectoryDeptDetailPath: '/departments/{deptId}',
        orgDirectoryUserWindowPath: '',
        orgDirectoryDeptWindowPath: '',
        orgEventSdkEnabled: false,
        orgEventConsumerGroup: 'bid-org-consumer-test',
        orgEventServerRegisterUrl: '',
      }),
      testResult: ref(null),
      load: vi.fn(),
      save: vi.fn(),
      testConnection: vi.fn(),
    })
  })

  it('groups the three manual operations as input and button pairs', () => {
    const wrapper = mount(OrganizationIntegrationCard, { global: { stubs, directives } })

    const operations = wrapper.findAll('.resync-operation')
    expect(operations).toHaveLength(3)
    expect(operations[0].text()).toContain('重同步用户')
    expect(operations[1].text()).toContain('重同步部门')
    expect(operations[2].text()).toContain('重放死信')
  })

  it('renders the YAPI directory configuration collapse panel', () => {
    const wrapper = mount(OrganizationIntegrationCard, { global: { stubs, directives } })

    expect(wrapper.find('.yapi-config-collapse').exists()).toBe(true)
    expect(wrapper.text()).toContain('YAPI 目录配置')
  })

  it('renders YAPI connection config via child form component', () => {
    const wrapper = mount(OrganizationIntegrationCard, { global: { stubs, directives } })

    const bodyText = wrapper.text()
    expect(bodyText).toContain('YAPI 连接配置')
    expect(bodyText).toContain('SDK 事件配置')
    expect(bodyText).toContain('Consumer Group')
    expect(bodyText).toContain('测试 YAPI 连接')
    expect(bodyText).toContain('保存配置')
  })

  it('keeps desktop grouped columns and mobile single-column controls', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/views/System/settings/integration/OrganizationIntegrationCard.vue'),
      'utf8'
    )

    expect(source).toContain('grid-template-columns: repeat(3, minmax(0, 1fr));')
    expect(source).toContain('.resync-operation {\n    grid-template-columns: 1fr;')
    expect(source).toContain('.resync-operation :deep(.el-button) {\n    width: 100%;')
  })
})
