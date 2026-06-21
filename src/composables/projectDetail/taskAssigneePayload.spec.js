import { describe, it, expect, vi } from 'vitest'
import {
  createTaskAssigneePayload,
  normalizeTaskAttachmentFiles,
  createTaskAttachmentPayload,
  uploadTaskAttachments,
  uploadTaskAttachmentsWithFallback,
} from './taskAssigneePayload.js'

describe('taskAssigneePayload', () => {
  describe('createTaskAssigneePayload', () => {
    it('prefers explicit form values over store defaults', () => {
      const data = {
        assigneeId: 42,
        owner: '李四',
        assigneeDeptCode: 'D1',
        assigneeDeptName: '技术部',
        assigneeRoleCode: 'R1',
        assigneeRoleName: '工程师',
      }
      const userStore = { currentUser: { id: 1 }, userName: '张三' }
      expect(createTaskAssigneePayload(data, userStore)).toEqual({
        assigneeId: 42,
        assigneeName: '李四',
        assigneeDeptCode: 'D1',
        assigneeDeptName: '技术部',
        assigneeRoleCode: 'R1',
        assigneeRoleName: '工程师',
      })
    })

    it('falls back to store values when form data is empty', () => {
      const userStore = { currentUser: { id: 7 }, userName: '王五' }
      expect(createTaskAssigneePayload({}, userStore)).toEqual({
        assigneeId: 7,
        assigneeName: '王五',
        assigneeDeptCode: '',
        assigneeDeptName: '',
        assigneeRoleCode: '',
        assigneeRoleName: '',
      })
    })

    it('keeps null assigneeId when no value is provided', () => {
      expect(createTaskAssigneePayload()).toEqual({
        assigneeId: null,
        assigneeName: undefined,
        assigneeDeptCode: '',
        assigneeDeptName: '',
        assigneeRoleCode: '',
        assigneeRoleName: '',
      })
    })
  })

  describe('normalizeTaskAttachmentFiles', () => {
    it('extracts raw/file wrappers', () => {
      const file1 = new File(['a'], 'a.pdf')
      const file2 = new File(['b'], 'b.pdf')
      expect(normalizeTaskAttachmentFiles([{ raw: file1 }, { file: file2 }])).toEqual([file1, file2])
    })

    it('returns plain files as-is', () => {
      const file = new File(['c'], 'c.pdf')
      expect(normalizeTaskAttachmentFiles([file])).toEqual([file])
    })

    it('filters out falsy items', () => {
      expect(normalizeTaskAttachmentFiles([null, undefined, ''])).toEqual([])
    })
  })

  describe('createTaskAttachmentPayload', () => {
    it('builds payload with file metadata', () => {
      const file = new File(['d'], 'd.pdf')
      const userStore = { currentUser: { id: 9 }, userName: '赵六' }
      expect(createTaskAttachmentPayload(file, userStore)).toEqual({
        name: 'd.pdf',
        documentCategory: 'TASK_ATTACHMENT',
        file,
        uploaderId: 9,
        uploaderName: '赵六',
      })
    })

    it('uses default name when file name is missing', () => {
      expect(createTaskAttachmentPayload({}, {})).toEqual({
        name: '任务附件',
        documentCategory: 'TASK_ATTACHMENT',
        file: {},
        uploaderId: null,
        uploaderName: undefined,
      })
    })
  })

  describe('uploadTaskAttachments', () => {
    it('uploads files and updates task attachments', async () => {
      const saved = { id: 100, name: 'saved.pdf' }
      const uploadTaskAttachment = vi.fn().mockResolvedValue(saved)
      const projectStore = { uploadTaskAttachment }
      const file = new File(['x'], 'x.pdf')
      const task = { id: 1, attachments: [] }

      await uploadTaskAttachments(task, [file], { projectStore, projectId: 'p1', userStore: {} })

      expect(uploadTaskAttachment).toHaveBeenCalledWith('p1', 1, expect.objectContaining({ name: 'x.pdf' }))
      expect(task.attachments).toEqual([saved])
    })

    it('skips attachments when uploadTaskAttachment returns falsy', async () => {
      const uploadTaskAttachment = vi.fn().mockResolvedValue(null)
      const projectStore = { uploadTaskAttachment }
      const task = { id: 1, attachments: [] }

      await uploadTaskAttachments(task, [new File(['x'], 'x.pdf')], { projectStore, projectId: 'p1', userStore: {} })

      expect(task.attachments).toEqual([])
    })

    it('deduplicates attachments by id', async () => {
      const saved = { id: 100, name: 'new.pdf' }
      const uploadTaskAttachment = vi.fn().mockResolvedValue(saved)
      const projectStore = { uploadTaskAttachment }
      const task = { id: 1, attachments: [{ id: 100, name: 'old.pdf' }] }

      await uploadTaskAttachments(task, [new File(['x'], 'x.pdf')], { projectStore, projectId: 'p1', userStore: {} })

      expect(task.attachments).toEqual([saved])
    })
  })

  describe('uploadTaskAttachmentsWithFallback', () => {
    it('does nothing when there are no attachments', async () => {
      const message = { warning: vi.fn() }
      await expect(uploadTaskAttachmentsWithFallback({}, [], {}, 'msg', message)).resolves.toBe(true)
      expect(message.warning).not.toHaveBeenCalled()
    })

    it('warns but does not throw when upload fails', async () => {
      const uploadTaskAttachment = vi.fn().mockRejectedValue(new Error('network'))
      const projectStore = { uploadTaskAttachment }
      const message = { warning: vi.fn() }
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
      const task = { id: 1 }

      await expect(
        uploadTaskAttachmentsWithFallback(
          task,
          [new File(['x'], 'x.pdf')],
          { projectStore, projectId: 'p1', userStore: {} },
          '保存成功但附件上传失败',
          message
        )
      ).resolves.toBe(false)

      expect(message.warning).toHaveBeenCalledWith('保存成功但附件上传失败')
      consoleSpy.mockRestore()
    })

    it('uploads successfully when no error occurs', async () => {
      const saved = { id: 100, name: 'saved.pdf' }
      const uploadTaskAttachment = vi.fn().mockResolvedValue(saved)
      const projectStore = { uploadTaskAttachment }
      const message = { warning: vi.fn() }
      const task = { id: 1, attachments: [] }

      await uploadTaskAttachmentsWithFallback(
        task,
        [new File(['x'], 'x.pdf')],
        { projectStore, projectId: 'p1', userStore: {} },
        'msg',
        message
      )

      expect(task.attachments).toEqual([saved])
      expect(message.warning).not.toHaveBeenCalled()
    })
  })
})
