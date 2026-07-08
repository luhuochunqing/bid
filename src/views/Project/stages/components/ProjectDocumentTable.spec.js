import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'

const getDocumentsMock = vi.hoisted(() => vi.fn())
const uploadDocumentMock = vi.hoisted(() => vi.fn())
const deleteDocumentMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/modules/projects.js', () => ({
  projectsApi: {
    getDocuments: getDocumentsMock,
    uploadDocument: uploadDocumentMock,
    deleteDocument: deleteDocumentMock,
  },
}))

vi.mock('@/utils/download.js', () => ({
  downloadWithFilename: vi.fn(),
}))

// 只 mock ElMessage / ElMessageBox（避免调用真实通知 API），其余 element-plus 用真实组件
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
    ElMessageBox: { confirm: vi.fn() },
  }
})

import ProjectDocumentTable from './ProjectDocumentTable.vue'

function mountTable(props = {}) {
  return mount(ProjectDocumentTable, {
    props: { projectId: 1, ...props },
    global: { plugins: [ElementPlus] },
  })
}

function generateDocs(count) {
  return Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    name: `doc-${i + 1}.pdf`,
    uploader: '张三',
    createdAt: '2026-06-17T10:00:00',
  }))
}

describe('ProjectDocumentTable — serial number before file name and pagination', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDocumentsMock.mockReset()
    uploadDocumentMock.mockReset()
    deleteDocumentMock.mockReset()
  })

  it('renders serial number before document name on the first page', async () => {
    getDocumentsMock.mockResolvedValue({ success: true, data: generateDocs(1) })
    const wrapper = mountTable()
    await flushPromises()

    expect(wrapper.text()).toContain('1. doc-1.pdf')
  })

  it('shows up to 5 rows per page and pagination when there are more than 5 documents', async () => {
    getDocumentsMock.mockResolvedValue({ success: true, data: generateDocs(6) })
    const wrapper = mountTable()
    await flushPromises()

    expect(wrapper.findAll('.el-table__row').length).toBe(5)
    expect(wrapper.find('.el-pagination').exists()).toBe(true)
  })

  it('does not render pagination when there are 5 or fewer documents', async () => {
    getDocumentsMock.mockResolvedValue({ success: true, data: generateDocs(5) })
    const wrapper = mountTable()
    await flushPromises()

    expect(wrapper.findAll('.el-table__row').length).toBe(5)
    expect(wrapper.find('.el-pagination').exists()).toBe(false)
  })

  it('switches to page 2 and shows continuous serial number', async () => {
    getDocumentsMock.mockResolvedValue({ success: true, data: generateDocs(6) })
    const wrapper = mountTable()
    await flushPromises()

    const pagination = wrapper.findComponent({ name: 'ElPagination' })
    pagination.vm.$emit('update:current-page', 2)
    await flushPromises()
    await nextTick()

    const rows = wrapper.findAll('.el-table__row')
    expect(rows.length).toBe(1)
    expect(rows.at(0).text()).toContain('6. doc-6.pdf')
  })

  it('returns to previous page when current page becomes empty after deletion', async () => {
    getDocumentsMock.mockResolvedValue({ success: true, data: generateDocs(6) })
    deleteDocumentMock.mockResolvedValue({ success: true })

    // CO-558: 删除按钮默认不可见（canDelete=false），本用例聚焦分页回退，显式开启删除权限
    const wrapper = mountTable({ canDelete: true })
    await flushPromises()

    const pagination = wrapper.findComponent({ name: 'ElPagination' })
    pagination.vm.$emit('update:current-page', 2)
    await flushPromises()
    await nextTick()

    // 模拟删除成功后，重新拉取列表时只剩 5 条
    getDocumentsMock.mockResolvedValue({ success: true, data: generateDocs(5) })

    // 模拟用户点击第二页唯一行的删除按钮
    const deleteButton = wrapper.findAll('.el-button').find((btn) => btn.text().includes('删除'))
    expect(deleteButton).toBeDefined()

    await deleteButton.trigger('click')
    await flushPromises()
    await nextTick()

    // 删除后当前页（第 2 页）变空，应自动回到第 1 页，并显示 5 行
    expect(wrapper.findAll('.el-table__row').length).toBe(5)
    expect(wrapper.text()).toContain('1. doc-1.pdf')
  })
})

// CO-558: 下载/删除按钮按角色矩阵通过 canDownload/canDelete props 控制
describe('ProjectDocumentTable — CO-558 download/delete permission props', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDocumentsMock.mockReset()
    uploadDocumentMock.mockReset()
    deleteDocumentMock.mockReset()
    getDocumentsMock.mockResolvedValue({ success: true, data: generateDocs(1) })
  })

  it('hides the delete button by default (canDelete=false)', async () => {
    const wrapper = mountTable()
    await flushPromises()

    const opColumn = wrapper.findAll('.el-table__row').at(0)
    expect(opColumn?.text()).not.toContain('删除')
  })

  it('shows the delete button when canDelete=true', async () => {
    const wrapper = mountTable({ canDelete: true })
    await flushPromises()

    const opColumn = wrapper.findAll('.el-table__row').at(0)
    expect(opColumn?.text()).toContain('删除')
  })

  it('hides the download button when canDownload=false', async () => {
    const wrapper = mountTable({ canDownload: false })
    await flushPromises()

    const opColumn = wrapper.findAll('.el-table__row').at(0)
    expect(opColumn?.text()).not.toContain('下载')
  })

  it('shows the download button when canDownload=true (default)', async () => {
    const wrapper = mountTable()
    await flushPromises()

    const opColumn = wrapper.findAll('.el-table__row').at(0)
    expect(opColumn?.text()).toContain('下载')
  })

  it('hides export but keeps upload when canDownload=false (矩阵：全员可上传)', async () => {
    const wrapper = mountTable({ canDownload: false })
    await flushPromises()

    // CO-558 + 矩阵 §2.3.3：导出与下载同权（隐藏），上传全员可见（保留）
    const actions = wrapper.find('.doc-actions')
    expect(actions.exists()).toBe(true)
    expect(actions.text()).not.toContain('导出')
    expect(actions.text()).toContain('上传')
  })

  it('shows both export and upload when canDownload=true', async () => {
    const wrapper = mountTable({ canDownload: true })
    await flushPromises()

    const actions = wrapper.find('.doc-actions')
    expect(actions.exists()).toBe(true)
    expect(actions.text()).toContain('导出')
    expect(actions.text()).toContain('上传')
  })
})
