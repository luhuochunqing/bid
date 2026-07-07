import { describe, it, expect, vi } from 'vitest'
import {
  createTaskAssigneePayload,
  normalizeTaskAttachmentFiles,
  createTaskAttachmentPayload,
  uploadTaskAttachments,
  uploadTaskAttachmentsWithFallback,
  uploadTaskFilesWithFallback,
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

    // CO-519: 非 File 对象（如 el-upload 传入的 wrapper 对象）必须被过滤
    // 否则 FormData.set('file', wrapper) 会转成 "[object Object]"，导致后端 400
    it('filters out non-File objects (CO-519)', () => {
      const file = new File(['a'], 'a.pdf')
      const wrapperNoRaw = { name: 'b.pdf', status: 'ready', size: 100, uid: 1 }
      const wrapperRawUndefined = { raw: undefined, name: 'c.pdf' }
      const plainObject = { foo: 'bar' }
      const stringVal = '[object Object]'

      expect(normalizeTaskAttachmentFiles([file, wrapperNoRaw, wrapperRawUndefined, plainObject, stringVal]))
        .toEqual([file])
    })

    it('filters out empty-file wrappers leaving only valid files', () => {
      const validFile = new File(['content'], 'valid.pdf')
      const emptyWrapper = { raw: { name: 'empty.pdf' } }  // raw 不是 File/Blob
      expect(normalizeTaskAttachmentFiles([validFile, emptyWrapper])).toEqual([validFile])
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

    // CO-519: 用户选了文件但全部提取失败（非 File 对象），应明确抛错避免静默失败
    it('throws when all attachments are non-File objects (CO-519)', async () => {
      const uploadTaskAttachment = vi.fn()
      const projectStore = { uploadTaskAttachment }
      const task = { id: 1, attachments: [] }
      const nonFileWrapper = { name: 'broken.pdf', status: 'ready', size: 100, uid: 1 }

      await expect(
        uploadTaskAttachments(task, [nonFileWrapper], { projectStore, projectId: 'p1', userStore: {} })
      ).rejects.toThrow(/文件读取失败/)

      expect(uploadTaskAttachment).not.toHaveBeenCalled()
    })

    it('skips non-File objects but uploads valid ones (partial failure)', async () => {
      const saved = { id: 100, name: 'valid.pdf' }
      const uploadTaskAttachment = vi.fn().mockResolvedValue(saved)
      const projectStore = { uploadTaskAttachment }
      const task = { id: 1, attachments: [] }
      const validFile = new File(['x'], 'valid.pdf')
      const nonFileWrapper = { name: 'broken.pdf', status: 'ready' }

      await uploadTaskAttachments(task, [validFile, nonFileWrapper], { projectStore, projectId: 'p1', userStore: {} })

      expect(uploadTaskAttachment).toHaveBeenCalledTimes(1)
      expect(uploadTaskAttachment).toHaveBeenCalledWith('p1', 1, expect.objectContaining({ name: 'valid.pdf' }))
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

  // CO-529: 附件失败不应阻塞交付物上传和任务流转
  // 之前 results.every(Boolean) 导致附件失败时整体返回 false，任务无法流转
  // 修复后：交付物成功 = 整体成功，附件失败只给用户提示
  describe('uploadTaskFilesWithFallback (CO-529)', () => {
    function makeDeps({ uploadTaskAttachment, addDeliverable }) {
      return {
        projectStore: { uploadTaskAttachment, addDeliverable },
        projectId: 'p1',
        userStore: { currentUser: { id: 1 } },
      }
    }

    function makeMessages() {
      return {
        attachments: '任务已提交，但附件上传失败，请重试',
        deliverables: '任务已提交，但交付物上传失败，请重试',
      }
    }

    it('附件失败 + 交付物成功 → 整体成功，任务可流转', async () => {
      const uploadTaskAttachment = vi.fn().mockRejectedValue(new Error('附件网络错误'))
      const addDeliverable = vi.fn().mockResolvedValue({ id: 200, name: 'deliverable.pdf' })
      const deps = makeDeps({ uploadTaskAttachment, addDeliverable })
      const message = { warning: vi.fn() }
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
      const task = { id: 1, attachments: [], assigneeId: 1 }

      const result = await uploadTaskFilesWithFallback(
        task,
        { attachments: [new File(['x'], 'x.pdf')], deliverableFiles: [new File(['y'], 'y.pdf')] },
        deps,
        makeMessages(),
        message
      )

      // 关键：附件失败但交付物成功，整体应返回 true，任务可流转
      expect(result).toBe(true)
      // 附件失败的提示应展示给用户
      expect(message.warning).toHaveBeenCalledWith('任务已提交，但附件上传失败，请重试')
      // 交付物应被正常上传
      expect(addDeliverable).toHaveBeenCalled()
      consoleSpy.mockRestore()
    })

    it('附件成功 + 交付物成功 → 整体成功', async () => {
      const uploadTaskAttachment = vi.fn().mockResolvedValue({ id: 100, name: 'attachment.pdf' })
      const addDeliverable = vi.fn().mockResolvedValue({ id: 200, name: 'deliverable.pdf' })
      const deps = makeDeps({ uploadTaskAttachment, addDeliverable })
      const message = { warning: vi.fn() }
      const task = { id: 1, attachments: [], assigneeId: 1 }

      const result = await uploadTaskFilesWithFallback(
        task,
        { attachments: [new File(['x'], 'x.pdf')], deliverableFiles: [new File(['y'], 'y.pdf')] },
        deps,
        makeMessages(),
        message
      )

      expect(result).toBe(true)
      expect(message.warning).not.toHaveBeenCalled()
    })

    it('附件失败 + 交付物失败 → 整体失败，任务阻塞', async () => {
      const uploadTaskAttachment = vi.fn().mockRejectedValue(new Error('附件失败'))
      const addDeliverable = vi.fn().mockRejectedValue(new Error('交付物失败'))
      const deps = makeDeps({ uploadTaskAttachment, addDeliverable })
      const message = { warning: vi.fn() }
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
      const task = { id: 1, attachments: [], assigneeId: 1 }

      const result = await uploadTaskFilesWithFallback(
        task,
        { attachments: [new File(['x'], 'x.pdf')], deliverableFiles: [new File(['y'], 'y.pdf')] },
        deps,
        makeMessages(),
        message
      )

      // 两者都失败 → 阻塞任务流转
      expect(result).toBe(false)
      expect(message.warning).toHaveBeenCalledTimes(2)
      consoleSpy.mockRestore()
    })

    it('附件成功 + 交付物失败 → 整体失败，任务阻塞', async () => {
      const uploadTaskAttachment = vi.fn().mockResolvedValue({ id: 100, name: 'attachment.pdf' })
      const addDeliverable = vi.fn().mockRejectedValue(new Error('交付物失败'))
      const deps = makeDeps({ uploadTaskAttachment, addDeliverable })
      const message = { warning: vi.fn() }
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
      const task = { id: 1, attachments: [], assigneeId: 1 }

      const result = await uploadTaskFilesWithFallback(
        task,
        { attachments: [new File(['x'], 'x.pdf')], deliverableFiles: [new File(['y'], 'y.pdf')] },
        deps,
        makeMessages(),
        message
      )

      // 交付物失败 → 阻塞任务流转（交付物是任务完成的核心证据）
      expect(result).toBe(false)
      expect(message.warning).toHaveBeenCalledWith('任务已提交，但交付物上传失败，请重试')
      consoleSpy.mockRestore()
    })

    it('两者都没有文件 → 整体成功（无文件需要上传）', async () => {
      const uploadTaskAttachment = vi.fn()
      const addDeliverable = vi.fn()
      const deps = makeDeps({ uploadTaskAttachment, addDeliverable })
      const message = { warning: vi.fn() }
      const task = { id: 1, attachments: [], assigneeId: 1 }

      const result = await uploadTaskFilesWithFallback(
        task,
        { attachments: [], deliverableFiles: [] },
        deps,
        makeMessages(),
        message
      )

      expect(result).toBe(true)
      expect(uploadTaskAttachment).not.toHaveBeenCalled()
      expect(addDeliverable).not.toHaveBeenCalled()
    })
  })
})
