// Input: mounted composable with onSave callback
// Output: useDeliverableUpload dialog/file/form management coverage
// Pos: src/composables/ - useDeliverableUpload unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn(), success: vi.fn() },
}))

import { useDeliverableUpload } from './useDeliverableUpload.js'

function mountWith(onSave) {
  let captured
  const Harness = {
    template: '<div />',
    setup() {
      captured = useDeliverableUpload({ onSave })
      return {}
    }
  }
  const wrapper = mount(Harness)
  return { wrapper, getState: () => captured }
}

describe('useDeliverableUpload', () => {
  let onSave

  beforeEach(() => {
    onSave = vi.fn()
  })

  it('returns correct initial state', () => {
    const { getState } = mountWith(onSave)
    expect(getState().dialogVisible.value).toBe(false)
    expect(getState().currentTask.value).toBeNull()
    expect(getState().fileList.value).toEqual([])
    expect(getState().form.value).toEqual({ name: '', type: 'document', file: null })
    expect(getState().saving.value).toBe(false)
  })

  describe('openDialog', () => {
    it('sets currentTask, resets form, and opens dialog', async () => {
      const task = { id: 1, name: 'test', projectId: 'p1' }
      const { getState } = mountWith(onSave)

      // mutate form first
      getState().form.value = { name: 'old', type: 'technical', file: new File([''], 'old.txt') }
      getState().fileList.value = [{ name: 'old.txt' }]

      getState().openDialog(task)
      await nextTick()

      expect(getState().currentTask.value).toEqual(task)
      expect(getState().form.value).toEqual({ name: '', type: 'document', file: null })
      expect(getState().fileList.value).toEqual([])
      expect(getState().dialogVisible.value).toBe(true)
    })
  })

  describe('handleFileChange', () => {
    it('sets form.file from the raw file', () => {
      const { getState } = mountWith(onSave)
      const file = new File(['content'], 'doc.docx')
      getState().handleFileChange({ raw: file })
      expect(getState().form.value.file).toBe(file)
    })
  })

  describe('save', () => {
    it('warns and does not call onSave when name is empty', async () => {
      const { ElMessage } = await import('element-plus')
      const { getState } = mountWith(onSave)
      getState().currentTask.value = { id: 1 }
      getState().form.value.name = ''

      await getState().save()
      await flushPromises()

      expect(onSave).not.toHaveBeenCalled()
      expect(ElMessage.warning).toHaveBeenCalledWith('请填写交付物名称')
      expect(getState().saving.value).toBe(false)
    })

    it('warns and does not call onSave when currentTask is null', async () => {
      const { ElMessage } = await import('element-plus')
      const { getState } = mountWith(onSave)
      getState().form.value.name = 'my doc'

      await getState().save()
      await flushPromises()

      expect(onSave).not.toHaveBeenCalled()
      expect(ElMessage.warning).toHaveBeenCalledWith('请填写交付物名称')
      expect(getState().saving.value).toBe(false)
    })

    it('calls onSave with correct args on success, shows success, closes dialog and resets form', async () => {
      onSave.mockResolvedValue()
      const { ElMessage } = await import('element-plus')
      const { getState } = mountWith(onSave)
      const task = { id: 7, projectId: 'p1' }
      const file = new File(['data'], 'plan.pdf')
      getState().openDialog(task)
      getState().form.value.name = '技术方案'
      getState().form.value.type = 'technical'
      getState().handleFileChange({ raw: file })
      await nextTick()

      await getState().save()
      await flushPromises()

      expect(onSave).toHaveBeenCalledTimes(1)
      expect(onSave).toHaveBeenCalledWith(task, { name: '技术方案', type: 'technical', file })
      expect(ElMessage.success).toHaveBeenCalledWith('交付物已保存')
      expect(getState().dialogVisible.value).toBe(false)
      expect(getState().form.value).toEqual({ name: '', type: 'document', file: null })
      expect(getState().fileList.value).toEqual([])
      expect(getState().saving.value).toBe(false)
    })

    it('shows error when onSave rejects', async () => {
      onSave.mockRejectedValue(new Error('网络错误'))
      const { ElMessage } = await import('element-plus')
      const { getState } = mountWith(onSave)
      getState().openDialog({ id: 1 })
      getState().form.value.name = 'doc'
      await nextTick()

      await getState().save()
      await flushPromises()

      expect(getState().dialogVisible.value).toBe(true)
      expect(getState().saving.value).toBe(false)
      expect(ElMessage.error).toHaveBeenCalledWith('网络错误')
    })

    it('sets saving true during save and false after', async () => {
      let resolveSave
      onSave.mockReturnValue(new Promise((resolve) => { resolveSave = resolve }))
      const { getState } = mountWith(onSave)
      getState().openDialog({ id: 1 })
      getState().form.value.name = 'doc'
      await nextTick()

      const savePromise = getState().save()
      expect(getState().saving.value).toBe(true)

      resolveSave()
      await flushPromises()
      expect(getState().saving.value).toBe(false)
    })
  })
})