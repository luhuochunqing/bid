import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useProjectDetailDocuments } from './useProjectDetailDocuments.js'

describe('useProjectDetailDocuments', () => {
  it('handleArchiveDocuments errors when not an API project', async () => {
    const error = vi.fn()
    const project = ref({ id: 'demo-1', name: 'Demo项目', documents: [] })
    const context = {
      project,
      route: { params: { id: 'demo-1' } },
      userStore: { userName: '测试用户' },
      projectsApi: {},
      isApiProject: ref(false),
      message: { success: vi.fn(), error },
      activities: ref([]),
      projectExpenses: ref([]),
    }

    const { handleArchiveDocuments } = useProjectDetailDocuments(context)
    await handleArchiveDocuments()
    expect(error).toHaveBeenCalledWith('当前项目仅支持通过 API 归档资料')
  })

  it('handleSetReminder errors when not an API project', async () => {
    const error = vi.fn()
    const project = ref({ id: 'demo-1', name: 'Demo项目' })
    const context = {
      project,
      route: { params: { id: 'demo-1' } },
      userStore: { userName: '测试用户' },
      projectsApi: {},
      isApiProject: ref(false),
      message: { success: vi.fn(), error },
      activities: ref([]),
      projectExpenses: ref([]),
    }

    const { handleSetReminder } = useProjectDetailDocuments(context)
    await handleSetReminder()
    expect(error).toHaveBeenCalledWith('当前项目仅支持通过 API 设置提醒')
  })

  it('handleAddDocument errors when not an API project', async () => {
    const error = vi.fn()
    const project = ref({ id: 'demo-1', name: 'Demo项目', documents: [] })
    const context = {
      project,
      route: { params: { id: 'demo-1' } },
      userStore: { userName: '测试用户' },
      projectsApi: {},
      isApiProject: ref(false),
      message: { success: vi.fn(), error },
      activities: ref([]),
      projectExpenses: ref([]),
    }

    const { handleAddDocument } = useProjectDetailDocuments(context)
    await handleAddDocument()
    expect(error).toHaveBeenCalledWith('当前项目仅支持通过 API 添加文档')
  })

  it('handleUpload errors when not an API project', async () => {
    const error = vi.fn()
    const project = ref({ id: 'demo-1', name: 'Demo项目', documents: [] })
    const file = new File(['test'], 'test.docx')
    const context = {
      project,
      route: { params: { id: 'demo-1' } },
      userStore: { userName: '测试用户' },
      projectsApi: {},
      isApiProject: ref(false),
      message: { success: vi.fn(), error },
      activities: ref([]),
      projectExpenses: ref([]),
    }

    const { handleUpload } = useProjectDetailDocuments(context)
    await handleUpload(file)
    expect(error).toHaveBeenCalledWith('当前项目仅支持通过 API 上传文档')
  })
})
