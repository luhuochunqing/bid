import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/modules/projects.js', () => ({
  projectsApi: {
    updateTask: vi.fn(),
    updateTaskStatus: vi.fn(),
  }
}))
vi.mock('@/api/modules/taskDeliverables.js', () => ({
  createTaskDeliverable: vi.fn(),
}))

const mockUserState = { currentUser: { id: 1, name: '当前用户' } }
vi.mock('@/stores/user.js', () => ({
  useUserStore: () => mockUserState
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
  },
}))

import { useTaskActions } from './useTaskActions.js'
import { projectsApi } from '@/api/modules/projects.js'

function createMockTask(overrides = {}) {
  return {
    id: 1,
    title: '测试任务',
    status: 'TODO',
    assigneeId: 1,
    deliverables: [],
    completionNotes: '',
    ...overrides,
  }
}

describe('useTaskActions', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('isTaskAssignee', () => {
    it('returns true when current user is the assignee', () => {
      const { isTaskAssignee } = useTaskActions()
      const task = createMockTask({ assigneeId: 1 })
      expect(isTaskAssignee(task)).toBe(true)
    })

    it('returns false when current user is not the assignee', () => {
      const { isTaskAssignee } = useTaskActions()
      const task = createMockTask({ assigneeId: 999 })
      expect(isTaskAssignee(task)).toBe(false)
    })

    it('returns false when task has no assignee', () => {
      const { isTaskAssignee } = useTaskActions()
      const task = createMockTask({ assigneeId: null })
      expect(isTaskAssignee(task)).toBe(false)
    })
  })

  describe('hasDeliverable', () => {
    it('returns true when deliverables array has items', () => {
      const { hasDeliverable } = useTaskActions()
      const task = createMockTask({ deliverables: [{ id: 1, name: 'file.pdf' }] })
      expect(hasDeliverable(task)).toBe(true)
    })

    it('returns false when deliverables array is empty', () => {
      const { hasDeliverable } = useTaskActions()
      const task = createMockTask({ deliverables: [] })
      expect(hasDeliverable(task)).toBe(false)
    })

    it('returns true when deliverableUrl exists', () => {
      const { hasDeliverable } = useTaskActions()
      const task = createMockTask({ deliverableUrl: 'http://example.com/file.pdf' })
      expect(hasDeliverable(task)).toBe(true)
    })

    it('returns true when deliverableName exists', () => {
      const { hasDeliverable } = useTaskActions()
      const task = createMockTask({ deliverableName: 'file.pdf' })
      expect(hasDeliverable(task)).toBe(true)
    })

    it('returns false for null task', () => {
      const { hasDeliverable } = useTaskActions()
      expect(hasDeliverable(null)).toBe(false)
    })

    it('returns false for undefined task', () => {
      const { hasDeliverable } = useTaskActions()
      expect(hasDeliverable(undefined)).toBe(false)
    })
  })

  describe('openDeliverableUpload', () => {
    it('opens dialog and populates task data', () => {
      const { openDeliverableUpload, showSubmitDialog, submittingTask, submitNotes, deliverableFileList } = useTaskActions()
      const task = createMockTask({ completionNotes: '已完成' })
      openDeliverableUpload(task)
      expect(showSubmitDialog.value).toBe(true)
      expect(submittingTask.value).toStrictEqual(task)
      expect(submitNotes.value).toBe('已完成')
      expect(deliverableFileList.value).toEqual([])
    })

    it('builds file list from deliverableUrl', () => {
      const { openDeliverableUpload, deliverableFileList } = useTaskActions()
      const task = createMockTask({ deliverableUrl: 'http://example.com/file.pdf', deliverableName: 'file.pdf' })
      openDeliverableUpload(task)
      expect(deliverableFileList.value).toEqual([{ name: 'file.pdf', url: 'http://example.com/file.pdf' }])
    })
  })

  describe('openSubmitDialog', () => {
    it('opens dialog when task has deliverable', () => {
      const { openSubmitDialog, showSubmitDialog, submittingTask } = useTaskActions()
      const task = createMockTask({ deliverables: [{ id: 1 }] })
      openSubmitDialog(task)
      expect(showSubmitDialog.value).toBe(true)
      expect(submittingTask.value).toStrictEqual(task)
    })

    it('shows warning when task has no deliverable', async () => {
      const { openSubmitDialog, showSubmitDialog } = useTaskActions()
      const { ElMessage } = await import('element-plus')
      const task = createMockTask({ deliverables: [] })
      openSubmitDialog(task)
      expect(showSubmitDialog.value).toBe(false)
      expect(ElMessage.warning).toHaveBeenCalledWith('请先上传交付物')
    })
  })

  describe('closeSubmitDialog', () => {
    it('resets all dialog state', () => {
      const { closeSubmitDialog, openDeliverableUpload, showSubmitDialog, submittingTask, submitNotes, deliverableFileList } = useTaskActions()
      const task = createMockTask({ completionNotes: 'test' })
      openDeliverableUpload(task)
      closeSubmitDialog()
      expect(showSubmitDialog.value).toBe(false)
      expect(submittingTask.value).toBeNull()
      expect(submitNotes.value).toBe('')
      expect(deliverableFileList.value).toEqual([])
    })
  })

  describe('confirmSubmit', () => {
    it('submits task to REVIEW status with completionNotes', async () => {
      const { confirmSubmit, openDeliverableUpload, submitNotes, submittingTaskLoading } = useTaskActions({
        getProjectId: () => 42,
      })
      const task = createMockTask({ deliverables: [{ id: 1 }] })
      openDeliverableUpload(task)
      submitNotes.value = '已完成'
      await confirmSubmit()
      expect(projectsApi.updateTaskStatus).toHaveBeenCalledWith(42, 1, 'REVIEW', null, '已完成')
      expect(submittingTaskLoading.value).toBe(false)
    })

    it('shows warning when submitNotes is empty', async () => {
      const { ElMessage } = await import('element-plus')
      const { confirmSubmit, openDeliverableUpload } = useTaskActions({
        getProjectId: () => 42,
      })
      const task = createMockTask({ deliverables: [{ id: 1 }] })
      openDeliverableUpload(task)
      await confirmSubmit()
      expect(ElMessage.warning).toHaveBeenCalledWith('提交审核时必须填写完成情况')
      expect(projectsApi.updateTaskStatus).not.toHaveBeenCalled()
    })

    it('calls onSubmitted callback after success', async () => {
      const onSubmitted = vi.fn()
      const { confirmSubmit, openDeliverableUpload, submitNotes } = useTaskActions({
        getProjectId: () => 42,
        onSubmitted,
      })
      const task = createMockTask({ deliverables: [{ id: 1 }] })
      openDeliverableUpload(task)
      submitNotes.value = '完成了'
      await confirmSubmit()
      expect(onSubmitted).toHaveBeenCalledWith(task)
    })
  })
})
