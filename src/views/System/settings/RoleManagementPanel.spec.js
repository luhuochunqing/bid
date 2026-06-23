// Input: mounted RoleManagementPanel with mocked handlers
// Output: coverage for the OSS menu sync dialog flow (open -> confirm -> handler)
// Pos: src/views/System/settings/ - component tests

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('element-plus', async () => {
  const actual = await vi.importActual('element-plus')
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
    },
  }
})

import { ElMessage } from 'element-plus'
import RoleManagementPanel from './RoleManagementPanel.vue'

const stubs = {
  ElTable: {
    template: '<table><tbody><tr v-for="(r,i) in data" :key="i"><slot name="default" v-bind="{row:r, $index:i}"/></tr></tbody></table>',
    props: ['data', 'rowKey', 'loading', 'border'],
    provide() {
      return { currentRow: this.currentRow, currentIndex: this.currentIndex }
    },
    data() { return { currentRow: {}, currentIndex: 0 } },
    watch: {
      data: { immediate: true, handler(v) { if (v?.[0]) { this.currentRow = v[0]; this.currentIndex = 0 } } }
    }
  },
  ElTableColumn: {
    props: ['prop', 'label', 'width', 'min-width', 'fixed', 'show-overflow-tooltip'],
    inject: { currentRow: { default: {} }, currentIndex: { default: 0 } },
    render() {
      return this.$slots?.default?.({ row: this.currentRow, $index: this.currentIndex })
    }
  },
  ElTag: {
    template: '<span class="el-tag"><slot/></span>',
    props: ['type', 'size'],
  },
  ElButtonGroup: {
    template: '<div class="el-button-group"><slot/></div>',
  },
  ElButton: {
    template: '<button class="el-button" :disabled="loading || disabled" @click="$emit(\'click\', $event)"><slot/></button>',
    props: ['loading', 'type', 'link', 'disabled', 'icon', 'size', 'plain'],
    emits: ['click'],
  },
  ElIcon: { template: '<span><slot/></span>' },
  Plus: { template: '<span>+</span>' },
  ElDialog: {
    template: '<div v-if="modelValue" class="el-dialog"><slot/><div class="el-dialog__footer"><slot name="footer"/></div></div>',
    props: ['modelValue', 'title', 'width', 'closeOnClickModal', 'destroyOnClose'],
  },
  ElAlert: {
    template: '<div class="el-alert"><slot/></div>',
    props: ['type', 'closable', 'showIcon'],
  },
  ElForm: { template: '<div class="el-form"><slot/></div>', props: ['model', 'labelWidth'] },
  ElFormItem: { template: '<div class="el-form-item"><slot/></div>', props: ['label', 'required'] },
  ElInput: {
    template: '<input class="el-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'placeholder', 'type', 'rows', 'disabled'],
    emits: ['update:modelValue'],
  },
}

const baseRole = {
  id: 7,
  code: 'bid-projectLeader',
  name: '投标项目负责人',
  description: '立项发起人',
  enabled: true,
  isSystem: true,
  userCount: 3,
  dataScope: 'self',
  menuPermissions: ['dashboard', 'bidding'],
  allowedProjects: [],
  allowedDepts: []
}

function mountPanel({ syncOssHandler } = {}) {
  const handlers = {
    saveHandler: vi.fn(),
    toggleHandler: vi.fn(),
    resetHandler: vi.fn(),
    syncOssHandler: syncOssHandler ?? vi.fn(),
  }
  const wrapper = mount(RoleManagementPanel, {
    props: { roles: [baseRole], ...handlers },
    global: { stubs, directives: { loading: {} } }
  })
  return { wrapper, handlers }
}

function findButtonByText(wrapper, text) {
  return wrapper.findAll('button').find((b) => b.text().trim() === text)
}

describe('RoleManagementPanel - OSS menu sync', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders a 从OSS同步 button per role row', () => {
    const { wrapper } = mountPanel()
    expect(findButtonByText(wrapper, '从OSS同步')).toBeTruthy()
  })

  it('opens the sync dialog with the role name prefilled when clicking 从OSS同步', async () => {
    const { wrapper } = mountPanel()
    await findButtonByText(wrapper, '从OSS同步').trigger('click')
    await flushPromises()

    expect(wrapper.find('.el-dialog').exists()).toBe(true)
    expect(wrapper.text()).toContain('投标项目负责人')
  })

  it('warns and does not call handler when job number is empty on confirm', async () => {
    const { wrapper, handlers } = mountPanel()
    await findButtonByText(wrapper, '从OSS同步').trigger('click')
    await flushPromises()

    await findButtonByText(wrapper, '同步').trigger('click')
    await flushPromises()

    expect(ElMessage.warning).toHaveBeenCalledWith('请填写 OSS 工号')
    expect(handlers.syncOssHandler).not.toHaveBeenCalled()
  })

  it('calls syncOssHandler with the role and trimmed job number on confirm', async () => {
    const { wrapper, handlers } = mountPanel()
    await findButtonByText(wrapper, '从OSS同步').trigger('click')
    await flushPromises()

    await wrapper.find('.el-dialog input').setValue('  08402  ')
    await flushPromises()

    await findButtonByText(wrapper, '同步').trigger('click')
    await flushPromises()

    expect(handlers.syncOssHandler).toHaveBeenCalledTimes(1)
    expect(handlers.syncOssHandler).toHaveBeenCalledWith(baseRole, '08402')
  })

  it('shows an error message and keeps the dialog open when the handler rejects', async () => {
    const syncOssHandler = vi.fn().mockRejectedValue(new Error('OSS timeout'))
    const { wrapper } = mountPanel({ syncOssHandler })

    await findButtonByText(wrapper, '从OSS同步').trigger('click')
    await flushPromises()

    await wrapper.find('.el-dialog input').setValue('08402')
    await flushPromises()

    await findButtonByText(wrapper, '同步').trigger('click')
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledWith('OSS timeout')
    expect(wrapper.find('.el-dialog').exists()).toBe(true)
  })
})
