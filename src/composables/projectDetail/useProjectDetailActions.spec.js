import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { archive } = vi.hoisted(() => ({
  archive: vi.fn(),
}))

vi.mock('@/api', () => ({
  collaborationApi: {
    exports: {
      archive,
    },
  },
}))

import { useProjectDetailDocumentActions } from './useProjectDetailDocumentActions.js'
import { useProjectDetailTaskActions } from './useProjectDetailTaskActions.js'

async function flushMicrotasks() {
  await Promise.resolve()
  await Promise.resolve()
  await new Promise((resolve) => setTimeout(resolve, 0))
}

describe('project detail action regressions', () => {
  beforeEach(() => {
    archive.mockReset()
  })

  it('initializes the task list before inserting a newly created API task', async () => {
    const success = vi.fn()
    const error = vi.fn()
    const createTask = vi.fn().mockResolvedValue({
      success: true,
      data: {
        id: 301,
        name: '新增任务 1',
      },
    })
    const state = {
      project: ref({ id: 12, name: '测试项目' }),
      activities: ref([]),
      scoreDraftDialogVisible: ref(false),
      currentTask: ref(null),
    }

    const { handleAddTask } = useProjectDetailTaskActions({
      route: { params: { id: '12' } },
      userStore: { userName: '测试用户', currentUser: { id: 9 } },
      projectStore: {},
      projectsApi: { createTask },
      isApiProject: ref(true),
      message: { success, error },
      state,
      workflow: {},
    })

    handleAddTask()
    await flushMicrotasks()

    expect(createTask).toHaveBeenCalled()
    expect(state.project.value.tasks).toHaveLength(1)
    expect(state.project.value.tasks[0].name).toBe('新增任务 1')
    expect(success).toHaveBeenCalledWith('任务已新增')
    expect(error).not.toHaveBeenCalled()
  })

  it('falls back to the imported collaboration api when archiving project documents', async () => {
    const success = vi.fn()
    const error = vi.fn()
    const state = {
      project: ref({ id: 66, name: '归档项目', status: 'preparing' }),
      activities: ref([]),
    }

    archive.mockResolvedValue({
      success: true,
      data: {
        archiveReason: '项目资料整理完成，归档留存',
      },
    })

    const { handleArchiveDocuments } = useProjectDetailDocumentActions({
      route: { params: { id: '66' } },
      project: state.project,
      projectExpenses: ref([]),
      userStore: { userName: '归档人', currentUser: { id: 11 } },
      projectsApi: {},
      isApiProject: ref(true),
      message: { success, error },
      state,
    })

    await handleArchiveDocuments()

    expect(archive).toHaveBeenCalledWith('66', expect.objectContaining({
      archivedBy: 11,
      archivedByName: '归档人',
    }))
    expect(state.project.value.status).toBe('archived')
    expect(success).toHaveBeenCalledWith('项目资料归档成功')
    expect(error).not.toHaveBeenCalled()
  })
})
