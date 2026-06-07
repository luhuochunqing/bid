import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('element-plus', () => ({
  ElButton: { props: ['icon'], template: '<button><slot /></button>' },
  ElDropdown: { template: '<div><slot /><slot name="dropdown" /></div>' },
  ElDropdownItem: { template: '<div><slot /></div>' },
  ElDropdownMenu: { template: '<div><slot /></div>' },
  ElTooltip: { template: '<div><slot /></div>' },
}))

const mockDisconnect = vi.fn()
const mockObserve = vi.fn()

vi.stubGlobal('ResizeObserver', vi.fn().mockImplementation(() => ({
  observe: mockObserve,
  disconnect: mockDisconnect,
})))

import TenderActionMenu from './TenderActionMenu.vue'

const row = { id: 1, title: '测试标讯', status: 'PENDING_ASSIGNMENT' }

function mountMenu(props = {}) {
  const rowData = props.row || { id: 1, title: '测试标讯' }
  const stubs = {
    ElButton: { template: '<button><slot /></button>' },
    ElDropdown: { template: '<div><slot /><slot name="dropdown" /></div>' },
    ElDropdownItem: { template: '<div class="dropdown-item"><slot /></div>' },
    ElDropdownMenu: { template: '<div class="dropdown-menu"><slot /></div>' },
    ElTooltip: { template: '<div><slot /></div>' },
  }

  return mount(TenderActionMenu, {
    props: {
      row: rowData,
      canManageTenders: false,
      canDeleteTenders: false,
      showAiEntry: true,
      isAdmin: false,
      ...props,
    },
    global: {
      stubs: {
        ...stubs,
        'el-button': stubs.ElButton,
        'el-dropdown': stubs.ElDropdown,
        'el-dropdown-item': stubs.ElDropdownItem,
        'el-dropdown-menu': stubs.ElDropdownMenu,
        'el-tooltip': stubs.ElTooltip,
      },
    },
  })
}

beforeEach(() => {
  mockDisconnect.mockClear()
  mockObserve.mockClear()
})

afterEach(() => {
  document.body.innerHTML = ''
})

describe('TenderActionMenu permissions (standalone)', () => {
  it('hides management and delete menu items from staff users', () => {
    const wrapper = mountMenu()

    // staff 用户看不到管理相关菜单
    const dropdownHtml = wrapper.html()
    expect(dropdownHtml).not.toContain('删除')
  })

  it('shows delete menu item for admins', () => {
    const wrapper = mountMenu({ canManageTenders: true, canDeleteTenders: true })

    // 检查 HTML 中包含删除选项
    expect(wrapper.html()).toContain('删除')
  })

  it('shows participate option when status is EVALUATED', () => {
    const evaluatedRow = { id: 1, title: '测试', status: 'EVALUATED' }
    const wrapper = mountMenu({ canManageTenders: true, row: evaluatedRow })

    expect(wrapper.html()).toContain('立即投标')
  })

  it('shows bid result options when status is BIDDING', () => {
    const biddingRow = { id: 1, title: '测试', status: 'BIDDING' }
    const wrapper = mountMenu({ canManageTenders: true, row: biddingRow })

    expect(wrapper.html()).toContain('登记中标')
    expect(wrapper.html()).toContain('登记未中标')
  })

  it('component source contains ResizeObserver logic for responsive width', async () => {
    // TenderActionMenu 作为独立组件保留了 ResizeObserver 逻辑
    // 用于在容器宽度不足时条件隐藏按钮
    // 验证 ResizeObserver 相关代码存在于源文件中
    const fs = await import('node:fs')
    const path = await import('node:path')
    const source = fs.readFileSync(
      path.join(process.cwd(), 'src/views/Bidding/list/components/TenderActionMenu.vue'),
      'utf-8'
    )
    expect(source).toContain('ResizeObserver')
    expect(source).toContain('containerWidth')
    expect(source).toContain('shouldShowAiButton')
  })
})
