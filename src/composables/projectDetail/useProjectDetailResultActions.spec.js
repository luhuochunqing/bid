import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useProjectDetailResultActions } from './useProjectDetailResultActions.js'
import { ApiCode } from '@/constants/apiCode'

describe('useProjectDetailResultActions', () => {
  it('handleUploadSuccess 在 code=SUCCESS 时写入 noticeFile 并提示成功', () => {
    const success = vi.fn()
    const error = vi.fn()
    const context = {
      state: { resultForm: ref({ noticeFile: '' }) },
      message: { success, error },
      navigation: { goToResultPage: vi.fn() },
    }
    const { handleUploadSuccess } = useProjectDetailResultActions(context)
    handleUploadSuccess({ code: ApiCode.SUCCESS, data: { url: '/files/b.pdf' } })
    expect(context.state.resultForm.value.noticeFile).toBe('/files/b.pdf')
    expect(success).toHaveBeenCalledWith('上传成功')
    expect(error).not.toHaveBeenCalled()
  })

  it('handleUploadSuccess 在 code!=SUCCESS 时提示错误', () => {
    const success = vi.fn()
    const error = vi.fn()
    const context = {
      state: { resultForm: ref({ noticeFile: '' }) },
      message: { success, error },
      navigation: { goToResultPage: vi.fn() },
    }
    const { handleUploadSuccess } = useProjectDetailResultActions(context)
    handleUploadSuccess({ code: 500, msg: '失败' })
    expect(error).toHaveBeenCalledWith('失败')
    expect(context.state.resultForm.value.noticeFile).toBe('')
    expect(success).not.toHaveBeenCalled()
  })
})
